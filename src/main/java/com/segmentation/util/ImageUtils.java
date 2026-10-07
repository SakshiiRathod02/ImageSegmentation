package com.segmentation.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * Utility functions for image I/O, label map visualization, boundary contour overlays,
 * and composite side-by-side comparisons.
 */
public final class ImageUtils {

    private ImageUtils() {}

    /**
     * Reads an image file into a BufferedImage (TYPE_INT_RGB or TYPE_INT_ARGB).
     */
    public static BufferedImage loadImage(File file) throws IOException {
        BufferedImage img = ImageIO.read(file);
        if (img == null) {
            throw new IOException("Failed to decode image from: " + file.getAbsolutePath());
        }
        // Normalize to standard TYPE_INT_RGB for uniform fast pixel manipulation
        if (img.getType() != BufferedImage.TYPE_INT_RGB) {
            BufferedImage converted = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = converted.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
            return converted;
        }
        return img;
    }

    /**
     * Saves a BufferedImage to disk with parent directory creation.
     */
    public static void saveImage(BufferedImage img, File outputFile, String format) throws IOException {
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        ImageIO.write(img, format, outputFile);
    }

    /**
     * Extracts packed RGB pixel array from a BufferedImage.
     */
    public static int[] getRgbPixels(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] pixels = new int[w * h];
        img.getRGB(0, 0, w, h, pixels, 0, w);
        return pixels;
    }

    /**
     * Generates a segmented image where each segment's pixels are colored by the
     * average (mean) RGB color of that segment in the original image.
     */
    public static BufferedImage createMeanColorImage(BufferedImage original, int[] labels, int numSegments) {
        int w = original.getWidth();
        int h = original.getHeight();
        int[] originalPixels = getRgbPixels(original);

        long[] sumR = new long[numSegments];
        long[] sumG = new long[numSegments];
        long[] sumB = new long[numSegments];
        int[] count = new int[numSegments];

        for (int i = 0; i < labels.length; i++) {
            int lbl = labels[i];
            if (lbl >= 0 && lbl < numSegments) {
                int rgb = originalPixels[i];
                sumR[lbl] += (rgb >> 16) & 0xFF;
                sumG[lbl] += (rgb >> 8) & 0xFF;
                sumB[lbl] += rgb & 0xFF;
                count[lbl]++;
            }
        }

        int[] meanRgb = new int[numSegments];
        for (int k = 0; k < numSegments; k++) {
            if (count[k] > 0) {
                int r = (int) (sumR[k] / count[k]);
                int g = (int) (sumG[k] / count[k]);
                int b = (int) (sumB[k] / count[k]);
                meanRgb[k] = (0xFF << 24) | (r << 16) | (g << 8) | b;
            }
        }

        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] resultPixels = new int[w * h];
        for (int i = 0; i < labels.length; i++) {
            int lbl = labels[i];
            resultPixels[i] = (lbl >= 0 && lbl < numSegments) ? meanRgb[lbl] : 0;
        }
        result.setRGB(0, 0, w, h, resultPixels, 0, w);
        return result;
    }

    /**
     * Generates a segmented image where each segment receives a pseudo-random distinct color
     * to easily distinguish neighboring regions visually.
     */
    public static BufferedImage createDistinctColorImage(int width, int height, int[] labels, int numSegments) {
        int[] palette = generateDistinctPalette(numSegments);
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] pixels = new int[width * height];
        for (int i = 0; i < labels.length; i++) {
            int lbl = labels[i];
            pixels[i] = (lbl >= 0 && lbl < palette.length) ? palette[lbl] : 0xFF000000;
        }
        result.setRGB(0, 0, width, height, pixels, 0, width);
        return result;
    }

    /**
     * Generates an overlay of segment boundaries directly drawn on the original image.
     * Boundary pixels are marked where adjacent pixels (right or bottom) have differing segment labels.
     *
     * @param original Original source image
     * @param labels Label array of length width * height
     * @param boundaryColor Packed RGB color to draw boundaries (e.g. 0xFFFF0000 for red)
     */
    public static BufferedImage createBoundaryOverlayImage(BufferedImage original, int[] labels, int boundaryColor) {
        int w = original.getWidth();
        int h = original.getHeight();
        int[] pixels = getRgbPixels(original);
        boolean[] isBoundary = computeBoundaryMask(labels, w, h);

        for (int i = 0; i < pixels.length; i++) {
            if (isBoundary[i]) {
                pixels[i] = boundaryColor;
            }
        }

        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        result.setRGB(0, 0, w, h, pixels, 0, w);
        return result;
    }

    /**
     * Computes a boolean mask where true indicates a boundary pixel.
     */
    public static boolean[] computeBoundaryMask(int[] labels, int width, int height) {
        boolean[] boundary = new boolean[width * height];
        for (int y = 0; y < height; y++) {
            int rowOffset = y * width;
            for (int x = 0; x < width; x++) {
                int idx = rowOffset + x;
                int lbl = labels[idx];
                // Check right neighbor
                if (x + 1 < width && labels[idx + 1] != lbl) {
                    boundary[idx] = true;
                    boundary[idx + 1] = true;
                }
                // Check bottom neighbor
                if (y + 1 < height && labels[idx + width] != lbl) {
                    boundary[idx] = true;
                    boundary[idx + width] = true;
                }
            }
        }
        return boundary;
    }

    /**
     * Generates a 3-panel side-by-side composite image:
     * [ Panel 1: Original | Panel 2: Mean Color Segmented | Panel 3: Boundary Overlay ]
     * with text headers.
     */
    public static BufferedImage createSideBySideComparison(BufferedImage original,
                                                           BufferedImage meanColor,
                                                           BufferedImage boundaryOverlay,
                                                           String algorithmName,
                                                           int segmentCount,
                                                           long executionTimeMs) {
        int w = original.getWidth();
        int h = original.getHeight();
        int headerHeight = 50;
        int gutter = 6;
        int totalWidth = w * 3 + gutter * 2;
        int totalHeight = h + headerHeight;

        BufferedImage composite = new BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = composite.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Dark modern background
        g.setColor(new Color(24, 28, 36));
        g.fillRect(0, 0, totalWidth, totalHeight);

        // Header text
        g.setColor(new Color(230, 235, 245));
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        String title = String.format("Algorithm: %s | Segments: %d | Runtime: %d ms | Res: %dx%d",
                algorithmName, segmentCount, executionTimeMs, w, h);
        g.drawString(title, 14, 22);

        // Panel sub-labels
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(170, 180, 200));
        g.drawString("1. Original Image", 14, 42);
        g.drawString("2. Mean-Color Segmented", w + gutter + 14, 42);
        g.drawString("3. Boundary Contours", (w + gutter) * 2 + 14, 42);

        // Draw image panels
        g.drawImage(original, 0, headerHeight, null);
        g.drawImage(meanColor, w + gutter, headerHeight, null);
        g.drawImage(boundaryOverlay, (w + gutter) * 2, headerHeight, null);

        // Clean border lines between panels
        g.setColor(new Color(60, 70, 90));
        g.drawLine(w, headerHeight, w, totalHeight);
        g.drawLine(w + gutter * 2, headerHeight, w + gutter * 2, totalHeight);

        g.dispose();
        return composite;
    }

    /**
     * Generates distinct saturated colors using the golden ratio hue distribution.
     */
    private static int[] generateDistinctPalette(int count) {
        int[] palette = new int[Math.max(count, 1)];
        Random rng = new Random(42);
        float goldenRatioConjugate = 0.618033988749895f;
        float hue = rng.nextFloat();
        for (int i = 0; i < count; i++) {
            hue += goldenRatioConjugate;
            hue %= 1.0f;
            Color c = Color.getHSBColor(hue, 0.75f, 0.90f);
            palette[i] = c.getRGB();
        }
        return palette;
    }
}
