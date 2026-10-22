package com.submithub.core.similarity;

import java.util.ArrayList;
import java.util.List;

/**
 * Winnowing 지문 추출 (Schleimer, Wilkerson, Aiken, "Winnowing: Local Algorithms for
 * Document Fingerprinting", SIGMOD 2003). 스탠퍼드 표절 검사 시스템 MOSS 의 핵심 알고리즘이다.
 *
 * 1) 토큰 k개씩 묶은 k-gram 마다 해시를 만든다
 * 2) 연속된 w개 해시를 창(window)으로 보고, 각 창에서 가장 작은 해시만 지문으로 고른다
 *
 * 이렇게 하면 길이가 (w + k - 1) 토큰 이상인 공통 구간은 반드시 하나 이상의 지문을 공유하게 되고,
 * 학생 수가 많아도 지문 집합끼리만 비교하면 되므로 모든 쌍을 LCS 로 비교하는 것보다 훨씬 빠르다.
 */
public final class Winnowing {

    /** k-gram 하나의 해시와, 그 k-gram 이 시작·끝나는 원본 줄 번호 */
    public record Fingerprint(long hash, int startLine, int endLine) {
    }

    public static final int DEFAULT_K = 5;
    public static final int DEFAULT_W = 4;

    private Winnowing() {
    }

    public static List<Fingerprint> fingerprints(List<CodeTokenizer.Token> tokens) {
        return fingerprints(tokens, DEFAULT_K, DEFAULT_W);
    }

    public static List<Fingerprint> fingerprints(List<CodeTokenizer.Token> tokens, int k, int w) {
        List<Fingerprint> result = new ArrayList<>();
        int gramCount = tokens.size() - k + 1;
        if (gramCount <= 0) {
            return result;
        }

        long[] hashes = new long[gramCount];
        for (int i = 0; i < gramCount; i++) {
            long h = 1125899906842597L;
            for (int j = 0; j < k; j++) {
                h = 31 * h + tokens.get(i + j).value().hashCode();
            }
            hashes[i] = mix(h);
        }

        if (gramCount <= w) {
            // 창 하나보다 짧으면 가장 작은 해시 하나만
            int min = 0;
            for (int i = 1; i < gramCount; i++) {
                if (hashes[i] <= hashes[min]) min = i;
            }
            result.add(of(tokens, hashes, min, k));
            return result;
        }

        int lastPicked = -1;
        for (int start = 0; start + w <= gramCount; start++) {
            int min = start;
            for (int i = start + 1; i < start + w; i++) {
                if (hashes[i] <= hashes[min]) min = i;   // 같은 값이면 오른쪽 것 (논문 규칙)
            }
            if (min != lastPicked) {
                result.add(of(tokens, hashes, min, k));
                lastPicked = min;
            }
        }
        return result;
    }

    private static Fingerprint of(List<CodeTokenizer.Token> tokens, long[] hashes, int index, int k) {
        return new Fingerprint(hashes[index], tokens.get(index).line(), tokens.get(index + k - 1).line());
    }

    /** 해시 값을 고르게 퍼뜨린다 (SplitMix64 마무리 단계) */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
