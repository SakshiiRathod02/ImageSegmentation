package com.segmentation.benchmark;

import com.segmentation.util.ImageUtils;
import com.segmentation.util.ThreadConfig;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Swing dashboard that plots values from the benchmark CSV files.
 */
public class BenchmarkChartViewer extends JFrame {

    private static final Color[] SERIES_COLORS = {
            new Color(43, 108, 176), new Color(217, 119, 6),
            new Color(49, 151, 149), new Color(180, 50, 60),
            new Color(120, 80, 170)
    };

    public BenchmarkChartViewer(File outputDir) {
        super("Image Segmentation: Scalability & Performance Analytics Dashboard");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1050, 750);
        setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.BOLD, 13));
        tabs.addTab("Thread Scaling", buildThreadScalingPanel(outputDir));
        tabs.addTab("Batch Latency", buildBatchLatencyPanel(outputDir));
        tabs.addTab("Resolution Scaling", buildResolutionPanel(outputDir));
        tabs.addTab("Visual Segmentation Gallery", new VisualGalleryPanel(outputDir));
        add(tabs, BorderLayout.CENTER);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 8));
        statusPanel.setBackground(new Color(24, 28, 36));
        JLabel status = new JLabel(String.format(
                "Available logical processors: %d | Configured workers: %d | Java: %s | Charts read measured CSV data",
                ThreadConfig.getAvailableProcessors(), ThreadConfig.getThreadCount(),
                System.getProperty("java.version")));
        status.setForeground(new Color(200, 210, 230));
        status.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusPanel.add(status);
        add(statusPanel, BorderLayout.SOUTH);
    }

    public static void showDashboard(File outputDir) {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("[GUI Dashboard] Headless environment detected; skipping window display.");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                System.err.println("[GUI Dashboard] Could not set system look and feel: " + e.getMessage());
            }
            BenchmarkChartViewer viewer = new BenchmarkChartViewer(outputDir);
            viewer.setVisible(true);
            viewer.toFront();
        });
    }

    private static JPanel buildThreadScalingPanel(File outputDir) {
        File csv = new File(outputDir, "benchmark_thread_scaling.csv");
        List<Series> series = new ArrayList<>();
        String message = null;
        try {
            List<String[]> rows = readCsv(csv);
            List<Point> speedups = new ArrayList<>();
            List<Point> efficiencies = new ArrayList<>();
            for (String[] row : rows) {
                if (row.length < 12 || !row[11].isEmpty()) {
                    continue;
                }
                double threads = number(row, 3);
                double speedup = number(row, 7);
                double efficiency = number(row, 8);
                if (Double.isFinite(threads) && Double.isFinite(speedup) && Double.isFinite(efficiency)
                        && threads > 0 && speedup > 0) {
                    speedups.add(new Point(threads, speedup));
                    efficiencies.add(new Point(threads, efficiency));
                }
            }
            speedups.sort(Comparator.comparingDouble(point -> point.x));
            efficiencies.sort(Comparator.comparingDouble(point -> point.x));
            if (!speedups.isEmpty()) {
                series.add(new Series("Speedup (x)", speedups, SERIES_COLORS[0], false));
                series.add(new Series("Parallel efficiency (%)", efficiencies, SERIES_COLORS[1], true));
            } else {
                message = "No successful thread-scaling measurements found. Run the thread-scaling benchmark first.";
            }
        } catch (IOException | NumberFormatException e) {
            message = "Could not read measured thread-scaling CSV: " + e.getMessage();
        }
        return new ChartPanel("Measured Thread Scaling",
                "Each point comes from benchmark_thread_scaling.csv; efficiency uses the right-hand axis.",
                "Configured worker threads", "Speedup", "Efficiency (%)",
                series, message, true);
    }

    private static JPanel buildBatchLatencyPanel(File outputDir) {
        File csv = new File(outputDir, "benchmark_dataset_batch_scaling.csv");
        List<Series> series = new ArrayList<>();
        String message = null;
        try {
            Map<String, List<Point>> grouped = new LinkedHashMap<>();
            for (String[] row : readCsv(csv)) {
                int resolutionIndex = findResolutionIndex(row);
                int batchIndex = resolutionIndex - 1;
                int modeIndex = resolutionIndex + 1;
                int timeIndex = resolutionIndex + 2;
                if (resolutionIndex < 1 || batchIndex < 0 || timeIndex >= row.length) {
                    continue;
                }
                String mode = row[modeIndex];
                if (!mode.startsWith("Sequential") && !mode.startsWith("Parallel")) {
                    continue;
                }
                String key = mode.startsWith("Sequential") ? "Sequential" : mode;
                grouped.computeIfAbsent(key, ignored -> new ArrayList<>())
                        .add(new Point(number(row, batchIndex), number(row, timeIndex)));
            }
            int colorIndex = 0;
            for (Map.Entry<String, List<Point>> entry : grouped.entrySet()) {
                entry.getValue().sort(Comparator.comparingDouble(point -> point.x));
                series.add(new Series(entry.getKey(), entry.getValue(),
                        SERIES_COLORS[colorIndex++ % SERIES_COLORS.length], false));
            }
            if (series.isEmpty()) {
                message = "No batch measurements found. Run the benchmark first.";
            }
        } catch (IOException | NumberFormatException e) {
            message = "Could not read measured batch CSV: " + e.getMessage();
        }
        return new ChartPanel("Measured Batch Processing Time",
                "Total wall-clock time from benchmark_dataset_batch_scaling.csv.",
                "Images in batch", "Total time (ms)", null, series, message, false);
    }

    private static JPanel buildResolutionPanel(File outputDir) {
        File csv = new File(outputDir, "benchmark_resolution_scaling.csv");
        List<Series> series = new ArrayList<>();
        String message = null;
        try {
            Map<String, List<Point>> grouped = new LinkedHashMap<>();
            for (String[] row : readCsv(csv)) {
                int resolutionIndex = findResolutionIndex(row);
                if (resolutionIndex < 0 || resolutionIndex + 3 >= row.length) {
                    continue;
                }
                String algorithm = String.join(",", java.util.Arrays.copyOfRange(row, 0, resolutionIndex));
                grouped.computeIfAbsent(algorithm, ignored -> new ArrayList<>())
                        .add(new Point(number(row, resolutionIndex + 2), number(row, resolutionIndex + 3)));
            }
            int colorIndex = 0;
            for (Map.Entry<String, List<Point>> entry : grouped.entrySet()) {
                entry.getValue().sort(Comparator.comparingDouble(point -> point.x));
                series.add(new Series(entry.getKey(), entry.getValue(),
                        SERIES_COLORS[colorIndex++ % SERIES_COLORS.length], false));
            }
            if (series.isEmpty()) {
                message = "No resolution measurements found. Run the benchmark first.";
            }
        } catch (IOException | NumberFormatException e) {
            message = "Could not read measured resolution CSV: " + e.getMessage();
        }
        return new ChartPanel("Measured Resolution Scaling",
                "Execution times are read from benchmark_resolution_scaling.csv.",
                "Image size (megapixels)", "Execution time (ms)", null,
                series, message, false);
    }

    private static double number(String[] row, int index) {
        return Double.parseDouble(row[index].replace("%", "").trim());
    }

    private static int findResolutionIndex(String[] row) {
        for (int i = 0; i < row.length; i++) {
            if (row[i].matches("\\d+x\\d+")) {
                return i;
            }
        }
        return -1;
    }

    private static List<String[]> readCsv(File file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        if (!file.isFile()) {
            throw new IOException("File does not exist: " + file.getAbsolutePath());
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }
                if (!line.trim().isEmpty()) {
                    rows.add(parseCsvLine(line));
                }
            }
        }
        return rows;
    }

    private static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(ch);
            }
        }
        fields.add(field.toString());
        return fields.toArray(new String[0]);
    }

    private static final class Point {
        final double x;
        final double y;

        Point(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class Series {
        final String name;
        final List<Point> points;
        final Color color;
        final boolean rightAxis;

        Series(String name, List<Point> points, Color color, boolean rightAxis) {
            this.name = name;
            this.points = points;
            this.color = color;
            this.rightAxis = rightAxis;
        }
    }

    private static final class ChartPanel extends JPanel {
        private final String title;
        private final String subtitle;
        private final String xLabel;
        private final String yLabel;
        private final String rightLabel;
        private final List<Series> series;
        private final String message;
        private final boolean percentRightAxis;

        ChartPanel(String title, String subtitle, String xLabel, String yLabel,
                   String rightLabel, List<Series> series, String message,
                   boolean percentRightAxis) {
            this.title = title;
            this.subtitle = subtitle;
            this.xLabel = xLabel;
            this.yLabel = yLabel;
            this.rightLabel = rightLabel;
            this.series = series;
            this.message = message;
            this.percentRightAxis = percentRightAxis;
            setBackground(new Color(250, 252, 255));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int left = 85, right = rightLabel == null ? 45 : 75, top = 80, bottom = 75;
            int plotWidth = Math.max(1, getWidth() - left - right);
            int plotHeight = Math.max(1, getHeight() - top - bottom);

            g.setColor(new Color(26, 54, 93));
            g.setFont(new Font("SansSerif", Font.BOLD, 17));
            g.drawString(title, left, 35);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(new Color(80, 95, 120));
            g.drawString(subtitle, left, 55);

            if (message != null || series.isEmpty()) {
                g.setColor(new Color(90, 100, 115));
                g.drawString(message == null ? "No measured data available." : message,
                        left, top + 35);
                g.dispose();
                return;
            }

            double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
            double maxY = 0.0;
            double maxRightY = 100.0;
            for (Series s : series) {
                for (Point point : s.points) {
                    minX = Math.min(minX, point.x);
                    maxX = Math.max(maxX, point.x);
                    if (!s.rightAxis) {
                        maxY = Math.max(maxY, point.y);
                    } else {
                        maxRightY = Math.max(maxRightY, point.y);
                    }
                }
            }
            if (maxX == minX) {
                minX -= 0.5;
                maxX += 0.5;
            }
            maxY = Math.max(1.0, Math.ceil(maxY / 5.0) * 5.0);
            if (percentRightAxis) {
                maxRightY = Math.ceil(maxRightY / 20.0) * 20.0;
            }

            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            for (int tick = 0; tick <= 5; tick++) {
                int y = top + plotHeight - tick * plotHeight / 5;
                g.setColor(new Color(225, 230, 240));
                g.drawLine(left, y, left + plotWidth, y);
                g.setColor(new Color(60, 75, 100));
                g.drawString(String.format("%.1f", maxY * tick / 5.0), left - 48, y + 4);
                if (percentRightAxis) {
                    g.setColor(new Color(180, 100, 20));
                    g.drawString(String.format("%.0f%%", maxRightY * tick / 5.0),
                            left + plotWidth + 10, y + 4);
                }
            }
            g.setColor(new Color(45, 55, 70));
            g.drawString(yLabel, 12, top - 14);
            if (rightLabel != null) {
                g.setColor(new Color(180, 100, 20));
                g.drawString(rightLabel, left + plotWidth - 100, top - 14);
            }
            g.setColor(new Color(45, 55, 70));
            g.drawString(xLabel, left + plotWidth / 2 - 35, getHeight() - 18);

            for (int tick = 0; tick <= 5; tick++) {
                double value = minX + (maxX - minX) * tick / 5.0;
                int x = left + tick * plotWidth / 5;
                g.setColor(new Color(60, 75, 100));
                g.drawString(String.format("%.2f", value), x - 15, top + plotHeight + 18);
            }

            int legendX = left + 10;
            int legendY = top + 10;
            for (Series s : series) {
                if (s.points.isEmpty()) {
                    continue;
                }
                g.setColor(s.color);
                g.setStroke(new BasicStroke(2.5f));
                Point previous = null;
                for (Point point : s.points) {
                    double yMax = s.rightAxis && percentRightAxis ? maxRightY : maxY;
                    int x = left + (int) ((point.x - minX) / (maxX - minX) * plotWidth);
                    int y = top + plotHeight - (int) (Math.min(yMax, point.y) / yMax * plotHeight);
                    if (previous != null) {
                        int prevX = left + (int) ((previous.x - minX) / (maxX - minX) * plotWidth);
                        int prevY = top + plotHeight - (int) (Math.min(yMax, previous.y) / yMax * plotHeight);
                        g.drawLine(prevX, prevY, x, y);
                    }
                    g.fillOval(x - 4, y - 4, 8, 8);
                    previous = point;
                }
                g.setFont(new Font("SansSerif", Font.BOLD, 11));
                g.drawLine(legendX, legendY + 4, legendX + 20, legendY + 4);
                g.drawString(s.name, legendX + 26, legendY + 8);
                legendY += 20;
            }
            g.dispose();
        }
    }

    private static final class VisualGalleryPanel extends JPanel {
        VisualGalleryPanel(File outputDir) {
            setLayout(new BorderLayout());
            JPanel cards = new JPanel(new GridLayout(2, 2, 10, 10));
            cards.setBackground(new Color(245, 248, 252));
            cards.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            File visuals = new File(outputDir, "visuals");
            String[][] items = {
                    {"Felzenszwalb Graph", "comparison_Felzenszwalb_Graph__k_350__minSize_60_.png"},
                    {"SLIC Superpixels", "comparison_SLIC_Superpixels__k_120__m_15_.png"},
                    {"K-Means", "comparison_K-Means__k_8__spatial_true_.png"},
                    {"Multi-Level Otsu", "comparison_Multi-Level_Otsu__classes_3_.png"}
            };
            for (String[] item : items) {
                JPanel card = new JPanel(new BorderLayout(5, 5));
                card.setBorder(BorderFactory.createLineBorder(new Color(210, 220, 235), 1));
                card.setBackground(Color.WHITE);
                JLabel title = new JLabel(item[0], SwingConstants.CENTER);
                title.setFont(new Font("SansSerif", Font.BOLD, 12));
                card.add(title, BorderLayout.NORTH);
                File imageFile = new File(visuals, item[1]);
                if (imageFile.isFile()) {
                    try {
                        BufferedImage image = ImageUtils.loadImage(imageFile);
                        card.add(new JLabel(new ImageIcon(image.getScaledInstance(460, 230, Image.SCALE_SMOOTH))),
                                BorderLayout.CENTER);
                    } catch (IOException e) {
                        card.add(new JLabel("Could not load image: " + e.getMessage(), SwingConstants.CENTER),
                                BorderLayout.CENTER);
                    }
                } else {
                    card.add(new JLabel("Run the benchmark to generate this image.", SwingConstants.CENTER),
                            BorderLayout.CENTER);
                }
                cards.add(card);
            }
            add(new JScrollPane(cards), BorderLayout.CENTER);
        }
    }
}
