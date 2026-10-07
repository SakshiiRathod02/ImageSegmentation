package com.segmentation.benchmark;

import com.segmentation.algorithm.*;
import com.segmentation.dataset.GroundTruthEvaluator;
import com.segmentation.dataset.GroundTruthEvaluator.EvaluationReport;
import com.segmentation.dataset.SyntheticDatasetGenerator;
import com.segmentation.dataset.SyntheticDatasetGenerator.SyntheticSample;
import com.segmentation.util.ImageUtils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

/**
 * Benchmark orchestration engine executing rigorous empirical experiments on:
 * 1. Image Resolution Scaling (large images).
 * 2. Dataset Batch Size Scaling (sequential vs. multi-threaded parallel throughput).
 * 3. Segmentation Quality Evaluation (mIoU, Dice, Boundary F1).
 */
public class BenchmarkRunner {

    private final File outputDir;

    public BenchmarkRunner(File outputDir) {
        this.outputDir = outputDir;
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
    }

    /**
     * Executes the complete benchmark suite, prints console summaries, and exports reports.
     */
    public void runFullSuite() throws IOException {
        int availableCores = Runtime.getRuntime().availableProcessors();
        System.out.println("================================================================================");
        System.out.println("             IMAGE SEGMENTATION BENCHMARK & INVESTIGATION SUITE                 ");
        System.out.println("================================================================================");
        System.out.printf("Available Logical Cores: %d | Java Version: %s%n%n",
                availableCores, System.getProperty("java.version"));

        // 1. Run Image Resolution Scaling (Large Images)
        System.out.println(">>> 1. Investigating Performance on Progressive Image Sizes...");
        List<BenchmarkMetric> resMetrics = runResolutionScalingBenchmark();

        // 2. Run Dataset Batch Size Scaling (Increasing Dataset Sizes)
        System.out.println("\n>>> 2. Investigating Throughput on Increasing Dataset Sizes (Sequential vs Parallel)...");
        List<BenchmarkMetric> batchMetrics = runDatasetBatchBenchmark(availableCores);

        // 3. Run Segmentation Quality Evaluation
        System.out.println("\n>>> 3. Evaluating Segmentation Accuracy against Ground Truth...");
        Map<String, EvaluationReport> accuracyReports = runAccuracyEvaluation();

        // 4. Generate Visual Comparisons
        System.out.println("\n>>> 4. Generating Visual Comparison Images...");
        generateSampleVisualOutputs();

        // 5. Export Reports
        System.out.println("\n>>> 5. Exporting Benchmark Reports and CSV Data...");
        File csvRes = new File(outputDir, "benchmark_resolution_scaling.csv");
        File csvBatch = new File(outputDir, "benchmark_dataset_batch_scaling.csv");
        File csvAcc = new File(outputDir, "benchmark_accuracy_metrics.csv");
        File mdReport = new File(outputDir, "BENCHMARK_REPORT.md");

        BenchmarkReportExporter.exportResolutionScalingCsv(resMetrics, csvRes);
        BenchmarkReportExporter.exportDatasetScalingCsv(batchMetrics, csvBatch);
        BenchmarkReportExporter.exportAccuracyCsv(accuracyReports, csvAcc);
        BenchmarkReportExporter.exportFullMarkdownReport(resMetrics, batchMetrics, accuracyReports, availableCores, mdReport);

        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("Benchmark successfully finished!");
        System.out.println("Resolution CSV: " + csvRes.getAbsolutePath());
        System.out.println("Dataset CSV:    " + csvBatch.getAbsolutePath());
        System.out.println("Accuracy CSV:   " + csvAcc.getAbsolutePath());
        System.out.println("Markdown Report:" + mdReport.getAbsolutePath());
        System.out.println("Visual Outputs: " + new File(outputDir, "visuals").getAbsolutePath());
        System.out.println("================================================================================");
    }

    /**
     * Investigation 1: Image Resolution Scaling.
     * Evaluates 256x256, 512x512, 1024x1024, 2048x2048, and 3072x3072.
     */
    public List<BenchmarkMetric> runResolutionScalingBenchmark() {
        int[][] resolutions = {
                {256, 256},
                {512, 512},
                {1024, 1024},
                {2048, 2048},
                {3072, 3072}
        };

        List<SegmentationAlgorithm> algorithms = Arrays.asList(
                new OtsuMultiThreshold(3),
                new KMeansSegmentation(8, 12, false, 0.0),
                new SLICSuperpixelSegmentation(150, 15.0, 6),
                new FelzenszwalbSegmentation(0.5, 300.0, 50)
        );

        List<BenchmarkMetric> metrics = new ArrayList<>();

        // Warm-up round with 256x256 image to prime JIT compiler
        System.out.println("    [JIT Warmup] Priming JVM JIT compilation...");
        SyntheticSample warmupSample = SyntheticDatasetGenerator.generateSample(256, 256, 4, 5.0, 999L, "warmup");
        for (SegmentationAlgorithm algo : algorithms) {
            algo.segment(warmupSample.image);
        }

        System.out.printf("    %-24s | %-10s | %-14s | %-12s | %-10s%n",
                "Algorithm", "Res", "Pixels (MP)", "Time (ms)", "Throughput");
        System.out.println("    -----------------------------------------------------------------------------");

        for (int[] res : resolutions) {
            int w = res[0];
            int h = res[1];
            SyntheticSample sample = SyntheticDatasetGenerator.generateSample(w, h, 6, 8.0, 101L + w, "test_" + w);

            for (SegmentationAlgorithm algo : algorithms) {
                // If Felzenszwalb on 3072x3072, edge sorting ~36M edges may take extra memory, check heap
                if (w > 2048 && algo instanceof FelzenszwalbSegmentation) {
                    // Skip or run if memory permits
                    long freeMem = Runtime.getRuntime().freeMemory();
                    if (freeMem < 200_000_000) {
                        System.gc();
                    }
                }

                System.gc();
                long startMem = getUsedMemory();
                SegmentationResult result = algo.segment(sample.image);
                long memUsed = Math.max(0, getUsedMemory() - startMem);

                BenchmarkMetric m = new BenchmarkMetric(
                        BenchmarkMetric.ExperimentType.RESOLUTION_SCALING,
                        algo.getName(),
                        w, h, 1,
                        "Single-Image",
                        result.getExecutionTimeMs(),
                        memUsed / (1024.0 * 1024.0),
                        result.getNumSegments(),
                        1.0, 1.0
                );
                metrics.add(m);

                System.out.printf("    %-24s | %-10s | %8d (%4.2f MP) | %8d ms | %6.2f MP/s%n",
                        truncate(algo.getName(), 24),
                        w + "x" + h,
                        w * h,
                        m.megaPixels,
                        m.totalTimeMs,
                        m.throughputMegaPixelsPerSec);
            }
            System.out.println("    -----------------------------------------------------------------------------");
        }
        return metrics;
    }

    /**
     * Investigation 2: Dataset Batch Size Scaling.
     * Evaluates sequential vs parallel throughput on increasing numbers of images.
     */
    public List<BenchmarkMetric> runDatasetBatchBenchmark(int availableCores) {
        int[] batchSizes = {5, 10, 25, 50};
        int imgW = 512;
        int imgH = 512;

        List<BenchmarkMetric> metrics = new ArrayList<>();
        // Representative algorithm: SLIC Superpixels
        SegmentationAlgorithm algo = new SLICSuperpixelSegmentation(120, 15.0, 6);

        System.out.printf("    Testing Algorithm: %s on %dx%d images%n", algo.getName(), imgW, imgH);
        System.out.printf("    %-12s | %-15s | %-12s | %-14s | %-10s | %-10s%n",
                "Batch Size", "Mode", "Total Time", "Avg Time/Img", "Speedup", "Efficiency");
        System.out.println("    -----------------------------------------------------------------------------");

        for (int count : batchSizes) {
            // Generate batch in memory
            List<BufferedImage> batch = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                SyntheticSample sample = SyntheticDatasetGenerator.generateSample(imgW, imgH, 4, 5.0, 5000L + i, "batch_" + i);
                batch.add(sample.image);
            }

            // 1. Sequential Mode (Single-Threaded)
            long tSeqStart = System.currentTimeMillis();
            for (BufferedImage img : batch) {
                algo.segment(img);
            }
            long tSeqTotal = Math.max(1, System.currentTimeMillis() - tSeqStart);

            BenchmarkMetric mSeq = new BenchmarkMetric(
                    BenchmarkMetric.ExperimentType.DATASET_BATCH_SCALING,
                    algo.getName(),
                    imgW, imgH, count,
                    "Sequential (1T)",
                    tSeqTotal,
                    0.0, 120, 1.0, 1.0
            );
            metrics.add(mSeq);

            // 2. Parallel Mode (Multi-Threaded via ForkJoinPool)
            long tParStart = System.currentTimeMillis();
            batch.parallelStream().forEach(algo::segment);
            long tParTotal = Math.max(1, System.currentTimeMillis() - tParStart);

            double speedup = (double) tSeqTotal / tParTotal;
            double efficiency = speedup / availableCores;

            BenchmarkMetric mPar = new BenchmarkMetric(
                    BenchmarkMetric.ExperimentType.DATASET_BATCH_SCALING,
                    algo.getName(),
                    imgW, imgH, count,
                    "Parallel (" + availableCores + "T)",
                    tParTotal,
                    0.0, 120, speedup, efficiency
            );
            metrics.add(mPar);

            System.out.printf("    %-12d | %-15s | %8d ms | %10.1f ms | %-10s | %-10s%n",
                    count, "Sequential (1T)", tSeqTotal, mSeq.avgTimePerImageMs, "1.00x", "100.0%");
            System.out.printf("    %-12d | %-15s | %8d ms | %10.1f ms | %9.2fx | %8.1f%%%n",
                    count, "Parallel (" + availableCores + "T)", tParTotal, mPar.avgTimePerImageMs, speedup, efficiency * 100.0);
            System.out.println("    -----------------------------------------------------------------------------");
        }
        return metrics;
    }

    /**
     * Investigation 3: Segmentation Quality Evaluation.
     */
    public Map<String, EvaluationReport> runAccuracyEvaluation() {
        int width = 512;
        int height = 512;
        SyntheticSample sample = SyntheticDatasetGenerator.generateSample(width, height, 5, 8.0, 888L, "eval_sample");

        Map<String, SegmentationAlgorithm> candidates = new LinkedHashMap<>();
        candidates.put("Otsu Multi-Threshold (k=3)", new OtsuMultiThreshold(3));
        candidates.put("K-Means (k=6, RGB)", new KMeansSegmentation(6, 15, false, 0.0));
        candidates.put("K-Means (k=6, Spatial-XY)", new KMeansSegmentation(6, 15, true, 0.5));
        candidates.put("SLIC Superpixels (k=80)", new SLICSuperpixelSegmentation(80, 12.0, 8));
        candidates.put("Felzenszwalb Graph", new FelzenszwalbSegmentation(0.5, 400.0, 80));

        Map<String, EvaluationReport> reports = new LinkedHashMap<>();

        System.out.printf("    %-28s | %-8s | %-8s | %-10s | %-10s%n",
                "Algorithm", "mIoU", "mDice", "Pixel Acc", "Boundary F1");
        System.out.println("    -----------------------------------------------------------------------------");

        for (Map.Entry<String, SegmentationAlgorithm> entry : candidates.entrySet()) {
            String name = entry.getKey();
            SegmentationAlgorithm algo = entry.getValue();

            SegmentationResult result = algo.segment(sample.image);
            EvaluationReport report = GroundTruthEvaluator.evaluate(
                    result.getLabels(), result.getNumSegments(),
                    sample.groundTruthLabels, sample.numTrueRegions,
                    width, height
            );
            reports.put(name, report);

            System.out.printf("    %-28s | %8.4f | %8.4f | %8.2f%% | %10.4f%n",
                    truncate(name, 28), report.meanIoU, report.meanDice,
                    report.pixelAccuracy * 100.0, report.boundaryF1);
        }
        return reports;
    }

    /**
     * Generates visual side-by-side composite images for inspection.
     */
    private void generateSampleVisualOutputs() throws IOException {
        File visualsDir = new File(outputDir, "visuals");
        visualsDir.mkdirs();

        int w = 512;
        int h = 512;
        SyntheticSample sample = SyntheticDatasetGenerator.generateSample(w, h, 6, 6.0, 777L, "visual_demo");

        // Save original and ground truth mask
        ImageUtils.saveImage(sample.image, new File(visualsDir, "00_original_scene.png"), "PNG");
        BufferedImage gtVisual = ImageUtils.createDistinctColorImage(w, h, sample.groundTruthLabels, sample.numTrueRegions);
        ImageUtils.saveImage(gtVisual, new File(visualsDir, "01_ground_truth_mask.png"), "PNG");

        List<SegmentationAlgorithm> algorithms = Arrays.asList(
                new SLICSuperpixelSegmentation(120, 15.0, 8),
                new KMeansSegmentation(8, 15, true, 0.4),
                new FelzenszwalbSegmentation(0.6, 350.0, 60),
                new OtsuMultiThreshold(3)
        );

        for (SegmentationAlgorithm algo : algorithms) {
            SegmentationResult res = algo.segment(sample.image);

            BufferedImage meanColor = ImageUtils.createMeanColorImage(sample.image, res.getLabels(), res.getNumSegments());
            BufferedImage boundary = ImageUtils.createBoundaryOverlayImage(sample.image, res.getLabels(), 0xFFFF2222);
            BufferedImage composite = ImageUtils.createSideBySideComparison(
                    sample.image, meanColor, boundary,
                    algo.getName(), res.getNumSegments(), res.getExecutionTimeMs()
            );

            String safeName = algo.getName().replaceAll("[^a-zA-Z0-9_-]", "_");
            File compFile = new File(visualsDir, "comparison_" + safeName + ".png");
            ImageUtils.saveImage(composite, compFile, "PNG");
            System.out.printf("    Saved visual comparison: %s%n", compFile.getName());
        }
    }

    private static String truncate(String s, int len) {
        if (s.length() <= len) return s;
        return s.substring(0, len - 3) + "...";
    }

    private static long getUsedMemory() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }
}
