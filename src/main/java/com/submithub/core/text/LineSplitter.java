package com.submithub.core.text;

import java.util.ArrayList;
import java.util.List;

/**
 * 텍스트를 줄 목록으로 나눈다.
 * CRLF / CR / LF 를 모두 같은 줄바꿈으로 취급해서
 * Windows에서 저장한 버전과 Mac/Linux에서 저장한 버전을 비교해도 모든 줄이 바뀐 것으로 나오지 않게 한다.
 */
public final class LineSplitter {

    private LineSplitter() {
    }

    public static List<String> split(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r') {
                lines.add(current.toString());
                current.setLength(0);
                if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
            } else if (c == '\n') {
                lines.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        // 마지막 줄 뒤에 줄바꿈이 없으면 그 줄도 포함. 줄바꿈으로 끝나면 빈 줄을 추가하지 않는다.
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }
}
