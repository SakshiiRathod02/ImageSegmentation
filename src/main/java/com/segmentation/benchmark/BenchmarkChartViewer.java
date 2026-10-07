package com.segmentation.benchmark;

import com.segmentation.util.ImageUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/**
 * Interactive Swing GUI Dashboard that visually plots and displays the performance benchmark
 * graphs, multithreaded speedup curves, resolution scaling lines, and segmented image outputs.
 */
public class BenchmarkChartViewer extends JFrame {

    public BenchmarkChartViewer(File outputDir) {
        super("Image Segmentation: Scalability & Performance Analytics Dashboard");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1050, 750);
        setLocationRelativeTo(null);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("SansSerif", Font.BOLD, 13));

        // Tab 1: Thread Speedup & Amdahl's Law Curve
        tabbedPane.addTab("Thread Speedup (Amdahl's Law)", new ThreadSpeedupPanel());

        // Tab 2: Sequential vs Parallel Latency Bar Chart
        tabbedPane.addTab("Batch Latency (1T vs 12T)", new BatchLatencyPanel());

        // Tab 3: Large Image Resolution Scaling Curve
        tabbedPane.addTab("Large Image Scaling (MP vs Time)", new ResolutionScalingPanel());

        // Tab 4: Visual Segmentation Gallery
        tabbedPane.addTab("Visual Segmentation Gallery", new VisualGalleryPanel(outputDir));

        add(tabbedPane, BorderLayout.CENTER);

        // Bottom status banner
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 8));
        statusPanel.setBackground(new Color(24, 28, 36));
        JLabel statusLabel = new JLabel("Host: 12 CPU Logical Cores | Java: " + System.getProperty("java.version") +
                " | Pure Java Graphics2D Rendering");
        statusLabel.setForeground(new Color(200, 210, 230));
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusPanel.add(statusLabel);
        add(statusPanel, BorderLayout.SOUTH);
    }

    /**
     * Safely displays the interactive dashboard on the Event Dispatch Thread (EDT)
     * if GUI display is supported by the OS environment.
     */
    public static void showDashboard(File outputDir) {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("[GUI Dashboard] Headless environment detected; skipping window display.");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            BenchmarkChartViewer viewer = new BenchmarkChartViewer(outputDir);
            viewer.setVisible(true);
            viewer.toFront();
            System.out.println("\n[GUI Dashboard] Analytics Dashboard opened on screen!");
        });
    }

    // =========================================================================
    // PANEL 1: THREAD SPEEDUP & AMDAHL'S LAW PLOT
    // =========================================================================
    private static class ThreadSpeedupPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Background
            g2.setColor(new Color(250, 252, 255));
            g2.fillRect(0, 0, w, h);

            int padLeft = 80;
            int padRight = 80;
            int padTop = 70;
            int padBottom = 70;

            int plotW = w - padLeft - padRight;
            int plotH = h - padTop - padBottom;

            // Title
            g2.setColor(new Color(26, 54, 93));
            g2.setFont(new Font("SansSerif", Font.BOLD, 17));
            g2.drawString("Investigation 2: Parallel Speedup & Thread Efficiency vs. Dataset Size", padLeft, 38);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g2.setColor(new Color(80, 95, 120));
            g2.drawString("SLIC Superpixels (512x512 images) across batches on 12 CPU Logical Cores (ForkJoinPool)", padLeft, 56);

            // Data
            int[] batches = {5, 10, 25, 50};
            double[] speedup = {4.21, 6.07, 6.80, 6.40};
            double[] efficiency = {35.0, 50.5, 56.6, 53.4};

            double maxSpeedup = 8.0;

            // Draw Grid & Y-Axis (Speedup)
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            for (int s = 0; s <= 8; s += 2) {
                int y = padTop + plotH - (int) ((s / maxSpeedup) * plotH);
                g2.setColor(new Color(225, 230, 240));
                g2.drawLine(padLeft, y, padLeft + plotW, y);
                g2.setColor(new Color(60, 75, 100));
                g2.drawString(s + ".0x", padLeft - 45, y + 4);

                // Right Y-Axis (Efficiency %)
                int effVal = (int) ((s / maxSpeedup) * 100);
                g2.setColor(new Color(180, 100, 20));
                g2.drawString(effVal + "%", padLeft + plotW + 12, y + 4);
            }

            // Baseline Line (1.0x)
            int yBase = padTop + plotH - (int) ((1.0 / maxSpeedup) * plotH);
            g2.setColor(new Color(160, 170, 185));
            Stroke dashed = new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6}, 0);
            Stroke solid = new BasicStroke(2.8f);
            g2.setStroke(dashed);
            g2.drawLine(padLeft, yBase, padLeft + plotW, yBase);
            g2.drawString("Baseline (1 Thread = 1.0x)", padLeft + 15, yBase - 6);

            // Plot Speedup Curve (Navy Blue)
            g2.setStroke(solid);
            g2.setColor(new Color(30, 75, 140));

            int[] px = new int[batches.length];
            int[] py = new int[batches.length];
            for (int i = 0; i < batches.length; i++) {
                px[i] = padLeft + (int) (((double) i / (batches.length - 1)) * plotW);
                py[i] = padTop + plotH - (int) ((speedup[i] / maxSpeedup) * plotH);
            }

            for (int i = 0; i < batches.length - 1; i++) {
                g2.drawLine(px[i], py[i], px[i + 1], py[i + 1]);
            }

            // Draw Points and Annotations
            for (int i = 0; i < batches.length; i++) {
                g2.setColor(new Color(30, 75, 140));
                g2.fill(new Ellipse2D.Double(px[i] - 6, py[i] - 6, 12, 12));
                g2.setColor(Color.WHITE);
                g2.fill(new Ellipse2D.Double(px[i] - 3, py[i] - 3, 6, 6));

                g2.setColor(new Color(20, 50, 100));
                g2.setFont(new Font("SansSerif", Font.BOLD, 12));
                g2.drawString(String.format("%.2fx", speedup[i]), px[i] - 16, py[i] - 12);

                // X-Axis labels
                g2.setFont(new Font("SansSerif", Font.BOLD, 11));
                g2.setColor(new Color(50, 60, 80));
                g2.drawString(batches[i] + " Images", px[i] - 25, padTop + plotH + 25);
            }

            // Plot Parallel Efficiency Curve (Orange dashed)
            g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{5, 4}, 0));
            g2.setColor(new Color(217, 119, 6));

            int[] pyEff = new int[batches.length];
            for (int i = 0; i < batches.length; i++) {
                pyEff[i] = padTop + plotH - (int) ((efficiency[i] / 100.0) * plotH);
            }
            for (int i = 0; i < batches.length - 1; i++) {
                g2.drawLine(px[i], pyEff[i], px[i + 1], pyEff[i + 1]);
            }
            for (int i = 0; i < batches.length; i++) {
                g2.fillRect(px[i] - 4, pyEff[i] - 4, 8, 8);
                g2.setFont(new Font("SansSerif", Font.BOLD, 11));
                g2.drawString(String.format("%.1f%%", efficiency[i]), px[i] - 16, pyEff[i] + 18);
            }

            // Legend
            int legX = padLeft + 20;
            int legY = padTop + 20;
            g2.setColor(new Color(240, 245, 255));
            g2.fillRect(legX, legY, 260, 50);
            g2.setColor(new Color(180, 200, 230));
            g2.drawRect(legX, legY, 260, 50);

            g2.setColor(new Color(30, 75, 140));
            g2.fillRect(legX + 12, legY + 12, 16, 5);
            g2.drawString("Speedup Factor (S = Tseq / Tpar)", legX + 36, legY + 18);

            g2.setColor(new Color(217, 119, 6));
            g2.fillRect(legX + 12, legY + 32, 16, 5);
            g2.drawString("Parallel Efficiency (E = S / 12 Cores)", legX + 36, legY + 38);
        }
    }

    // =========================================================================
    // PANEL 2: BATCH LATENCY (1T vs 12T) BAR CHART
    // =========================================================================
    private static class BatchLatencyPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            g2.setColor(new Color(250, 252, 255));
            g2.fillRect(0, 0, w, h);

            int padLeft = 90;
            int padRight = 60;
            int padTop = 70;
            int padBottom = 70;

            int plotW = w - padLeft - padRight;
            int plotH = h - padTop - padBottom;

            // Title
            g2.setColor(new Color(26, 54, 93));
            g2.setFont(new Font("SansSerif", Font.BOLD, 17));
            g2.drawString("Batch Processing Wall-Clock Latency: Sequential vs. Multi-Threaded", padLeft, 38);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g2.setColor(new Color(80, 95, 120));
            g2.drawString("Demonstrates latency reduction from 5.5s down to 863 ms (17.3 ms/img) using 12 parallel threads", padLeft, 56);

            String[] categories = {"5 Images", "10 Images", "25 Images", "50 Images"};
            int[] seqMs = {593, 1304, 3058, 5527};
            int[] parMs = {141, 215, 450, 863};
            double maxTime = 6000.0;

            // Y-Axis
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            for (int t = 0; t <= 6000; t += 1000) {
                int y = padTop + plotH - (int) ((t / maxTime) * plotH);
                g2.setColor(new Color(225, 230, 240));
                g2.drawLine(padLeft, y, padLeft + plotW, y);
                g2.setColor(new Color(60, 75, 100));
                g2.drawString(t + " ms", padLeft - 60, y + 4);
            }

            int groupW = plotW / categories.length;
            int barW = Math.min(45, groupW / 3);

            for (int i = 0; i < categories.length; i++) {
                int groupCenter = padLeft + i * groupW + groupW / 2;

                int hSeq = (int) ((seqMs[i] / maxTime) * plotH);
                int hPar = (int) ((parMs[i] / maxTime) * plotH);

                int xSeq = groupCenter - barW - 4;
                int ySeq = padTop + plotH - hSeq;

                int xPar = groupCenter + 4;
                int yPar = padTop + plotH - hPar;

                // Sequential Bar (Red)
                g2.setColor(new Color(229, 62, 62));
                g2.fillRoundRect(xSeq, ySeq, barW, hSeq, 4, 4);

                // Parallel Bar (Blue)
                g2.setColor(new Color(43, 108, 176));
                g2.fillRoundRect(xPar, yPar, barW, hPar, 4, 4);

                // Labels above bars
                g2.setFont(new Font("SansSerif", Font.BOLD, 10));
                g2.setColor(new Color(180, 40, 40));
                g2.drawString(seqMs[i] + " ms", xSeq - 4, ySeq - 6);

                g2.setColor(new Color(20, 70, 140));
                g2.drawString(parMs[i] + " ms", xPar - 4, yPar - 6);

                // X label
                g2.setFont(new Font("SansSerif", Font.BOLD, 12));
                g2.setColor(new Color(40, 50, 70));
                g2.drawString(categories[i], groupCenter - 25, padTop + plotH + 25);
            }

            // Legend
            int legX = padLeft + 20;
            int legY = padTop + 20;
            g2.setColor(new Color(240, 245, 255));
            g2.fillRect(legX, legY, 280, 55);
            g2.setColor(new Color(180, 200, 230));
            g2.drawRect(legX, legY, 280, 55);

            g2.setColor(new Color(229, 62, 62));
            g2.fillRect(legX + 12, legY + 14, 16, 12);
            g2.setColor(new Color(40, 50, 70));
            g2.drawString("Sequential (1 Core Baseline)", legX + 36, legY + 24);

            g2.setColor(new Color(43, 108, 176));
            g2.fillRect(legX + 12, legY + 34, 16, 12);
            g2.drawString("Parallel (12 Cores ForkJoinPool)", legX + 36, legY + 44);
        }
    }

    // =========================================================================
    // PANEL 3: LARGE IMAGE RESOLUTION SCALING
    // =========================================================================
    private static class ResolutionScalingPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            g2.setColor(new Color(250, 252, 255));
            g2.fillRect(0, 0, w, h);

            int padLeft = 85;
            int padRight = 80;
            int padTop = 70;
            int padBottom = 70;

            int plotW = w - padLeft - padRight;
            int plotH = h - padTop - padBottom;

            // Title
            g2.setColor(new Color(26, 54, 93));
            g2.setFont(new Font("SansSerif", Font.BOLD, 17));
            g2.drawString("Investigation 1: Large Image Scaling (Execution Time vs Megapixels)", padLeft, 38);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g2.setColor(new Color(80, 95, 120));
            g2.drawString("Contrasts SLIC's linear O(N) scaling (4.3s at 9.44 MP) against Felzenszwalb's O(E log E) sorting bottleneck (66.8s)", padLeft, 56);

            String[] resLabels = {"256x256\n(0.07 MP)", "512x512\n(0.26 MP)", "1024x1024\n(1.05 MP)", "2048x2048\n(4.19 MP)", "3072x3072\n(9.44 MP)"};
            double[] mpVals = {0.065, 0.262, 1.048, 4.194, 9.437};

            int[] slicTime = {29, 126, 415, 2076, 4336};
            int[] felzTime = {382, 1139, 5507, 27270, 66840};
            int[] kmeansTime = {77, 181, 485, 2427, 4466};
            int[] otsuTime = {24, 17, 28, 78, 185};

            double maxTime = 70000.0;

            // Grid
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            for (int t = 0; t <= 70000; t += 10000) {
                int y = padTop + plotH - (int) ((t / maxTime) * plotH);
                g2.setColor(new Color(225, 230, 240));
                g2.drawLine(padLeft, y, padLeft + plotW, y);
                g2.setColor(new Color(60, 75, 100));
                g2.drawString((t / 1000) + " s", padLeft - 50, y + 4);
            }

            int n = mpVals.length;
            int[] px = new int[n];
            for (int i = 0; i < n; i++) {
                px[i] = padLeft + (int) ((mpVals[i] / 9.5) * plotW);
            }

            // Draw Felzenszwalb (Red)
            g2.setColor(new Color(229, 62, 62));
            g2.setStroke(new BasicStroke(2.8f));
            for (int i = 0; i < n - 1; i++) {
                int y1 = padTop + plotH - (int) ((felzTime[i] / maxTime) * plotH);
                int y2 = padTop + plotH - (int) ((felzTime[i + 1] / maxTime) * plotH);
                g2.drawLine(px[i], y1, px[i + 1], y2);
            }

            // Draw SLIC (Blue)
            g2.setColor(new Color(43, 108, 176));
            for (int i = 0; i < n - 1; i++) {
                int y1 = padTop + plotH - (int) ((slicTime[i] / maxTime) * plotH);
                int y2 = padTop + plotH - (int) ((slicTime[i + 1] / maxTime) * plotH);
                g2.drawLine(px[i], y1, px[i + 1], y2);
            }

            // Draw KMeans (Teal)
            g2.setColor(new Color(49, 151, 149));
            for (int i = 0; i < n - 1; i++) {
                int y1 = padTop + plotH - (int) ((kmeansTime[i] / maxTime) * plotH);
                int y2 = padTop + plotH - (int) ((kmeansTime[i + 1] / maxTime) * plotH);
                g2.drawLine(px[i], y1, px[i + 1], y2);
            }

            // Annotations at 9.44 MP
            int yFelzLast = padTop + plotH - (int) ((felzTime[n - 1] / maxTime) * plotH);
            g2.setColor(new Color(229, 62, 62));
            g2.drawString("Felzenszwalb: 66.8s (37.7M edges sorted)", px[n - 1] - 250, yFelzLast - 10);

            int ySlicLast = padTop + plotH - (int) ((slicTime[n - 1] / maxTime) * plotH);
            g2.setColor(new Color(43, 108, 176));
            g2.drawString("SLIC: 4.3s (Linear O(N))", px[n - 1] - 150, ySlicLast - 10);

            // X-Axis ticks
            g2.setColor(new Color(40, 50, 70));
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            for (int i = 0; i < n; i++) {
                g2.drawLine(px[i], padTop + plotH, px[i], padTop + plotH + 5);
                g2.drawString(String.format("%.2f MP", mpVals[i]), px[i] - 15, padTop + plotH + 20);
            }

            // Legend
            int legX = padLeft + 20;
            int legY = padTop + 20;
            g2.setColor(new Color(240, 245, 255));
            g2.fillRect(legX, legY, 300, 75);
            g2.setColor(new Color(180, 200, 230));
            g2.drawRect(legX, legY, 300, 75);

            g2.setColor(new Color(43, 108, 176));
            g2.fillRect(legX + 12, legY + 12, 16, 5);
            g2.drawString("SLIC Superpixels (O(N) Linear Time)", legX + 36, legY + 18);

            g2.setColor(new Color(49, 151, 149));
            g2.fillRect(legX + 12, legY + 32, 16, 5);
            g2.drawString("K-Means (k=8, Parallel Lloyd)", legX + 36, legY + 38);

            g2.setColor(new Color(229, 62, 62));
            g2.fillRect(legX + 12, legY + 52, 16, 5);
            g2.drawString("Felzenszwalb Graph (O(E log E) Sorting)", legX + 36, legY + 58);
        }
    }

    // =========================================================================
    // PANEL 4: VISUAL SEGMENTATION GALLERY
    // =========================================================================
    private static class VisualGalleryPanel extends JPanel {
        private final File outputDir;

        public VisualGalleryPanel(File outputDir) {
            this.outputDir = outputDir;
            setLayout(new BorderLayout());

            JPanel cardsPanel = new JPanel(new GridLayout(2, 2, 10, 10));
            cardsPanel.setBackground(new Color(245, 248, 252));
            cardsPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

            File visualsDir = new File(outputDir, "visuals");

            String[][] items = {
                    {"Felzenszwalb Graph (k=350, minSize=60)", "comparison_Felzenszwalb_Graph__k_350__minSize_60_.png"},
                    {"SLIC Superpixels (k=120, m=15)", "comparison_SLIC_Superpixels__k_120__m_15_.png"},
                    {"K-Means Clustering (k=8, Spatial XY)", "comparison_K-Means__k_8__spatial_true_.png"},
                    {"Multi-Level Otsu (classes=3)", "comparison_Multi-Level_Otsu__classes_3_.png"}
            };

            for (String[] it : items) {
                JPanel card = new JPanel(new BorderLayout(5, 5));
                card.setBorder(BorderFactory.createLineBorder(new Color(210, 220, 235), 1));
                card.setBackground(Color.WHITE);

                JLabel title = new JLabel(it[0], SwingConstants.CENTER);
                title.setFont(new Font("SansSerif", Font.BOLD, 12));
                title.setForeground(new Color(26, 54, 93));
                card.add(title, BorderLayout.NORTH);

                File imgFile = new File(visualsDir, it[1]);
                if (imgFile.exists()) {
                    try {
                        BufferedImage img = ImageUtils.loadImage(imgFile);
                        // Scale to fit card
                        Image scaled = img.getScaledInstance(460, 230, Image.SCALE_SMOOTH);
                        card.add(new JLabel(new ImageIcon(scaled)), BorderLayout.CENTER);
                    } catch (Exception ignored) {}
                }
                cardsPanel.add(card);
            }

            add(new JScrollPane(cardsPanel), BorderLayout.CENTER);
        }
    }
}
