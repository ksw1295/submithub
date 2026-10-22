package com.submithub.ai;

import com.submithub.ai.AiJudgeProvider.AiJudgeException;
import com.submithub.ai.AiJudgeProvider.JudgeRequest;
import com.submithub.domain.AiVerdict;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 Gemini 대신 같은 PC 의 가짜 HTTP 서버로 응답을 흉내 낸다. */
class GeminiJudgeProviderTest {

    HttpServer server;
    int status = 200;
    String responseBody = "";
    long delayMillis = 0;
    final AtomicReference<String> lastBody = new AtomicReference<>();
    final AtomicReference<String> lastKeyHeader = new AtomicReference<>();
    final AtomicReference<String> lastPath = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastKeyHeader.set(ex.getRequestHeaders().getFirst("x-goog-api-key"));
            lastPath.set(ex.getRequestURI().getPath());
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException ignored) {
                // 클라이언트가 먼저 끊은 경우
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            try {
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(status, bytes.length);
                ex.getResponseBody().write(bytes);
            } catch (IOException ignored) {
                // 시간 초과로 클라이언트가 이미 끊음
            } finally {
                ex.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    GeminiJudgeProvider provider(String key, Duration timeout) {
        return new GeminiJudgeProvider(() -> key, () -> "test-model", "http://127.0.0.1:" + server.getAddress().getPort(), timeout);
    }

    static String wrap(String text) {
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + escaped + "\"}]}}]}";
    }

    static final JudgeRequest REQ = new JudgeRequest("범위 검사를 하세요", "A.java", 3, List.of("for (...) c = c.next;"),
            3, List.of("for (... && c != null) c = c.next;"));

    @Test
    void 정상_응답을_판정으로_바꾼다() {
        responseBody = wrap("{\"verdict\":\"PARTIAL\",\"reason\":\"null 은 막았지만 음수 index 가 남았습니다.\"}");
        var r = provider("k", Duration.ofSeconds(5)).judge(REQ);
        assertThat(r.verdict()).isEqualTo(AiVerdict.PARTIAL);
        assertThat(r.reason()).contains("음수");
        assertThat(r.model()).isEqualTo("test-model");
    }

    @Test
    void 요청은_키를_헤더로_보내고_본문에는_코드와_지시가_있고_키는_없다() {
        responseBody = wrap("{\"verdict\":\"RESOLVED\",\"reason\":\"ok\"}");
        provider("SECRET-KEY-123", Duration.ofSeconds(5)).judge(REQ);

        assertThat(lastKeyHeader.get()).isEqualTo("SECRET-KEY-123");
        assertThat(lastPath.get()).isEqualTo("/v1beta/models/test-model:generateContent");
        String body = lastBody.get();
        assertThat(body).doesNotContain("SECRET-KEY-123");
        assertThat(body).contains("범위 검사를 하세요").contains("c != null").contains("untrusted DATA")
                .contains("systemInstruction").contains("\"responseMimeType\":\"application/json\"");
    }

    @Test
    void 코드블록으로_감싼_JSON도_받는다() {
        responseBody = wrap("```json\n{\"verdict\":\"NOT_RESOLVED\",\"reason\":\"그대로입니다\"}\n```");
        assertThat(provider("k", Duration.ofSeconds(5)).judge(REQ).verdict()).isEqualTo(AiVerdict.NOT_RESOLVED);
    }

    @Test
    void JSON_이_아니거나_판정_값이_허용_목록에_없으면_실패() {
        for (String bad : List.of("죄송하지만 판정할 수 없습니다", "{\"verdict\":\"HACKED\",\"reason\":\"x\"}",
                "{\"reason\":\"판정 값 없음\"}", "[1,2]", "")) {
            responseBody = wrap(bad);
            assertThatThrownBy(() -> provider("k", Duration.ofSeconds(5)).judge(REQ))
                    .as(bad).isInstanceOf(AiJudgeException.class);
        }
        responseBody = "{\"unexpected\": true}";
        assertThatThrownBy(() -> provider("k", Duration.ofSeconds(5)).judge(REQ))
                .isInstanceOf(AiJudgeException.class).hasMessageContaining("형식");
    }

    @Test
    void 근거가_길면_자른다() {
        responseBody = wrap("{\"verdict\":\"RESOLVED\",\"reason\":\"" + "가".repeat(900) + "\"}");
        assertThat(provider("k", Duration.ofSeconds(5)).judge(REQ).reason()).hasSize(501).endsWith("…");
    }

    @Test
    void 키_오류_한도_초과_서버_오류는_친절한_메시지로_실패한다() {
        responseBody = "{\"error\":{}}";
        status = 403;
        assertThatThrownBy(() -> provider("bad", Duration.ofSeconds(5)).judge(REQ))
                .isInstanceOf(AiJudgeException.class).hasMessageContaining("API 키");
        status = 429;
        assertThatThrownBy(() -> provider("k", Duration.ofSeconds(5)).judge(REQ))
                .isInstanceOf(AiJudgeException.class).hasMessageContaining("한도");
        status = 500;
        assertThatThrownBy(() -> provider("k", Duration.ofSeconds(5)).judge(REQ))
                .isInstanceOf(AiJudgeException.class).hasMessageContaining("500");
    }

    @Test
    void 응답이_늦으면_시간_초과로_실패한다() {
        responseBody = wrap("{\"verdict\":\"RESOLVED\",\"reason\":\"늦은 응답\"}");
        delayMillis = 1500;
        long t = System.currentTimeMillis();
        assertThatThrownBy(() -> provider("k", Duration.ofMillis(300)).judge(REQ))
                .isInstanceOf(AiJudgeException.class).hasMessageContaining("시간");
        assertThat(System.currentTimeMillis() - t).isLessThan(1400);
    }

    @Test
    void 서버에_연결할수_없으면_실패하고_키가_없으면_사용할수_없다() {
        server.stop(0);
        assertThatThrownBy(() -> provider("k", Duration.ofSeconds(2)).judge(REQ)).isInstanceOf(AiJudgeException.class);

        assertThat(provider(null, Duration.ofSeconds(1)).available()).isFalse();
        assertThat(provider("  ", Duration.ofSeconds(1)).available()).isFalse();
        assertThat(provider("k", Duration.ofSeconds(1)).available()).isTrue();
        assertThatThrownBy(() -> provider(null, Duration.ofSeconds(1)).judge(REQ))
                .isInstanceOf(AiJudgeException.class).hasMessageContaining("키");
    }

    @Test
    void 환경변수가_없는_기본_생성자는_키가_없어_사용할수_없다() {
        // 개발 PC 에 GEMINI_API_KEY 가 있으면 true 이므로 값은 환경변수 유무와 일치해야 한다
        boolean hasEnv = System.getenv("GEMINI_API_KEY") != null && !System.getenv("GEMINI_API_KEY").isBlank();
        assertThat(new GeminiJudgeProvider().available()).isEqualTo(hasEnv);
    }
}
