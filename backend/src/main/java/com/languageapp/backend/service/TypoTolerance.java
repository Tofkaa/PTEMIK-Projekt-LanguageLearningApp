package com.languageapp.backend.service;

import java.util.Arrays;

/** Thresholded Levenshtein: only five diagonals matter for the existing two-edit feedback. */
final class TypoTolerance {
    private static final int LIMIT = 2;
    private static final int OUTSIDE = LIMIT + 1;
    private TypoTolerance() { }

    static boolean withinTwoEdits(String a, String b) {
        if (Math.abs(a.length() - b.length()) > LIMIT) return false;
        if (a.length() < b.length()) { String swap = a; a = b; b = swap; }
        if (b.isEmpty()) return a.length() <= LIMIT;
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        Arrays.fill(previous, OUTSIDE);
        for (int j = 0; j <= Math.min(b.length(), LIMIT); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int first = Math.max(1, i - LIMIT);
            int last = Math.min(b.length(), i + LIMIT);
            current[0] = Math.min(i, OUTSIDE);
            if (first > 1) current[first - 1] = OUTSIDE;
            int best = current[0];
            for (int j = first; j <= last; j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(OUTSIDE, Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1)));
                best = Math.min(best, current[j]);
            }
            if (last < b.length()) current[last + 1] = OUTSIDE;
            if (best > LIMIT) return false;
            int[] swap = previous; previous = current; current = swap;
        }
        return previous[b.length()] <= LIMIT;
    }
}
