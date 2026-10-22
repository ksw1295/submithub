package com.submithub.support;

import com.submithub.github.GitHubClient;
import com.submithub.github.GitHubException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * GitHub 응답을 흉내 낸 가짜 클라이언트 (네트워크를 쓰지 않는다).
 * commits: "owner/repo@ref" → 커밋, files: 커밋 SHA → (경로 → 내용). zipball 은 GitHub 처럼 맨 위에 "repo-sha/" 폴더를 둔다.
 */
public class FakeGitHubClient implements GitHubClient {

    public final Map<String, Commit> commits = new LinkedHashMap<>();
    public final Map<String, Map<String, byte[]>> files = new LinkedHashMap<>();
    public volatile GitHubException failure;
    public final List<String> calls = new ArrayList<>();

    public static String sha(int n) {
        return String.format("%040x", n);
    }

    /** 커밋 하나를 등록한다 (같은 ref 를 다시 등록하면 그 ref 가 새 커밋을 가리킨다) */
    public String commit(String repo, String ref, int n, String message, Map<String, String> content) {
        String sha = sha(n);
        commits.put(repo + "@" + ref, new Commit(sha, message));
        Map<String, byte[]> bytes = new LinkedHashMap<>();
        content.forEach((p, c) -> bytes.put(p, c.getBytes(StandardCharsets.UTF_8)));
        files.put(sha, bytes);
        return sha;
    }

    public void reset() {
        commits.clear();
        files.clear();
        failure = null;
        calls.clear();
    }

    @Override
    public Commit resolve(String owner, String repo, String ref) {
        calls.add("resolve " + owner + "/" + repo + "@" + ref);
        if (failure != null) {
            throw failure;
        }
        Commit c = commits.get(owner + "/" + repo + "@" + ref);
        if (c == null) {
            throw new GitHubException("저장소를 찾을 수 없습니다.");
        }
        return c;
    }

    @Override
    public byte[] zipball(String owner, String repo, String sha, long maxBytes) {
        calls.add("zipball " + owner + "/" + repo + "@" + sha);
        if (failure != null) {
            throw failure;
        }
        return zip(repo + "-" + sha.substring(0, 7), files.get(sha));
    }

    public static byte[] zip(String top, Map<String, byte[]> content) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream z = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
                z.putNextEntry(new ZipEntry(top + "/"));
                z.closeEntry();
                for (var e : content.entrySet()) {
                    z.putNextEntry(new ZipEntry(top + "/" + e.getKey()));
                    z.write(e.getValue());
                    z.closeEntry();
                }
            }
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
