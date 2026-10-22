package com.submithub.grading;

import com.submithub.AppProperties;
import com.submithub.domain.GradingLanguage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Docker 격리 컨테이너에서 제출 코드를 실행한다. 이 서버(호스트)에서 학생 코드를 직접 실행하는 경로는 없다:
 * 이 클래스가 시작하는 프로세스는 항상 `docker` 명령이고, 학생 코드는 컨테이너 안에서만 돈다.
 *
 * 흐름: 작업 폴더에 제출 파일을 쓴다 → (Java) 컴파일 컨테이너 → 테스트 케이스마다 새 컨테이너로 실행(읽기 전용 마운트)
 *       → 작업 폴더 삭제. 시간 초과 시 `docker rm -f` 로 컨테이너를 지운다.
 */
@Component
public class DockerGradingRunner implements GradingRunner {

    private static final Logger log = LoggerFactory.getLogger(DockerGradingRunner.class);

    static final int STORED_OUTPUT_BYTES = 64 * 1024;
    static final int MAX_OUTPUT_BYTES = 1024 * 1024;
    /** 컨테이너를 띄우는 시간(이미지 로딩, JVM 시작)을 시간 제한에 더해 주는 여유 */
    static final long START_GRACE_MILLIS = 3000;
    static final long COMPILE_TIMEOUT_MILLIS = 60_000;
    private static final long AVAILABILITY_TTL_MILLIS = 30_000;

    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_.]*)\\s*;");

    private final List<String> docker;
    private final Path workRoot;
    private volatile long checkedAt;
    private volatile boolean lastAvailable;

    @Autowired
    public DockerGradingRunner(AppProperties props) {
        this(List.of("docker"), Path.of(props.storageDir()).toAbsolutePath().normalize().resolve("grading-work"));
    }

    /** 테스트에서 가짜 docker 명령으로 바꿔 쓰려고 열어 둔 생성자 */
    DockerGradingRunner(List<String> dockerCommand, Path workRoot) {
        this.docker = List.copyOf(dockerCommand);
        this.workRoot = workRoot;
    }

    @Override
    public String name() {
        return "docker";
    }

    @Override
    public boolean available() {
        long now = System.currentTimeMillis();
        if (now - checkedAt < AVAILABILITY_TTL_MILLIS) {
            return lastAvailable;
        }
        boolean ok;
        try {
            Exec e = exec(List.of("version", "--format", "{{.Server.Version}}"), null, 8000, null);
            ok = !e.timedOut() && e.exit() == 0 && !e.stdout().isBlank();
        } catch (RuntimeException e) {
            ok = false;
        }
        lastAvailable = ok;
        checkedAt = now;
        return ok;
    }

    @Override
    public Outcome run(Request request) {
        validate(request);
        Path work = null;
        try {
            Files.createDirectories(workRoot);
            work = Files.createTempDirectory(workRoot, "run-");
            Path src = Files.createDirectories(work.resolve("src"));
            Path out = Files.createDirectories(work.resolve("out"));
            openPermissions(work, src, out);
            writeFiles(src, request.files());
            String hostDir = work.toAbsolutePath().toString();

            String mainClass = null;
            if (request.language() == GradingLanguage.JAVA) {
                mainClass = mainClass(request);
                Outcome compile = compileJava(hostDir);
                if (compile != null) {
                    return compile;
                }
            }

            List<CaseOutcome> outcomes = new ArrayList<>();
            for (Case c : request.cases()) {
                String name = containerName();
                List<String> cmd = request.language() == GradingLanguage.JAVA
                        ? DockerCommands.runJava(name, hostDir, mainClass, request.memoryLimitMb())
                        : DockerCommands.runPython(name, hostDir, request.mainFile(), request.memoryLimitMb());
                Exec e = exec(cmd, c.input() == null ? "" : c.input(),
                        request.timeLimitSeconds() * 1000L + START_GRACE_MILLIS, name);
                if (e.exit() == 125 && !e.timedOut()) {
                    throw new GradingException("docker 실행 오류: " + abbreviate(e.stderr()));
                }
                if (request.language() == GradingLanguage.PYTHON && outcomes.isEmpty() && isPythonSyntaxError(e)) {
                    return new Outcome(false, abbreviate(e.stderr()), List.of());
                }
                outcomes.add(toOutcome(c.id(), e));
            }
            return new Outcome(true, null, outcomes);
        } catch (IOException e) {
            throw new GradingException("채점 작업 폴더를 준비하지 못했습니다.", e);
        } finally {
            deleteQuietly(work);
        }
    }

    // ---------- 준비 ----------

    private static void validate(Request r) {
        if (r.files() == null || !r.files().containsKey(r.mainFile())) {
            throw new GradingException("실행할 파일이 제출물에 없습니다: " + r.mainFile());
        }
        if (r.language() == GradingLanguage.PYTHON && !DockerCommands.validPythonPath(r.mainFile())) {
            throw new GradingException("실행할 Python 파일 경로가 올바르지 않습니다: " + r.mainFile());
        }
    }

    /** 제출 파일을 src/ 아래에 쓴다. 경로가 src/ 밖으로 나가면 거부한다 (이미 정규화된 경로지만 한 번 더 확인) */
    static void writeFiles(Path src, Map<String, byte[]> files) throws IOException {
        Path root = src.toAbsolutePath().normalize();
        for (var e : files.entrySet()) {
            Path target = root.resolve(e.getKey()).normalize();
            if (!target.startsWith(root) || target.equals(root)) {
                throw new GradingException("허용되지 않는 파일 경로입니다: " + e.getKey());
            }
            Files.createDirectories(target.getParent());
            Files.write(target, e.getValue());
        }
    }

    /** 컨테이너의 비루트 사용자가 쓸 수 있도록 작업 폴더 권한을 연다 (POSIX 에서만) */
    private static void openPermissions(Path... dirs) {
        for (Path d : dirs) {
            try {
                Files.setPosixFilePermissions(d, PosixFilePermissions.fromString("rwxrwxrwx"));
            } catch (UnsupportedOperationException | IOException ignored) {
                // Windows 등: Docker Desktop 이 마운트 권한을 알아서 처리한다
            }
        }
    }

    /** Java 실행 클래스: 파일 이름에서 확장자를 떼고, 파일에 package 선언이 있으면 붙인다 */
    static String mainClass(Request r) {
        String file = r.mainFile();
        String base = file.substring(file.lastIndexOf('/') + 1);
        if (!base.endsWith(".java")) {
            throw new GradingException("Java 실행 파일은 .java 로 끝나야 합니다: " + file);
        }
        String simple = base.substring(0, base.length() - ".java".length());
        String source = new String(r.files().get(file), StandardCharsets.UTF_8);
        Matcher m = PACKAGE.matcher(source);
        String name = m.find() ? m.group(1) + "." + simple : simple;
        if (!DockerCommands.validClassName(name)) {
            throw new GradingException("실행할 클래스 이름이 올바르지 않습니다: " + name);
        }
        return name;
    }

    private Outcome compileJava(String hostDir) {
        String name = containerName();
        Exec c = exec(DockerCommands.compileJava(name, hostDir), null, COMPILE_TIMEOUT_MILLIS, name);
        if (c.timedOut()) {
            return new Outcome(false, "컴파일 시간이 초과되었습니다.", List.of());
        }
        if (c.exit() == 125) {
            throw new GradingException("docker 실행 오류: " + abbreviate(c.stderr()));
        }
        if (c.exit() != 0) {
            String msg = (c.stdout() + c.stderr()).strip();
            return new Outcome(false, abbreviate(msg.isEmpty() ? "컴파일에 실패했습니다 (종료 코드 " + c.exit() + ")" : msg), List.of());
        }
        return null;
    }

    private static boolean isPythonSyntaxError(Exec e) {
        return e.exit() != 0 && !e.timedOut()
                && (e.stderr().contains("SyntaxError") || e.stderr().contains("IndentationError"));
    }

    private static CaseOutcome toOutcome(Long id, Exec e) {
        if (e.timedOut()) {
            return new CaseOutcome(id, CaseStatus.TIMEOUT, e.stdout(), e.stderr(), e.millis());
        }
        if (e.outputTooLarge()) {
            return new CaseOutcome(id, CaseStatus.RUNTIME_ERROR, e.stdout(), "출력이 너무 많습니다 (최대 1MB).", e.millis());
        }
        if (e.exit() == 0) {
            return new CaseOutcome(id, CaseStatus.OK, e.stdout(), e.stderr(), e.millis());
        }
        String err = e.exit() == 137
                ? "프로세스가 강제 종료되었습니다 (메모리 제한을 넘었을 수 있습니다).\n" + e.stderr()
                : e.stderr();
        return new CaseOutcome(id, CaseStatus.RUNTIME_ERROR, e.stdout(), abbreviate(err), e.millis());
    }

    // ---------- 프로세스 실행 ----------

    private record Exec(int exit, String stdout, String stderr, boolean timedOut, boolean outputTooLarge, long millis) {
    }

    private static String containerName() {
        return "submithub-grade-" + UUID.randomUUID();
    }

    /**
     * docker 명령을 실행한다. 표준입력으로 stdin 을 보내고, 표준출력·표준오류는 일정 크기까지만 저장한다.
     * 시간이 초과되면 containerName 의 컨테이너를 지우고 프로세스를 끝낸다.
     */
    private Exec exec(List<String> dockerArgs, String stdin, long timeoutMillis, String containerName) {
        List<String> cmd = new ArrayList<>(docker);
        cmd.addAll(dockerArgs);
        long start = System.nanoTime();
        Process p;
        try {
            p = new ProcessBuilder(cmd).start();
        } catch (IOException e) {
            throw new GradingException("docker 를 실행할 수 없습니다.", e);
        }
        Capture out = new Capture(p.getInputStream());
        Capture err = new Capture(p.getErrorStream());
        Thread tOut = daemon(out, "grade-out");
        Thread tErr = daemon(err, "grade-err");
        Thread tIn = daemon(() -> {
            try (OutputStream os = p.getOutputStream()) {
                if (stdin != null) {
                    os.write(stdin.getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
                // 프로그램이 입력을 다 읽기 전에 끝난 경우
            }
        }, "grade-in");
        tOut.start();
        tErr.start();
        tIn.start();

        boolean timedOut = false;
        boolean tooLarge = false;
        try {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            while (true) {
                long left = deadline - System.nanoTime();
                if (left <= 0) {
                    timedOut = true;
                    break;
                }
                if (p.waitFor(Math.min(50, TimeUnit.NANOSECONDS.toMillis(left) + 1), TimeUnit.MILLISECONDS)) {
                    break;
                }
                if (out.total() + err.total() > MAX_OUTPUT_BYTES) {
                    tooLarge = true;
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            timedOut = true;
        }
        if (timedOut || tooLarge) {
            killContainer(containerName);
            p.destroyForcibly();
            try {
                p.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        boolean killed = timedOut || tooLarge;
        if (killed) {
            // 끝낸 뒤에도 남아 있을 수 있는 하위 프로세스가 출력을 계속 쓰지 않도록 스트림을 닫는다.
            // 닫는 일이 읽는 스레드 때문에 막힐 수 있어서 별도 스레드에서 한다
            daemon(() -> {
                closeQuietly(p.getInputStream());
                closeQuietly(p.getErrorStream());
            }, "grade-close").start();
        }
        join(tOut, killed ? 1000 : 3000);
        join(tErr, killed ? 1000 : 3000);
        long millis = (System.nanoTime() - start) / 1_000_000;
        int exit = p.isAlive() ? -1 : p.exitValue();
        if (out.total() + err.total() > MAX_OUTPUT_BYTES) {
            tooLarge = true;
        }
        return new Exec(exit, out.text(), err.text(), timedOut, tooLarge, millis);
    }

    private void killContainer(String containerName) {
        if (containerName == null) {
            return;
        }
        try {
            List<String> cmd = new ArrayList<>(docker);
            cmd.addAll(List.of("rm", "-f", containerName));
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            p.waitFor(15, TimeUnit.SECONDS);
        } catch (IOException e) {
            log.warn("컨테이너를 지우지 못했습니다: {}", containerName, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // 이미 닫힘
        }
    }

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }

    private static void join(Thread t, long millis) {
        try {
            t.join(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 스트림을 끝까지 읽되 앞부분 STORED_OUTPUT_BYTES 만 저장한다 */
    private static final class Capture implements Runnable {
        private final InputStream in;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        private volatile long total;

        Capture(InputStream in) {
            this.in = in;
        }

        @Override
        public void run() {
            byte[] chunk = new byte[8192];
            try {
                int n;
                while ((n = in.read(chunk)) >= 0) {
                    synchronized (buf) {
                        int room = STORED_OUTPUT_BYTES - buf.size();
                        if (room > 0) {
                            buf.write(chunk, 0, Math.min(room, n));
                        }
                    }
                    total += n;
                }
            } catch (IOException ignored) {
                // 프로세스가 끝나면서 스트림이 닫힘
            }
        }

        long total() {
            return total;
        }

        String text() {
            synchronized (buf) {
                String s = buf.toString(StandardCharsets.UTF_8);
                return total > STORED_OUTPUT_BYTES ? s + "\n... (출력이 잘렸습니다)" : s;
            }
        }
    }

    private static String abbreviate(String s) {
        String t = s == null ? "" : s.strip();
        return t.length() > 4000 ? t.substring(0, 4000) + "\n... (생략)" : t;
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.deleteIfExists(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("채점 작업 폴더를 지우지 못했습니다: {}", dir, e);
        }
    }
}
