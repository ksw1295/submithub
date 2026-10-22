package com.submithub.core.diff;

/**
 * diff 결과의 한 줄.
 *
 * @param type      EQUAL / ADDED / REMOVED
 * @param oldLineNo 이전 버전의 줄 번호(1부터). 이전 버전에 없는 줄이면 {@link #NONE}
 * @param newLineNo 새 버전의 줄 번호(1부터). 새 버전에 없는 줄이면 {@link #NONE}
 * @param text      화면에 보여줄 원문
 */
public record DiffLine(DiffType type, int oldLineNo, int newLineNo, String text) {

    public static final int NONE = 0;

    public static DiffLine equal(int oldLineNo, int newLineNo, String text) {
        return new DiffLine(DiffType.EQUAL, oldLineNo, newLineNo, text);
    }

    public static DiffLine added(int newLineNo, String text) {
        return new DiffLine(DiffType.ADDED, NONE, newLineNo, text);
    }

    public static DiffLine removed(int oldLineNo, String text) {
        return new DiffLine(DiffType.REMOVED, oldLineNo, NONE, text);
    }
}
