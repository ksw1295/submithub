package com.submithub.grading;

import com.submithub.domain.GradingLanguage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 학생 코드를 실행하는 docker 명령의 격리 옵션이 빠지지 않았는지 검사한다.
 * (실제 Docker 없이 명령 인자만 확인하므로 어디서나 돌아간다)
 */
class DockerCommandsTest {

    static void assertHasPair(List<String> cmd, String flag, String value) {
        int i = cmd.indexOf(flag);
        assertThat(i).as(flag + " 옵션이 있어야 한다").isGreaterThanOrEqualTo(0);
        assertThat(cmd.get(i + 1)).as(flag + " 의 값").isEqualTo(value);
    }

    static void assertIsolated(List<String> cmd) {
        assertThat(cmd.get(0)).isEqualTo("run");
        assertThat(cmd).contains("--rm", "--read-only");
        assertHasPair(cmd, "--network", "none");
        assertHasPair(cmd, "--cap-drop", "ALL");
        assertHasPair(cmd, "--security-opt", "no-new-privileges");
        assertHasPair(cmd, "-u", "65534:65534");
        assertHasPair(cmd, "--cpus", "1");
        assertThat(cmd).contains("--pids-limit", "--memory", "--memory-swap", "--ulimit", "--tmpfs");
        // 실행 권한 없는 임시 영역만 쓴다
        assertThat(cmd.get(cmd.indexOf("--tmpfs") + 1)).startsWith("/tmp:rw,noexec,nosuid");
        // 위험한 옵션이 없다
        assertThat(cmd).doesNotContain("--privileged", "--pid=host", "--network=host", "--cap-add", "--user=root", "-u=0");
        assertThat(String.join(" ", cmd)).doesNotContain("docker.sock").doesNotContain("--net=host");
        // 마운트는 작업 폴더 하나뿐
        assertThat(cmd.stream().filter(a -> a.equals("-v")).count()).isEqualTo(1);
    }

    @Test
    void Java_컴파일_명령은_격리되고_작업_폴더만_쓰기로_마운트한다() {
        List<String> cmd = DockerCommands.compileJava("c1", "/host/work");
        assertIsolated(cmd);
        assertHasPair(cmd, "-v", "/host/work:/work:rw");
        assertHasPair(cmd, "--memory", "512m");
        assertThat(cmd).contains(DockerCommands.JAVA_IMAGE);
        assertThat(cmd.subList(cmd.size() - 3, cmd.size())).containsExactly("sh", "-c", DockerCommands.JAVAC_SCRIPT);
        // 셸 문자열에는 고정 스크립트만 있다 (학생 값이 들어가지 않는다)
        assertThat(DockerCommands.JAVAC_SCRIPT).contains("-print0").contains("xargs -0").doesNotContain("$");
    }

    @Test
    void Java_실행_명령은_읽기_전용_마운트이고_입력을_표준입력으로_받는다() {
        List<String> cmd = DockerCommands.runJava("c2", "/host/work", "Main", 256);
        assertIsolated(cmd);
        assertThat(cmd).contains("-i");
        assertHasPair(cmd, "-v", "/host/work:/work:ro");
        assertHasPair(cmd, "--memory", "256m");
        assertHasPair(cmd, "--memory-swap", "256m");
        assertHasPair(cmd, "--pids-limit", "64");
        assertThat(cmd.subList(cmd.size() - 3, cmd.size())).containsExactly("-cp", "/work/out", "Main");
        assertThat(cmd).contains("-Xmx153m");        // 메모리 제한의 60%
        assertThat(cmd).contains(DockerCommands.JAVA_IMAGE);
    }

    @Test
    void Python_실행_명령도_같은_격리를_쓴다() {
        List<String> cmd = DockerCommands.runPython("c3", "/host/work", "pkg/main.py", 128);
        assertIsolated(cmd);
        assertHasPair(cmd, "-v", "/host/work:/work:ro");
        assertThat(cmd).contains("PYTHONDONTWRITEBYTECODE=1", DockerCommands.PYTHON_IMAGE);
        assertThat(cmd.subList(cmd.size() - 3, cmd.size())).containsExactly("python", "-B", "/work/src/pkg/main.py");
    }

    @Test
    void 인자에_들어가는_이름은_검증된_것만_쓴다() {
        for (String evil : List.of("Main; rm -rf /", "Main && id", "../Main", "Main$(id)", "a b", "", "1Main", "Ma-in")) {
            assertThatThrownBy(() -> DockerCommands.runJava("c", "/w", evil, 256))
                    .as(evil).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(DockerCommands.validClassName("com.example.Main")).isTrue();
        for (String evil : List.of("../main.py", "/etc/passwd", "a b.py", "main.py; id", "a/../b.py", "", "x/")) {
            assertThatThrownBy(() -> DockerCommands.runPython("c", "/w", evil, 256))
                    .as(evil).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(DockerCommands.validPythonPath("src/main.py")).isTrue();
    }

    @Test
    void 이미지는_언어별로_고정되어_있다() {
        assertThat(DockerCommands.image(GradingLanguage.JAVA)).isEqualTo("eclipse-temurin:17-jdk");
        assertThat(DockerCommands.image(GradingLanguage.PYTHON)).isEqualTo("python:3.12-slim");
    }
}
