package com.submithub.experiments;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 실험 데이터셋의 모든 프로그램이 컴파일되고, 변형이 원본과 같은 출력을 내는지 확인한다. */
class DatasetValidityTest {

    @Test
    void 모든_변형이_컴파일되고_원본과_같은_출력을_낸다() throws Exception {
        var programs = PlagiarismDataset.build(42);
        assertThat(programs).hasSize(25 * 10);
        Path work = Path.of("target", "experiments", "validity");
        Map<String, String> originalOutput = new HashMap<>();
        List<String> problems = new ArrayList<>();
        for (var p : programs) {
            try {
                String out = JavaChecker.compileAndRun(p.code(), work.resolve(p.id()));
                if (p.variant().equals(PlagiarismDataset.ORIGINAL)) {
                    originalOutput.put(p.family(), out);
                    assertThat(out).as(p.id() + " 출력이 비어 있음").isNotBlank();
                } else if (!out.equals(originalOutput.get(p.family()))) {
                    problems.add(p.id() + " 출력이 원본과 다름");
                }
            } catch (Exception e) {
                problems.add(p.id() + ": " + e.getMessage().lines().limit(6).reduce("", (a, b) -> a + "\n   " + b));
            }
        }
        System.out.println("검증한 프로그램 수 = " + programs.size() + ", 문제 = " + problems.size());
        problems.forEach(System.out::println);
        assertThat(problems).isEmpty();
    }

    @Test
    void 변형은_원본과_다른_텍스트이고_표시가_남지_않는다() throws Exception {
        var programs = PlagiarismDataset.build(42);
        Map<String, String> originals = new HashMap<>();
        programs.stream().filter(p -> p.variant().equals("orig")).forEach(p -> originals.put(p.family(), p.code()));
        for (var p : programs) {
            assertThat(p.code()).as(p.id()).doesNotContain("//@");
            if (!p.variant().equals("orig")) {
                assertThat(p.code()).as(p.id() + " 이 원본과 같음").isNotEqualTo(originals.get(p.family()));
            }
        }
        Files.createDirectories(Path.of("target", "experiments"));
    }
}
