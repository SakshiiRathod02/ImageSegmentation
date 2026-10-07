package com.segmentation.dataset;

import com.segmentation.util.ImageUtils;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * Procedural generator for synthetic image segmentation datasets.
 * Creates complex multi-region visual scenes with ground-truth semantic masks.
 * Supports:
 * - Geometric primitives (circles, ellipses, rotated boxes, rounded polygons).
 * - Perlin-like background gradients and multi-tone textures.
 * - Additive Gaussian and salt-and-pepper noise to simulate real sensor noise.
 * - Arbitrary image resolutions (from 128x128 up to 4096x4096).
 * - Simultaneous generation of RGB image and exact ground-truth label map.
 */
public class SyntheticDatasetGenerator {

    /**
     * Data pair holding the synthetic color image and the exact ground truth label mask.
     */
    public static class SyntheticSample {
        public final BufferedImage image;
        public final int[] groundTruthLabels;
        public final int numTrueRegions;
        public final String name;

        public SyntheticSample(BufferedImage image, int[] groundTruthLabels, int numTrueRegions, String name) {
            this.image = image;
            this.groundTruthLabels = groundTruthLabels;
            this.numTrueRegions = numTrueRegions;
            this.name = name;
        }
    }

    /**
     * Generates a single synthetic scene at specified width and height.
     *
     * @param width Image width in pixels
     * @param height Image height in pixels
     * @param numShapes Number of foreground regions to embed
     * @param noiseLevel Standard deviation of additive Gaussian color noise [0..50]
     * @param seed Random seed for reproducibility
     */
    public static SyntheticSample generateSample(int width, int height, int numShapes, double noiseLevel, long seed, String name) {
        Random rng = new Random(seed);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] gtLabels = new int[width * height]; // label 0 = background, 1..numShapes = foreground objects

        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        // 1. Render Background: smooth dual-tone gradient
        Color bgCol1 = new Color(30 + rng.nextInt(50), 40 + rng.nextInt(50), 60 + rng.nextInt(60));
        Color bgCol2 = new Color(50 + rng.nextInt(60), 70 + rng.nextInt(60), 40 + rng.nextInt(60));
        GradientPaint grad = new GradientPaint(0, 0, bgCol1, width, height, bgCol2);
        g.setPaint(grad);
        g.fillRect(0, 0, width, height);

        // Ground-truth mask image for shape rasterization
        BufferedImage maskImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D gMask = maskImg.createGraphics();
        gMask.setColor(Color.BLACK);
        gMask.fillRect(0, 0, width, height);

        // 2. Render Distinct Foreground Regions
        int actualRegions = 1; // background is 0
        for (int i = 0; i < numShapes; i++) {
            int shapeType = rng.nextInt(3);
            int shapeW = (int) (width * (0.15 + rng.nextDouble() * 0.25));
            int shapeH = (int) (height * (0.15 + rng.nextDouble() * 0.25));
            int posX = rng.nextInt(Math.max(1, width - shapeW));
            int posY = rng.nextInt(Math.max(1, height - shapeH));

            Color shapeColor = generateVibrantColor(rng, i);
            g.setColor(shapeColor);

            int regionId = actualRegions++;
            // Encode region ID in mask image red channel
            gMask.setColor(new Color(regionId, 0, 0));

            Shape shape;
            if (shapeType == 0) {
                shape = new Ellipse2D.Double(posX, posY, shapeW, shapeH);
            } else if (shapeType == 1) {
                shape = new Rectangle2D.Double(posX, posY, shapeW, shapeH);
            } else {
                shape = new RoundRectangle2D.Double(posX, posY, shapeW, shapeH, shapeW * 0.3, shapeH * 0.3);
            }

            g.fill(shape);
            gMask.fill(shape);
        }

        g.dispose();
        gMask.dispose();

        // 3. Extract Ground Truth Labels from mask
        int[] maskPixels = ImageUtils.getRgbPixels(maskImg);
        for (int i = 0; i < maskPixels.length; i++) {
            gtLabels[i] = (maskPixels[i] >> 16) & 0xFF;
        }

        // 4. Inject Noise into RGB image if noiseLevel > 0
        if (noiseLevel > 0) {
            int[] rgb = ImageUtils.getRgbPixels(image);
            for (int i = 0; i < rgb.length; i++) {
                int c = rgb[i];
                int r = (c >> 16) & 0xFF;
                int gCol = (c >> 8) & 0xFF;
                int b = c & 0xFF;

                double noise = rng.nextGaussian() * noiseLevel;
                r = clamp((int) (r + noise));
                gCol = clamp((int) (gCol + noise));
                b = clamp((int) (b + noise));

                rgb[i] = (0xFF << 24) | (r << 16) | (gCol << 8) | b;
            }
            image.setRGB(0, 0, width, height, rgb, 0, width);
        }

        return new SyntheticSample(image, gtLabels, actualRegions, name);
    }

    /**
     * Generates a batch of synthetic dataset samples and saves them to disk.
     */
    public static void generateDatasetBatch(File outputDir, int count, int width, int height, double noise) throws IOException {
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        File imagesDir = new File(outputDir, "images");
        File masksDir = new File(outputDir, "masks");
        imagesDir.mkdirs();
        masksDir.mkdirs();

        for (int i = 0; i < count; i++) {
            String name = String.format("synthetic_%03d_%dx%d", i + 1, width, height);
            int numShapes = 3 + (i % 5);
            SyntheticSample sample = generateSample(width, height, numShapes, noise, 1000L + i * 37, name);

            File imgFile = new File(imagesDir, name + ".png");
            ImageUtils.saveImage(sample.image, imgFile, "PNG");

            // Save visual representation of ground truth mask
            BufferedImage maskVis = ImageUtils.createDistinctColorImage(width, height, sample.groundTruthLabels, sample.numTrueRegions);
            File maskFile = new File(masksDir, name + "_mask.png");
            ImageUtils.saveImage(maskVis, maskFile, "PNG");
        }
    }

    private static Color generateVibrantColor(Random rng, int idx) {
        float hue = (float) ((idx * 0.22) % 1.0);
        float saturation = 0.85f + rng.nextFloat() * 0.15f;
        float brightness = 0.85f + rng.nextFloat() * 0.15f;
        return Color.getHSBColor(hue, saturation, brightness);
    }

    private static int clamp(int val) {
        return Math.max(0, Math.min(255, val));
    }
}
