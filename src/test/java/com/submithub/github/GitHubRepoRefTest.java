package com.submithub.github;

import com.submithub.service.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubRepoRefTest {

    @Test
    void 주소와_브랜치를_정규화한다() {
        var r = GitHubRepoRef.parse("https://github.com/octocat/Hello-World", "main");
        assertThat(r.owner()).isEqualTo("octocat");
        assertThat(r.repo()).isEqualTo("Hello-World");
        assertThat(r.ref()).isEqualTo("main");
        assertThat(r.fullName()).isEqualTo("octocat/Hello-World");
    }

    @Test
    void git_접미사_슬래시_tree_경로_www_는_받아준다() {
        assertThat(GitHubRepoRef.parse("https://github.com/a/b.git", "").repo()).isEqualTo("b");
        assertThat(GitHubRepoRef.parse("  https://www.github.com/a/b/  ", null).repo()).isEqualTo("b");
        assertThat(GitHubRepoRef.parse("https://github.com/a/b/tree/main/src", "x").repo()).isEqualTo("b");
    }

    @Test
    void 브랜치를_비우면_기본_브랜치_HEAD() {
        assertThat(GitHubRepoRef.parse("https://github.com/a/b", "  ").ref()).isEqualTo("HEAD");
    }

    @Test
    void SHA_와_슬래시가_있는_브랜치를_받는다() {
        assertThat(GitHubRepoRef.parse("https://github.com/a/b", "7fd1a60b01f91b314f59955a4e4d4e80d8edf11d").looksLikeSha()).isTrue();
        assertThat(GitHubRepoRef.parse("https://github.com/a/b", "feature/login-1.2").ref()).isEqualTo("feature/login-1.2");
        assertThat(GitHubRepoRef.parse("https://github.com/a/b", "main").looksLikeSha()).isFalse();
    }

    @Test
    void github_com_이외의_주소는_거부한다() {
        for (String bad : new String[]{
                "https://gitlab.com/a/b", "https://evil.com/github.com/a/b", "https://github.com.evil.com/a/b",
                "https://api.github.com/repos/a/b", "http://github.com/a/b", "ftp://github.com/a/b",
                "https://user:pw@github.com/a/b", "https://github.com:8443/a/b", "git@github.com:a/b.git",
                "file:///etc/passwd", "https://127.0.0.1/a/b", "https://raw.githubusercontent.com/a/b/main/x"}) {
            assertThatThrownBy(() -> GitHubRepoRef.parse(bad, "main")).as(bad).isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void 소유자_저장소가_없거나_이상하면_거부한다() {
        for (String bad : new String[]{"", "   ", "https://github.com", "https://github.com/onlyowner",
                "https://github.com/a/..", "https://github.com/-a/b", "https://github.com/a b/c", "https://github.com/a/b%2F..%2Fc"}) {
            assertThatThrownBy(() -> GitHubRepoRef.parse(bad, "main")).as(bad).isInstanceOf(BusinessException.class);
        }
        assertThatThrownBy(() -> GitHubRepoRef.parse(null, "main")).isInstanceOf(BusinessException.class);
    }

    @Test
    void 위험한_브랜치_문자열은_거부한다() {
        for (String bad : new String[]{"../x", "a..b", "-rf", "a b", "a?b", "a#b", "a%2e%2e", "a//b", "/abs", "x/", "a;b", "a\nb", "x.lock"}) {
            assertThatThrownBy(() -> GitHubRepoRef.parse("https://github.com/a/b", bad)).as(bad).isInstanceOf(BusinessException.class);
        }
    }
}
