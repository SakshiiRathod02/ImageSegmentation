package com.segmentation;

import com.segmentation.algorithm.*;
import com.segmentation.benchmark.BenchmarkRunner;
import com.segmentation.dataset.ImageDatasetLoader;
import com.segmentation.dataset.ImageDatasetLoader.ImageEntry;
import com.segmentation.dataset.SyntheticDatasetGenerator;
import com.segmentation.util.ImageUtils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * Main entry point for the Computational Image Segmentation project.
 * Supports:
 * - Automated benchmark and performance investigation for large images and dataset sizes.
 * - Procedural synthetic dataset generation with ground-truth masks.
 * - Batch segmentation of custom datasets (e.g. Kaggle datasets).
 * - Single-image multi-algorithm comparative visual segmentation.
 */
public class Main {

    private static final String DEFAULT_OUTPUT_DIR = "output";

    public static void main(String[] args) {
        try {
            if (args.length > 0) {
                handleCommandLineArgs(args);
            } else {
                runInteractiveConsole();
            }
        } catch (Exception e) {
            System.err.println("Fatal Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void handleCommandLineArgs(String[] args) throws IOException {
        String command = args[0].toLowerCase();
        File outDir = new File(DEFAULT_OUTPUT_DIR);

        switch (command) {
            case "--benchmark":
            case "-b":
                if (args.length > 1) {
                    outDir = new File(args[1]);
                }
                new BenchmarkRunner(outDir).runFullSuite();
                break;

            case "--generate-dataset":
            case "-g":
                int count = (args.length > 1) ? Integer.parseInt(args[1]) : 20;
                int w = (args.length > 2) ? Integer.parseInt(args[2]) : 512;
                int h = (args.length > 3) ? Integer.parseInt(args[3]) : 512;
                File targetDir = (args.length > 4) ? new File(args[4]) : new File(outDir, "synthetic_dataset");
                System.out.printf("Generating synthetic dataset (%d images, %dx%d)...%n", count, w, h);
                SyntheticDatasetGenerator.generateDatasetBatch(targetDir, count, w, h, 6.0);
                System.out.println("Dataset generated at: " + targetDir.getAbsolutePath());
                break;

            case "--segment-dataset":
            case "-d":
                if (args.length < 2) {
                    System.err.println("Usage: --segment-dataset <inputDirectory> [outputDirectory]");
                    return;
                }
                File inputDir = new File(args[1]);
                File batchOutDir = (args.length > 2) ? new File(args[2]) : new File(outDir, "batch_results");
                processCustomDataset(inputDir, batchOutDir);
                break;

            case "--segment-image":
            case "-i":
                if (args.length < 2) {
                    System.err.println("Usage: --segment-image <imageFile> [outputDirectory]");
                    return;
                }
                File imgFile = new File(args[1]);
                File imgOutDir = (args.length > 2) ? new File(args[2]) : new File(outDir, "single_image_results");
                segmentSingleImage(imgFile, imgOutDir);
                break;

            default:
                printUsage();
                break;
        }
    }

    private static void runInteractiveConsole() throws IOException {
        System.out.println("================================================================================");
        System.out.println("                COMPUTATIONAL IMAGE SEGMENTATION PLATFORM                       ");
        System.out.println("================================================================================");
        System.out.println("  1. Run Full Performance Benchmark & Scaling Investigation (Recommended)");
        System.out.println("  2. Generate Synthetic Dataset with Ground-Truth Masks");
        System.out.println("  3. Process Custom / Kaggle Image Dataset Folder");
        System.out.println("  4. Segment a Single Image with Visual Comparison Overlays");
        System.out.println("  5. Exit");
        System.out.println("================================================================================");
        System.out.print("Please enter option [1-5] (default 1): ");

        Scanner scanner = new Scanner(System.in);
        String input = scanner.hasNextLine() ? scanner.nextLine().trim() : "1";
        if (input.isEmpty()) input = "1";

        File outDir = new File(DEFAULT_OUTPUT_DIR);

        switch (input) {
            case "1":
                new BenchmarkRunner(outDir).runFullSuite();
                break;
            case "2":
                File synDir = new File(outDir, "synthetic_dataset");
                System.out.println("Generating 25 synthetic benchmark images (512x512)...");
                SyntheticDatasetGenerator.generateDatasetBatch(synDir, 25, 512, 512, 6.0);
                System.out.println("Done! Saved to: " + synDir.getAbsolutePath());
                break;
            case "3":
                System.out.print("Enter path to image folder: ");
                String path = scanner.nextLine().trim();
                File inFolder = new File(path);
                processCustomDataset(inFolder, new File(outDir, "batch_results"));
                break;
            case "4":
                System.out.print("Enter path to image file: ");
                String fileStr = scanner.nextLine().trim();
                File inFile = new File(fileStr);
                segmentSingleImage(inFile, new File(outDir, "single_image_results"));
                break;
            case "5":
                System.out.println("Exiting.");
                break;
            default:
                System.out.println("Unknown option. Running full benchmark suite by default.");
                new BenchmarkRunner(outDir).runFullSuite();
                break;
        }
    }

    private static void processCustomDataset(File inputDir, File outputDir) throws IOException {
        List<ImageEntry> entries = ImageDatasetLoader.loadDirectory(inputDir);
        if (entries.isEmpty()) {
            System.err.println("No images found in: " + inputDir.getAbsolutePath());
            return;
        }

        System.out.printf("Discovered %d images in %s. Processing with multi-threaded SLIC Superpixels...%n",
                entries.size(), inputDir.getAbsolutePath());

        outputDir.mkdirs();
        SegmentationAlgorithm algo = new SLICSuperpixelSegmentation(150, 15.0, 6);

        long start = System.currentTimeMillis();
        entries.parallelStream().forEach(entry -> {
            try {
                BufferedImage img = entry.load();
                SegmentationResult res = algo.segment(img);

                BufferedImage meanColor = ImageUtils.createMeanColorImage(img, res.getLabels(), res.getNumSegments());
                BufferedImage boundary = ImageUtils.createBoundaryOverlayImage(img, res.getLabels(), 0xFFFF3333);
                BufferedImage sideBySide = ImageUtils.createSideBySideComparison(
                        img, meanColor, boundary, algo.getName(), res.getNumSegments(), res.getExecutionTimeMs()
                );

                String baseName = entry.name.replaceAll("\\.[^.]+$", "");
                File dest = new File(outputDir, baseName + "_segmented.png");
                ImageUtils.saveImage(sideBySide, dest, "PNG");
                System.out.printf("  Processed: %s (%dx%d) in %d ms -> %s%n",
                        entry.name, img.getWidth(), img.getHeight(), res.getExecutionTimeMs(), dest.getName());
            } catch (Exception e) {
                System.err.printf("  Failed to process %s: %s%n", entry.name, e.getMessage());
            }
        });

        long totalTime = System.currentTimeMillis() - start;
        System.out.printf("Batch processing complete! Total time: %d ms (Avg: %.1f ms/image). Output at: %s%n",
                totalTime, (double) totalTime / entries.size(), outputDir.getAbsolutePath());
    }

    private static void segmentSingleImage(File imgFile, File outputDir) throws IOException {
        if (!imgFile.exists()) {
            System.err.println("Image file not found: " + imgFile.getAbsolutePath());
            return;
        }

        BufferedImage img = ImageUtils.loadImage(imgFile);
        outputDir.mkdirs();

        System.out.printf("Loaded image: %s (%dx%d, %,d pixels)%n",
                imgFile.getName(), img.getWidth(), img.getHeight(), img.getWidth() * img.getHeight());

        List<SegmentationAlgorithm> algorithms = Arrays.asList(
                new SLICSuperpixelSegmentation(150, 15.0, 8),
                new KMeansSegmentation(8, 15, true, 0.4),
                new FelzenszwalbSegmentation(0.6, 350.0, 60),
                new OtsuMultiThreshold(3)
        );

        String baseName = imgFile.getName().replaceAll("\\.[^.]+$", "");

        for (SegmentationAlgorithm algo : algorithms) {
            System.out.printf("Running %s... ", algo.getName());
            SegmentationResult res = algo.segment(img);
            System.out.printf("Done in %d ms (%d segments, %.2f MP/s)%n",
                    res.getExecutionTimeMs(), res.getNumSegments(), res.getThroughputMegaPixelsPerSec());

            BufferedImage meanColor = ImageUtils.createMeanColorImage(img, res.getLabels(), res.getNumSegments());
            BufferedImage boundary = ImageUtils.createBoundaryOverlayImage(img, res.getLabels(), 0xFFFF2222);
            BufferedImage composite = ImageUtils.createSideBySideComparison(
                    img, meanColor, boundary, algo.getName(), res.getNumSegments(), res.getExecutionTimeMs()
            );

            String safeAlgo = algo.getName().replaceAll("[^a-zA-Z0-9_-]", "_");
            File out = new File(outputDir, baseName + "_" + safeAlgo + ".png");
            ImageUtils.saveImage(composite, out, "PNG");
        }
        System.out.println("All comparisons saved to: " + outputDir.getAbsolutePath());
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java -cp bin com.segmentation.Main [options]");
        System.out.println("Options:");
        System.out.println("  --benchmark [outDir]                       Run full performance and scaling investigation");
        System.out.println("  --generate-dataset <count> <w> <h> [dir]   Generate synthetic images with masks");
        System.out.println("  --segment-dataset <inDir> [outDir]         Batch segment images in a folder");
        System.out.println("  --segment-image <imageFile> [outDir]       Segment single image with all algorithms");
    }
}
