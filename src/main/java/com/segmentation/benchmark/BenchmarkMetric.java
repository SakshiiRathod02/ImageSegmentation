package com.segmentation.benchmark;

/**
 * Data structure holding performance measurement results.
 */
public class BenchmarkMetric {

    public enum ExperimentType {
        RESOLUTION_SCALING,
        DATASET_BATCH_SCALING,
        ACCURACY_EVALUATION
    }

    public final ExperimentType experimentType;
    public final String algorithmName;
    public final int width;
    public final int height;
    public final double megaPixels;
    public final int batchSize;
    public final String executionMode; // "Sequential (1T)" or "Parallel (MT)"
    public final long totalTimeMs;
    public final double avgTimePerImageMs;
    public final double throughputMegaPixelsPerSec;
    public final double throughputImagesPerSec;
    public final double memoryMb;
    public final int numSegments;
    public final double speedupFactor;
    public final double parallelEfficiency;
    public final int configuredThreadCount;
    public final int actualPoolParallelism;
    public final String failure;
    public final String timingMethod;
    public final int measurementRepeats;

    public BenchmarkMetric(ExperimentType experimentType, String algorithmName,
                           int width, int height, int batchSize,
                           String executionMode, long totalTimeMs,
                           double memoryMb, int numSegments,
                           double speedupFactor, double parallelEfficiency) {
        this(experimentType, algorithmName, width, height, batchSize, executionMode,
                totalTimeMs, memoryMb, numSegments, speedupFactor, parallelEfficiency,
                1, 1, "");
    }

    public BenchmarkMetric(ExperimentType experimentType, String algorithmName,
                           int width, int height, int batchSize,
                           String executionMode, long totalTimeMs,
                           double memoryMb, int numSegments,
                           double speedupFactor, double parallelEfficiency,
                           int configuredThreadCount, int actualPoolParallelism,
                           String failure) {
        this(experimentType, algorithmName, width, height, batchSize, executionMode,
                totalTimeMs, memoryMb, numSegments, speedupFactor, parallelEfficiency,
                configuredThreadCount, actualPoolParallelism, failure,
                "single measured execution", 1);
    }

    public BenchmarkMetric(ExperimentType experimentType, String algorithmName,
                           int width, int height, int batchSize,
                           String executionMode, long totalTimeMs,
                           double memoryMb, int numSegments,
                           double speedupFactor, double parallelEfficiency,
                           int configuredThreadCount, int actualPoolParallelism,
                           String failure, String timingMethod, int measurementRepeats) {
        this.experimentType = experimentType;
        this.algorithmName = algorithmName;
        this.width = width;
        this.height = height;
        this.megaPixels = (double) (width * height) / 1_000_000.0;
        this.batchSize = batchSize;
        this.executionMode = executionMode;
        this.totalTimeMs = Math.max(1, totalTimeMs);
        this.avgTimePerImageMs = (double) this.totalTimeMs / Math.max(1, batchSize);
        this.throughputMegaPixelsPerSec = (megaPixels * batchSize) / (this.totalTimeMs / 1000.0);
        this.throughputImagesPerSec = (double) batchSize / (this.totalTimeMs / 1000.0);
        this.memoryMb = memoryMb;
        this.numSegments = numSegments;
        this.speedupFactor = speedupFactor;
        this.parallelEfficiency = parallelEfficiency;
        this.configuredThreadCount = configuredThreadCount;
        this.actualPoolParallelism = actualPoolParallelism;
        this.failure = failure == null ? "" : failure;
        this.timingMethod = timingMethod;
        this.measurementRepeats = Math.max(1, measurementRepeats);
    }
}
