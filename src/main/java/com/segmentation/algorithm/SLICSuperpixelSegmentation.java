package com.segmentation.algorithm;

import com.segmentation.util.ColorSpaceConverter;
import com.segmentation.util.ImageUtils;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SLIC (Simple Linear Iterative Clustering) Superpixel Segmentation.
 * Reference: Radhakrishna Achanta et al., "SLIC Superpixels Compared to State-of-the-Art Superpixel Methods",
 * IEEE TPAMI, 2012.
 *
 * Characteristics:
 * - Operates in 5D [L, a, b, x, y] space.
 * - Restricts distance search to a 2S x 2S neighborhood around cluster centers.
 * - Strictly O(N) linear time complexity with respect to pixel count N.
 * - Produces compact, regular superpixels adhering closely to image boundaries.
 * - Includes connected components relabeling to guarantee spatial connectivity.
 */
public class SLICSuperpixelSegmentation implements SegmentationAlgorithm {

    private final int desiredSuperpixels;
    private final double compactness; // m parameter (default 10.0 - 20.0)
    private final int iterations;

    public SLICSuperpixelSegmentation(int desiredSuperpixels) {
        this(desiredSuperpixels, 15.0, 8);
    }

    public SLICSuperpixelSegmentation(int desiredSuperpixels, double compactness, int iterations) {
        this.desiredSuperpixels = Math.max(4, desiredSuperpixels);
        this.compactness = Math.max(1.0, compactness);
        this.iterations = Math.max(3, iterations);
    }

    @Override
    public String getName() {
        return "SLIC Superpixels (k=" + desiredSuperpixels + ", m=" + (int) compactness + ")";
    }

    @Override
    public SegmentationResult segment(BufferedImage image) {
        long startMem = getUsedMemory();
        long startTime = System.currentTimeMillis();

        int width = image.getWidth();
        int height = image.getHeight();
        int numPixels = width * height;
        int[] rgbPixels = ImageUtils.getRgbPixels(image);

        // Precompute CIELAB image channels
        double[] lChannel = new double[numPixels];
        double[] aChannel = new double[numPixels];
        double[] bChannel = new double[numPixels];
        double[] tempLab = new double[3];

        for (int i = 0; i < numPixels; i++) {
            int rgb = rgbPixels[i];
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            ColorSpaceConverter.rgbToLab(r, g, b, tempLab);
            lChannel[i] = tempLab[0];
            aChannel[i] = tempLab[1];
            bChannel[i] = tempLab[2];
        }

        // 1. Initialize cluster centers at grid intervals S = sqrt(N / K)
        double S = Math.sqrt((double) numPixels / (double) desiredSuperpixels);
        int stepS = Math.max(2, (int) Math.round(S));

        List<double[]> centers = new ArrayList<>(); // each is [L, a, b, x, y]
        for (int y = stepS / 2; y < height; y += stepS) {
            for (int x = stepS / 2; x < width; x += stepS) {
                int idx = y * width + x;
                centers.add(new double[]{
                        lChannel[idx],
                        aChannel[idx],
                        bChannel[idx],
                        x,
                        y
                });
            }
        }

        int actualK = centers.size();
        double[][] centerArr = centers.toArray(new double[actualK][5]);

        // 2. Perturb cluster centers to lowest gradient in 3x3 window to avoid image edges/noise
        perturbCentersToLowestGradient(centerArr, lChannel, width, height);

        int[] labels = new int[numPixels];
        Arrays.fill(labels, -1);
        double[] distances = new double[numPixels];

        double mSqOverSSq = (compactness * compactness) / (S * S);

        // 3. Iterative Lloyd-like assignment restricted to 2S x 2S local window
        for (int iter = 0; iter < iterations; iter++) {
            Arrays.fill(distances, Double.MAX_VALUE);

            for (int k = 0; k < actualK; k++) {
                double cL = centerArr[k][0];
                double cA = centerArr[k][1];
                double cB = centerArr[k][2];
                int cX = (int) Math.round(centerArr[k][3]);
                int cY = (int) Math.round(centerArr[k][4]);

                int minX = Math.max(0, cX - stepS);
                int maxX = Math.min(width - 1, cX + stepS);
                int minY = Math.max(0, cY - stepS);
                int maxY = Math.min(height - 1, cY + stepS);

                for (int y = minY; y <= maxY; y++) {
                    int rowOffset = y * width;
                    for (int x = minX; x <= maxX; x++) {
                        int idx = rowOffset + x;

                        double dL = lChannel[idx] - cL;
                        double da = aChannel[idx] - cA;
                        double db = bChannel[idx] - cB;
                        double colorDistSq = dL * dL + da * da + db * db;

                        double dx = x - cX;
                        double dy = y - cY;
                        double spatialDistSq = dx * dx + dy * dy;

                        double totalDist = colorDistSq + spatialDistSq * mSqOverSSq;

                        if (totalDist < distances[idx]) {
                            distances[idx] = totalDist;
                            labels[idx] = k;
                        }
                    }
                }
            }

            // Update cluster centers
            double[][] sum = new double[actualK][5];
            int[] counts = new int[actualK];

            for (int y = 0; y < height; y++) {
                int rowOffset = y * width;
                for (int x = 0; x < width; x++) {
                    int idx = rowOffset + x;
                    int k = labels[idx];
                    if (k >= 0) {
                        counts[k]++;
                        sum[k][0] += lChannel[idx];
                        sum[k][1] += aChannel[idx];
                        sum[k][2] += bChannel[idx];
                        sum[k][3] += x;
                        sum[k][4] += y;
                    }
                }
            }

            for (int k = 0; k < actualK; k++) {
                if (counts[k] > 0) {
                    centerArr[k][0] = sum[k][0] / counts[k];
                    centerArr[k][1] = sum[k][1] / counts[k];
                    centerArr[k][2] = sum[k][2] / counts[k];
                    centerArr[k][3] = sum[k][3] / counts[k];
                    centerArr[k][4] = sum[k][4] / counts[k];
                }
            }
        }

        // 4. Enforce spatial connectivity (eliminate orphan fragments)
        int minSize = Math.max(1, (int) ((S * S) / 4.0));
        int finalSegments = enforceConnectivity(labels, width, height, actualK, minSize);

        long endTime = System.currentTimeMillis();
        long endMem = getUsedMemory();
        long elapsedMs = Math.max(1, endTime - startTime);
        long memUsed = Math.max(0, endMem - startMem);

        return new SegmentationResult(
                getName(),
                width,
                height,
                labels,
                finalSegments,
                elapsedMs,
                memUsed,
                iterations
        );
    }

    private void perturbCentersToLowestGradient(double[][] centers, double[] lChannel, int width, int height) {
        int[] dx = {-1, 0, 1, -1, 1, -1, 0, 1};
        int[] dy = {-1, -1, -1, 0, 0, 1, 1, 1};

        for (double[] center : centers) {
            int cx = (int) Math.round(center[3]);
            int cy = (int) Math.round(center[4]);
            double minGrad = Double.MAX_VALUE;
            int bestX = cx;
            int bestY = cy;

            for (int d = 0; d < 8; d++) {
                int nx = cx + dx[d];
                int ny = cy + dy[d];
                if (nx > 0 && nx < width - 1 && ny > 0 && ny < height - 1) {
                    double gx = lChannel[ny * width + (nx + 1)] - lChannel[ny * width + (nx - 1)];
                    double gy = lChannel[(ny + 1) * width + nx] - lChannel[(ny - 1) * width + nx];
                    double grad = gx * gx + gy * gy;
                    if (grad < minGrad) {
                        minGrad = grad;
                        bestX = nx;
                        bestY = ny;
                    }
                }
            }
            center[3] = bestX;
            center[4] = bestY;
            int bestIdx = bestY * width + bestX;
            center[0] = lChannel[bestIdx];
        }
    }

    /**
     * Relabels connected components and merges tiny orphan components smaller than minSize
     * into adjacent superpixels using BFS flood fill.
     */
    private int enforceConnectivity(int[] labels, int width, int height, int maxInitialLabels, int minSize) {
        int[] newLabels = new int[labels.length];
        Arrays.fill(newLabels, -1);

        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};

        int[] queueX = new int[labels.length];
        int[] queueY = new int[labels.length];

        int currentLabel = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int startIdx = y * width + x;
                if (newLabels[startIdx] >= 0) {
                    continue;
                }

                int origLabel = labels[startIdx];
                int head = 0;
                int tail = 0;

                queueX[tail] = x;
                queueY[tail] = y;
                tail++;
                newLabels[startIdx] = currentLabel;

                // Find adjacent label to merge with if component is too small
                int adjacentLabel = (currentLabel > 0) ? currentLabel - 1 : 0;

                while (head < tail) {
                    int qx = queueX[head];
                    int qy = queueY[head];
                    head++;

                    for (int d = 0; d < 4; d++) {
                        int nx = qx + dx[d];
                        int ny = qy + dy[d];
                        if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                            int nIdx = ny * width + nx;
                            if (labels[nIdx] == origLabel && newLabels[nIdx] < 0) {
                                newLabels[nIdx] = currentLabel;
                                queueX[tail] = nx;
                                queueY[tail] = ny;
                                tail++;
                            } else if (newLabels[nIdx] >= 0 && newLabels[nIdx] != currentLabel) {
                                adjacentLabel = newLabels[nIdx];
                            }
                        }
                    }
                }

                // If component size is below threshold, relabel to adjacent superpixel
                int componentSize = tail;
                if (componentSize < minSize) {
                    for (int i = 0; i < tail; i++) {
                        int px = queueX[i];
                        int py = queueY[i];
                        newLabels[py * width + px] = adjacentLabel;
                    }
                } else {
                    currentLabel++;
                }
            }
        }

        // Copy cleaned labels back
        System.arraycopy(newLabels, 0, labels, 0, labels.length);
        return Math.max(1, currentLabel);
    }

    private static long getUsedMemory() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }
}
