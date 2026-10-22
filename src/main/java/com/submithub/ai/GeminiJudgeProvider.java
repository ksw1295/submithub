package com.submithub.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.submithub.domain.AiVerdict;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Gemini REST API(generateContent)로 반영 여부를 판정한다.
 * API 키는 환경변수 GEMINI_API_KEY 로만 읽는다 (설정 파일·DB 에 두지 않는다). 모델은 GEMINI_MODEL 로 바꿀 수 있다.
 */
@Component
public class GeminiJudgeProvider implements AiJudgeProvider {

    public static final String DEFAULT_MODEL = "gemini-3.8-flash";
    public static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";
    public static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Supplier<String> apiKey;
    private final Supplier<String> model;
    private final String baseUrl;
    private final Duration timeout;
    private final HttpClient http;

    @Autowired
    public GeminiJudgeProvider() {
        this(() -> System.getenv("GEMINI_API_KEY"),
                () -> {
                    String m = System.getenv("GEMINI_MODEL");
                    return m == null || m.isBlank() ? DEFAULT_MODEL : m;
                },
                DEFAULT_BASE_URL, TIMEOUT);
    }

    /** 테스트에서 가짜 서버 주소와 짧은 제한 시간을 쓰려고 열어 둔 생성자 */
    GeminiJudgeProvider(Supplier<String> apiKey, Supplier<String> model, String baseUrl, Duration timeout) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public boolean available() {
        String key = apiKey.get();
        return key != null && !key.isBlank();
    }

    @Override
    public JudgeResult judge(JudgeRequest request) {
        if (!available()) {
            throw new AiJudgeException("AI API 키가 설정되지 않았습니다.");
        }
        String modelName = model.get();
        String token = JudgePrompt.newToken();
        HttpRequest http = HttpRequest.newBuilder(URI.create(baseUrl + "/v1beta/models/" + modelName + ":generateContent"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey.get())
                .POST(HttpRequest.BodyPublishers.ofString(body(request, token)))
                .build();

        HttpResponse<String> response;
        try {
            response = this.http.send(http, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new AiJudgeException("AI 응답 시간이 초과되었습니다 (" + timeout.toSeconds() + "초).", e);
        } catch (IOException e) {
            throw new AiJudgeException("AI 서비스에 연결하지 못했습니다.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiJudgeException("AI 판정이 중단되었습니다.", e);
        }
        int status = response.statusCode();
        if (status == 400 || status == 401 || status == 403) {
            throw new AiJudgeException("AI API 키 또는 요청이 올바르지 않습니다 (HTTP " + status + ").");
        }
        if (status == 429) {
            throw new AiJudgeException("AI 요청 한도를 초과했습니다. 잠시 후 다시 시도하세요.");
        }
        if (status != 200) {
            throw new AiJudgeException("AI 서비스 오류 (HTTP " + status + ").");
        }
        return parse(response.body(), modelName);
    }

    private static String body(JudgeRequest request, String token) {
        ObjectNode root = JSON.createObjectNode();
        root.putObject("systemInstruction").putArray("parts").addObject().put("text", JudgePrompt.SYSTEM);
        ArrayNode contents = root.putArray("contents");
        ObjectNode user = contents.addObject();
        user.put("role", "user");
        user.putArray("parts").addObject().put("text", JudgePrompt.user(request, token));
        ObjectNode gen = root.putObject("generationConfig");
        gen.put("temperature", 0);
        gen.put("responseMimeType", "application/json");
        gen.putObject("thinkingConfig").put("thinkingLevel", "LOW");
        return root.toString();
    }

    /** API 응답 전체에서 판정 JSON 을 꺼내 검증한다. 형식이 다르면 실패로 본다 */
    static JudgeResult parse(String responseBody, String modelName) {
        try {
            JsonNode text = JSON.readTree(responseBody).path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (!text.isTextual()) {
                throw new AiJudgeException("AI 응답 형식이 올바르지 않습니다.");
            }
            return parseVerdict(text.asText(), modelName);
        } catch (IOException e) {
            throw new AiJudgeException("AI 응답을 해석하지 못했습니다.", e);
        }
    }

    static JudgeResult parseVerdict(String text, String modelName) {
        String t = text.strip();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        JsonNode node;
        try {
            node = JSON.readTree(t);
        } catch (IOException e) {
            throw new AiJudgeException("AI 응답이 JSON 이 아닙니다.", e);
        }
        String verdict = node.path("verdict").asText("");
        AiVerdict v = switch (verdict) {
            case "RESOLVED" -> AiVerdict.RESOLVED;
            case "PARTIAL" -> AiVerdict.PARTIAL;
            case "NOT_RESOLVED" -> AiVerdict.NOT_RESOLVED;
            default -> throw new AiJudgeException("AI 응답의 판정 값이 올바르지 않습니다.");
        };
        String reason = node.path("reason").asText("").strip();
        if (reason.isEmpty()) {
            reason = "(근거 없음)";
        }
        if (reason.length() > 500) {
            reason = reason.substring(0, 500) + "…";
        }
        return new JudgeResult(v, reason, modelName);
    }
}
