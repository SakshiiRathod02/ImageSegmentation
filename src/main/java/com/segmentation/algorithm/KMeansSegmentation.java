package com.segmentation.algorithm;

import com.segmentation.util.ColorSpaceConverter;
import com.segmentation.util.ImageUtils;

import java.awt.image.BufferedImage;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

/**
 * High-performance K-Means Clustering Segmentation.
 * Features:
 * - K-Means++ initialization for rapid convergence and superior cluster distribution.
 * - Perceptually uniform CIELAB color space + optional spatial coordinate weighting (XY).
 * - Multi-threaded parallel assignment step for multi-megapixel image acceleration.
 * - Flat primitive arrays to prevent GC pauses on large images.
 */
public class KMeansSegmentation implements SegmentationAlgorithm {

    private final int k;
    private final int maxIterations;
    private final double convergenceTolerance;
    private final boolean includeSpatial;
    private final double spatialWeight;
    private final long randomSeed;

    public KMeansSegmentation(int k, int maxIterations, boolean includeSpatial, double spatialWeight) {
        this(k, maxIterations, 0.05, includeSpatial, spatialWeight, 42L);
    }

    public KMeansSegmentation(int k, int maxIterations, double convergenceTolerance,
                              boolean includeSpatial, double spatialWeight, long randomSeed) {
        this.k = Math.max(2, k);
        this.maxIterations = Math.max(1, maxIterations);
        this.convergenceTolerance = convergenceTolerance;
        this.includeSpatial = includeSpatial;
        this.spatialWeight = spatialWeight;
        this.randomSeed = randomSeed;
    }

    @Override
    public String getName() {
        return "K-Means (k=" + k + (includeSpatial ? ", spatial=true" : "") + ")";
    }

    @Override
    public SegmentationResult segment(BufferedImage image) {
        long startMem = getUsedMemory();
        long startTime = System.currentTimeMillis();

        int width = image.getWidth();
        int height = image.getHeight();
        int numPixels = width * height;
        int[] rgbPixels = ImageUtils.getRgbPixels(image);

        // Feature vector dimension: 3 (L, a, b) or 5 (L, a, b, x_scaled, y_scaled)
        int dims = includeSpatial ? 5 : 3;
        double[][] features = new double[dims][numPixels];

        // Normalization factor for spatial coordinates
        double maxDim = Math.max(width, height);
        double spatialScale = (spatialWeight * 100.0) / maxDim;

        double[] tempLab = new double[3];
        for (int i = 0; i < numPixels; i++) {
            int rgb = rgbPixels[i];
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            ColorSpaceConverter.rgbToLab(r, g, b, tempLab);
            features[0][i] = tempLab[0]; // L*
            features[1][i] = tempLab[1]; // a*
            features[2][i] = tempLab[2]; // b*

            if (includeSpatial) {
                int px = i % width;
                int py = i / width;
                features[3][i] = px * spatialScale;
                features[4][i] = py * spatialScale;
            }
        }

        // Initialize centroids using K-Means++
        double[][] centroids = initializeCentroidsKMeansPlusPlus(features, numPixels, dims, k, randomSeed);

        int[] labels = new int[numPixels];
        double[] minDistances = new double[numPixels];
        int iterationsDone = 0;

        // Lloyd's iteration loop
        for (int iter = 0; iter < maxIterations; iter++) {
            iterationsDone++;

            // Parallel Assignment Step
            IntStream.range(0, numPixels).parallel().forEach(i -> {
                double bestDist = Double.MAX_VALUE;
                int bestCluster = 0;
                for (int c = 0; c < k; c++) {
                    double d = 0.0;
                    for (int dim = 0; dim < dims; dim++) {
                        double diff = features[dim][i] - centroids[c][dim];
                        d += diff * diff;
                    }
                    if (d < bestDist) {
                        bestDist = d;
                        bestCluster = c;
                    }
                }
                labels[i] = bestCluster;
                minDistances[i] = bestDist;
            });

            // Update Centroids Step
            double[][] sumFeatures = new double[k][dims];
            int[] counts = new int[k];

            for (int i = 0; i < numPixels; i++) {
                int c = labels[i];
                counts[c]++;
                for (int dim = 0; dim < dims; dim++) {
                    sumFeatures[c][dim] += features[dim][i];
                }
            }

            double maxShift = 0.0;
            Random rng = new Random(randomSeed + iter);

            for (int c = 0; c < k; c++) {
                if (counts[c] == 0) {
                    // Empty cluster: reinitialize to random pixel
                    int randIdx = rng.nextInt(numPixels);
                    for (int dim = 0; dim < dims; dim++) {
                        centroids[c][dim] = features[dim][randIdx];
                    }
                    continue;
                }

                double shiftDistSq = 0.0;
                for (int dim = 0; dim < dims; dim++) {
                    double newMean = sumFeatures[c][dim] / counts[c];
                    double diff = newMean - centroids[c][dim];
                    shiftDistSq += diff * diff;
                    centroids[c][dim] = newMean;
                }
                maxShift = Math.max(maxShift, Math.sqrt(shiftDistSq));
            }

            // Convergence check
            if (maxShift < convergenceTolerance) {
                break;
            }
        }

        long endTime = System.currentTimeMillis();
        long endMem = getUsedMemory();
        long elapsedMs = Math.max(1, endTime - startTime);
        long memUsed = Math.max(0, endMem - startMem);

        return new SegmentationResult(
                getName(),
                width,
                height,
                labels,
                k,
                elapsedMs,
                memUsed,
                iterationsDone
        );
    }

    /**
     * K-Means++ centroid initialization algorithm.
     */
    private double[][] initializeCentroidsKMeansPlusPlus(double[][] features, int numPixels, int dims, int k, long seed) {
        Random rng = new Random(seed);
        double[][] centroids = new double[k][dims];

        // 1. Choose first center uniformly at random
        int firstIdx = rng.nextInt(numPixels);
        for (int dim = 0; dim < dims; dim++) {
            centroids[0][dim] = features[dim][firstIdx];
        }

        double[] minDistSq = new double[numPixels];
        for (int i = 0; i < numPixels; i++) {
            minDistSq[i] = computeDistSq(features, i, centroids[0], dims);
        }

        // 2. Choose remaining k - 1 centers probabilistically proportional to D(x)^2
        for (int c = 1; c < k; c++) {
            double totalDistSq = 0.0;
            for (int i = 0; i < numPixels; i++) {
                totalDistSq += minDistSq[i];
            }

            double target = rng.nextDouble() * totalDistSq;
            double cumulative = 0.0;
            int chosenIdx = numPixels - 1;

            for (int i = 0; i < numPixels; i++) {
                cumulative += minDistSq[i];
                if (cumulative >= target) {
                    chosenIdx = i;
                    break;
                }
            }

            for (int dim = 0; dim < dims; dim++) {
                centroids[c][dim] = features[dim][chosenIdx];
            }

            // Update minDistSq with newly chosen centroid
            for (int i = 0; i < numPixels; i++) {
                double d = computeDistSq(features, i, centroids[c], dims);
                if (d < minDistSq[i]) {
                    minDistSq[i] = d;
                }
            }
        }

        return centroids;
    }

    private static double computeDistSq(double[][] features, int pixelIdx, double[] centroid, int dims) {
        double sum = 0.0;
        for (int d = 0; d < dims; d++) {
            double diff = features[d][pixelIdx] - centroid[d];
            sum += diff * diff;
        }
        return sum;
    }

    private static long getUsedMemory() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }
}
