package com.paris.mocap.cycle;

public final class SettingsCycle {
    public static final int[] DURATIONS_TICKS = {0, 200, 600, 1200, 2400, 6000};
    public static final double[] AREA_RADII = {0.0, 10.0, 20.0, 30.0, 50.0};
    public static final int[] TICK_RATES = {1, 2, 4};
    public static final double[] SPEEDS = {
        -4.0, -3.0, -2.0, -1.5, -1.0, -0.5, -0.25,
        0.25, 0.5, 1.0, 1.5, 2.0, 3.0, 4.0
    };
    public static final int[] LOOP_COUNTS = {-1, 2, 3, 5, 10};

    private SettingsCycle() {
    }

    public static int nextInt(int[] table, int current) {
        int index = indexOf(table, current);
        return table[(index + 1) % table.length];
    }

    public static double nextDouble(double[] table, double current) {
        int index = indexOf(table, current);
        return table[(index + 1) % table.length];
    }

    public static double stepDouble(double[] table, double current, int delta) {
        int index = nearestIndex(table, current) + delta;
        if (index < 0) {
            return table[0];
        }
        if (index >= table.length) {
            return table[table.length - 1];
        }
        return table[index];
    }

    private static int nearestIndex(double[] table, double current) {
        int index = indexOf(table, current);
        if (index >= 0 && Double.compare(table[index], current) == 0) {
            return index;
        }
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < table.length; i++) {
            double dist = Math.abs(table[i] - current);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    private static int indexOf(int[] table, int current) {
        for (int i = 0; i < table.length; i++) {
            if (table[i] == current) {
                return i;
            }
        }
        return table.length - 1;
    }

    private static int indexOf(double[] table, double current) {
        for (int i = 0; i < table.length; i++) {
            if (Double.compare(table[i], current) == 0) {
                return i;
            }
        }
        return table.length - 1;
    }
}
