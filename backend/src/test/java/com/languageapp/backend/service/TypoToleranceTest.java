package com.languageapp.backend.service;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TypoToleranceTest {
    @Test void matchesAnIndependentFullMatrixForEveryShortBinaryStringPair() {
        List<String> words = new ArrayList<>(List.of(""));
        for (int size = 1; size <= 5; size++) {
            for (int bits = 0; bits < (1 << size); bits++) {
                StringBuilder text = new StringBuilder();
                for (int i = 0; i < size; i++) text.append((bits & (1 << i)) == 0 ? 'a' : 'b');
                words.add(text.toString());
            }
        }
        words.addAll(List.of("árvíz", "árviz", "tükör", "tükrök", "😀alma"));
        for (String a : words) for (String b : words)
            assertEquals(distance(a,b) <= 2, TypoTolerance.withinTwoEdits(a,b), () -> a + " / " + b);
    }
    @Test void checksMaximumLengthSimilarInputsWithoutAQuadraticMatrix() {
        String text = "a".repeat(10_000);
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            assertTrue(TypoTolerance.withinTwoEdits(text, text.substring(0,9998)+"bb"));
            assertFalse(TypoTolerance.withinTwoEdits(text, text.substring(0,9997)+"bbb"));
            assertFalse(TypoTolerance.withinTwoEdits(text,"b"));
        });
    }
    private int distance(String a, String b) {
        int[][] matrix = new int[a.length()+1][b.length()+1];
        for (int i=0;i<=a.length();i++) matrix[i][0]=i;
        for (int j=0;j<=b.length();j++) matrix[0][j]=j;
        for (int i=1;i<=a.length();i++) for (int j=1;j<=b.length();j++)
            matrix[i][j]=Math.min(matrix[i-1][j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1),Math.min(matrix[i-1][j]+1,matrix[i][j-1]+1));
        return matrix[a.length()][b.length()];
    }
}
