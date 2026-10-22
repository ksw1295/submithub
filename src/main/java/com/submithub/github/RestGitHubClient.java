package com.submithub.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Predicate;

/**
 * GitHub REST API (인증 없이 공개 저장소만) 호출 구현. 호출하는 곳은 api.github.com 과 그 zipball 이 넘겨주는
 * codeload.github.com 두 곳뿐이다. 리다이렉트는 자동으로 따라가지 않고, 주소가 허용된 호스트일 때만 직접 따라간다.
 * 환경변수 GITHUB_TOKEN 이 있으면 API 호출에만 붙여서 요청 한도를 늘린다 (zipball 다운로드 서버로는 보내지 않는다).
 */
@Component
public class RestGitHubClient implements GitHubClient {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long MAX_JSON_BYTES = 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient http;
    private final String apiBase;
    private final String token;
    private final Predicate<URI> downloadAllowed;

    @Autowired
    public RestGitHubClient() {
        this("https://api.github.com", System.getenv("GITHUB_TOKEN"),
                u -> "https".equals(u.getScheme()) && "codeload.github.com".equalsIgnoreCase(u.getHost()));
    }

    /** 테스트에서 주소를 바꿔 쓰려고 열어 둔 생성자 */
    RestGitHubClient(String apiBase, String token, Predicate<URI> downloadAllowed) {
        this.apiBase = apiBase;
        this.token = token == null || token.isBlank() ? null : token.strip();
        this.downloadAllowed = downloadAllowed;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public Commit resolve(String owner, String repo, String ref) {
        URI uri = URI.create(apiBase + "/repos/" + seg(owner) + "/" + seg(repo) + "/commits/" + refPath(ref));
        HttpResponse<InputStream> res = send(api(uri));
        try (InputStream body = res.body()) {
            if (res.statusCode() != 200) {
                throw error(res);
            }
            JsonNode json = JSON.readTree(body.readNBytes((int) MAX_JSON_BYTES));
            String sha = json.path("sha").asText("");
            if (!sha.matches("[0-9a-f]{40}")) {
                throw new GitHubException("GitHub 응답을 이해하지 못했습니다. 잠시 뒤에 다시 시도하세요.");
            }
            return new Commit(sha, json.path("commit").path("message").asText(""));
        } catch (IOException e) {
            throw unreachable(e);
        }
    }

    @Override
    public byte[] zipball(String owner, String repo, String sha, long maxBytes) {
        URI uri = URI.create(apiBase + "/repos/" + seg(owner) + "/" + seg(repo) + "/zipball/" + seg(sha));
        HttpResponse<InputStream> res = send(api(uri));
        try {
            if (res.statusCode() / 100 == 3) {
                String location = res.headers().firstValue("Location").orElse("");
                res.body().close();
                URI target;
                try {
                    target = URI.create(location);
                } catch (IllegalArgumentException e) {
                    throw new GitHubException("GitHub 에서 파일을 받아오지 못했습니다.");
                }
                if (!downloadAllowed.test(target)) {
                    throw new GitHubException("GitHub 에서 파일을 받아오지 못했습니다.");
                }
                // 다운로드 서버에는 토큰을 보내지 않는다
                res = send(HttpRequest.newBuilder(target).timeout(TIMEOUT).GET().build());
            }
            try (InputStream body = res.body()) {
                if (res.statusCode() != 200) {
                    throw error(res);
                }
                byte[] bytes = body.readNBytes((int) Math.min(maxBytes + 1, Integer.MAX_VALUE - 8));
                if (bytes.length > maxBytes) {
                    throw new GitHubException("저장소가 너무 큽니다 (내려받는 크기가 " + (maxBytes / 1024 / 1024)
                            + "MB 를 넘습니다). 필요한 파일만 있는 저장소나 zip 으로 제출하세요.");
                }
                return bytes;
            }
        } catch (IOException e) {
            throw unreachable(e);
        }
    }

    private HttpRequest api(URI uri) {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET()
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "SubmitHub");
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return b.build();
    }

    private HttpResponse<InputStream> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubException("GitHub 요청이 중단되었습니다. 다시 시도하세요.");
        } catch (IOException e) {
            throw unreachable(e);
        }
    }

    private static GitHubException unreachable(IOException e) {
        return new GitHubException("GitHub 에 연결하지 못했습니다. 잠시 뒤에 다시 시도하거나 zip 파일로 제출하세요.");
    }

    /** 응답 상태를 학생이 이해할 수 있는 안내로 바꾼다 */
    static GitHubException error(HttpResponse<?> res) {
        int status = res.statusCode();
        var h = res.headers();
        boolean limited = h.firstValue("X-RateLimit-Remaining").map("0"::equals).orElse(false)
                || h.firstValue("Retry-After").isPresent();
        if (status == 403 || status == 429) {
            if (limited || status == 429) {
                return new GitHubException("GitHub API 요청 한도를 넘었습니다. " + waitText(h)
                        + " 다시 시도하거나, 급하면 zip 파일로 제출하세요.");
            }
            return new GitHubException("이 저장소에 접근할 수 없습니다. 공개 저장소인지 확인하세요.");
        }
        return switch (status) {
            case 404 -> new GitHubException("저장소를 찾을 수 없습니다. 주소가 맞는지, 공개 저장소인지 확인하세요. (비공개 저장소는 사용할 수 없습니다)");
            case 422 -> new GitHubException("해당 브랜치 또는 커밋을 찾을 수 없습니다. 이름이나 SHA 를 확인하세요.");
            case 409 -> new GitHubException("비어 있는 저장소입니다. 커밋을 올린 뒤 다시 시도하세요.");
            case 401 -> new GitHubException("GitHub 인증에 실패했습니다. 서버 관리자에게 GITHUB_TOKEN 설정을 확인해 달라고 하세요.");
            default -> status >= 500
                    ? new GitHubException("GitHub 서버에 문제가 있습니다. 잠시 뒤에 다시 시도하세요.")
                    : new GitHubException("GitHub 에서 정보를 가져오지 못했습니다 (응답 " + status + ").");
        };
    }

    private static String waitText(java.net.http.HttpHeaders h) {
        try {
            var reset = h.firstValue("X-RateLimit-Reset");
            if (reset.isPresent()) {
                long minutes = Math.max(1, (Long.parseLong(reset.get()) - Instant.now().getEpochSecond() + 59) / 60);
                return "약 " + minutes + "분 뒤에";
            }
            var retry = h.firstValue("Retry-After");
            if (retry.isPresent()) {
                long minutes = Math.max(1, (Long.parseLong(retry.get()) + 59) / 60);
                return "약 " + minutes + "분 뒤에";
            }
        } catch (NumberFormatException ignored) {
            // 헤더 값을 읽지 못하면 일반 문구를 쓴다
        }
        return "잠시 뒤에";
    }

    private static String seg(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** 브랜치 이름은 "feature/x" 처럼 슬래시를 포함할 수 있어서 구간별로 인코딩한다 */
    private static String refPath(String ref) {
        StringBuilder sb = new StringBuilder();
        for (String part : ref.split("/")) {
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(seg(part));
        }
        return sb.toString();
    }
}
