package com.submithub.experiments;

import com.submithub.core.similarity.SimilarityChecker;
import de.jplag.JPlag;
import de.jplag.JPlagComparison;
import de.jplag.JPlagResult;
import de.jplag.java.JavaLanguage;
import de.jplag.options.JPlagOptions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 표절 검사 정확도 실험 (docs/EVALUATION.md 1장).
 * 같은 데이터로 우리 구현(Winnowing)과 JPlag 5.1.0 을 비교하고, 결과를 docs/experiments, docs/img 에 쓴다.
 * 실행: mvn -Pexperiments test -Dtest=PlagiarismExperimentTest
 */
class PlagiarismExperimentTest {

    static final String[] METHODS = {"우리 구현 (Winnowing)", "JPlag 기본 (평균)", "JPlag 기본 (최대)",
            "JPlag 최소일치5 (평균)", "JPlag 최소일치5 (최대)"};
    static final double[] THRESHOLDS = {0.05, 0.10, 0.15, 0.20, 0.25, 0.30, 0.35, 0.40, 0.45, 0.50, 0.55, 0.60, 0.65,
            0.70, 0.75, 0.80, 0.85, 0.90, 0.95};

    /** 평가 대상 쌍 하나 */
    record Pair(String a, String b, boolean positive, String type, boolean hardNegative, double[] scores) {
    }

    record Metrics(int tp, int fp, int fn, int tn, int hardFp, int hardTotal) {
        double precision() {
            return tp + fp == 0 ? 1.0 : (double) tp / (tp + fp);
        }

        double recall() {
            return tp + fn == 0 ? 0.0 : (double) tp / (tp + fn);
        }

        double f1() {
            double p = precision();
            double r = recall();
            return p + r == 0 ? 0 : 2 * p * r / (p + r);
        }
    }

    @Test
    void 표절_검사_정확도를_측정하고_JPlag_와_비교한다() throws Exception {
        var programs = PlagiarismDataset.build(42);
        int n = programs.size();
        Path datasetDir = Path.of("target", "experiments", "dataset");
        Files.createDirectories(datasetDir);
        for (var p : programs) {
            Path dir = datasetDir.resolve(p.id());
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("Main.java"), p.code(), StandardCharsets.UTF_8);
        }

        // ---- 우리 구현 ----
        Map<String, String> codes = new LinkedHashMap<>();
        programs.forEach(p -> codes.put(p.id(), p.code()));
        long t0 = System.nanoTime();
        var ours = SimilarityChecker.compareAll(codes, null, 0.0);
        double oursSeconds = (System.nanoTime() - t0) / 1e9;
        assertThat(ours).as("모든 쌍이 비교되어야 한다 (지문이 너무 적은 프로그램이 없어야 한다)").hasSize(n * (n - 1) / 2);

        // ---- JPlag ----
        JPlagOptions options = new JPlagOptions(new JavaLanguage(), Set.of(datasetDir.toFile()), Set.<File>of())
                .withSimilarityThreshold(0.0)
                .withMaximumNumberOfComparisons(JPlagOptions.SHOW_ALL_COMPARISONS);
        long t1 = System.nanoTime();
        JPlagResult jplag = JPlag.run(options);
        double jplagSeconds = (System.nanoTime() - t1) / 1e9;
        // 우리 설정(토큰 5개 단위 지문)과 비슷한 granularity 로 한 번 더: 최소 토큰 일치 5
        JPlagResult jplag5 = JPlag.run(options.withMinimumTokenMatch(5));

        Map<String, double[]> scores = new HashMap<>();
        for (var c : ours) {
            scores.computeIfAbsent(key(c.idA(), c.idB()), k -> new double[METHODS.length])[0] = c.score();
        }
        int jplagPairs = 0;
        for (JPlagComparison c : jplag.getAllComparisons()) {
            double[] s = scores.get(key(c.firstSubmission().getName(), c.secondSubmission().getName()));
            if (s != null) {
                s[1] = c.similarity();
                s[2] = c.maximalSimilarity();
                jplagPairs++;
            }
        }
        for (JPlagComparison c : jplag5.getAllComparisons()) {
            double[] s5 = scores.get(key(c.firstSubmission().getName(), c.secondSubmission().getName()));
            if (s5 != null) {
                s5[3] = c.similarity();
                s5[4] = c.maximalSimilarity();
            }
        }
        System.out.println("JPlag 가 비교한 쌍 = " + jplagPairs + " / " + scores.size());

        // ---- 쌍 분류 ----
        Map<String, PlagiarismDataset.Program> byId = new HashMap<>();
        programs.forEach(p -> byId.put(p.id(), p));
        List<Pair> pairs = new ArrayList<>();
        for (var e : scores.entrySet()) {
            String[] ids = e.getKey().split("\\|");
            var pa = byId.get(ids[0]);
            var pb = byId.get(ids[1]);
            boolean positive = pa.family().equals(pb.family());
            String type = "";
            if (positive) {
                boolean aOrig = pa.variant().equals(PlagiarismDataset.ORIGINAL);
                boolean bOrig = pb.variant().equals(PlagiarismDataset.ORIGINAL);
                type = aOrig ? pb.variant() : bOrig ? pa.variant() : "variant-variant";
            }
            boolean hard = !positive && PlagiarismDataset.sameTask(pa.family(), pb.family());
            pairs.add(new Pair(ids[0], ids[1], positive, type, hard, e.getValue()));
        }
        long positives = pairs.stream().filter(Pair::positive).count();
        long hardNeg = pairs.stream().filter(Pair::hardNegative).count();
        System.out.printf(Locale.ROOT, "프로그램 %d개, 쌍 %d개 (표절 %d, 어려운 음성 %d, 쉬운 음성 %d)%n",
                n, pairs.size(), positives, hardNeg, pairs.size() - positives - hardNeg);
        assertThat(positives).isEqualTo(25L * 45);

        // ---- 임계값별 지표 ----
        Map<String, Map<Double, Metrics>> table = new LinkedHashMap<>();
        for (int m = 0; m < METHODS.length; m++) {
            Map<Double, Metrics> row = new LinkedHashMap<>();
            for (double t : THRESHOLDS) {
                row.put(t, metrics(pairs, m, t));
            }
            table.put(METHODS[m], row);
        }
        double[] bestT = new double[METHODS.length];
        for (int m = 0; m < METHODS.length; m++) {
            double best = -1;
            for (var e : table.get(METHODS[m]).entrySet()) {
                if (e.getValue().f1() > best + 1e-12) {
                    best = e.getValue().f1();
                    bestT[m] = e.getKey();
                }
            }
        }

        // ---- 위장 유형별 탐지율 (원본 ↔ 변형 쌍) ----
        List<String> typeIds = new ArrayList<>();
        List<String> typeLabels = new ArrayList<>();
        for (var t : ProgramTransforms.Type.values()) {
            typeIds.add(t.id);
            typeLabels.add(t.label);
        }
        double[][] detectAtHalf = new double[METHODS.length][typeIds.size()];
        double[][] detectAtBest = new double[METHODS.length][typeIds.size()];
        double[][] minScore = new double[METHODS.length][typeIds.size()];
        for (int ti = 0; ti < typeIds.size(); ti++) {
            final String type = typeIds.get(ti);
            List<Pair> sel = pairs.stream().filter(p -> p.positive() && p.type().equals(type)).toList();
            assertThat(sel).hasSize(25);
            for (int m = 0; m < METHODS.length; m++) {
                final int mm = m;
                detectAtHalf[m][ti] = sel.stream().filter(p -> p.scores()[mm] >= 0.5).count() / (double) sel.size();
                detectAtBest[m][ti] = sel.stream().filter(p -> p.scores()[mm] >= bestT[mm]).count() / (double) sel.size();
                minScore[m][ti] = sel.stream().mapToDouble(p -> p.scores()[mm]).min().orElse(0);
            }
        }
        long vv = pairs.stream().filter(p -> p.positive() && p.type().equals("variant-variant")).count();

        // ---- 점수 분포 요약 ----
        Map<String, double[]> dist = new LinkedHashMap<>();
        for (int m = 0; m < METHODS.length; m++) {
            final int mm = m;
            double[] pos = pairs.stream().filter(Pair::positive).mapToDouble(p -> p.scores()[mm]).sorted().toArray();
            double[] easy = pairs.stream().filter(p -> !p.positive() && !p.hardNegative()).mapToDouble(p -> p.scores()[mm]).sorted().toArray();
            double[] hard = pairs.stream().filter(Pair::hardNegative).mapToDouble(p -> p.scores()[mm]).sorted().toArray();
            dist.put(METHODS[m], new double[]{
                    pos[0], quantile(pos, 0.05), median(pos), easy[easy.length - 1], quantile(easy, 0.99), median(easy),
                    hard[hard.length - 1], median(hard)});
        }

        // ---- 파일 쓰기 ----
        Path expDir = Path.of("docs", "experiments");
        Path imgDir = Path.of("docs", "img");
        Files.createDirectories(expDir);
        StringBuilder csv = new StringBuilder("method,threshold,tp,fp,fn,tn,precision,recall,f1,hard_negative_fp\n");
        for (var m : table.entrySet()) {
            for (var r : m.getValue().entrySet()) {
                Metrics x = r.getValue();
                csv.append(String.format(Locale.ROOT, "%s,%.2f,%d,%d,%d,%d,%.4f,%.4f,%.4f,%d%n", m.getKey(), r.getKey(),
                        x.tp(), x.fp(), x.fn(), x.tn(), x.precision(), x.recall(), x.f1(), x.hardFp()));
            }
        }
        Files.writeString(expDir.resolve("plagiarism-thresholds.csv"), csv.toString(), StandardCharsets.UTF_8);

        StringBuilder byType = new StringBuilder("type,label,method,detect_at_0.5,detect_at_best_f1,best_f1_threshold,min_score\n");
        for (int ti = 0; ti < typeIds.size(); ti++) {
            for (int m = 0; m < METHODS.length; m++) {
                byType.append(String.format(Locale.ROOT, "%s,%s,%s,%.4f,%.4f,%.2f,%.4f%n", typeIds.get(ti), typeLabels.get(ti),
                        METHODS[m], detectAtHalf[m][ti], detectAtBest[m][ti], bestT[m], minScore[m][ti]));
            }
        }
        Files.writeString(expDir.resolve("plagiarism-by-type.csv"), byType.toString(), StandardCharsets.UTF_8);

        // 방법별로 가장 낮게 나온 표절 쌍 (놓친 이유를 분석하기 위한 자료)
        StringBuilder worst = new StringBuilder("method,a,b,type,score\n");
        for (int m = 0; m < METHODS.length; m++) {
            final int mm = m;
            pairs.stream().filter(Pair::positive).sorted(Comparator.comparingDouble(p -> p.scores()[mm])).limit(20)
                    .forEach(p -> worst.append(String.format(Locale.ROOT, "%s,%s,%s,%s,%.4f%n", METHODS[mm], p.a(), p.b(),
                            p.type(), p.scores()[mm])));
        }
        Files.writeString(expDir.resolve("plagiarism-worst-pairs.csv"), worst.toString(), StandardCharsets.UTF_8);

        // 마크다운 표 (EVALUATION.md 에 붙여 넣는다)
        StringBuilder md = new StringBuilder();
        md.append(String.format(Locale.ROOT, "프로그램 %d개 (원본 25 × 계열당 10개), 쌍 %d개: 표절 %d, 어려운 음성(같은 과제를 독립적으로 푼 쌍) %d, 쉬운 음성 %d%n%n",
                n, pairs.size(), positives, hardNeg, pairs.size() - positives - hardNeg));
        md.append(String.format(Locale.ROOT, "처리 시간(250개 전체 쌍): 우리 구현 %.2f초, JPlag(기본) %.2f초 (JVM 워밍업·파싱 포함)%n%n", oursSeconds, jplagSeconds));
        md.append("### 임계값별 정밀도 / 재현율 / F1\n\n| 임계값 |");
        for (String m : METHODS) {
            md.append(" ").append(m).append(" P / R / F1 |");
        }
        md.append("\n|---|---|---|---|---|---|\n");
        for (double t : THRESHOLDS) {
            md.append(String.format(Locale.ROOT, "| %.2f |", t));
            for (String m : METHODS) {
                Metrics x = table.get(m).get(t);
                md.append(String.format(Locale.ROOT, " %.3f / %.3f / %.3f |", x.precision(), x.recall(), x.f1()));
            }
            md.append("\n");
        }
        md.append("\n### 방법별 최적 F1\n\n| 방법 | 최적 임계값 | 정밀도 | 재현율 | F1 | 어려운 음성 오탐 |\n|---|---|---|---|---|---|\n");
        for (int m = 0; m < METHODS.length; m++) {
            Metrics x = table.get(METHODS[m]).get(bestT[m]);
            md.append(String.format(Locale.ROOT, "| %s | %.2f | %.3f | %.3f | %.3f | %d / %d |%n", METHODS[m], bestT[m],
                    x.precision(), x.recall(), x.f1(), x.hardFp(), x.hardTotal()));
        }
        md.append("\n### 위장 유형별 탐지율 (원본 ↔ 변형 25쌍씩, 임계값 0.50 / 방법별 최적 임계값)\n\n| 위장 유형 |");
        for (String m : METHODS) {
            md.append(" ").append(m).append(" |");
        }
        md.append("\n|---|---|---|---|---|---|\n");
        for (int ti = 0; ti < typeIds.size(); ti++) {
            md.append("| ").append(typeLabels.get(ti)).append(" |");
            for (int m = 0; m < METHODS.length; m++) {
                md.append(String.format(Locale.ROOT, " %.0f%% / %.0f%% |", detectAtHalf[m][ti] * 100, detectAtBest[m][ti] * 100));
            }
            md.append("\n");
        }
        md.append(String.format(Locale.ROOT, "%n(변형끼리의 쌍 %d개는 위 표에 넣지 않고 전체 지표에만 포함했다)%n", vv));
        md.append("\n### 점수 분포\n\n| 방법 | 표절 쌍 최저 | 표절 쌍 하위 5% | 표절 쌍 중앙값 | 쉬운 음성 최고 | 쉬운 음성 상위 1% | 쉬운 음성 중앙값 | 어려운 음성 최고 | 어려운 음성 중앙값 |\n|---|---|---|---|---|---|---|---|---|\n");
        for (var e : dist.entrySet()) {
            double[] d = e.getValue();
            md.append(String.format(Locale.ROOT, "| %s | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f |%n", e.getKey(),
                    d[0], d[1], d[2], d[3], d[4], d[5], d[6], d[7]));
        }
        Files.writeString(expDir.resolve("plagiarism-summary.md"), md.toString(), StandardCharsets.UTF_8);
        System.out.println(md);

        // ---- 그래프 ----
        double[][] f1 = new double[METHODS.length][THRESHOLDS.length];
        double[][] oursPrf = new double[3][THRESHOLDS.length];
        for (int m = 0; m < METHODS.length; m++) {
            for (int i = 0; i < THRESHOLDS.length; i++) {
                f1[m][i] = table.get(METHODS[m]).get(THRESHOLDS[i]).f1();
            }
        }
        for (int i = 0; i < THRESHOLDS.length; i++) {
            Metrics x = table.get(METHODS[0]).get(THRESHOLDS[i]);
            oursPrf[0][i] = x.precision();
            oursPrf[1][i] = x.recall();
            oursPrf[2][i] = x.f1();
        }
        ChartPng.line(imgDir.resolve("plagiarism-f1.png"), "임계값에 따른 F1", "표절 쌍 " + positives + "개 / 비표절 쌍 " + (pairs.size() - positives) + "개",
                "유사도 임계값", "F1", THRESHOLDS, METHODS, f1, false, false, 0, 1, null);
        ChartPng.line(imgDir.resolve("plagiarism-ours-prf.png"), "우리 구현: 정밀도 · 재현율 · F1", "임계값을 올리면 정밀도는 오르고 재현율은 내려간다",
                "유사도 임계값", "값", THRESHOLDS, new String[]{"정밀도", "재현율", "F1"}, oursPrf, false, false, 0, 1, null);
        List<String> cats = new ArrayList<>();
        for (String l : typeLabels) {
            cats.add(l.replace("(", "\n(").replaceFirst(" ", "\n"));
        }
        ChartPng.groupedBars(imgDir.resolve("plagiarism-detection-by-type.png"), "위장 유형별 탐지율 (임계값 0.50)",
                "원본 ↔ 변형 25쌍씩 중 임계값 이상으로 잡힌 비율 (100% 막대는 숫자를 생략)", "탐지율", cats, METHODS, detectAtHalf, 1.0, true);

        // ---- 최소한의 기대 (실험이 깨지지 않았는지) ----
        Metrics best = table.get(METHODS[0]).get(bestT[0]);
        assertThat(best.f1()).as("우리 구현의 최적 F1").isGreaterThan(0.8);
        assertThat(Files.exists(imgDir.resolve("plagiarism-f1.png"))).isTrue();
    }

    static Metrics metrics(List<Pair> pairs, int method, double threshold) {
        int tp = 0, fp = 0, fn = 0, tn = 0, hardFp = 0, hardTotal = 0;
        for (Pair p : pairs) {
            boolean predicted = p.scores()[method] >= threshold;
            if (p.hardNegative()) {
                hardTotal++;
                if (predicted) {
                    hardFp++;
                }
            }
            if (p.positive()) {
                if (predicted) {
                    tp++;
                } else {
                    fn++;
                }
            } else if (predicted) {
                fp++;
            } else {
                tn++;
            }
        }
        return new Metrics(tp, fp, fn, tn, hardFp, hardTotal);
    }

    static String key(String a, String b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    static double median(double[] sorted) {
        return quantile(sorted, 0.5);
    }

    static double quantile(double[] sorted, double q) {
        if (sorted.length == 0) {
            return 0;
        }
        return sorted[(int) Math.min(sorted.length - 1, Math.floor(q * sorted.length))];
    }
}
