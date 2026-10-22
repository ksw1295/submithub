package com.submithub.experiments;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 생성한 변형이 (1) 컴파일되고 (2) 원본과 같은 출력을 내는지 확인한다. 변형 생성기가 의미를 바꾸지 않았다는 근거. */
final class JavaChecker {

    private JavaChecker() {
    }

    /** @return 프로그램의 표준 출력. 컴파일/실행에 실패하면 예외 */
    static String compileAndRun(String code, Path workDir) throws Exception {
        Files.createDirectories(workDir);
        Path source = workDir.resolve("Main.java");
        Files.writeString(source, code, StandardCharsets.UTF_8);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int rc = compiler.run(null, null, err, "-encoding", "UTF-8", "-d", workDir.toString(), source.toString());
        if (rc != 0) {
            throw new IllegalStateException("컴파일 실패:\n" + err.toString(StandardCharsets.UTF_8));
        }
        PrintStream oldOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{workDir.toUri().toURL()}, null)) {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            Class<?> main = loader.loadClass("Main");
            main.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(oldOut);
        }
        return captured.toString(StandardCharsets.UTF_8);
    }
}
