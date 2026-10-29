package com.submithub.service;

import com.submithub.domain.Assignment;
import com.submithub.domain.AssignmentRepository;
import com.submithub.domain.Course;
import com.submithub.domain.SubmissionRepository;
import com.submithub.domain.Team;
import com.submithub.domain.TeamMember;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @Autowired com.submithub.domain.TeamMemberRepository teamMembers;
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

    @Test
    void 내_브랜치_최신_커밋_원클릭_제출_성공_및_감사_화면_일치_표시() {
        github.commit("org/repo", "feature/m1", 1, "내 첫 커밋", Map.of("A.java", "class A { int x; }"));
        var result = submissionService.submitFromMyBranch(assignment.getId(), m1, "내 브랜치 제출");

        assertThat(result.versionNo()).isEqualTo(1);
        assertThat(submissions.findById(result.id()).orElseThrow().getSourceRef()).isEqualTo("feature/m1");
        assertThat(status()).isEqualTo("브랜치 일치 ✔");
    }

    @Test
    void 내_브랜치_제출_같은_커밋_재제출_시_변경_없음_차단() {
        github.commit("org/repo", "feature/m1", 1, "동일 커밋", Map.of("A.java", "class A {}"));
        submissionService.submitFromMyBranch(assignment.getId(), m1, "첫 번째");

        assertThatThrownBy(() -> submissionService.submitFromMyBranch(assignment.getId(), m1, "두 번째"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("변경 없음");
    }

    @Test
    void 내_브랜치_제출_팀원_아니면_403() {
        AppUser other = data.student();
        Course course = assignment.getCourse();
        courseService.join(other, course.getInviteCode());

        assertThatThrownBy(() -> submissionService.submitFromMyBranch(assignment.getId(), other, "제출 시도"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("팀에 속해 있지 않습니다.");
    }

    @Test
    void 내_브랜치_제출_브랜치_미등록_시_안내_예외() {
        TeamMember member = team.getMembers().get(0);
        member.setBranchName(null);
        teamMembers.saveAndFlush(member);

        assertThatThrownBy(() -> submissionService.submitFromMyBranch(assignment.getId(), m1, "브랜치 공백"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("팀 저장소와 브랜치를 먼저 등록하세요");
    }
}
