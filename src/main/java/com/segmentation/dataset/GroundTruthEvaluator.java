package com.segmentation.dataset;

import com.segmentation.util.ImageUtils;

import java.util.Arrays;

/**
 * Quantitative accuracy evaluator comparing unsupervised segmentation output
 * against known ground-truth region masks.
 * Computes:
 * - Mean Intersection-over-Union (mIoU / Jaccard Index)
 * - Mean Dice Similarity Coefficient (mDice / F1-score)
 * - Pixel Accuracy (under optimal majority region matching)
 * - Boundary Precision, Boundary Recall, and Boundary F1-Score
 */
public class GroundTruthEvaluator {

    public static class EvaluationReport {
        public final double meanIoU;
        public final double meanDice;
        public final double pixelAccuracy;
        public final double boundaryPrecision;
        public final double boundaryRecall;
        public final double boundaryF1;

        public EvaluationReport(double meanIoU, double meanDice, double pixelAccuracy,
                                double boundaryPrecision, double boundaryRecall, double boundaryF1) {
            this.meanIoU = meanIoU;
            this.meanDice = meanDice;
            this.pixelAccuracy = pixelAccuracy;
            this.boundaryPrecision = boundaryPrecision;
            this.boundaryRecall = boundaryRecall;
            this.boundaryF1 = boundaryF1;
        }

        @Override
        public String toString() {
            return String.format("mIoU: %.4f | mDice: %.4f | Accuracy: %.2f%% | Boundary [P: %.3f, R: %.3f, F1: %.3f]",
                    meanIoU, meanDice, pixelAccuracy * 100.0,
                    boundaryPrecision, boundaryRecall, boundaryF1);
        }
    }

    /**
     * Evaluates a predicted label map against the ground-truth label map.
     */
    public static EvaluationReport evaluate(int[] predicted, int numPredSegments,
                                            int[] groundTruth, int numGtRegions,
                                            int width, int height) {
        int numPixels = width * height;

        // 1. Build contingency matrix: contingency[gt][pred] = overlap count
        int[][] contingency = new int[numGtRegions][numPredSegments];
        int[] gtSizes = new int[numGtRegions];
        int[] predSizes = new int[numPredSegments];

        for (int i = 0; i < numPixels; i++) {
            int g = groundTruth[i];
            int p = predicted[i];
            if (g >= 0 && g < numGtRegions && p >= 0 && p < numPredSegments) {
                contingency[g][p]++;
                gtSizes[g]++;
                predSizes[p]++;
            }
        }

        // 2. Compute mIoU and mDice across ground-truth regions
        double sumIoU = 0.0;
        double sumDice = 0.0;
        int correctlyMappedPixels = 0;

        for (int g = 0; g < numGtRegions; g++) {
            if (gtSizes[g] == 0) continue;
            double bestIoU = 0.0;
            double bestDice = 0.0;
            int bestPredOverlap = 0;

            for (int p = 0; p < numPredSegments; p++) {
                int intersection = contingency[g][p];
                int union = gtSizes[g] + predSizes[p] - intersection;
                if (union > 0) {
                    double iou = (double) intersection / union;
                    if (iou > bestIoU) {
                        bestIoU = iou;
                    }
                    double dice = (2.0 * intersection) / (gtSizes[g] + predSizes[p]);
                    if (dice > bestDice) {
                        bestDice = dice;
                    }
                }
                if (intersection > bestPredOverlap) {
                    bestPredOverlap = intersection;
                }
            }
            sumIoU += bestIoU;
            sumDice += bestDice;
            correctlyMappedPixels += bestPredOverlap;
        }

        double meanIoU = sumIoU / numGtRegions;
        double meanDice = sumDice / numGtRegions;
        double pixelAccuracy = (double) correctlyMappedPixels / numPixels;

        // 3. Boundary Precision, Recall, and F1 with tolerance radius (1 pixel)
        boolean[] gtBoundary = ImageUtils.computeBoundaryMask(groundTruth, width, height);
        boolean[] predBoundary = ImageUtils.computeBoundaryMask(predicted, width, height);

        int gtBoundaryCount = 0;
        int predBoundaryCount = 0;
        int matchedGtCount = 0;
        int matchedPredCount = 0;

        int tolerance = 1;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                if (gtBoundary[idx]) {
                    gtBoundaryCount++;
                    if (hasNearbyBoundary(predBoundary, x, y, width, height, tolerance)) {
                        matchedGtCount++;
                    }
                }
                if (predBoundary[idx]) {
                    predBoundaryCount++;
                    if (hasNearbyBoundary(gtBoundary, x, y, width, height, tolerance)) {
                        matchedPredCount++;
                    }
                }
            }
        }

        double recall = (gtBoundaryCount > 0) ? (double) matchedGtCount / gtBoundaryCount : 1.0;
        double precision = (predBoundaryCount > 0) ? (double) matchedPredCount / predBoundaryCount : 1.0;
        double f1 = (precision + recall > 0) ? (2.0 * precision * recall) / (precision + recall) : 0.0;

        return new EvaluationReport(meanIoU, meanDice, pixelAccuracy, precision, recall, f1);
    }

    private static boolean hasNearbyBoundary(boolean[] boundary, int cx, int cy, int w, int h, int r) {
        int minX = Math.max(0, cx - r);
        int maxX = Math.min(w - 1, cx + r);
        int minY = Math.max(0, cy - r);
        int maxY = Math.min(h - 1, cy + r);

        for (int y = minY; y <= maxY; y++) {
            int rowOffset = y * w;
            for (int x = minX; x <= maxX; x++) {
                if (boundary[rowOffset + x]) {
                    return true;
                }
            }
        }
        return false;
    }
}
