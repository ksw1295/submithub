package com.submithub.github;

import com.sun.net.httpserver.HttpServer;
import com.submithub.support.FakeGitHubClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 GitHub 대신 이 PC 의 작은 HTTP 서버로 GitHub API 응답(성공, 404, 한도 초과, 리다이렉트)을 흉내 내서 검증한다. */
class RestGitHubClientTest {

    HttpServer server;
    String base;
    final List<String> seen = new CopyOnWriteArrayList<>();
    final List<String> authHeaders = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    void route(String path, int status, String body, Map<String, String> headers) {
        server.createContext(path, ex -> {
            seen.add(ex.getRequestURI().toString());
            authHeaders.add(String.valueOf(ex.getRequestHeaders().getFirst("Authorization")));
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            headers.forEach((k, v) -> ex.getResponseHeaders().add(k, v));
            ex.sendResponseHeaders(status, b.length == 0 ? -1 : b.length);
            if (b.length > 0) {
                ex.getResponseBody().write(b);
            }
            ex.close();
        });
    }

    void routeBytes(String path, int status, byte[] body) {
        server.createContext(path, ex -> {
            seen.add(ex.getRequestURI().toString());
            authHeaders.add(String.valueOf(ex.getRequestHeaders().getFirst("Authorization")));
            ex.sendResponseHeaders(status, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
    }

    RestGitHubClient client(String token) {
        Predicate<URI> local = u -> "127.0.0.1".equals(u.getHost());
        return new RestGitHubClient(base, token, local);
    }

    @Test
    void 커밋을_찾아_SHA_와_메시지를_돌려준다() {
        String sha = "7fd1a60b01f91b314f59955a4e4d4e80d8edf11d";
        route("/repos/octocat/Hello-World/commits/main", 200,
                "{\"sha\":\"" + sha + "\",\"commit\":{\"message\":\"첫 줄\\n\\n본문\"}}", Map.of());
        var c = client(null).resolve("octocat", "Hello-World", "main");
        assertThat(c.sha()).isEqualTo(sha);
        assertThat(c.message()).startsWith("첫 줄");
    }

    @Test
    void 슬래시가_있는_브랜치는_구간별로_인코딩해서_보낸다() {
        route("/repos/a/b/commits/", 200,
                "{\"sha\":\"" + "a".repeat(40) + "\",\"commit\":{\"message\":\"m\"}}", Map.of());
        assertThat(client(null).resolve("a", "b", "feature/x y").sha()).isEqualTo("a".repeat(40));
        assertThat(seen.get(0)).contains("feature/x%20y");
    }

    @Test
    void 없는_저장소는_친절한_메시지() {
        route("/repos/a/none/commits/main", 404, "{\"message\":\"Not Found\"}", Map.of());
        assertThatThrownBy(() -> client(null).resolve("a", "none", "main"))
                .isInstanceOf(GitHubException.class).hasMessageContaining("저장소를 찾을 수 없습니다");
    }

    @Test
    void 없는_브랜치_422_는_브랜치_안내() {
        route("/repos/a/b/commits/nope", 422, "{\"message\":\"No commit found for SHA: nope\"}", Map.of());
        assertThatThrownBy(() -> client(null).resolve("a", "b", "nope"))
                .isInstanceOf(GitHubException.class).hasMessageContaining("브랜치 또는 커밋");
    }

    @Test
    void 요청_한도_초과는_기다릴_시간을_알려준다() {
        long reset = java.time.Instant.now().getEpochSecond() + 15 * 60;
        route("/repos/a/b/commits/main", 403, "{\"message\":\"API rate limit exceeded\"}",
                Map.of("X-RateLimit-Remaining", "0", "X-RateLimit-Reset", String.valueOf(reset)));
        assertThatThrownBy(() -> client(null).resolve("a", "b", "main"))
                .isInstanceOf(GitHubException.class).hasMessageContaining("요청 한도").hasMessageContaining("15분");
    }

    @Test
    void 한도_표시_없는_403_은_접근_불가_안내() {
        route("/repos/a/priv/commits/main", 403, "{\"message\":\"Forbidden\"}", Map.of());
        assertThatThrownBy(() -> client(null).resolve("a", "priv", "main"))
                .isInstanceOf(GitHubException.class).hasMessageContaining("접근할 수 없습니다");
    }

    @Test
    void GitHub_서버_오류와_연결_실패도_안내_문구() {
        route("/repos/a/b/commits/main", 503, "", Map.of());
        assertThatThrownBy(() -> client(null).resolve("a", "b", "main"))
                .isInstanceOf(GitHubException.class).hasMessageContaining("GitHub 서버");
        server.stop(0);
        assertThatThrownBy(() -> client(null).resolve("a", "b", "main"))
                .isInstanceOf(GitHubException.class).hasMessageContaining("연결하지 못했습니다");
    }

    @Test
    void 이상한_응답_형식은_이해하지_못했다고_알린다() {
        route("/repos/a/b/commits/main", 200, "{\"sha\":\"zzz\"}", Map.of());
        assertThatThrownBy(() -> client(null).resolve("a", "b", "main"))
                .isInstanceOf(GitHubException.class).hasMessageContaining("이해하지 못했습니다");
    }

    @Test
    void zipball_리다이렉트를_따라가고_토큰은_다운로드_서버로_보내지_않는다() {
        byte[] zip = FakeGitHubClient.zip("b-abc1234", Map.of("A.java", "class A {}".getBytes(StandardCharsets.UTF_8)));
        route("/repos/a/b/zipball/" + "c".repeat(40), 302, "", Map.of("Location", base + "/download/x.zip"));
        routeBytes("/download/x.zip", 200, zip);
        byte[] got = client("secret-token").zipball("a", "b", "c".repeat(40), 1024 * 1024);
        assertThat(got).isEqualTo(zip);
        assertThat(authHeaders).containsExactly("Bearer secret-token", "null");   // API 에만 토큰, 다운로드에는 없음
    }

    @Test
    void 허용되지_않은_호스트로의_리다이렉트는_따라가지_않는다() {
        route("/repos/a/b/zipball/" + "c".repeat(40), 302, "", Map.of("Location", "http://internal.example:9/secret"));
        assertThatThrownBy(() -> client(null).zipball("a", "b", "c".repeat(40), 1024))
                .isInstanceOf(GitHubException.class).hasMessageContaining("받아오지 못했습니다");
        assertThat(seen).hasSize(1);
    }

    @Test
    void 다운로드가_상한을_넘으면_중단한다() {
        route("/repos/a/b/zipball/" + "c".repeat(40), 302, "", Map.of("Location", base + "/download/big.zip"));
        routeBytes("/download/big.zip", 200, new byte[5000]);
        assertThatThrownBy(() -> client(null).zipball("a", "b", "c".repeat(40), 1000))
                .isInstanceOf(GitHubException.class).hasMessageContaining("너무 큽니다");
    }

    @Test
    void 토큰이_없으면_인증_헤더를_보내지_않는다() {
        route("/repos/a/b/commits/main", 200, "{\"sha\":\"" + "a".repeat(40) + "\",\"commit\":{\"message\":\"m\"}}", Map.of());
        client("  ").resolve("a", "b", "main");
        assertThat(authHeaders).containsExactly("null");
    }
}
