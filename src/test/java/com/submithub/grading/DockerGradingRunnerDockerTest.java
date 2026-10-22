package com.submithub.grading;

import com.submithub.domain.GradingLanguage;
import com.submithub.grading.GradingRunner.Case;
import com.submithub.grading.GradingRunner.CaseStatus;
import com.submithub.grading.GradingRunner.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 진짜 Docker 컨테이너로 채점이 되는지 확인하는 통합 테스트. Docker 가 없거나 이미지를 받을 수 없으면 건너뛴다
 * (건너뛰었다는 사실은 보고서에 그대로 적는다 — 통과한 것이 아니다).
 */
class DockerGradingRunnerDockerTest {

    @TempDir Path tmp;
    DockerGradingRunner runner;

    @BeforeEach
    void setUp() {
        runner = new DockerGradingRunner(List.of("docker"), tmp.resolve("work"));
        assumeTrue(runner.available(), "Docker 를 사용할 수 없어 건너뜁니다");
    }

    private static Request java(String code, int timeLimit, String... inputs) {
        var cases = new java.util.ArrayList<Case>();
        for (int i = 0; i < inputs.length; i++) cases.add(new Case((long) i, inputs[i]));
        return new Request(GradingLanguage.JAVA, "Main.java", Map.of("Main.java", code.getBytes(StandardCharsets.UTF_8)),
                cases, timeLimit, 256);
    }

    @Test
    void 컴파일하고_표준입력을_넘겨_출력을_받는다() {
        var o = runner.run(java("""
                import java.util.Scanner;
                public class Main { public static void main(String[] a) { System.out.println(new Scanner(System.in).nextInt() * 2); } }
                """, 10, "21\n"));
        assertThat(o.compiled()).isTrue();
        assertThat(o.cases().get(0).stdout().trim()).isEqualTo("42");
    }

    @Test
    void 컴파일_오류는_메시지로_돌려준다() {
        var o = runner.run(java("public class Main { void x( }", 10, ""));
        assertThat(o.compiled()).isFalse();
        assertThat(o.compileMessage()).isNotBlank();
    }

    @Test
    void 무한_루프는_시간_초과로_끝나고_네트워크는_막혀_있다() {
        var loop = runner.run(java("public class Main { public static void main(String[] a) { while (true) { } } }", 2, ""));
        assertThat(loop.cases().get(0).status()).isEqualTo(CaseStatus.TIMEOUT);

        var net = runner.run(java("""
                public class Main { public static void main(String[] a) throws Exception {
                    try { new java.net.Socket("1.1.1.1", 80).close(); System.out.println("CONNECTED"); }
                    catch (Exception e) { System.out.println("BLOCKED"); } } }
                """, 10, ""));
        assertThat(net.cases().get(0).stdout()).contains("BLOCKED");
    }
}
