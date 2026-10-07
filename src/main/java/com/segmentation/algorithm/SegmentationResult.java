package com.segmentation.algorithm;

import java.awt.image.BufferedImage;

/**
 * Encapsulates the results of an image segmentation execution,
 * including pixel labels, segment count, execution profiling metrics,
 * and helper methods for generating visual outputs.
 */
public class SegmentationResult {

    private final String algorithmName;
    private final int width;
    private final int height;
    private final int[] labels; // label for each pixel (row-major: y * width + x)
    private final int numSegments;
    private final long executionTimeMs;
    private final long memoryUsedBytes;
    private final int iterations;

    public SegmentationResult(String algorithmName, int width, int height,
                              int[] labels, int numSegments,
                              long executionTimeMs, long memoryUsedBytes,
                              int iterations) {
        this.algorithmName = algorithmName;
        this.width = width;
        this.height = height;
        this.labels = labels;
        this.numSegments = numSegments;
        this.executionTimeMs = executionTimeMs;
        this.memoryUsedBytes = memoryUsedBytes;
        this.iterations = iterations;
    }

    public String getAlgorithmName() {
        return algorithmName;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int[] getLabels() {
        return labels;
    }

    public int getNumSegments() {
        return numSegments;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public long getMemoryUsedBytes() {
        return memoryUsedBytes;
    }

    public int getIterations() {
        return iterations;
    }

    public double getThroughputMegaPixelsPerSec() {
        if (executionTimeMs <= 0) return 0.0;
        double megaPixels = (double) (width * height) / 1_000_000.0;
        return megaPixels / (executionTimeMs / 1000.0);
    }

    @Override
    public String toString() {
        return String.format("[%s] Res: %dx%d (%d px) | Segments: %d | Time: %d ms (%.2f MP/s) | Memory: %.2f MB | Iterations: %d",
                algorithmName, width, height, width * height, numSegments,
                executionTimeMs, getThroughputMegaPixelsPerSec(),
                memoryUsedBytes / (1024.0 * 1024.0), iterations);
    }
}
