package com.segmentation;

import com.segmentation.algorithm.FelzenszwalbSegmentation;
import com.segmentation.algorithm.KMeansSegmentation;
import com.segmentation.algorithm.OtsuMultiThreshold;
import com.segmentation.algorithm.SLICSuperpixelSegmentation;
import com.segmentation.algorithm.SegmentationResult;
import com.segmentation.benchmark.BenchmarkMetric;
import com.segmentation.benchmark.BenchmarkRunner;
import com.segmentation.benchmark.BenchmarkReportExporter;
import com.segmentation.benchmark.ThreadScalingMetric;
import com.segmentation.util.ThreadConfig;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Dependency-free regression tests; run with test.bat.
 */
public final class ThreadConfigurationTests {

    private ThreadConfigurationTests() {}

    public static void main(String[] args) throws Exception {
        int originalThreads = ThreadConfig.getThreadCount();
        try {
            testThreadCountValidation();
            testPoolConfigurationAndMapping();
            testKMeansDeterminism();
            testSegmentationAlgorithms();
            testThreadScalingBenchmark();
            testMetricsAndCsvExport();
            testWorkerFailurePropagation();
            System.out.println("All thread configuration tests passed.");
        } finally {
            ThreadConfig.setThreadCount(originalThreads);
            ThreadConfig.shutdown();
        }
        check(ThreadConfig.isPoolShutdown(), "Configured pool should shut down cleanly.");
    }

    private static void testThreadCountValidation() {
        expectThrows(IllegalArgumentException.class, () -> ThreadConfig.setThreadCount(0));
        expectThrows(IllegalArgumentException.class, () -> ThreadConfig.setThreadCount(-2));
        expectThrows(IllegalArgumentException.class, () -> ThreadConfig.setThreadCount(32768));
    }

    private static void testPoolConfigurationAndMapping() {
        ThreadConfig.setThreadCount(2);
        check(ThreadConfig.getThreadCount() == 2, "Configured thread count should update.");
        check(ThreadConfig.getPoolParallelism() == 2, "Private pool parallelism should match configuration.");
        String worker = ThreadConfig.runInPool(() -> Thread.currentThread().getName());
        check(worker.startsWith("ForkJoinPool-"), "Task should run on the configured private ForkJoinPool.");

        List<Integer> mapped = ThreadConfig.parallelMap(Arrays.asList(1, 2, 3, 4), value -> value * value);
        check(mapped.equals(Arrays.asList(1, 4, 9, 16)), "Parallel mapping should preserve ordered results.");
        ThreadConfig.setThreadCount(1);
        check(ThreadConfig.getPoolParallelism() == 1, "Single-thread configuration should have parallelism one.");
        check(ThreadConfig.parallelMap(Arrays.asList(2, 3), value -> value + 1)
                .equals(Arrays.asList(3, 4)), "Single-thread mapping should produce correct results.");
    }

    private static void testKMeansDeterminism() {
        BufferedImage image = testImage(32, 32);
        ThreadConfig.setThreadCount(1);
        SegmentationResult sequential = new KMeansSegmentation(3, 6, false, 0.0).segment(image);
        ThreadConfig.setThreadCount(2);
        SegmentationResult parallel = new KMeansSegmentation(3, 6, false, 0.0).segment(image);
        check(Arrays.equals(sequential.getLabels(), parallel.getLabels()),
                "K-Means labels should be deterministic across pool sizes.");
    }

    private static void testSegmentationAlgorithms() {
        BufferedImage image = testImage(24, 24);
        SegmentationResult[] results = {
                new OtsuMultiThreshold(3).segment(image),
                new SLICSuperpixelSegmentation(8, 10.0, 3).segment(image),
                new FelzenszwalbSegmentation(0.5, 100.0, 5).segment(image)
        };
        for (SegmentationResult result : results) {
            check(result.getLabels().length == image.getWidth() * image.getHeight(),
                    result.getAlgorithmName() + " should label each pixel.");
            check(result.getNumSegments() > 0, result.getAlgorithmName() + " should return at least one segment.");
        }
    }

    private static void testMetricsAndCsvExport() throws Exception {
        BenchmarkMetric metric = new BenchmarkMetric(
                BenchmarkMetric.ExperimentType.DATASET_BATCH_SCALING,
                "test algorithm", 1000, 1000, 1, "Parallel (2T)",
                2000, 0, 1, 2, 1, 2, 2, "");
        check(Math.abs(metric.throughputMegaPixelsPerSec - 0.5) < 0.0001,
                "Throughput should be total megapixels divided by elapsed seconds.");

        File csv = File.createTempFile("thread-scaling-test", ".csv");
        File batchCsv = File.createTempFile("batch-metrics-test", ".csv");
        File report = File.createTempFile("thread-report-test", ".md");
        try {
            ThreadScalingMetric scale = new ThreadScalingMetric(
                    "SLIC (test, comma)", 4, 32, 32, 2, 2,
                    20, 12, 20.0 / 12.0, 83.333, 0.341, "N/A", "", 3);
            BenchmarkReportExporter.exportThreadScalingCsv(Arrays.asList(scale), csv);
            String contents = Files.readString(csv.toPath());
            check(contents.contains("ConfiguredThreadCount,ActualPoolParallelism"),
                    "Thread scaling CSV should include configured and actual parallelism.");
            check(contents.contains("\"SLIC (test, comma)\""),
                    "CSV exporter should quote algorithm names containing commas.");
            BenchmarkReportExporter.exportDatasetScalingCsv(Arrays.asList(metric), batchCsv);
            check(Files.readString(batchCsv.toPath()).contains("ConfiguredThreadCount,ActualPoolParallelism"),
                    "Existing batch CSV should append thread configuration columns.");
            BenchmarkReportExporter.exportFullMarkdownReport(Collections.emptyList(),
                    Collections.singletonList(metric), Collections.emptyMap(),
                    Arrays.asList(scale), 2, 2, report);
            String reportContents = Files.readString(report.toPath());
            check(reportContents.contains("Configured Thread Scaling")
                            || reportContents.contains("Investigation 3: Configured Thread Scaling"),
                    "Markdown report should include measured thread-scaling results.");
            check(reportContents.contains("SLIC (test, comma)"),
                    "Markdown report should include algorithm data.");
        } finally {
            Files.deleteIfExists(csv.toPath());
            Files.deleteIfExists(batchCsv.toPath());
            Files.deleteIfExists(report.toPath());
        }

    }

    private static void testThreadScalingBenchmark() throws Exception {
        Path temporaryOutput = Files.createTempDirectory("segmentation-thread-scale-test");
        try {
            List<ThreadScalingMetric> metrics = new BenchmarkRunner(temporaryOutput.toFile())
                    .runThreadScalingBenchmark(4, 32, 32, 1);
            check(!metrics.isEmpty(), "Thread scaling benchmark should test valid pool sizes.");
            for (ThreadScalingMetric metric : metrics) {
                check(metric.configuredThreadCount == metric.actualPoolParallelism,
                        "Configured count should match private pool parallelism.");
                check(metric.failure.isEmpty(), "Thread scaling run should complete: " + metric.failure);
                check(Double.isFinite(metric.speedup), "Successful thread scaling should record finite speedup.");
                check(Math.abs(metric.parallelEfficiencyPct
                                - metric.speedup / metric.configuredThreadCount * 100.0) < 0.01,
                        "Parallel efficiency should use configured pool size.");
            }
        } finally {
            Files.deleteIfExists(temporaryOutput);
        }
    }

    private static void testWorkerFailurePropagation() {
        expectThrows(IllegalStateException.class, () -> ThreadConfig.runInPool(() -> {
            throw new IllegalStateException("expected test failure");
        }));
    }

    private static BufferedImage testImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = x < width / 2 ? 0xFFCC3322 : 0xFF2266CC;
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }

    private static void expectThrows(Class<? extends Throwable> expected, Runnable action) {
        try {
            action.run();
        } catch (Throwable actual) {
            check(expected.isInstance(actual), "Expected " + expected.getSimpleName() + " but got " + actual);
            return;
        }
        throw new AssertionError("Expected " + expected.getSimpleName());
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
