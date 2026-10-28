package com.submithub.core.diff;

/**
 * @param ignoreWhitespace true면 앞뒤 공백을 무시하고, 줄 안의 연속 공백·탭을 하나로 보고 비교한다.
 *                         (들여쓰기만 바꾼 줄을 변경으로 보지 않음)
 * @param maxEditDistance  최대 편집 거리(D) 상한 (초과 시 전체 교체로 반환)
 */
public record DiffOptions(boolean ignoreWhitespace, int maxEditDistance) {

    public static final int DEFAULT_MAX_EDIT_DISTANCE = 5000;
    public static final DiffOptions DEFAULT = new DiffOptions(false, DEFAULT_MAX_EDIT_DISTANCE);
    public static final DiffOptions IGNORE_WHITESPACE = new DiffOptions(true, DEFAULT_MAX_EDIT_DISTANCE);

    public DiffOptions(boolean ignoreWhitespace) {
        this(ignoreWhitespace, DEFAULT_MAX_EDIT_DISTANCE);
    }

    String key(String line) {
        if (!ignoreWhitespace) {
            return line;
        }
        return line.strip().replaceAll("\\s+", " ");
    }
}
