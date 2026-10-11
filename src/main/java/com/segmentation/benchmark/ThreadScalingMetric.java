package com.segmentation.benchmark;

/**
 * Median timing and scaling measurements for one configured pool size.
 */
public final class ThreadScalingMetric {

    public final String algorithmName;
    public final int batchSize;
    public final int width;
    public final int height;
    public final int configuredThreadCount;
    public final int actualPoolParallelism;
    public final double sequentialMedianMs;
    public final double parallelMedianMs;
    public final double speedup;
    public final double parallelEfficiencyPct;
    public final double throughputMegaPixelsPerSec;
    public final String accuracyMetrics;
    public final String failure;
    public final int measurementRepeats;

    public ThreadScalingMetric(String algorithmName, int batchSize, int width, int height,
                               int configuredThreadCount, int actualPoolParallelism,
                               double sequentialMedianMs, double parallelMedianMs,
                               double speedup, double parallelEfficiencyPct,
                               double throughputMegaPixelsPerSec, String accuracyMetrics,
                               String failure, int measurementRepeats) {
        this.algorithmName = algorithmName;
        this.batchSize = batchSize;
        this.width = width;
        this.height = height;
        this.configuredThreadCount = configuredThreadCount;
        this.actualPoolParallelism = actualPoolParallelism;
        this.sequentialMedianMs = sequentialMedianMs;
        this.parallelMedianMs = parallelMedianMs;
        this.speedup = speedup;
        this.parallelEfficiencyPct = parallelEfficiencyPct;
        this.throughputMegaPixelsPerSec = throughputMegaPixelsPerSec;
        this.accuracyMetrics = accuracyMetrics;
        this.failure = failure == null ? "" : failure;
        this.measurementRepeats = Math.max(1, measurementRepeats);
    }
}
