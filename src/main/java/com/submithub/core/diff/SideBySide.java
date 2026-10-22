package com.submithub.core.diff;

import java.util.ArrayList;
import java.util.List;

/**
 * diff 결과를 좌(이전 버전) / 우(새 버전) 분할 화면용 행으로 바꾼다.
 * 연속된 삭제·추가 줄은 순서대로 짝지어 "수정됨(MODIFIED)" 행으로 보여준다.
 */
public final class SideBySide {

    public enum RowType { EQUAL, MODIFIED, REMOVED, ADDED, SKIPPED }

    /**
     * @param leftLineNo  이전 버전 줄 번호 (없으면 0)
     * @param leftText    이전 버전 내용 (없으면 null)
     * @param rightLineNo 새 버전 줄 번호 (없으면 0)
     * @param rightText   새 버전 내용 (없으면 null)
     * @param skipped     SKIPPED 행일 때 접힌 줄 수
     */
    public record Row(RowType type, int leftLineNo, String leftText, int rightLineNo, String rightText, int skipped) {
    }

    private SideBySide() {
    }

    public static List<Row> build(DiffResult diff) {
        List<Row> rows = new ArrayList<>();
        List<DiffLine> removed = new ArrayList<>();
        List<DiffLine> added = new ArrayList<>();
        for (DiffLine line : diff.lines()) {
            switch (line.type()) {
                case REMOVED -> removed.add(line);
                case ADDED -> added.add(line);
                case EQUAL -> {
                    flush(rows, removed, added);
                    rows.add(new Row(RowType.EQUAL, line.oldLineNo(), line.text(), line.newLineNo(), line.text(), 0));
                }
            }
        }
        flush(rows, removed, added);
        return rows;
    }

    /**
     * 변경 없는 긴 구간을 접는다. 변경 줄 앞뒤로 context 줄만 남기고 나머지는 SKIPPED 행 하나로 바꾼다.
     */
    public static List<Row> fold(List<Row> rows, int context) {
        int size = rows.size();
        boolean[] keep = new boolean[size];
        for (int i = 0; i < size; i++) {
            if (rows.get(i).type() != RowType.EQUAL) {
                for (int j = Math.max(0, i - context); j <= Math.min(size - 1, i + context); j++) {
                    keep[j] = true;
                }
            }
        }
        List<Row> out = new ArrayList<>();
        int skipped = 0;
        for (int i = 0; i < size; i++) {
            if (keep[i]) {
                if (skipped > 0) {
                    out.add(new Row(RowType.SKIPPED, 0, null, 0, null, skipped));
                    skipped = 0;
                }
                out.add(rows.get(i));
            } else {
                skipped++;
            }
        }
        if (skipped > 0) {
            out.add(new Row(RowType.SKIPPED, 0, null, 0, null, skipped));
        }
        return out;
    }

    private static void flush(List<Row> rows, List<DiffLine> removed, List<DiffLine> added) {
        int pairs = Math.min(removed.size(), added.size());
        for (int i = 0; i < pairs; i++) {
            DiffLine r = removed.get(i);
            DiffLine a = added.get(i);
            rows.add(new Row(RowType.MODIFIED, r.oldLineNo(), r.text(), a.newLineNo(), a.text(), 0));
        }
        for (int i = pairs; i < removed.size(); i++) {
            DiffLine r = removed.get(i);
            rows.add(new Row(RowType.REMOVED, r.oldLineNo(), r.text(), 0, null, 0));
        }
        for (int i = pairs; i < added.size(); i++) {
            DiffLine a = added.get(i);
            rows.add(new Row(RowType.ADDED, 0, null, a.newLineNo(), a.text(), 0));
        }
        removed.clear();
        added.clear();
    }
}
