package com.submithub.core.diff;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MyersDiffTest {

    @Test
    void identicalTextsHaveNoChanges() {
        DiffResult r = MyersDiff.diff("a\nb\nc", "a\nb\nc");
        assertFalse(r.hasChanges());
        assertEquals(3, r.lines().size());
    }

    @Test
    void detectsModifiedLineWithCorrectLineNumbers() {
        DiffResult r = MyersDiff.diff("a\nb\nc", "a\nB\nc");
        assertEquals("+1 / -1", r.summary());
        // 삭제가 추가보다 먼저 나와야 한다
        assertEquals(DiffType.REMOVED, r.lines().get(1).type());
        assertEquals(2, r.lines().get(1).oldLineNo());
        assertEquals(DiffType.ADDED, r.lines().get(2).type());
        assertEquals(2, r.lines().get(2).newLineNo());
        DiffLine last = r.lines().get(3);
        assertEquals(3, last.oldLineNo());
        assertEquals(3, last.newLineNo());
    }

    @Test
    void insertAndDelete() {
        DiffResult r = MyersDiff.diff("a\nb\nc\nd", "a\nc\nd\ne");
        assertEquals(1, r.removedCount());
        assertEquals(1, r.addedCount());
    }

    @Test
    void emptyOldOrNew() {
        assertEquals("+3 / -0", MyersDiff.diff("", "x\ny\nz").summary());
        assertEquals("+0 / -2", MyersDiff.diff("x\ny", null).summary());
        assertEquals("+0 / -0", MyersDiff.diff(null, "").summary());
    }

    @Test
    void crlfAndLfAreTheSame() {
        assertFalse(MyersDiff.diff("a\r\nb\r\nc\r\n", "a\nb\nc\n").hasChanges());
    }

    @Test
    void ignoreWhitespaceOption() {
        String oldText = "if (x) {\n  run();\n}";
        String newText = "if (x) {\n\t\trun();\n}";
        assertTrue(MyersDiff.diff(oldText, newText).hasChanges());
        assertFalse(MyersDiff.diff(oldText, newText, DiffOptions.IGNORE_WHITESPACE).hasChanges());
    }

    /** 무작위 입력 1,000건: diff 로 두 텍스트를 복원할 수 있고, 변경 줄 수가 최소(LCS 기준)인지 확인 */
    @Test
    void randomizedReconstructionAndMinimality() {
        Random random = new Random(42);
        for (int t = 0; t < 1000; t++) {
            List<String> a = randomLines(random);
            List<String> b = randomLines(random);
            DiffResult r = MyersDiff.diff(a, b, DiffOptions.DEFAULT);

            List<String> oldRebuilt = new ArrayList<>();
            List<String> newRebuilt = new ArrayList<>();
            int lastOld = 0;
            int lastNew = 0;
            for (DiffLine l : r.lines()) {
                if (l.type() != DiffType.ADDED) {
                    oldRebuilt.add(l.text());
                    assertEquals(lastOld + 1, l.oldLineNo());
                    lastOld = l.oldLineNo();
                }
                if (l.type() != DiffType.REMOVED) {
                    newRebuilt.add(l.text());
                    assertEquals(lastNew + 1, l.newLineNo());
                    lastNew = l.newLineNo();
                }
            }
            assertEquals(a, oldRebuilt);
            assertEquals(b, newRebuilt);

            int lcs = lcs(a, b);
            assertEquals(a.size() + b.size() - 2 * lcs, r.addedCount() + r.removedCount(),
                    "최소 diff 가 아님: " + a + " -> " + b);
        }
    }

    @Test
    void largeFileWithFewChangesIsFast() {
        StringBuilder oldText = new StringBuilder();
        StringBuilder newText = new StringBuilder();
        for (int i = 0; i < 20000; i++) {
            oldText.append("line ").append(i).append('\n');
            newText.append(i % 2000 == 0 ? "changed " + i : "line " + i).append('\n');
        }
        long start = System.nanoTime();
        DiffResult r = MyersDiff.diff(oldText.toString(), newText.toString());
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertEquals("+10 / -10", r.summary());
        assertTrue(ms < 2000, "너무 느림: " + ms + "ms");
    }

    private static List<String> randomLines(Random random) {
        int size = random.nextInt(12);
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            lines.add(String.valueOf((char) ('a' + random.nextInt(4))));
        }
        return lines;
    }

    private static int lcs(List<String> a, List<String> b) {
        int[][] dp = new int[a.size() + 1][b.size() + 1];
        for (int i = a.size() - 1; i >= 0; i--) {
            for (int j = b.size() - 1; j >= 0; j--) {
                dp[i][j] = a.get(i).equals(b.get(j)) ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1]);
            }
        }
        return dp[0][0];
    }
}
