package com.segmentation.algorithm;

import java.awt.image.BufferedImage;

/**
 * Common contract for all computational image segmentation algorithms.
 */
public interface SegmentationAlgorithm {

    /**
     * Unique display name of the algorithm.
     */
    String getName();

    /**
     * Executes segmentation on the provided image and returns a detailed SegmentationResult.
     *
     * @param image Input BufferedImage
     * @return Result containing pixel label map, segment count, and execution profile
     */
    SegmentationResult segment(BufferedImage image);
}
