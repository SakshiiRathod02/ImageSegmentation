package com.segmentation.algorithm;

import com.segmentation.util.ImageUtils;

import java.awt.image.BufferedImage;

/**
 * Multi-Level Otsu Thresholding Segmentation.
 * Maximizes between-class variance on image luminance histogram.
 * Highly computational, deterministic baseline for intensity-based segmentation.
 */
public class OtsuMultiThreshold implements SegmentationAlgorithm {

    private final int numClasses; // Number of output segmented classes (e.g., 3 or 4)

    public OtsuMultiThreshold(int numClasses) {
        this.numClasses = Math.max(2, Math.min(5, numClasses));
    }

    @Override
    public String getName() {
        return "Multi-Level Otsu (classes=" + numClasses + ")";
    }

    @Override
    public SegmentationResult segment(BufferedImage image) {
        long startMem = getUsedMemory();
        long startTime = System.currentTimeMillis();

        int width = image.getWidth();
        int height = image.getHeight();
        int numPixels = width * height;
        int[] rgb = ImageUtils.getRgbPixels(image);

        // Compute 256-bin grayscale intensity histogram
        int[] hist = new int[256];
        int[] gray = new int[numPixels];

        for (int i = 0; i < numPixels; i++) {
            int c = rgb[i];
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;
            int val = (int) (0.299 * r + 0.587 * g + 0.114 * b);
            gray[i] = val;
            hist[val]++;
        }

        // Find optimal thresholds maximizing between-class variance
        int[] thresholds = findOptimalThresholds(hist, numPixels, numClasses - 1);

        // Assign labels according to threshold bins
        int[] labels = new int[numPixels];
        for (int i = 0; i < numPixels; i++) {
            int val = gray[i];
            int label = 0;
            while (label < thresholds.length && val > thresholds[label]) {
                label++;
            }
            labels[i] = label;
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
                numClasses,
                elapsedMs,
                memUsed,
                1
        );
    }

    private int[] findOptimalThresholds(int[] hist, int totalPixels, int numThresholds) {
        if (numThresholds == 1) {
            // Standard 2-class Otsu
            double sumAll = 0;
            for (int t = 0; t < 256; t++) {
                sumAll += t * hist[t];
            }

            double sumB = 0;
            int wB = 0;
            double maxVariance = -1;
            int bestT = 128;

            for (int t = 0; t < 256; t++) {
                wB += hist[t];
                if (wB == 0) continue;
                int wF = totalPixels - wB;
                if (wF == 0) break;

                sumB += (double) t * hist[t];
                double mB = sumB / wB;
                double mF = (sumAll - sumB) / wF;

                double varianceBetween = (double) wB * (double) wF * (mB - mF) * (mB - mF);
                if (varianceBetween > maxVariance) {
                    maxVariance = varianceBetween;
                    bestT = t;
                }
            }
            return new int[]{bestT};
        }

        if (numThresholds == 2) {
            // 3-class Otsu
            double sumAll = 0;
            for (int t = 0; t < 256; t++) sumAll += t * hist[t];

            double maxVar = -1;
            int[] best = new int[]{85, 170};

            for (int t1 = 0; t1 < 254; t1++) {
                for (int t2 = t1 + 1; t2 < 255; t2++) {
                    int w0 = 0, w1 = 0, w2 = 0;
                    double s0 = 0, s1 = 0, s2 = 0;

                    for (int i = 0; i <= t1; i++) { w0 += hist[i]; s0 += i * hist[i]; }
                    for (int i = t1 + 1; i <= t2; i++) { w1 += hist[i]; s1 += i * hist[i]; }
                    for (int i = t2 + 1; i < 256; i++) { w2 += hist[i]; s2 += i * hist[i]; }

                    if (w0 == 0 || w1 == 0 || w2 == 0) continue;

                    double u0 = s0 / w0;
                    double u1 = s1 / w1;
                    double u2 = s2 / w2;
                    double uT = sumAll / totalPixels;

                    double var = w0 * (u0 - uT) * (u0 - uT) +
                                 w1 * (u1 - uT) * (u1 - uT) +
                                 w2 * (u2 - uT) * (u2 - uT);

                    if (var > maxVar) {
                        maxVar = var;
                        best[0] = t1;
                        best[1] = t2;
                    }
                }
            }
            return best;
        }

        // Default quantiles for >= 3 thresholds
        int[] quantiles = new int[numThresholds];
        for (int i = 0; i < numThresholds; i++) {
            quantiles[i] = (256 / (numThresholds + 1)) * (i + 1);
        }
        return quantiles;
    }

    private static long getUsedMemory() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }
}
