package com.submithub.core.similarity;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimilarityCheckerTest {

    private static final String ORIGINAL = """
            public class Grade {
                public static String grade(int score) {
                    if (score >= 90) {
                        return "A";
                    } else if (score >= 80) {
                        return "B";
                    } else if (score >= 70) {
                        return "C";
                    }
                    return "F";
                }

                public static double average(int[] scores) {
                    int total = 0;
                    for (int i = 0; i < scores.length; i++) {
                        total += scores[i];
                    }
                    return (double) total / scores.length;
                }
            }
            """;

    /** 변수명·클래스명 변경, 주석 추가, 들여쓰기·줄바꿈 변경, 문자열 값 변경 */
    private static final String DISGUISED = """
            // 학번 2024123457 과제 제출
            public class MyGrade
            {
              /* 점수를 학점으로 바꿈 */
              public static String toLetter(int pts)
              {
                if (pts >= 90) { return "A+"; }
                else if (pts >= 80) { return "B+"; }
                else if (pts >= 70) { return "C+"; }
                return "F";
              }

              public static double mean(int[] arr)
              {
                int sum = 0;   // 합계
                for (int k = 0; k < arr.length; k++) { sum += arr[k]; }
                return (double) sum / arr.length;
              }
            }
            """;

    private static final String DIFFERENT = """
            import java.util.*;

            public class Stack<T> {
                private final List<T> items = new ArrayList<>();

                public void push(T item) {
                    items.add(item);
                }

                public T pop() {
                    if (items.isEmpty()) throw new RuntimeException("empty");
                    return items.remove(items.size() - 1);
                }

                public boolean isEmpty() {
                    return items.isEmpty();
                }
            }
            """;

    @Test
    void tokenizerRemovesNamesCommentsAndLiterals() {
        List<CodeTokenizer.Token> a = CodeTokenizer.tokenize("int total = price * 3; // 합계");
        List<CodeTokenizer.Token> b = CodeTokenizer.tokenize("int   sum=cost*10;/* x */");
        assertEquals(a.stream().map(CodeTokenizer.Token::value).toList(),
                b.stream().map(CodeTokenizer.Token::value).toList());
    }

    @Test
    void tokenizerTracksLineNumbers() {
        List<CodeTokenizer.Token> t = CodeTokenizer.tokenize("a\n/* 1\n2 */\nb \"x\"");
        assertEquals(1, t.get(0).line());
        assertEquals(4, t.get(1).line());
        assertEquals("S", t.get(2).value());
    }

    @Test
    void disguisedCopyScoresHigh() {
        SimilarityChecker.Pair p = pair(ORIGINAL, DISGUISED, null);
        assertTrue(p.score() >= 0.8, "위장 복사 점수가 낮음: " + p.score());
        assertFalse(p.matchedLinesA().isEmpty());
        assertFalse(p.matchedLinesB().isEmpty());
    }

    @Test
    void unrelatedCodeScoresLow() {
        SimilarityChecker.Pair p = pair(ORIGINAL, DIFFERENT, null);
        assertTrue(p.score() < 0.3, "관련 없는 코드 점수가 높음: " + p.score());
    }

    @Test
    void baselineTemplateIsExcluded() {
        // 둘 다 교수 제공 뼈대(ORIGINAL)를 그대로 포함하고, 각자 다른 코드만 덧붙인 경우
        String a = ORIGINAL + "\n" + DIFFERENT;
        String b = ORIGINAL + "\nclass Queue { int[] q = new int[10]; int head; void add(int v) { q[head++] = v; } }";
        assertTrue(pair(a, b, null).score() > 0.5);
        assertTrue(pair(a, b, ORIGINAL).score() < 0.3, "뼈대 코드를 빼지 않음");
    }

    @Test
    void compareAllSortsAndFilters() {
        Map<String, String> subs = new LinkedHashMap<>();
        subs.put("stu01", ORIGINAL);
        subs.put("stu02", DISGUISED);
        subs.put("stu03", DIFFERENT);
        subs.put("stu04", "int x;");   // 너무 짧아서 제외
        List<SimilarityChecker.Pair> pairs = SimilarityChecker.compareAll(subs, null, 0.5);
        assertEquals(1, pairs.size());
        assertEquals("stu01", pairs.get(0).idA());
        assertEquals("stu02", pairs.get(0).idB());
    }

    private static SimilarityChecker.Pair pair(String a, String b, String baseline) {
        var base = SimilarityChecker.baseline(baseline);
        return SimilarityChecker.compare(
                SimilarityChecker.document("a", a, base),
                SimilarityChecker.document("b", b, base));
    }
}
