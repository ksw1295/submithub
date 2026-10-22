package com.submithub.grading;

import java.util.ArrayList;
import java.util.List;

/** 채점 출력 비교: 줄바꿈 종류, 줄 끝 공백, 끝의 빈 줄 차이는 무시한다. 그 밖의 차이는 모두 틀린 것으로 본다. */
public final class OutputComparator {

    private OutputComparator() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String unified = text.replace("\r\n", "\n").replace('\r', '\n');
        List<String> lines = new ArrayList<>(List.of(unified.split("\n", -1)));
        for (int i = 0; i < lines.size(); i++) {
            lines.set(i, stripTrailing(lines.get(i)));
        }
        int end = lines.size();
        while (end > 0 && lines.get(end - 1).isEmpty()) {
            end--;
        }
        return String.join("\n", lines.subList(0, end));
    }

    public static boolean matches(String expected, String actual) {
        return normalize(expected).equals(normalize(actual));
    }

    private static String stripTrailing(String s) {
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == ' ' || s.charAt(end - 1) == '\t')) {
            end--;
        }
        return s.substring(0, end);
    }
}
