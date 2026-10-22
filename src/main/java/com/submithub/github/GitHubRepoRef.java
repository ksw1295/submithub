package com.submithub.github;

import com.submithub.service.BusinessException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

/**
 * 학생이 입력한 GitHub 저장소 주소와 브랜치(또는 커밋 SHA)를 검사해 정규화한다.
 * github.com 의 공개 저장소 주소(https)만 받는다. 다른 호스트, 사용자 정보·포트가 붙은 주소, http 는 거부한다.
 * 여기서 걸러진 owner/repo/ref 만 API 주소에 들어간다 (그래서 학생 입력으로 다른 서버를 호출할 수 없다).
 */
public record GitHubRepoRef(String owner, String repo, String ref) {

    private static final Pattern OWNER = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})");
    private static final Pattern REPO = Pattern.compile("[A-Za-z0-9._-]{1,100}");
    private static final Pattern REF = Pattern.compile("[A-Za-z0-9._/@+-]{1,200}");
    private static final Pattern SHA = Pattern.compile("[0-9a-fA-F]{7,40}");

    /** @param ref 브랜치 이름이나 커밋 SHA. 비어 있으면 저장소의 기본 브랜치(HEAD) */
    public static GitHubRepoRef parse(String url, String ref) {
        if (url == null || url.isBlank()) {
            throw new BusinessException("GitHub 저장소 주소를 입력하세요.");
        }
        String trimmed = url.strip();
        if (trimmed.length() > 300) {
            throw new BusinessException("저장소 주소가 너무 깁니다.");
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new BusinessException("올바른 GitHub 저장소 주소가 아닙니다. 예: https://github.com/소유자/저장소");
        }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                || !(host.equalsIgnoreCase("github.com") || host.equalsIgnoreCase("www.github.com"))
                || uri.getUserInfo() != null || uri.getPort() != -1) {
            throw new BusinessException("github.com 의 공개 저장소 주소(https://github.com/소유자/저장소)만 사용할 수 있습니다.");
        }
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        String[] parts = path.replaceAll("^/+", "").split("/");
        if (parts.length < 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            throw new BusinessException("저장소 주소에 소유자와 저장소 이름이 필요합니다. 예: https://github.com/소유자/저장소");
        }
        String owner = parts[0];
        String repo = parts[1].endsWith(".git") ? parts[1].substring(0, parts[1].length() - 4) : parts[1];
        if (!OWNER.matcher(owner).matches() || !REPO.matcher(repo).matches() || repo.equals(".") || repo.equals("..")) {
            throw new BusinessException("저장소 주소의 소유자 또는 저장소 이름이 올바르지 않습니다.");
        }
        String r = ref == null ? "" : ref.strip();
        if (r.isEmpty()) {
            r = "HEAD";
        } else if (!REF.matcher(r).matches() || r.contains("..") || r.contains("//") || r.startsWith("/")
                || r.startsWith("-") || r.endsWith("/") || r.endsWith(".lock")) {
            throw new BusinessException("브랜치 또는 커밋 SHA 형식이 올바르지 않습니다.");
        }
        return new GitHubRepoRef(owner, repo, r);
    }

    public String fullName() {
        return owner + "/" + repo;
    }

    public boolean looksLikeSha() {
        return SHA.matcher(ref).matches();
    }
}
