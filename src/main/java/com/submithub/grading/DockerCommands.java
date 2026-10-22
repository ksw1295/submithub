package com.submithub.grading;

import com.submithub.domain.GradingLanguage;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 학생 코드를 실행하는 docker 명령(인자 목록)을 만든다. 격리 옵션은 모두 여기에 모아 두었고
 * DockerCommandsTest 가 옵션이 빠지지 않았는지 검사한다.
 *
 * 학생이 통제할 수 있는 값(파일 경로, 입력)은 명령 인자나 셸 문자열에 넣지 않는다: 파일은 마운트한 폴더로,
 * 입력은 표준입력으로만 전달한다. 인자에 들어가는 이름(클래스 이름, Python 파일 경로)은 정규식으로 검증한 것만 쓴다.
 */
public final class DockerCommands {

    public static final String JAVA_IMAGE = "eclipse-temurin:17-jdk";
    public static final String PYTHON_IMAGE = "python:3.12-slim";

    /** 컴파일: 파일 이름에 공백이 있어도 안전하도록 -print0 / xargs -0 을 쓴다. 학생 입력이 셸 문자열에 들어가지 않는다 */
    static final String JAVAC_SCRIPT =
            "find /work/src -name '*.java' -print0 | xargs -0 javac -encoding UTF-8 -J-XX:-UsePerfData -d /work/out 2>&1";

    private static final Pattern CLASS_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*$");
    private static final Pattern PY_PATH = Pattern.compile("^[A-Za-z0-9_][A-Za-z0-9_./-]*$");

    private DockerCommands() {
    }

    public static String image(GradingLanguage language) {
        return language == GradingLanguage.JAVA ? JAVA_IMAGE : PYTHON_IMAGE;
    }

    public static boolean validClassName(String name) {
        return name != null && CLASS_NAME.matcher(name).matches();
    }

    public static boolean validPythonPath(String path) {
        return path != null && PY_PATH.matcher(path).matches() && !path.contains("..") && !path.endsWith("/");
    }

    /**
     * 공통 격리 옵션: 네트워크 차단, 읽기 전용 루트 파일시스템, 작은 임시 영역, 메모리·CPU·프로세스 수 제한,
     * 모든 권한 제거, 권한 상승 금지, 비루트 사용자, 파일 크기·열린 파일 제한, 종료 시 컨테이너 삭제(--rm).
     * 마운트는 작업 폴더 하나뿐이다 (Docker 소켓이나 호스트 경로는 마운트하지 않는다).
     */
    static List<String> isolation(String containerName, String hostWorkDir, boolean workWritable, boolean interactive,
                                  int memoryMb, int pidsLimit, int tmpfsMb) {
        List<String> a = new ArrayList<>();
        a.add("run");
        a.add("--rm");
        if (interactive) {
            a.add("-i");
        }
        a.addAll(List.of(
                "--name", containerName,
                "--network", "none",
                "--read-only",
                "--tmpfs", "/tmp:rw,noexec,nosuid,size=" + tmpfsMb + "m",
                "--memory", memoryMb + "m",
                "--memory-swap", memoryMb + "m",
                "--cpus", "1",
                "--pids-limit", String.valueOf(pidsLimit),
                "--cap-drop", "ALL",
                "--security-opt", "no-new-privileges",
                "--ulimit", "nofile=256:256",
                "--ulimit", "fsize=10485760:10485760",
                "-u", "65534:65534",
                "-e", "LANG=C.UTF-8",
                "-v", hostWorkDir + ":/work:" + (workWritable ? "rw" : "ro"),
                "-w", "/work"));
        return a;
    }

    /** Java 컴파일 (작업 폴더에 out/ 을 써야 하므로 이 단계에서만 쓰기 가능으로 마운트) */
    static List<String> compileJava(String containerName, String hostWorkDir) {
        List<String> a = isolation(containerName, hostWorkDir, true, false, 512, 256, 64);
        a.addAll(List.of(JAVA_IMAGE, "sh", "-c", JAVAC_SCRIPT));
        return a;
    }

    static List<String> runJava(String containerName, String hostWorkDir, String mainClass, int memoryMb) {
        if (!validClassName(mainClass)) {
            throw new IllegalArgumentException("실행할 클래스 이름이 올바르지 않습니다: " + mainClass);
        }
        int heap = Math.max(16, (int) (memoryMb * 0.6));
        List<String> a = isolation(containerName, hostWorkDir, false, true, memoryMb, 64, 16);
        a.addAll(List.of(JAVA_IMAGE, "java", "-Xmx" + heap + "m", "-Xss512k", "-XX:-UsePerfData", "-XX:+UseSerialGC",
                "-cp", "/work/out", mainClass));
        return a;
    }

    static List<String> runPython(String containerName, String hostWorkDir, String mainFile, int memoryMb) {
        if (!validPythonPath(mainFile)) {
            throw new IllegalArgumentException("실행할 Python 파일 경로가 올바르지 않습니다: " + mainFile);
        }
        List<String> a = isolation(containerName, hostWorkDir, false, true, memoryMb, 64, 16);
        a.addAll(List.of("-e", "PYTHONDONTWRITEBYTECODE=1", PYTHON_IMAGE, "python", "-B", "/work/src/" + mainFile));
        return a;
    }
}
