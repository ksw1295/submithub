package com.submithub.experiments;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * diff 성능 실험 (docs/EVALUATION.md 2장): 기존 Swing 버전 LCS vs MyersDiff.
 * 측정마다 별도 JVM 을 -Xmx 로 제한해서 띄운다 (메모리 부족 지점을 실제로 재현). 결과는 docs/experiments, docs/img.
 * 실행: mvn -Pexperiments test -Dtest=DiffPerfExperimentTest
 */
class DiffPerfExperimentTest {

    record Run(String scenario, String algo, int lines, double ratio, String heap, String status, double ms,
               double allocMb, int changed) {
        boolean ok() {
            return status.equals("OK");
        }
    }

    static final long TIMEOUT_SECONDS = 240;

    @Test
    void 두_알고리즘의_결과가_같다는_것부터_확인한다() {
        for (int n : new int[]{50, 300, 1000}) {
            for (double ratio : new double[]{0.0, 0.05, 0.3, 1.0}) {
                String[] t = DiffData.generate(n, ratio, 11);
                assertThat(DiffPerfChild.run("myers", t)).as("n=%d ratio=%.2f", n, ratio).isEqualTo(DiffPerfChild.run("lcs", t));
            }
        }
    }

    @Test
    void 크기_변경비율_힙에_따른_시간과_메모리를_측정한다() throws Exception {
        List<Run> runs = new ArrayList<>();
        int[] sizes = {100, 500, 1000, 2000, 5000, 10000, 15000, 20000};
        for (int n : sizes) {
            for (String algo : List.of("lcs", "myers")) {
                runs.add(measure("size", algo, n, 0.05, "512m"));
            }
        }
        double[] ratios = {0.01, 0.05, 0.2, 0.5};
        for (int n : new int[]{5000, 20000}) {
            for (double ratio : ratios) {
                for (String algo : List.of("lcs", "myers")) {
                    if (ratio == 0.05 && n == 5000) {
                        continue;     // 위 크기 실험과 같은 조건
                    }
                    if (ratio == 0.05 && n == 20000) {
                        continue;
                    }
                    runs.add(measure("ratio", algo, n, ratio, "512m"));
                }
            }
        }
        for (String heap : List.of("256m", "512m", "1g", "2g", "4g")) {
            for (String algo : List.of("lcs", "myers")) {
                runs.add(measure("heap", algo, 20000, 0.05, heap));
            }
        }

        Path expDir = Path.of("docs", "experiments");
        Files.createDirectories(expDir);
        StringBuilder csv = new StringBuilder("scenario,algo,lines,change_ratio,heap,status,time_ms,allocated_mb,changed_lines\n");
        for (Run r : runs) {
            csv.append(String.format(Locale.ROOT, "%s,%s,%d,%.2f,%s,%s,%.2f,%.1f,%d%n", r.scenario(), r.algo(), r.lines(),
                    r.ratio(), r.heap(), r.status(), r.ms(), r.allocMb(), r.changed()));
        }
        Files.writeString(expDir.resolve("diff-performance.csv"), csv.toString(), StandardCharsets.UTF_8);
        Files.writeString(expDir.resolve("diff-summary.md"), summary(runs, sizes, ratios), StandardCharsets.UTF_8);
        System.out.println(summary(runs, sizes, ratios));
        charts(runs, sizes, ratios);

        // 두 알고리즘이 모두 성공한 경우 변경된 줄 수는 같아야 한다 (둘 다 최소 편집 스크립트)
        for (Run a : runs) {
            if (!a.algo().equals("lcs") || !a.ok()) {
                continue;
            }
            runs.stream().filter(b -> b.algo().equals("myers") && b.ok() && b.lines() == a.lines() && b.ratio() == a.ratio()
                    && b.scenario().equals(a.scenario()) && b.heap().equals(a.heap()))
                    .forEach(b -> assertThat(b.changed()).as("lines=%d ratio=%.2f", a.lines(), a.ratio()).isEqualTo(a.changed()));
        }
        assertThat(runs.stream().filter(r -> r.algo().equals("myers") && r.lines() == 20000 && r.ratio() == 0.05 && r.heap().equals("512m")))
                .allMatch(Run::ok);
    }

    static Run measure(String scenario, String algo, int lines, double ratio, String heap) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = new ProcessBuilder(java, "-Xmx" + heap, "-cp", System.getProperty("java.class.path"),
                "com.submithub.experiments.DiffPerfChild", algo, String.valueOf(lines), String.valueOf(ratio), "1");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output;
        if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            Run r = new Run(scenario, algo, lines, ratio, heap, "TIMEOUT", -1, -1, -1);
            System.out.println(r);
            return r;
        }
        output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String line = output.lines().filter(l -> l.startsWith("RESULT")).findFirst().orElse(null);
        Run r;
        if (line == null) {
            r = new Run(scenario, algo, lines, ratio, heap, "OOM", -1, -1, -1);   // 치명적 종료도 메모리 부족으로 본다
        } else {
            r = new Run(scenario, algo, lines, ratio, heap, field(line, "status"), Double.parseDouble(field(line, "ms")),
                    Double.parseDouble(field(line, "allocMB")), Integer.parseInt(field(line, "changed")));
        }
        System.out.println(r);
        return r;
    }

    static String field(String line, String name) {
        for (String part : line.split(" ")) {
            if (part.startsWith(name + "=")) {
                return part.substring(name.length() + 1);
            }
        }
        throw new IllegalArgumentException(line);
    }

    static Run find(List<Run> runs, String scenario, String algo, int lines, double ratio, String heap) {
        return runs.stream().filter(r -> r.scenario().equals(scenario) && r.algo().equals(algo) && r.lines() == lines
                && r.ratio() == ratio && r.heap().equals(heap)).findFirst().orElse(null);
    }

    /** 크기 실험은 size 시나리오에서, 5000/20000 의 5% 는 size 시나리오 결과를 재사용한다 */
    static Run sizeOrRatio(List<Run> runs, String algo, int lines, double ratio) {
        Run r = find(runs, "ratio", algo, lines, ratio, "512m");
        return r != null ? r : find(runs, "size", algo, lines, ratio, "512m");
    }

    static String cell(Run r, boolean time) {
        if (r == null) {
            return "-";
        }
        if (!r.ok()) {
            return r.status().equals("OOM") ? "**메모리 부족**" : "**시간 초과**";
        }
        return time ? String.format(Locale.ROOT, "%,.1f ms", r.ms()) : String.format(Locale.ROOT, "%,.1f MB", r.allocMb());
    }

    static String summary(List<Run> runs, int[] sizes, double[] ratios) {
        StringBuilder md = new StringBuilder();
        md.append("### 파일 크기별 (변경 5%, 힙 512MB)\n\n| 줄 수 | LCS 시간 | Myers 시간 | LCS 할당 메모리 | Myers 할당 메모리 |\n|---|---|---|---|---|\n");
        for (int n : sizes) {
            Run l = find(runs, "size", "lcs", n, 0.05, "512m");
            Run m = find(runs, "size", "myers", n, 0.05, "512m");
            md.append(String.format(Locale.ROOT, "| %,d | %s | %s | %s | %s |%n", n, cell(l, true), cell(m, true), cell(l, false), cell(m, false)));
        }
        for (int n : new int[]{5000, 20000}) {
            md.append(String.format(Locale.ROOT, "%n### 변경 비율별 (%,d줄, 힙 512MB)%n%n| 변경 비율 | LCS 시간 | Myers 시간 | LCS 할당 메모리 | Myers 할당 메모리 | 변경된 줄 수 |%n|---|---|---|---|---|---|%n", n));
            for (double ratio : ratios) {
                Run l = sizeOrRatio(runs, "lcs", n, ratio);
                Run m = sizeOrRatio(runs, "myers", n, ratio);
                md.append(String.format(Locale.ROOT, "| %.0f%% | %s | %s | %s | %s | %s |%n", ratio * 100, cell(l, true), cell(m, true),
                        cell(l, false), cell(m, false), m != null && m.ok() ? String.format("%,d", m.changed()) : "-"));
            }
        }
        md.append("\n### 힙 크기별 (20,000줄, 변경 5%)\n\n| 힙(-Xmx) | LCS | Myers |\n|---|---|---|\n");
        for (String heap : List.of("256m", "512m", "1g", "2g", "4g")) {
            Run l = find(runs, "heap", "lcs", 20000, 0.05, heap);
            Run m = find(runs, "heap", "myers", 20000, 0.05, heap);
            md.append(String.format(Locale.ROOT, "| %s | %s (%s) | %s (%s) |%n", heap, cell(l, true), cell(l, false), cell(m, true), cell(m, false)));
        }
        return md.toString();
    }

    static void charts(List<Run> runs, int[] sizes, double[] ratios) throws Exception {
        Path img = Path.of("docs", "img");
        double[] x = java.util.Arrays.stream(sizes).asDoubleStream().toArray();
        double[][] time = new double[2][sizes.length];
        double[][] mem = new double[2][sizes.length];
        for (int i = 0; i < sizes.length; i++) {
            int a = 0;
            for (String algo : List.of("lcs", "myers")) {
                Run r = find(runs, "size", algo, sizes[i], 0.05, "512m");
                time[a][i] = r != null && r.ok() ? Math.max(r.ms(), 0.01) : Double.NaN;
                mem[a][i] = r != null && r.ok() ? Math.max(r.allocMb(), 0.01) : Double.NaN;
                a++;
            }
        }
        String[] names = {"LCS (기존 Swing 버전)", "Myers (SubmitHub)"};
        ChartPng.line(img.resolve("diff-time-by-size.png"), "파일 크기별 diff 시간 (변경 5%, 힙 512MB)", "X 표시 = 메모리 부족으로 실패",
                "줄 수", "시간 (ms, 로그 눈금)", x, names, time, true, true, 0.01, 100000, null);
        ChartPng.line(img.resolve("diff-memory-by-size.png"), "파일 크기별 할당 메모리 (변경 5%, 힙 512MB)", "X 표시 = 메모리 부족으로 실패",
                "줄 수", "할당한 메모리 (MB, 로그 눈금)", x, names, mem, true, true, 0.01, 10000, null);

        double[] rx = {0, 1, 2, 3};
        String[] rl = new String[ratios.length];
        double[][] rtime = new double[2][ratios.length];
        for (int i = 0; i < ratios.length; i++) {
            rl[i] = String.format(Locale.ROOT, "%.0f%%", ratios[i] * 100);
            int a = 0;
            for (String algo : List.of("lcs", "myers")) {
                Run r = sizeOrRatio(runs, algo, 5000, ratios[i]);
                rtime[a++][i] = r != null && r.ok() ? Math.max(r.ms(), 0.01) : Double.NaN;
            }
        }
        // x 가 0 이면 로그 눈금을 쓸 수 없으니 선형 x 에 라벨만 붙인다
        ChartPng.line(img.resolve("diff-time-by-ratio.png"), "변경 비율별 diff 시간 (5,000줄, 힙 512MB)", "변경이 많아질수록 Myers 는 O(D²) 비용이 커진다",
                "변경한 줄의 비율", "시간 (ms, 로그 눈금)", new double[]{1, 2, 3, 4}, names, rtime, false, true, 1, 100000, rl);
    }
}
