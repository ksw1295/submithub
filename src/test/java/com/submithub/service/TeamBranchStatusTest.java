package com.submithub.service;

import com.submithub.domain.Assignment;
import com.submithub.domain.AssignmentRepository;
import com.submithub.domain.Course;
import com.submithub.domain.SubmissionRepository;
import com.submithub.domain.Team;
import com.submithub.security.AppUser;
import com.submithub.storage.UploadProcessor.Upload;
import com.submithub.support.FakeGitHubClient;
import com.submithub.support.GitHubTest;
import com.submithub.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 팀 감사 화면의 출처 표시: GitHub 제출의 저장소·브랜치(ref)를 팀 저장소·팀원 브랜치와 비교한다. */
@GitHubTest
class TeamBranchStatusTest {

    static final String TEAM_URL = "https://github.com/org/repo";

    @Autowired TestData data;
    @Autowired TeamService teamService;
    @Autowired TeamAuditService audit;
    @Autowired CourseService courseService;
    @Autowired SubmissionService submissionService;
    @Autowired SubmissionRepository submissions;
    @Autowired AssignmentRepository assignmentRepository;
    @Autowired FakeGitHubClient github;

    AppUser prof, m1;
    Assignment assignment;
    Team team;

    @BeforeEach
    void setUp() {
        github.reset();
        prof = data.professor();
        m1 = data.student();
        Course course = data.course(prof);
        courseService.join(m1, course.getInviteCode());
        assignment = data.assignment(course, prof, Duration.ofDays(7), false, null);
        assignment.configureTeam(true, 4);
        assignmentRepository.save(assignment);
        team = teamService.createTeam(assignment.getId(), "1조", TEAM_URL, m1.getId(), "feature/m1", "팀장");
    }

    String status() {
        return audit.auditTeam(team.getId(), prof).members().get(0).branchStatus();
    }

    SubmissionService.SubmitResult gh(String repo, String ref, int n) {
        github.commit(repo, ref, n, "커밋 " + n, Map.of("A.java", "class A { int x" + n + "; }"));
        return submissionService.submitFromGitHub(assignment.getId(), m1, "https://github.com/" + repo, ref, null);
    }

    @Test
    void 제출한_ref_가_저장되고_팀원_브랜치와_같으면_브랜치_일치() {
        var r = gh("org/repo", "feature/m1", 1);
        assertThat(submissions.findById(r.id()).orElseThrow().getSourceRef()).isEqualTo("feature/m1");
        assertThat(status()).isEqualTo("브랜치 일치 ✔");
    }

    @Test
    void 다른_브랜치로_냈으면_브랜치_불일치() {
        gh("org/repo", "main", 1);
        assertThat(status()).isEqualTo("브랜치 불일치 ✖");
    }

    @Test
    void 팀_저장소와_다른_저장소면_저장소_불일치() {
        gh("someone/else", "feature/m1", 1);
        assertThat(status()).isEqualTo("저장소 불일치 ✖");
    }

    @Test
    void SHA_로만_냈거나_브랜치를_비우면_브랜치_확인_불가() {
        gh("org/repo", FakeGitHubClient.sha(7), 7);          // 40자리 SHA 로 제출
        assertThat(status()).isEqualTo("브랜치 확인 불가");

        github.commit("org/repo", "HEAD", 8, "기본 브랜치", Map.of("A.java", "class A { int h; }"));
        submissionService.submitFromGitHub(assignment.getId(), m1, "https://github.com/org/repo", "", null);   // 비움 = HEAD
        assertThat(status()).isEqualTo("브랜치 확인 불가");
    }

    @Test
    void 파일_업로드는_출처_미확인() {
        submissionService.submit(assignment.getId(), m1,
                List.of(new Upload("A.java", "class A {}".getBytes(StandardCharsets.UTF_8))), "업로드");
        assertThat(status()).contains("출처 미확인");
    }

    @Test
    void 최신_제출을_기준으로_표시한다() {
        gh("org/repo", "main", 1);
        assertThat(status()).isEqualTo("브랜치 불일치 ✖");
        gh("org/repo", "feature/m1", 2);
        assertThat(status()).isEqualTo("브랜치 일치 ✔");
    }
}
