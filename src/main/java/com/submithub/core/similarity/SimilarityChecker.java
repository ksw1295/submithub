package com.submithub.core.similarity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 같은 과제에 제출된 코드끼리 유사도를 계산한다.
 *
 * 교수가 나눠준 뼈대 코드(baseline)는 모두가 똑같이 갖고 있으므로 지문에서 미리 뺀다.
 * (MOSS 의 -b 옵션과 같은 역할)
 */
public final class SimilarityChecker {

    /** 학생 한 명의 제출물에서 뽑은 지문 */
    public record Document(String id, Set<Long> hashes, Map<Long, List<int[]>> lineRanges) {
    }

    /**
     * 두 제출물의 비교 결과.
     *
     * @param coverageA   A 의 지문 중 B 와 겹치는 비율 (0~1)
     * @param coverageB   B 의 지문 중 A 와 겹치는 비율 (0~1)
     * @param matchedLinesA A 에서 겹치는 부분의 줄 번호 (화면 하이라이트용)
     * @param matchedLinesB B 에서 겹치는 부분의 줄 번호
     */
    public record Pair(String idA, String idB, double coverageA, double coverageB, int sharedFingerprints,
                       Set<Integer> matchedLinesA, Set<Integer> matchedLinesB) {

        /** 한쪽이 다른 쪽을 통째로 베끼고 코드를 덧붙인 경우도 잡도록 큰 쪽을 대표값으로 쓴다 */
        public double score() {
            return Math.max(coverageA, coverageB);
        }
    }

    /** 지문이 이보다 적은 아주 짧은 제출물은 우연히 겹치기 쉬워서 비교하지 않는다 */
    public static final int MIN_FINGERPRINTS = 5;

    private SimilarityChecker() {
    }

    public static Document document(String id, String code, Set<Long> baseline) {
        List<Winnowing.Fingerprint> fps = Winnowing.fingerprints(CodeTokenizer.tokenize(code));
        Set<Long> hashes = new HashSet<>();
        Map<Long, List<int[]>> ranges = new LinkedHashMap<>();
        for (Winnowing.Fingerprint fp : fps) {
            if (baseline.contains(fp.hash())) {
                continue;
            }
            hashes.add(fp.hash());
            ranges.computeIfAbsent(fp.hash(), h -> new ArrayList<>()).add(new int[]{fp.startLine(), fp.endLine()});
        }
        return new Document(id, hashes, ranges);
    }

    public static Set<Long> baseline(String baselineCode) {
        Set<Long> set = new HashSet<>();
        if (baselineCode == null || baselineCode.isBlank()) {
            return set;
        }
        for (Winnowing.Fingerprint fp : Winnowing.fingerprints(CodeTokenizer.tokenize(baselineCode))) {
            set.add(fp.hash());
        }
        return set;
    }

    public static Pair compare(Document a, Document b) {
        Set<Long> shared = new HashSet<>(a.hashes());
        shared.retainAll(b.hashes());
        double covA = a.hashes().isEmpty() ? 0 : (double) shared.size() / a.hashes().size();
        double covB = b.hashes().isEmpty() ? 0 : (double) shared.size() / b.hashes().size();
        return new Pair(a.id(), b.id(), covA, covB, shared.size(),
                lines(a, shared), lines(b, shared));
    }

    /**
     * 모든 제출물 쌍을 비교해서 threshold 이상인 쌍을 유사도 높은 순으로 돌려준다.
     *
     * @param submissions 학생 ID(또는 제출 ID) → 코드
     * @param baselineCode 교수 제공 뼈대 코드 (없으면 null)
     * @param threshold   0~1, 예: 0.6
     */
    public static List<Pair> compareAll(Map<String, String> submissions, String baselineCode, double threshold) {
        Set<Long> base = baseline(baselineCode);
        List<Document> docs = new ArrayList<>();
        for (Map.Entry<String, String> e : submissions.entrySet()) {
            Document d = document(e.getKey(), e.getValue(), base);
            if (d.hashes().size() >= MIN_FINGERPRINTS) {
                docs.add(d);
            }
        }
        List<Pair> pairs = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            for (int j = i + 1; j < docs.size(); j++) {
                Pair p = compare(docs.get(i), docs.get(j));
                if (p.score() >= threshold) {
                    pairs.add(p);
                }
            }
        }
        pairs.sort(Comparator.comparingDouble(Pair::score).reversed());
        return pairs;
    }

    private static Set<Integer> lines(Document doc, Set<Long> shared) {
        Set<Integer> lines = new TreeSet<>();
        for (Long h : shared) {
            for (int[] range : doc.lineRanges().getOrDefault(h, List.of())) {
                for (int l = range[0]; l <= range[1]; l++) {
                    lines.add(l);
                }
            }
        }
        return lines;
    }
}
