package com.submithub.core.review;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LineTrackerTest {

    private static final String V1 = String.join("\n",
            "public int sum(int[] a) {",      // 1
            "    int s = 0;",                  // 2
            "    for (int i = 0; i <= a.length; i++) {", // 3  ← 교수 코멘트: 범위 오류
            "        s += a[i];",              // 4
            "    }",                           // 5
            "    return s;",                   // 6
            "}");                              // 7

    @Test
    void unchangedLineFollowsShiftedLineNumber() {
        String v2 = "// 합계 계산\n// 작성자: 홍길동\n" + V1;   // 위에 두 줄 추가
        LineTracker.Result r = LineTracker.track(List.of(V1, v2), 3);
        assertEquals(LineTracker.Status.UNCHANGED, r.status());
        assertEquals(5, r.startLine());
        assertEquals(-1, r.changedAtIndex());
    }

    @Test
    void modifiedLineIsDetected() {
        String v2 = V1.replace("i <= a.length", "i < a.length");
        LineTracker.Result r = LineTracker.track(List.of(V1, v2), 3);
        assertEquals(LineTracker.Status.MODIFIED, r.status());
        assertEquals(1, r.changedAtIndex());
    }

    @Test
    void indentationOnlyChangeIsNotTreatedAsFix() {
        String v2 = V1.replace("    for (int i", "\t\tfor (int i");
        assertEquals(LineTracker.Status.UNCHANGED, LineTracker.track(List.of(V1, v2), 3).status());
    }

    @Test
    void deletedRangeIsDetected() {
        String v2 = String.join("\n",
                "public int sum(int[] a) {",
                "    return java.util.Arrays.stream(a).sum();",
                "}");
        // 2~6번 줄이 사라지고 한 줄로 바뀜 → 일부는 수정, 일부는 삭제 → MODIFIED
        assertEquals(LineTracker.Status.MODIFIED, LineTracker.track(List.of(V1, v2), 2, 6).status());

        String v3 = String.join("\n", "public int sum(int[] a) {", "}");
        assertEquals(LineTracker.Status.DELETED, LineTracker.track(List.of(V1, v3), 2, 6).status());
    }

    @Test
    void reportsWhichVersionFixedIt() {
        String v2 = "// 주석 추가\n" + V1;                                   // 아직 안 고침
        String v3 = v2.replace("i <= a.length", "i < a.length");           // 여기서 고침
        String v4 = v3 + "\n// 끝";
        LineTracker.Result r = LineTracker.track(List.of(V1, v2, v3, v4), 3);
        assertEquals(LineTracker.Status.MODIFIED, r.status());
        assertEquals(2, r.changedAtIndex());   // versions[2] = v3
    }

    @Test
    void rangeStaysUnchanged() {
        String v2 = V1 + "\n\npublic int avg(int[] a) { return sum(a) / a.length; }";
        LineTracker.Result r = LineTracker.track(List.of(V1, v2), 3, 5);
        assertEquals(LineTracker.Status.UNCHANGED, r.status());
        assertEquals(3, r.startLine());
        assertEquals(5, r.endLine());
    }

    @Test
    void invalidRangeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> LineTracker.track(List.of(V1), 0));
        assertThrows(IllegalArgumentException.class, () -> LineTracker.track(List.of(V1), 5, 3));
    }
}
