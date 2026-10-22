package com.submithub.experiments;

import com.submithub.demo.DemoCode;
import com.submithub.demo.DemoGrading;
import org.junit.jupiter.api.Test;

import javax.tools.ToolProvider;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시연 데이터(과제 1)의 통과율 시나리오를 Docker 없이 확인하는 검증용 테스트.
 * 제품 코드에는 호스트 실행 경로가 없으므로, 우리가 직접 쓴 시연 코드를 이 실험 소스 안에서만 컴파일/실행한다.
 */
class DemoGradingFixtureTest {

    private static int passed(String linked, String main, Path dir) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("LinkedList.java"), linked, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("Main.java"), main, StandardCharsets.UTF_8);
        var javac = ToolProvider.getSystemJavaCompiler();
        int rc = javac.run(null, null, null, "-encoding", "UTF-8", "-d", dir.toString(),
                dir.resolve("LinkedList.java").toString(), dir.resolve("Main.java").toString());
        if (rc != 0) return -1;
        int ok = 0;
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        for (var c : DemoGrading.CASES) {
            Process p = new ProcessBuilder(java, "-cp", dir.toString(), "Main").redirectErrorStream(true).start();
            p.getOutputStream().write(c.input().getBytes(StandardCharsets.UTF_8));
            p.getOutputStream().close();
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                continue;
            }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean same = out.replace("\r\n", "\n").stripTrailing().equals(c.expected().stripTrailing());
            System.out.println("CASE " + dir.getFileName() + " " + c.name() + " -> " + same + " got=" + out.replace("\n", "|"));
            if (same) ok++;
        }
        return ok;
    }

    @Test
    void 이민준의_통과율은_버전마다_오른다() throws Exception {
        Path base = Path.of("target", "experiments", "demo-grading");
        Map<String, Integer> r = new LinkedHashMap<>();
        r.put("student1 v1", passed(DemoCode.S1_V1_LIST, DemoCode.S1_MAIN_V1, base.resolve("s1v1")));
        r.put("student1 v2", passed(DemoCode.S1_V2_LIST, DemoCode.S1_MAIN, base.resolve("s1v2")));
        r.put("student1 v3", passed(DemoCode.S1_V3_LIST, DemoCode.S1_MAIN, base.resolve("s1v3")));
        r.put("student2", passed(DemoCode.ORIGINAL, DemoCode.MAIN_S2, base.resolve("s2")));
        r.put("student3", passed(DemoCode.disguised(DemoCode.ORIGINAL), DemoCode.MAIN_S3, base.resolve("s3")));
        r.put("student4", passed(DemoCode.GENERIC_STYLE, DemoCode.MAIN_S4, base.resolve("s4")));
        r.put("student5 v1", passed(DemoCode.DOUBLY_V1, DemoCode.MAIN_S5, base.resolve("s5v1")));
        r.put("student5 v2", passed(DemoCode.DOUBLY_V2, DemoCode.MAIN_S5, base.resolve("s5v2")));
        r.put("student7", passed(DemoCode.SENTINEL_STYLE, DemoCode.MAIN_S7, base.resolve("s7")));
        System.out.println("DEMO-GRADING " + r);
        Files.writeString(base.resolve("result.txt"), r.toString());
        r.values().forEach(v -> assertThat(v).as("컴파일 실패").isGreaterThanOrEqualTo(0));
        assertThat(r.get("student1 v1")).isLessThan(r.get("student1 v2"));
        assertThat(r.get("student1 v2")).isLessThan(r.get("student1 v3"));
        assertThat(r.get("student1 v3")).isEqualTo(DemoGrading.CASES.size());
    }
}
