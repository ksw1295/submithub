package com.submithub.web;

import com.submithub.domain.Assignment;
import com.submithub.domain.Course;
import com.submithub.domain.SubmissionRepository;
import com.submithub.github.GitHubException;
import com.submithub.security.AppUser;
import com.submithub.service.BusinessException;
import com.submithub.service.CourseService;
import com.submithub.service.SubmissionService;
import com.submithub.support.FakeGitHubClient;
import com.submithub.support.GitHubTest;
import com.submithub.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** GitHub 커밋으로 제출: 가짜 GitHub 클라이언트로 저장·중복 차단·제한·오류 안내·화면을 검증한다. */
@GitHubTest
class GitHubSubmitTest {

    static final String REPO_URL = "https://github.com/minjun/linked-list";

    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired FakeGitHubClient github;
    @Autowired CourseService courseService;
    @Autowired SubmissionService submissionService;
    @Autowired SubmissionRepository submissions;

    AppUser prof, stu, other;
    Course course;
    Assignment assignment;

    @BeforeEach
    void setUp() {
        github.reset();
        prof = data.professor();
        stu = data.student();
        other = data.student();   // 수강하지 않은 학생
        course = data.course(prof);
        courseService.join(stu, course.getInviteCode());
        assignment = data.assignment(course, prof, Duration.ofDays(2), false, null);
    }

    Map<String, String> files(String code) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("src/LinkedList.java", code);
        m.put("README.md", "# 연결 리스트\n");
        m.put("logo.png", "PNGDATA");               // 소스가 아니라서 빠진다
        m.put("node_modules/x/index.js", "x");      // 의존성 폴더라서 빠진다
        m.put(".git/config", "[core]");             // 빠진다
        return m;
    }

    SubmissionService.SubmitResult submit(String ref) {
        return submissionService.submitFromGitHub(assignment.getId(), stu, REPO_URL, ref, null);
    }

    @Test
    void 커밋의_소스_파일을_새_버전으로_저장하고_SHA_와_메시지를_기록한다() {
        String sha = github.commit("minjun/linked-list", "main", 1, "리스트 구현\n\n자세한 설명", files("class A {}"));
        var r = submit("main");
        assertThat(r.versionNo()).isEqualTo(1);

        var view = submissionService.view(r.id(), null, stu);
        assertThat(view.message()).isEqualTo("리스트 구현");            // 메모를 비우면 커밋 메시지 첫 줄
        assertThat(view.sourceUrl()).isEqualTo("https://github.com/minjun/linked-list/commit/" + sha);
        assertThat(view.sourceLabel()).isEqualTo("minjun/linked-list@0000000");
        assertThat(view.tree().stream().filter(n -> !n.dir()).map(SubmissionService.FileNode::path))
                .containsExactlyInAnyOrder("src/LinkedList.java", "README.md");   // 맨 위 폴더는 떼고, 그림·의존성은 제외

        var sub = submissions.findById(r.id()).orElseThrow();
        assertThat(sub.getSourceCommit()).isEqualTo(sha);
        assertThat(sub.getSourceRepo()).isEqualTo("minjun/linked-list");
        var row = submissionService.history(assignment.getId(), stu.getId(), stu).versions().get(0);
        assertThat(row.sourceUrl()).isEqualTo(view.sourceUrl());
    }

    @Test
    void 직접_쓴_메모가_있으면_커밋_메시지_대신_쓴다() {
        github.commit("minjun/linked-list", "main", 1, "커밋 메시지", files("class A {}"));
        var r = submissionService.submitFromGitHub(assignment.getId(), stu, REPO_URL, "main", "  내 메모  ");
        assertThat(submissionService.view(r.id(), null, stu).message()).isEqualTo("내 메모");
    }

    @Test
    void 같은_커밋을_다시_내면_변경_없음으로_막고_파일을_받지_않는다() {
        github.commit("minjun/linked-list", "main", 1, "첫 커밋", files("class A {}"));
        submit("main");
        github.calls.clear();
        assertThatThrownBy(() -> submit("main"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("변경 없음").hasMessageContaining("v1");
        assertThat(github.calls).containsExactly("resolve minjun/linked-list@main");   // zipball 은 부르지 않는다
        assertThat(submissions.countByAssignmentIdAndStudentId(assignment.getId(), stu.getId())).isEqualTo(1);
    }

    @Test
    void 새_커밋은_다음_버전이고_이전_커밋으로_되돌린_제출도_새_버전이다() {
        String sha1 = github.commit("minjun/linked-list", "main", 1, "v1", files("class A {}"));
        submit("main");
        github.commit("minjun/linked-list", "main", 2, "v2", files("class A { int x; }"));
        assertThat(submit("main").versionNo()).isEqualTo(2);
        github.commits.put("minjun/linked-list@" + sha1, new com.submithub.github.GitHubClient.Commit(sha1, "v1"));
        assertThat(submit(sha1).versionNo()).isEqualTo(3);   // 직전 버전(2)과 다른 커밋이므로 허용
        assertThatThrownBy(() -> submit(sha1)).isInstanceOf(BusinessException.class).hasMessageContaining("변경 없음");
    }

    @Test
    void 파일_업로드_뒤에_같은_저장소를_내는_것은_막지_않는다() {
        submissionService.submit(assignment.getId(), stu,
                java.util.List.of(new com.submithub.storage.UploadProcessor.Upload("A.java", "class A {}".getBytes(StandardCharsets.UTF_8))), "zip");
        github.commit("minjun/linked-list", "main", 1, "c", files("class A {}"));
        assertThat(submit("main").versionNo()).isEqualTo(2);
    }

    @Test
    void github_com_이외의_주소는_GitHub_를_부르지_않고_거부한다() {
        for (String url : new String[]{"https://gitlab.com/a/b", "http://github.com/a/b", "https://evil.com/github.com/a/b"}) {
            assertThatThrownBy(() -> submissionService.submitFromGitHub(assignment.getId(), stu, url, "main", null))
                    .isInstanceOf(BusinessException.class);
        }
        assertThat(github.calls).isEmpty();
    }

    @Test
    void GitHub_오류는_안내_문구로_전달되고_버전이_생기지_않는다() {
        github.failure = new GitHubException("GitHub API 요청 한도를 넘었습니다. 약 12분 뒤에 다시 시도하거나, 급하면 zip 파일로 제출하세요.");
        assertThatThrownBy(() -> submit("main")).isInstanceOf(BusinessException.class).hasMessageContaining("요청 한도");
        assertThat(submissions.countByAssignmentIdAndStudentId(assignment.getId(), stu.getId())).isZero();
    }

    @Test
    void 없는_브랜치는_찾을_수_없다는_안내() {
        assertThatThrownBy(() -> submit("nope")).isInstanceOf(BusinessException.class).hasMessageContaining("찾을 수 없습니다");
    }

    @Test
    void 파일_수_크기_제한을_넘으면_거부한다() {
        Map<String, String> many = new LinkedHashMap<>();
        for (int i = 0; i < 201; i++) {
            many.put("src/F" + i + ".java", "class F" + i + " {}");
        }
        github.commit("minjun/linked-list", "many", 1, "많음", many);
        assertThatThrownBy(() -> submit("many")).isInstanceOf(BusinessException.class).hasMessageContaining("파일이 너무 많습니다");

        github.commit("minjun/linked-list", "big", 2, "큼", Map.of("Big.java", "x".repeat(5 * 1024 * 1024 + 1)));
        assertThatThrownBy(() -> submit("big")).isInstanceOf(BusinessException.class).hasMessageContaining("파일이 너무 큽니다");

        Map<String, String> total = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            total.put("Part" + i + ".java", "x".repeat(4_500_000));   // 합계 22.5MB > 20MB
        }
        github.commit("minjun/linked-list", "total", 3, "합계", total);
        assertThatThrownBy(() -> submit("total")).isInstanceOf(BusinessException.class).hasMessageContaining("제출 크기가 너무 큽니다");
        assertThat(submissions.countByAssignmentIdAndStudentId(assignment.getId(), stu.getId())).isZero();
    }

    @Test
    void 소스_파일이_하나도_없으면_거부한다() {
        github.commit("minjun/linked-list", "main", 1, "그림만", Map.of("logo.png", "x", "docs/a.pdf", "y"));
        assertThatThrownBy(() -> submit("main")).isInstanceOf(BusinessException.class).hasMessageContaining("소스 파일이 저장소에 없습니다");
    }

    @Test
    void 교수_수강하지_않은_학생은_제출할_수_없고_마감된_과제는_네트워크_전에_막는다() {
        github.commit("minjun/linked-list", "main", 1, "c", files("class A {}"));
        assertThatThrownBy(() -> submissionService.submitFromGitHub(assignment.getId(), prof, REPO_URL, "main", null))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> submissionService.submitFromGitHub(assignment.getId(), other, REPO_URL, "main", null))
                .isInstanceOf(RuntimeException.class);
        Assignment closed = data.assignment(course, prof, Duration.ofDays(-1), false, null);
        assertThatThrownBy(() -> submissionService.submitFromGitHub(closed.getId(), stu, REPO_URL, "main", null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("마감");
        assertThat(github.calls).isEmpty();
    }

    // ---- 화면 ----

    @Test
    void 제출하면_버전_화면으로_이동하고_커밋_링크가_보인다() throws Exception {
        String sha = github.commit("minjun/linked-list", "main", 1, "화면 확인", files("class A {}"));
        var res = mvc.perform(post("/assignments/" + assignment.getId() + "/submit/github").with(user(stu)).with(csrf())
                        .param("repoUrl", REPO_URL).param("ref", "main"))
                .andExpect(status().is3xxRedirection()).andReturn();
        String location = res.getResponse().getRedirectedUrl();
        assertThat(location).startsWith("/submissions/");
        String html = mvc.perform(get(location).with(user(stu))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("https://github.com/minjun/linked-list/commit/" + sha).contains("화면 확인")
                .contains("noopener");
        String history = mvc.perform(get("/assignments/" + assignment.getId() + "/students/" + stu.getId()).with(user(prof)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(history).contains("GitHub minjun/linked-list@0000000");
    }

    @Test
    void 과제_화면에_GitHub_제출_폼이_있다() throws Exception {
        String html = mvc.perform(get("/assignments/" + assignment.getId()).with(user(stu))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("GitHub 커밋으로 제출").contains("name=\"repoUrl\"")
                .contains("/assignments/" + assignment.getId() + "/submit/github");
    }

    @Test
    void 잘못된_주소는_오류_메시지와_함께_돌려보낸다() throws Exception {
        var res = mvc.perform(post("/assignments/" + assignment.getId() + "/submit/github").with(user(stu)).with(csrf())
                        .param("repoUrl", "https://gitlab.com/a/b").param("ref", "main"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error")).andReturn();
        assertThat(res.getFlashMap().get("error").toString()).contains("github.com");
    }

    @Test
    void 교수는_GitHub_제출_주소를_호출할_수_없다() throws Exception {
        mvc.perform(post("/assignments/" + assignment.getId() + "/submit/github").with(user(prof)).with(csrf())
                        .param("repoUrl", REPO_URL))
                .andExpect(status().isForbidden());
        mvc.perform(post("/assignments/" + assignment.getId() + "/submit/github").with(user(stu))
                        .param("repoUrl", REPO_URL))
                .andExpect(status().isForbidden());   // CSRF 토큰 없음
    }
}
