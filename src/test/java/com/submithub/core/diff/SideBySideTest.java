package com.submithub.core.diff;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SideBySideTest {

    @Test
    void pairsRemovedAndAddedAsModified() {
        List<SideBySide.Row> rows = SideBySide.build(MyersDiff.diff("a\nb\nc\nd", "a\nB\nC\nX\nd"));
        // a | a, b→B, c→C, (없음)→X, d | d
        assertEquals(5, rows.size());
        assertEquals(SideBySide.RowType.EQUAL, rows.get(0).type());
        assertEquals(SideBySide.RowType.MODIFIED, rows.get(1).type());
        assertEquals("b", rows.get(1).leftText());
        assertEquals("B", rows.get(1).rightText());
        assertEquals(SideBySide.RowType.MODIFIED, rows.get(2).type());
        assertEquals(SideBySide.RowType.ADDED, rows.get(3).type());
        assertEquals(4, rows.get(3).rightLineNo());
        assertEquals(SideBySide.RowType.EQUAL, rows.get(4).type());
        assertEquals(4, rows.get(4).leftLineNo());
        assertEquals(5, rows.get(4).rightLineNo());
    }

    @Test
    void foldCollapsesUnchangedRegions() {
        StringBuilder oldText = new StringBuilder();
        StringBuilder newText = new StringBuilder();
        for (int i = 1; i <= 30; i++) {
            oldText.append(i).append('\n');
            newText.append(i == 15 ? "fifteen" : String.valueOf(i)).append('\n');
        }
        List<SideBySide.Row> folded = SideBySide.fold(SideBySide.build(MyersDiff.diff(oldText.toString(), newText.toString())), 3);
        // 접힘(1~11줄) + 앞 3줄(12~14) + 수정 1줄(15) + 뒤 3줄(16~18) + 접힘(19~30줄)
        assertEquals(9, folded.size());
        assertEquals(SideBySide.RowType.SKIPPED, folded.get(0).type());
        assertEquals(11, folded.get(0).skipped());
        assertEquals(SideBySide.RowType.MODIFIED, folded.get(4).type());
        assertEquals(SideBySide.RowType.SKIPPED, folded.get(8).type());
        assertEquals(12, folded.get(8).skipped());
    }
}
