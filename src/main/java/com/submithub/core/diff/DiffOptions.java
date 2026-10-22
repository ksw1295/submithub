package com.submithub.core.diff;

/**
 * @param ignoreWhitespace true면 앞뒤 공백을 무시하고, 줄 안의 연속 공백·탭을 하나로 보고 비교한다.
 *                         (들여쓰기만 바꾼 줄을 변경으로 보지 않음)
 */
public record DiffOptions(boolean ignoreWhitespace) {

    public static final DiffOptions DEFAULT = new DiffOptions(false);
    public static final DiffOptions IGNORE_WHITESPACE = new DiffOptions(true);

    String key(String line) {
        if (!ignoreWhitespace) {
            return line;
        }
        return line.strip().replaceAll("\\s+", " ");
    }
}
