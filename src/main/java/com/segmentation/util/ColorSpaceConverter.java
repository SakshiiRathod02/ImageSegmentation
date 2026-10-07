package com.segmentation.util;

/**
 * Utility for fast color space conversions between standard sRGB and CIELAB.
 * CIELAB is perceptually uniform, meaning Euclidean distances correlate with
 * perceived color differences (Delta E), which is vital for high-quality
 * segmentation algorithms like SLIC and K-Means.
 */
public final class ColorSpaceConverter {

    private ColorSpaceConverter() {}

    // D65 standard illuminant reference white points
    private static final double X_REF = 95.047;
    private static final double Y_REF = 100.000;
    private static final double Z_REF = 108.883;

    /**
     * Converts RGB values [0..255] into CIELAB coordinates.
     *
     * @param r Red component [0, 255]
     * @param g Green component [0, 255]
     * @param b Blue component [0, 255]
     * @param lab Output array of size 3: [0] = L* (0..100), [1] = a* (-128..127), [2] = b* (-128..127)
     */
    public static void rgbToLab(int r, int g, int b, double[] lab) {
        // 1. Convert sRGB to linear RGB
        double rLinear = pivotRgb(r / 255.0);
        double gLinear = pivotRgb(g / 255.0);
        double bLinear = pivotRgb(b / 255.0);

        // 2. Convert linear RGB to CIE XYZ (Observer 2°, Illuminant D65)
        double x = (rLinear * 0.4124564 + gLinear * 0.3575761 + bLinear * 0.1804375) * 100.0;
        double y = (rLinear * 0.2126729 + gLinear * 0.7151522 + bLinear * 0.0721750) * 100.0;
        double z = (rLinear * 0.0193339 + gLinear * 0.1191920 + bLinear * 0.9503041) * 100.0;

        // 3. Convert XYZ to CIELAB
        double xr = pivotXyz(x / X_REF);
        double yr = pivotXyz(y / Y_REF);
        double zr = pivotXyz(z / Z_REF);

        lab[0] = Math.max(0.0, 116.0 * yr - 16.0); // L*
        lab[1] = 500.0 * (xr - yr);                // a*
        lab[2] = 200.0 * (yr - zr);                // b*
    }

    /**
     * Converts CIELAB coordinates back to packed sRGB integer (0xAARRGGBB).
     */
    public static int labToRgb(double l, double a, double b) {
        double yr = (l + 16.0) / 116.0;
        double xr = a / 500.0 + yr;
        double zr = yr - b / 200.0;

        double x = inversePivotXyz(xr) * X_REF;
        double y = inversePivotXyz(yr) * Y_REF;
        double z = inversePivotXyz(zr) * Z_REF;

        // Convert XYZ to linear RGB
        double rLinear = (x * 3.2404542 - y * 1.5371385 - z * 0.4985314) / 100.0;
        double gLinear = (-x * 0.9692660 + y * 1.8760108 + z * 0.0415560) / 100.0;
        double bLinear = (x * 0.0556434 - y * 0.2040259 + z * 1.0572252) / 100.0;

        int r = clampColor((int) Math.round(inversePivotRgb(rLinear) * 255.0));
        int g = clampColor((int) Math.round(inversePivotRgb(gLinear) * 255.0));
        int bl = clampColor((int) Math.round(inversePivotRgb(bLinear) * 255.0));

        return (0xFF << 24) | (r << 16) | (g << 8) | bl;
    }

    private static double pivotRgb(double c) {
        return (c > 0.04045) ? Math.pow((c + 0.055) / 1.055, 2.4) : (c / 12.92);
    }

    private static double inversePivotRgb(double c) {
        return (c > 0.0031308) ? (1.055 * Math.pow(c, 1.0 / 2.4) - 0.055) : (12.92 * c);
    }

    private static double pivotXyz(double t) {
        return (t > 0.008856) ? Math.cbrt(t) : (7.787 * t + 16.0 / 116.0);
    }

    private static double inversePivotXyz(double t) {
        double t3 = t * t * t;
        return (t3 > 0.008856) ? t3 : ((t - 16.0 / 116.0) / 7.787);
    }

    private static int clampColor(int val) {
        return Math.max(0, Math.min(255, val));
    }
}
