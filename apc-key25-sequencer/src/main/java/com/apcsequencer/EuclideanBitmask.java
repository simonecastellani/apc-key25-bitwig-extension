package com.apcsequencer;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates Euclidean (Bjorklund) rhythmic bitmasks.
 *
 * <p>The Bjorklund algorithm distributes {@code pulses} active steps as evenly as possible
 * across {@code steps} total slots, always starting with an active step.
 * Used for the per-track Euclidean Distribution feature (per-track knob 7).</p>
 *
 * <p>Reference: Toussaint, "The Euclidean Algorithm Generates Traditional Musical Rhythms" (2005).</p>
 */
public final class EuclideanBitmask {

    private EuclideanBitmask() {}

    /**
     * Generates a Euclidean bitmask using the Bjorklund list-redistribution algorithm.
     *
     * @param pulses number of active steps (0 ≤ pulses ≤ steps)
     * @param steps  total number of steps
     * @return boolean array of length {@code steps}; {@code true} = active step
     */
    public static boolean[] generate(int pulses, int steps) {
        boolean[] pattern = new boolean[steps];
        if (pulses <= 0) return pattern;
        if (pulses >= steps) {
            java.util.Arrays.fill(pattern, true);
            return pattern;
        }

        // Represent the sequence as two lists of int[] groups.
        // "left" groups start with 1; "right" groups are the shorter/remainder list.
        // Each redistribution step: append each right[i] to left[i]; the excess of
        // whichever list was longer becomes the new right list.
        List<int[]> left  = new ArrayList<>(pulses);
        List<int[]> right = new ArrayList<>(steps - pulses);

        for (int i = 0; i < pulses;        i++) left.add(new int[]{1});
        for (int i = 0; i < steps - pulses; i++) right.add(new int[]{0});

        while (right.size() > 1) {
            int n = Math.min(left.size(), right.size());
            List<int[]> newLeft = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                newLeft.add(concat(left.get(i), right.get(i)));
            }
            // The excess of whichever list was longer becomes the new right
            List<int[]> remainder = new ArrayList<>(
                left.size() > right.size()
                    ? left.subList(n, left.size())
                    : right.subList(n, right.size())
            );
            left  = newLeft;
            right = remainder;
        }

        // Flatten left + right into the output array
        int pos = 0;
        for (int[] group : left)  for (int v : group) pattern[pos++] = (v == 1);
        for (int[] group : right) for (int v : group) pattern[pos++] = (v == 1);
        return rotateToSecondPulse(pattern);
    }

    private static boolean[] rotateToSecondPulse(boolean[] pattern) {
        int first = -1;
        int second = -1;
        for (int i = 0; i < pattern.length; i++) {
            if (!pattern[i]) {
                continue;
            }
            if (first < 0) {
                first = i;
            } else {
                second = i;
                break;
            }
        }

        int shift = second >= 0 ? second : (first >= 0 ? first : 0);
        if (shift == 0) {
            return pattern;
        }

        boolean[] rotated = new boolean[pattern.length];
        for (int i = 0; i < pattern.length; i++) {
            rotated[i] = pattern[(i + shift) % pattern.length];
        }
        return rotated;
    }

    private static int[] concat(int[] a, int[] b) {
        int[] result = new int[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
