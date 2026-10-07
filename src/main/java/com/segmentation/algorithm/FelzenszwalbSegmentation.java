package com.segmentation.algorithm;

import com.segmentation.util.DisjointSet;
import com.segmentation.util.ImageUtils;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Efficient Graph-Based Image Segmentation.
 * Reference: Pedro F. Felzenszwalb and Daniel P. Huttenlocher,
 * "Efficient Graph-Based Image Segmentation", International Journal of Computer Vision (IJCV), 2004.
 *
 * Characteristics:
 * - Graph-theoretic formulation based on Minimum Spanning Tree (MST) and Kruskal's algorithm.
 * - Non-local adaptive threshold based on internal component variation.
 * - Time complexity O(E log E) dominated by edge sorting, where E ~ 4N.
 * - Captures perceptual boundaries while ignoring fine intra-region texture variations.
 */
public class FelzenszwalbSegmentation implements SegmentationAlgorithm {

    private final double sigma;    // Gaussian blur sigma (default: 0.5 - 0.8)
    private final double k;        // Threshold scale factor (larger k = larger regions, default: 300 - 500)
    private final int minSize;     // Minimum segment size (default: 50 - 100)

    public FelzenszwalbSegmentation() {
        this(0.5, 300.0, 50);
    }

    public FelzenszwalbSegmentation(double sigma, double k, int minSize) {
        this.sigma = sigma;
        this.k = Math.max(1.0, k);
        this.minSize = Math.max(1, minSize);
    }

    @Override
    public String getName() {
        return "Felzenszwalb Graph (k=" + (int) k + ", minSize=" + minSize + ")";
    }

    /**
     * Compact edge representation to minimize GC overhead.
     */
    private static class Edge implements Comparable<Edge> {
        final int u;
        final int v;
        final float weight;

        Edge(int u, int v, float weight) {
            this.u = u;
            this.v = v;
            this.weight = weight;
        }

        @Override
        public int compareTo(Edge o) {
            return Float.compare(this.weight, o.weight);
        }
    }

    @Override
    public SegmentationResult segment(BufferedImage image) {
        long startMem = getUsedMemory();
        long startTime = System.currentTimeMillis();

        int width = image.getWidth();
        int height = image.getHeight();
        int numPixels = width * height;

        // Apply Gaussian smoothing to RGB channels if sigma > 0
        float[][] smoothed = smoothImage(image, sigma);
        float[] rChannel = smoothed[0];
        float[] gChannel = smoothed[1];
        float[] bChannel = smoothed[2];

        // Construct 4-neighborhood graph edges (horizontal and vertical)
        int maxEdges = width * (height - 1) + (width - 1) * height + 2 * (width - 1) * (height - 1);
        Edge[] edges = new Edge[maxEdges];
        int edgeCount = 0;

        for (int y = 0; y < height; y++) {
            int rowOffset = y * width;
            for (int x = 0; x < width; x++) {
                int u = rowOffset + x;

                // Right neighbor (x + 1, y)
                if (x + 1 < width) {
                    int v = rowOffset + (x + 1);
                    edges[edgeCount++] = new Edge(u, v, colorDiff(rChannel, gChannel, bChannel, u, v));
                }
                // Down neighbor (x, y + 1)
                if (y + 1 < height) {
                    int v = (y + 1) * width + x;
                    edges[edgeCount++] = new Edge(u, v, colorDiff(rChannel, gChannel, bChannel, u, v));
                }
                // Down-Right diagonal (x + 1, y + 1)
                if (x + 1 < width && y + 1 < height) {
                    int v = (y + 1) * width + (x + 1);
                    edges[edgeCount++] = new Edge(u, v, colorDiff(rChannel, gChannel, bChannel, u, v));
                }
                // Down-Left diagonal (x - 1, y + 1)
                if (x - 1 >= 0 && y + 1 < height) {
                    int v = (y + 1) * width + (x - 1);
                    edges[edgeCount++] = new Edge(u, v, colorDiff(rChannel, gChannel, bChannel, u, v));
                }
            }
        }

        // Sort edges in ascending order of weight
        Arrays.sort(edges, 0, edgeCount);

        // Disjoint-set forest
        DisjointSet ds = new DisjointSet(numPixels);
        float[] internalDiff = new float[numPixels]; // Int(C) initialized to 0

        // Process edges (Algorithm 1)
        for (int i = 0; i < edgeCount; i++) {
            Edge e = edges[i];
            int rootU = ds.find(e.u);
            int rootV = ds.find(e.v);

            if (rootU != rootV) {
                float thresholdU = internalDiff[rootU] + (float) (k / ds.getSize(rootU));
                float thresholdV = internalDiff[rootV] + (float) (k / ds.getSize(rootV));

                if (e.weight <= Math.min(thresholdU, thresholdV)) {
                    ds.union(rootU, rootV);
                    int newRoot = ds.find(rootU);
                    internalDiff[newRoot] = e.weight;
                }
            }
        }

        // Post-processing: merge components smaller than minSize
        for (int i = 0; i < edgeCount; i++) {
            Edge e = edges[i];
            int rootU = ds.find(e.u);
            int rootV = ds.find(e.v);
            if (rootU != rootV) {
                if (ds.getSize(rootU) < minSize || ds.getSize(rootV) < minSize) {
                    ds.union(rootU, rootV);
                }
            }
        }

        // Map root IDs to contiguous compact segment labels [0..numSegments-1]
        int[] labels = new int[numPixels];
        Map<Integer, Integer> rootToLabel = new HashMap<>();
        int labelCounter = 0;

        for (int i = 0; i < numPixels; i++) {
            int root = ds.find(i);
            Integer assigned = rootToLabel.get(root);
            if (assigned == null) {
                assigned = labelCounter++;
                rootToLabel.put(root, assigned);
            }
            labels[i] = assigned;
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
                labelCounter,
                elapsedMs,
                memUsed,
                1
        );
    }

    private static float colorDiff(float[] r, float[] g, float[] b, int u, int v) {
        float dr = r[u] - r[v];
        float dg = g[u] - g[v];
        float db = b[u] - b[v];
        return (float) Math.sqrt(dr * dr + dg * dg + db * db);
    }

    /**
     * Applies separable 1D Gaussian kernel smoothing to avoid edge noise artifacts.
     */
    private static float[][] smoothImage(BufferedImage img, double sigma) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] rgb = ImageUtils.getRgbPixels(img);

        float[] r = new float[w * h];
        float[] g = new float[w * h];
        float[] b = new float[w * h];

        for (int i = 0; i < w * h; i++) {
            int c = rgb[i];
            r[i] = (c >> 16) & 0xFF;
            g[i] = (c >> 8) & 0xFF;
            b[i] = c & 0xFF;
        }

        if (sigma <= 0.01) {
            return new float[][]{r, g, b};
        }

        // 1D Gaussian filter kernel
        int radius = (int) Math.ceil(sigma * 3.0);
        float[] kernel = new float[radius * 2 + 1];
        float sum = 0.0f;
        for (int i = -radius; i <= radius; i++) {
            float val = (float) Math.exp(-(i * i) / (2.0 * sigma * sigma));
            kernel[i + radius] = val;
            sum += val;
        }
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] /= sum;
        }

        float[] rTemp = new float[w * h];
        float[] gTemp = new float[w * h];
        float[] bTemp = new float[w * h];

        // Horizontal pass
        for (int y = 0; y < h; y++) {
            int rowOffset = y * w;
            for (int x = 0; x < w; x++) {
                float rSum = 0, gSum = 0, bSum = 0;
                for (int k = -radius; k <= radius; k++) {
                    int kx = Math.max(0, Math.min(w - 1, x + k));
                    int idx = rowOffset + kx;
                    float weight = kernel[k + radius];
                    rSum += r[idx] * weight;
                    gSum += g[idx] * weight;
                    bSum += b[idx] * weight;
                }
                int target = rowOffset + x;
                rTemp[target] = rSum;
                gTemp[target] = gSum;
                bTemp[target] = bSum;
            }
        }

        // Vertical pass
        for (int y = 0; y < h; y++) {
            int rowOffset = y * w;
            for (int x = 0; x < w; x++) {
                float rSum = 0, gSum = 0, bSum = 0;
                for (int k = -radius; k <= radius; k++) {
                    int ky = Math.max(0, Math.min(h - 1, y + k));
                    int idx = ky * w + x;
                    float weight = kernel[k + radius];
                    rSum += rTemp[idx] * weight;
                    gSum += gTemp[idx] * weight;
                    bSum += bTemp[idx] * weight;
                }
                int target = rowOffset + x;
                r[target] = rSum;
                g[target] = gSum;
                b[target] = bSum;
            }
        }

        return new float[][]{r, g, b};
    }

    private static long getUsedMemory() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }
}
