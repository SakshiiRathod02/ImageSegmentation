package com.segmentation.benchmark;

import com.segmentation.dataset.GroundTruthEvaluator.EvaluationReport;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * Exporter for empirical benchmark results into CSV and detailed Markdown reports.
 */
public class BenchmarkReportExporter {

    public static void exportResolutionScalingCsv(List<BenchmarkMetric> metrics, File file) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Algorithm,Resolution,Pixels,MegaPixels,ExecutionTimeMs,ThroughputMPPerSec,MemoryMB,Segments,ConfiguredThreadCount,ActualPoolParallelism,Failure,TimingMethod,MeasurementRepeats");
            for (BenchmarkMetric m : metrics) {
                if (m.experimentType == BenchmarkMetric.ExperimentType.RESOLUTION_SCALING) {
                    pw.printf("%s,%dx%d,%d,%.3f,%d,%.3f,%.2f,%d,%d,%d,%s,%s,%d%n",
                            csv(m.algorithmName), m.width, m.height, m.width * m.height,
                            m.megaPixels, m.totalTimeMs, m.throughputMegaPixelsPerSec,
                            m.memoryMb, m.numSegments, m.configuredThreadCount,
                            m.actualPoolParallelism, csv(m.failure), csv(m.timingMethod),
                            m.measurementRepeats);
                }
            }
        }
    }

    public static void exportDatasetScalingCsv(List<BenchmarkMetric> metrics, File file) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Algorithm,BatchSize,ImageResolution,ExecutionMode,TotalTimeMs,AvgTimePerImageMs,ThroughputImagesPerSec,ThroughputMPPerSec,Speedup,ParallelEfficiencyPct,ConfiguredThreadCount,ActualPoolParallelism,Failure,TimingMethod,MeasurementRepeats");
            for (BenchmarkMetric m : metrics) {
                if (m.experimentType == BenchmarkMetric.ExperimentType.DATASET_BATCH_SCALING) {
                    pw.printf("%s,%d,%dx%d,%s,%d,%.2f,%.2f,%.2f,%.2f,%.1f%%,%d,%d,%s,%s,%d%n",
                            csv(m.algorithmName), m.batchSize, m.width, m.height,
                            csv(m.executionMode), m.totalTimeMs, m.avgTimePerImageMs,
                            m.throughputImagesPerSec, m.throughputMegaPixelsPerSec,
                            m.speedupFactor, m.parallelEfficiency * 100.0,
                            m.configuredThreadCount, m.actualPoolParallelism, csv(m.failure),
                            csv(m.timingMethod), m.measurementRepeats);
                }
            }
        }
    }

    public static void exportThreadScalingCsv(List<ThreadScalingMetric> metrics, File file) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Algorithm,BatchSize,Resolution,ConfiguredThreadCount,ActualPoolParallelism,SequentialMedianMs,ParallelMedianMs,Speedup,ParallelEfficiencyPct,ThroughputMPPerSec,AccuracyMetrics,Failure,TimingMethod,MeasurementRepeats");
            for (ThreadScalingMetric m : metrics) {
                pw.printf("%s,%d,%dx%d,%d,%d,%.3f,%.3f,%.4f,%.2f,%.3f,%s,%s,%s,%d%n",
                        csv(m.algorithmName), m.batchSize, m.width, m.height,
                        m.configuredThreadCount, m.actualPoolParallelism,
                        m.sequentialMedianMs, m.parallelMedianMs, m.speedup,
                        m.parallelEfficiencyPct, m.throughputMegaPixelsPerSec,
                        csv(m.accuracyMetrics), csv(m.failure), csv("median after warm-up"),
                        m.measurementRepeats);
            }
        }
    }

    public static void exportAccuracyCsv(Map<String, EvaluationReport> reports, File file) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Algorithm,MeanIoU,MeanDice,PixelAccuracyPct,BoundaryPrecision,BoundaryRecall,BoundaryF1");
            for (Map.Entry<String, EvaluationReport> entry : reports.entrySet()) {
                String algo = entry.getKey();
                EvaluationReport rep = entry.getValue();
                pw.printf("%s,%.4f,%.4f,%.2f%%,%.4f,%.4f,%.4f%n",
                        csv(algo), rep.meanIoU, rep.meanDice, rep.pixelAccuracy * 100.0,
                        rep.boundaryPrecision, rep.boundaryRecall, rep.boundaryF1);
            }
        }
    }

    public static void exportFullMarkdownReport(List<BenchmarkMetric> resMetrics,
                                                List<BenchmarkMetric> batchMetrics,
                                                Map<String, EvaluationReport> accuracyReports,
                                                List<ThreadScalingMetric> threadMetrics,
                                                int availableCores,
                                                int configuredThreads,
                                                File reportFile) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(reportFile))) {
            pw.println("# Computational Image Segmentation: Empirical Investigation & Performance Report");
            pw.println();
            pw.println("## 1. Executive Summary & Problem Scope");
            pw.println("This report investigates the computational performance, empirical scalability, and segmentation fidelity of algorithmic solutions developed in Java for dividing images into meaningful regions. Specifically, it analyzes:");
            pw.println("1. **Algorithmic Behavior on Large Images**: Scaling from 256x256 up to multi-megapixel resolutions.");
            pw.println("2. **Throughput on Increasing Dataset Sizes**: Sequential vs multi-threaded parallel batch execution on batches of images.");
            pw.println("3. **Segmentation Quality**: Quantitative evaluation (mIoU, Dice, Boundary F1) against synthetic ground truth.");
            pw.println();
            pw.printf("- **Host Architecture**: %d available logical processors, Java %s%n",
                    availableCores, System.getProperty("java.version"));
            pw.printf("- **Configured worker threads for the selected-thread benchmark**: %d%n", configuredThreads);
            pw.println();

            pw.println("---");
            pw.println("## 2. Investigation 1: Performance Scaling with Large Images");
            pw.println("In this experiment, image dimensions are scaled progressively while tracking execution time, throughput (Megapixels/sec), and memory footprint.");
            pw.println();
            pw.println("| Algorithm | Resolution | Total Pixels | Time (ms) | Throughput (MP/s) | Memory (MB) | Configured Threads | Pool Parallelism | Timing Repeats |");
            pw.println("|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|");
            for (BenchmarkMetric m : resMetrics) {
                if (m.experimentType == BenchmarkMetric.ExperimentType.RESOLUTION_SCALING) {
                    pw.printf("| %s | %dx%d | %,d px (%.2f MP) | %,d ms | **%.2f MP/s** | %.1f MB | %d | %d | %s (%d) |%n",
                            m.algorithmName, m.width, m.height, m.width * m.height, m.megaPixels,
                            m.totalTimeMs, m.throughputMegaPixelsPerSec, m.memoryMb,
                            m.configuredThreadCount, m.actualPoolParallelism,
                            m.timingMethod, m.measurementRepeats);
                }
            }
            pw.println();
            pw.println("### Key Complexity Insights for Large Images:");
            pw.println("- **SLIC Superpixel Segmentation**: Demonstrates strictly **linear O(N)** scaling. Because search windows are bounded to a local 2S x 2S region around each seed center, execution time increases proportionally to pixel count N.");
            pw.println("- **K-Means Clustering**: Shows **O(N * K * I)** scaling. For large images, cache locality and primitive array access maintain high multi-threaded throughput.");
            pw.println("- **Felzenszwalb Graph-Based**: Exhibits **O(E log E)** scaling dominated by edge weight sorting. As image size reaches multiple megapixels, edge count E ≈ 4N increases the sorting overhead.");
            pw.println();

            pw.println("---");
            pw.println("## 3. Investigation 2: Performance Scaling with Increasing Dataset Sizes");
            pw.println("In this experiment, dataset batch sizes are scaled from small batches to large sets, comparing a sequential baseline with the configured worker pool. Each result is the median of three measured runs after warm-up on the same in-memory images; generation and file output are excluded.");
            pw.println();
            pw.println("| Algorithm | Batch Size | Execution Mode | Total Time (ms) | Avg Time/Img (ms) | Speedup Factor | Parallel Efficiency | Configured Threads | Pool Parallelism | Timing Repeats |");
            pw.println("|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|");
            for (BenchmarkMetric m : batchMetrics) {
                if (m.experimentType == BenchmarkMetric.ExperimentType.DATASET_BATCH_SCALING) {
                    String speedupStr = (m.speedupFactor > 0) ? String.format("%.2fx", m.speedupFactor) : "-";
                    String effStr = (m.parallelEfficiency > 0) ? String.format("%.1f%%", m.parallelEfficiency * 100.0) : "-";
                    pw.printf("| %s | %d images | %s | %,d ms | %.1f ms | **%s** | %s | %d | %d | %s (%d) |%n",
                            m.algorithmName, m.batchSize, m.executionMode,
                            m.totalTimeMs, m.avgTimePerImageMs, speedupStr, effStr,
                            m.configuredThreadCount, m.actualPoolParallelism,
                            m.timingMethod, m.measurementRepeats);
                }
            }
            pw.println();
            pw.println("### Concurrency & Amdahl's Law Analysis:");
            pw.println("- Batch images are independent, but speedup is limited by scheduling, memory bandwidth, and other shared resources.");
            pw.println("- Efficiency is calculated as measured speedup divided by configured thread count; linear speedup is not guaranteed.");
            pw.println();

            int nextSection = 4;
            if (threadMetrics != null && !threadMetrics.isEmpty()) {
                pw.println("---");
                pw.printf("## %d. Investigation 3: Configured Thread Scaling%n", nextSection++);
                pw.println("The same generated batch and SLIC settings are used for each pool size. Each timing is the median of three measured runs after warm-up. Image generation and file output are outside the timed intervals.");
                pw.println();
                pw.println("| Algorithm | Configured Threads | Actual Pool Parallelism | Sequential Median (ms) | Parallel Median (ms) | Speedup | Efficiency | Throughput (MP/s) | Accuracy Metrics | Timing Repeats | Failure |");
                pw.println("|:---|:---:|:---:|---:|---:|---:|---:|---:|:---|:---:|:---|");
                for (ThreadScalingMetric m : threadMetrics) {
                    pw.printf("| %s | %d | %d | %.3f | %.3f | %.3fx | %.2f%% | %.3f | %s | %d | %s |%n",
                            m.algorithmName, m.configuredThreadCount, m.actualPoolParallelism,
                            m.sequentialMedianMs, m.parallelMedianMs, m.speedup,
                            m.parallelEfficiencyPct, m.throughputMegaPixelsPerSec,
                            m.accuracyMetrics, m.measurementRepeats,
                            m.failure.isEmpty() ? "None" : m.failure);
                }
                pw.println();
                pw.println("Accuracy is not measured in this performance-only thread-scaling experiment. It is recorded as N/A; segmentation accuracy is evaluated separately below.");
                pw.println("The scaling experiment uses valid distinct counts drawn from 1, 2, 4, 6, 8, and the machine's available logical processor count, excluding values above that count.");
                pw.println();
            }

            if (accuracyReports != null && !accuracyReports.isEmpty()) {
                pw.println("---");
                pw.printf("## %d. Investigation %d: Segmentation Quality on Ground-Truth Dataset%n",
                        nextSection, nextSection - 1);
                pw.println("Evaluation using synthetic benchmark images with known geometric ground-truth masks.");
                pw.println();
                pw.println("| Algorithm | Mean IoU (Jaccard) | Mean Dice (F1) | Pixel Accuracy | Boundary Precision | Boundary Recall | Boundary F1 |");
                pw.println("|:---|:---:|:---:|:---:|:---:|:---:|:---:|");
                for (Map.Entry<String, EvaluationReport> entry : accuracyReports.entrySet()) {
                    EvaluationReport rep = entry.getValue();
                    pw.printf("| %s | **%.4f** | %.4f | %.2f%% | %.3f | %.3f | **%.3f** |%n",
                            entry.getKey(), rep.meanIoU, rep.meanDice, rep.pixelAccuracy * 100.0,
                            rep.boundaryPrecision, rep.boundaryRecall, rep.boundaryF1);
                }
                pw.println();
            }

            pw.println("---");
            pw.printf("## %d. Algorithmic Comparison & Recommendation Matrix%n", nextSection + 1);
            pw.println();
            pw.println("| Criterion | SLIC Superpixels | K-Means Clustering | Felzenszwalb Graph | Otsu Multi-Threshold |");
            pw.println("|:---|:---|:---|:---|:---|");
            pw.println("| **Time Complexity** | O(N) | O(N * K * I) | O(E log E) | O(L^K) histogram |");
            pw.println("| **Space Complexity** | O(N) | O(N + K) | O(E) = O(N) | O(L) = O(256) |");
            pw.println("| **Boundary Adherence**| High (perceptual) | Moderate | High (adaptive) | Moderate (intensity only) |");
            pw.println("| **Region Regularity** | High (compact) | Unconstrained | Variable | Unconstrained |");
            pw.println("| **Large Image Suitability**| Excellent | Very Good | Good | Instantaneous |");
            pw.println("| **Recommended Use** | Pre-processing, Superpixels | Color clustering, Quantization | Natural scenes, Over-segmentation | Fast binarization, Medical |");
            pw.println();
            pw.println("### Experiment Limitations");
            pw.println("- Results depend on the Java runtime, hardware, system load, and memory availability; repeat on the target machine before drawing general conclusions.");
            pw.println("- The resolution and quality experiments are not thread-scaling experiments. K-Means pixel assignment uses the configured pool; SLIC, Otsu, and Felzenszwalb execute sequentially for a single image.");
            pw.println("- Thread-scaling accuracy is not recomputed because that experiment measures performance only; determinism tests verify K-Means labels across pool sizes.");
            pw.println();
            pw.println("Report automatically generated by `ImageSegmentationProject` Benchmark Engine.");
        }
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
