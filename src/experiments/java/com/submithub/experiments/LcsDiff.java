// 실험용 복사본: 기존 Swing 버전의 LCS 기반 diff (D:/Dev/Workspaces/eclipse_workspace/src/com/capstone/util/DiffUtil.java).
// 패키지와 클래스 이름만 바꾸고 알고리즘은 그대로 두었다. 성능 비교(docs/EVALUATION.md)에서 MyersDiff 의 상대로만 쓴다.
package com.submithub.experiments;

import java.util.ArrayList;
import java.util.List;

/**
 * 두 텍스트(제출 버전)를 줄 단위로 비교하는 diff 유틸.
 * LCS(최장 공통 부분열) 기반으로 EQUAL / ADDED / REMOVED 라인을 구분한다.
 * 외부 라이브러리 없이 순수 Java로 구현 (Myers diff의 단순화 버전).
 */
public class LcsDiff {

    public enum LineType { EQUAL, ADDED, REMOVED }

    public static class DiffLine {
        public final LineType type;
        public final String text;
        public final int oldLineNo;  // -1 이면 old에 없음
        public final int newLineNo;  // -1 이면 new에 없음

        public DiffLine(LineType type, String text, int oldLineNo, int newLineNo) {
            this.type = type;
            this.text = text;
            this.oldLineNo = oldLineNo;
            this.newLineNo = newLineNo;
        }
    }

    /**
     * oldText -> newText 로의 변경 사항을 줄 단위 diff 리스트로 반환한다.
     */
    public static List<DiffLine> diff(String oldText, String newText) {
        String[] a = (oldText == null ? "" : oldText).split("\n", -1);
        String[] b = (newText == null ? "" : newText).split("\n", -1);

        int n = a.length, m = b.length;
        int[][] lcs = new int[n + 1][m + 1];

        // LCS 테이블 구축
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (a[i].equals(b[j])) {
                    lcs[i][j] = lcs[i + 1][j + 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
        }

        // 역추적하며 diff 결과 생성
        List<DiffLine> result = new ArrayList<>();
        int i = 0, j = 0;
        while (i < n && j < m) {
            if (a[i].equals(b[j])) {
                result.add(new DiffLine(LineType.EQUAL, a[i], i + 1, j + 1));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                result.add(new DiffLine(LineType.REMOVED, a[i], i + 1, -1));
                i++;
            } else {
                result.add(new DiffLine(LineType.ADDED, b[j], -1, j + 1));
                j++;
            }
        }
        while (i < n) {
            result.add(new DiffLine(LineType.REMOVED, a[i], i + 1, -1));
            i++;
        }
        while (j < m) {
            result.add(new DiffLine(LineType.ADDED, b[j], -1, j + 1));
            j++;
        }
        return result;
    }

    /** 변경 통계 요약 (예: "+5 -2") */
    public static String summarize(List<DiffLine> diffLines) {
        long added = diffLines.stream().filter(d -> d.type == LineType.ADDED).count();
        long removed = diffLines.stream().filter(d -> d.type == LineType.REMOVED).count();
        return "+" + added + " / -" + removed;
    }
}
