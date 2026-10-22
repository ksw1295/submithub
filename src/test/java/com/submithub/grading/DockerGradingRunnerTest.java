package com.submithub.grading;

import com.submithub.domain.GradingLanguage;
import com.submithub.grading.GradingRunner.Case;
import com.submithub.grading.GradingRunner.CaseStatus;
import com.submithub.grading.GradingRunner.GradingException;
import com.submithub.grading.GradingRunner.Outcome;
import com.submithub.grading.GradingRunner.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * DockerGradingRunner 의 프로세스 처리(입력 전달, 시간 초과, 출력 폭주, 종료 코드, 경로 검사, 작업 폴더 정리)를
 * 실제 Docker 대신 가짜 docker 스크립트(bash)로 검증한다. bash 가 없으면 건너뛴다.
 * 실제 컨테이너 동작은 DockerGradingRunnerDockerTest (Docker 가 있을 때만 실행) 가 확인한다.
 */
class DockerGradingRunnerTest {

    @TempDir Path tmp;
    Path script;
    Path workRoot;
    DockerGradingRunner runner;

    @BeforeEach
    void setUp() throws Exception {
        script = tmp.resolve("fake-docker.sh");
        String content;
        try (var in = getClass().getResourceAsStream("/fake-docker.sh")) {
            content = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
        Files.writeString(script, content, StandardCharsets.UTF_8);
        workRoot = tmp.resolve("work");
        String scriptPath = script.toString().replace('\\', '/');
        List<String> bash = findBash(scriptPath);
        assumeTrue(bash != null, "가짜 docker 스크립트를 실행할 수 있는 bash 가 없어 건너뜁니다");
        runner = new DockerGradingRunner(bash, workRoot);
    }

    /**
     * 스크립트를 실제로 실행할 수 있는 bash 를 찾는다. Windows 에서는 PATH 의 bash 가 WSL 런처(System32)일 수 있어서
     * Git Bash 위치를 먼저 시도하고, "version" 을 실행해 기대한 출력이 나오는 것만 쓴다.
     */
    static List<String> findBash(String scriptPath) {
        for (String candidate : List.of("C:/Program Files/Git/bin/bash.exe", "C:/Program Files/Git/usr/bin/bash.exe", "/bin/bash", "bash")) {
            try {
                Process p = new ProcessBuilder(candidate, scriptPath, "version").redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (p.waitFor() == 0 && out.contains("99.0.0")) {
                    return List.of(candidate, scriptPath);
                }
            } catch (IOException | InterruptedException ignored) {
                // 다음 후보
            }
        }
        return null;
    }

    static Request javaRequest(String scenario, int timeLimit, String... inputs) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("Main.java", ("// SCENARIO:" + scenario + "\npublic class Main { public static void main(String[] a) {} }\n").getBytes(StandardCharsets.UTF_8));
        List<Case> cases = new java.util.ArrayList<>();
        for (int i = 0; i < inputs.length; i++) {
            cases.add(new Case((long) (i + 1), inputs[i]));
        }
        return new Request(GradingLanguage.JAVA, "Main.java", files, cases, timeLimit, 256);
    }

    List<String> dockerLog() throws IOException {
        Path log = tmp.resolve("docker.log");
        return Files.exists(log) ? Files.readAllLines(log) : List.of();
    }

    boolean workDirEmpty() throws IOException {
        if (!Files.exists(workRoot)) {
            return true;
        }
        try (Stream<Path> s = Files.list(workRoot)) {
            return s.findAny().isEmpty();
        }
    }

    @Test
    void docker_가_응답하면_사용할수_있고_없으면_사용할수_없다() {
        assertThat(runner.available()).isTrue();
        var missing = new DockerGradingRunner(List.of("this-docker-does-not-exist-xyz"), workRoot);
        assertThat(missing.available()).isFalse();
        assertThat(runner.name()).isEqualTo("docker");
    }

    @Test
    void 케이스마다_표준입력을_전달하고_표준출력을_돌려준다() throws Exception {
        Outcome o = runner.run(javaRequest("echo", 5, "hello\nworld\n", "", "x y z"));

        assertThat(o.compiled()).isTrue();
        assertThat(o.cases()).hasSize(3);
        assertThat(o.cases().get(0).status()).isEqualTo(CaseStatus.OK);
        assertThat(o.cases().get(0).stdout()).isEqualTo("hello\nworld\n");
        assertThat(o.cases().get(1).stdout()).isEmpty();
        assertThat(o.cases().get(2).stdout()).isEqualTo("x y z");
        assertThat(o.cases()).extracting(GradingRunner.CaseOutcome::id).containsExactly(1L, 2L, 3L);
    }

    @Test
    void 컴파일과_실행은_각각_별도_컨테이너이고_격리_옵션이_실제_명령에_들어간다() throws Exception {
        runner.run(javaRequest("echo", 5, "a", "b"));

        List<String> log = dockerLog().stream().filter(l -> l.startsWith("run ")).toList();
        assertThat(log).hasSize(3);                                  // 컴파일 1 + 케이스 2
        String compile = log.get(0);
        assertThat(compile).contains("xargs -0 javac").contains(":/work:rw");
        for (String line : log) {
            assertThat(line).contains("--network none").contains("--read-only").contains("--cap-drop ALL")
                    .contains("--security-opt no-new-privileges").contains("-u 65534:65534").contains("--pids-limit")
                    .contains("--memory-swap").contains("--rm").contains("--name submithub-grade-");
            assertThat(line).doesNotContain("--privileged").doesNotContain("docker.sock");
        }
        for (String line : log.subList(1, 3)) {
            assertThat(line).contains(":/work:ro").contains("--memory 256m").contains("-cp /work/out Main");
        }
        // 컨테이너 이름은 매번 다르다
        assertThat(log.stream().map(l -> l.replaceAll(".*--name (\\S+).*", "$1")).distinct()).hasSize(3);
    }

    @Test
    void 작업_폴더는_채점이_끝나면_지워진다() throws Exception {
        runner.run(javaRequest("echo", 5, "a"));
        assertThat(workDirEmpty()).isTrue();

        assertThatThrownBy(() -> runner.run(javaRequest("docker125", 5, "a"))).isInstanceOf(GradingException.class);
        assertThat(workDirEmpty()).as("오류가 나도 작업 폴더는 지워져야 한다").isTrue();
    }

    @Test
    void 컴파일_오류는_컴파일_메시지와_함께_돌려준다() {
        Outcome o = runner.run(javaRequest("compilefail", 5, "a"));
        assertThat(o.compiled()).isFalse();
        assertThat(o.compileMessage()).contains("error: ';' expected");
        assertThat(o.cases()).isEmpty();
    }

    @Test
    void 시간이_초과되면_컨테이너를_지우고_시간_초과로_돌려준다() throws Exception {
        long t = System.currentTimeMillis();
        Outcome o = runner.run(javaRequest("sleep", 1, "a"));
        long took = System.currentTimeMillis() - t;

        assertThat(o.cases()).hasSize(1);
        assertThat(o.cases().get(0).status()).isEqualTo(CaseStatus.TIMEOUT);
        assertThat(took).as("제한(1초 + 시작 여유 3초) 안에 끝나야 한다").isLessThan(12_000);
        // 시간 초과된 컨테이너를 docker rm -f 로 지웠다
        List<String> log = dockerLog();
        List<String> runs = log.stream().filter(l -> l.startsWith("run ") && l.contains("-cp /work/out Main")).toList();
        String name = runs.get(0).replaceAll(".*--name (\\S+).*", "$1");
        assertThat(log).contains("rm -f " + name);
    }

    @Test
    void 종료_코드에_따라_런타임_오류로_분류한다() {
        var fail = runner.run(javaRequest("fail1", 5, "a")).cases().get(0);
        assertThat(fail.status()).isEqualTo(CaseStatus.RUNTIME_ERROR);
        assertThat(fail.stderr()).contains("boom");

        var killed = runner.run(javaRequest("exit137", 5, "a")).cases().get(0);
        assertThat(killed.status()).isEqualTo(CaseStatus.RUNTIME_ERROR);
        assertThat(killed.stderr()).contains("메모리");
    }

    @Test
    void 출력이_너무_많으면_중단하고_잘라서_저장한다() {
        long t = System.currentTimeMillis();
        var c = runner.run(javaRequest("flood", 20, "a")).cases().get(0);
        long took = System.currentTimeMillis() - t;

        assertThat(c.status()).isEqualTo(CaseStatus.RUNTIME_ERROR);
        assertThat(c.stderr()).contains("출력이 너무 많습니다");
        assertThat(c.stdout().length()).isLessThan(DockerGradingRunner.STORED_OUTPUT_BYTES + 100);
        assertThat(took).as("시간 제한을 다 기다리지 않고 바로 끝난다").isLessThan(15_000);
    }

    @Test
    void docker_자체_오류는_학생_코드의_실패가_아니라_채점_예외다() {
        assertThatThrownBy(() -> runner.run(javaRequest("docker125", 5, "a")))
                .isInstanceOf(GradingException.class).hasMessageContaining("docker");
        var missing = new DockerGradingRunner(List.of("this-docker-does-not-exist-xyz"), workRoot);
        assertThatThrownBy(() -> missing.run(javaRequest("echo", 5, "a"))).isInstanceOf(GradingException.class);
    }

    @Test
    void Python_문법_오류는_컴파일_오류로_본다() {
        Map<String, byte[]> files = Map.of("main.py", "# SCENARIO:syntax\nprint(\n".getBytes(StandardCharsets.UTF_8));
        Outcome o = runner.run(new Request(GradingLanguage.PYTHON, "main.py", files, List.of(new Case(1L, ""), new Case(2L, "")), 5, 128));
        assertThat(o.compiled()).isFalse();
        assertThat(o.compileMessage()).contains("SyntaxError");
    }

    @Test
    void Python_은_실행_파일을_읽기_전용으로_실행한다() throws Exception {
        Map<String, byte[]> files = Map.of("main.py", "# SCENARIO:echo\n".getBytes(StandardCharsets.UTF_8));
        Outcome o = runner.run(new Request(GradingLanguage.PYTHON, "main.py", files, List.of(new Case(1L, "abc")), 5, 128));
        assertThat(o.cases().get(0).stdout()).isEqualTo("abc");
        String line = dockerLog().stream().filter(l -> l.startsWith("run ")).findFirst().orElseThrow();
        assertThat(line).contains(":/work:ro").contains("python -B /work/src/main.py").contains("python:3.12-slim");
        assertThat(dockerLog().stream().filter(l -> l.startsWith("run ")).count()).isEqualTo(1);   // 컴파일 단계 없음
    }

    @Test
    void 경로가_작업_폴더_밖으로_나가는_파일은_거부한다() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("Main.java", "// SCENARIO:echo\nclass Main {}".getBytes(StandardCharsets.UTF_8));
        files.put("../evil.txt", "x".getBytes(StandardCharsets.UTF_8));
        Request r = new Request(GradingLanguage.JAVA, "Main.java", files, List.of(new Case(1L, "")), 5, 256);

        assertThatThrownBy(() -> runner.run(r)).isInstanceOf(GradingException.class).hasMessageContaining("경로");
        try (Stream<Path> s = Files.walk(tmp)) {
            assertThat(s.filter(p -> p.getFileName().toString().equals("evil.txt"))).isEmpty();
        }
        assertThat(dockerLog().stream().filter(l -> l.startsWith("run "))).isEmpty();      // 컨테이너도 띄우지 않았다
    }

    @Test
    void 실행_파일이_없거나_클래스_이름이_올바르지_않으면_거부한다() {
        Request noMain = new Request(GradingLanguage.JAVA, "Main.java", Map.of("Other.java", new byte[0]), List.of(), 5, 256);
        assertThatThrownBy(() -> runner.run(noMain)).isInstanceOf(GradingException.class);

        Map<String, byte[]> bad = Map.of("a b.java", "class X {}".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> runner.run(new Request(GradingLanguage.JAVA, "a b.java", bad, List.of(new Case(1L, "")), 5, 256)))
                .isInstanceOf(GradingException.class);
    }

    @Test
    void package_선언이_있으면_전체_클래스_이름으로_실행한다() {
        Map<String, byte[]> files = Map.of("src/app/Main.java",
                "package app.core;\npublic class Main {}".getBytes(StandardCharsets.UTF_8));
        assertThat(DockerGradingRunner.mainClass(new Request(GradingLanguage.JAVA, "src/app/Main.java", files, List.of(), 5, 256)))
                .isEqualTo("app.core.Main");
        Map<String, byte[]> plain = Map.of("Main.java", "public class Main {}".getBytes(StandardCharsets.UTF_8));
        assertThat(DockerGradingRunner.mainClass(new Request(GradingLanguage.JAVA, "Main.java", plain, List.of(), 5, 256)))
                .isEqualTo("Main");
    }
}
