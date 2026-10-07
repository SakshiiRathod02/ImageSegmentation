package com.segmentation.dataset;

import com.segmentation.util.ImageUtils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Discovers and loads image files from local disk directories.
 * Supports standard formats: PNG, JPEG, JPG, BMP.
 * Allows easy ingestion of Kaggle datasets (e.g., BSDS500, Oxford-IIIT Pet, CamVid, Cityscapes).
 */
public class ImageDatasetLoader {

    public static class ImageEntry {
        public final File file;
        public final String name;

        public ImageEntry(File file) {
            this.file = file;
            this.name = file.getName();
        }

        public BufferedImage load() throws IOException {
            return ImageUtils.loadImage(file);
        }
    }

    /**
     * Scans a directory and returns all recognized image entries.
     */
    public static List<ImageEntry> loadDirectory(File dir) {
        List<ImageEntry> entries = new ArrayList<>();
        if (!dir.exists() || !dir.isDirectory()) {
            return entries;
        }

        File[] files = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".png") || lower.endsWith(".jpg") ||
                   lower.endsWith(".jpeg") || lower.endsWith(".bmp");
        });

        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File f : files) {
                entries.add(new ImageEntry(f));
            }
        }
        return entries;
    }
}
