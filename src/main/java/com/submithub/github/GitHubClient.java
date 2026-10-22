package com.submithub.github;

/**
 * GitHub 에서 커밋 정보와 그 시점의 파일 묶음(zip)을 가져오는 클라이언트. 운영 구현은 {@link RestGitHubClient},
 * 테스트에서는 GitHub 응답을 흉내 낸 가짜 구현으로 바꾼다.
 */
public interface GitHubClient {

    /** @param sha 40자리 커밋 SHA, @param message 커밋 메시지 전체 */
    record Commit(String sha, String message) {
    }

    /** ref(브랜치, 태그, SHA, HEAD)가 가리키는 커밋을 찾는다 */
    Commit resolve(String owner, String repo, String ref);

    /**
     * 커밋 시점의 저장소 전체를 zip 으로 받는다.
     *
     * @param maxBytes 이 크기를 넘으면 받다가 중단한다
     */
    byte[] zipball(String owner, String repo, String sha, long maxBytes);
}
