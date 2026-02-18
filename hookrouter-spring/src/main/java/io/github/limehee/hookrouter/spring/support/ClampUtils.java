package io.github.limehee.hookrouter.spring.support;

public final class ClampUtils {

    private ClampUtils() {
    }

    public static int clampInt(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    public static long clampLong(long value, long min, long max) {
        return Math.min(Math.max(value, min), max);
    }

    public static float clampFloat(float value, float min, float max) {
        return Math.min(Math.max(value, min), max);
    }

    public static double clampDouble(double value, double min, double max) {
        return Math.min(Math.max(value, min), max);
    }
}
