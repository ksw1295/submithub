package com.submithub.experiments;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** diff 성능 실험용 입력 생성: n 줄짜리 원본과, 그 중 ratio 비율의 줄을 수정/삭제/추가한 새 버전 */
final class DiffData {

    private static final String[] VOCAB = new String[600];

    static {
        Random r = new Random(7);
        for (int i = 0; i < VOCAB.length; i++) {
            StringBuilder sb = new StringBuilder();
            int len = 2 + r.nextInt(8);
            for (int k = 0; k < len; k++) {
                sb.append((char) ('a' + r.nextInt(26)));
            }
            VOCAB[i] = sb.toString();
        }
    }

    private DiffData() {
    }

    private static String line(Random r, int indentLevel) {
        // 코드처럼 어느 정도 겹치는 줄이 생기도록 어휘를 600개로 제한하고 3~4 단어로 한 줄을 만든다
        StringBuilder sb = new StringBuilder("    ".repeat(indentLevel));
        int words = 3 + r.nextInt(2);
        for (int i = 0; i < words; i++) {
            sb.append(VOCAB[r.nextInt(VOCAB.length)]).append(i < words - 1 ? " " : ";");
        }
        return sb.toString();
    }

    /** @return {oldText, newText} (줄바꿈은 \n) */
    static String[] generate(int n, double ratio, long seed) {
        Random r = new Random(seed);
        List<String> base = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            base.add(line(r, i % 3));
        }
        int edits = (int) Math.round(n * ratio);
        Set<Integer> positions = new HashSet<>();
        while (positions.size() < Math.min(edits, n)) {
            positions.add(r.nextInt(n));
        }
        List<String> changed = new ArrayList<>(n + edits);
        for (int i = 0; i < n; i++) {
            if (!positions.contains(i)) {
                changed.add(base.get(i));
                continue;
            }
            switch (r.nextInt(3)) {
                case 0 -> changed.add(line(r, i % 3));          // 수정
                case 1 -> {
                    // 삭제: 아무것도 추가하지 않는다
                }
                default -> {                                     // 추가
                    changed.add(base.get(i));
                    changed.add(line(r, i % 3));
                }
            }
        }
        return new String[]{String.join("\n", base) + "\n", String.join("\n", changed) + "\n"};
    }
}
