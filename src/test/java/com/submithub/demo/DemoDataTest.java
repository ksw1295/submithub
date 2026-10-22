package com.submithub.demo;

import com.submithub.domain.User;
import com.submithub.domain.UserRepository;
import com.submithub.security.AppUser;
import com.submithub.service.FeedbackService;
import com.submithub.service.SimilarityService;
import com.submithub.service.StatsService;
import com.submithub.service.SubmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/** 시연용 샘플 데이터가 의도한 시나리오(반영 추적, 표절, 지각, 미제출)를 실제로 만들어 내는지 검증한다. */
@SpringBootTest(properties = {
        "submithub.demo-data=true",
        "spring.datasource.url=jdbc:h2:mem:demo-test;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "submithub.storage-dir=./target/demo-uploads"})
class DemoDataTest {

    @Autowired UserRepository users;
    @Autowired com.submithub.domain.AssignmentRepository assignments;
    @Autowired com.submithub.domain.SubmissionRepository submissionRepo;
    @Autowired PasswordEncoder encoder;
    @Autowired StatsService statsService;
    @Autowired FeedbackService feedbackService;
    @Autowired SimilarityService similarityService;
    @Autowired SubmissionService submissionService;

    AppUser prof() {
        return new AppUser(users.findByLoginId(DemoDataLoader.PROFESSOR_ID).orElseThrow());
    }

    AppUser student(int n) {
        return new AppUser(users.findByLoginId("student" + n).orElseThrow());
    }

    Long courseId() {
        return statsService.course(
                // 강의 ID 를 모르므로 교수의 강의 목록 대신 통계 서비스에 쓸 ID 를 사용자 데이터에서 찾는다
                1L, prof()).courseId();
    }

    @Test
    void 계정이_만들어지고_비밀번호는_BCrypt_이다() {
        assertThat(users.count()).isEqualTo(8);
        User p = users.findByLoginId(DemoDataLoader.PROFESSOR_ID).orElseThrow();
        assertThat(p.isProfessor()).isTrue();
        assertThat(p.getPasswordHash()).startsWith("$2");
        assertThat(encoder.matches(DemoDataLoader.PROFESSOR_PASSWORD, p.getPasswordHash())).isTrue();
        for (int i = 1; i <= 7; i++) {
            User s = users.findByLoginId("student" + i).orElseThrow();
            assertThat(encoder.matches(DemoDataLoader.STUDENT_PASSWORD, s.getPasswordHash())).isTrue();
        }
    }

    @Test
    void 제출_현황_지각_미제출이_시나리오대로_나온다() {
        var stats = statsService.course(1L, prof());
        assertThat(stats.enrolled()).isEqualTo(7);
        assertThat(stats.assignments()).hasSize(2);

        var a1 = stats.assignments().get(0);
        assertThat(a1.title()).contains("연결 리스트");
        assertThat(a1.submitted()).isEqualTo(6);
        assertThat(a1.missing()).containsExactly("윤지우");
        assertThat(a1.lateStudents()).isEqualTo(1);
        assertThat(a1.totalSubmissions()).isEqualTo(9);
        assertThat(a1.pastDue()).isTrue();
        assertThat(a1.lastDaySubmissions()).isGreaterThan(0);

        var a2 = stats.assignments().get(1);
        assertThat(a2.submitted()).isEqualTo(3);
        assertThat(a2.pastDue()).isFalse();
    }

    @Test
    void 이민준의_피드백은_일부만_반영된_상태로_추적된다() {
        var student1 = student(1);
        var fb = feedbackService.forStudent(a1Id(), student1.getId(), prof());
        var s = fb.summary();

        assertThat(s.total()).isEqualTo(5);
        assertThat(s.modified()).isEqualTo(2);     // get 범위 검사(v2), 변수명 x(v3)
        assertThat(s.deleted()).isEqualTo(1);      // 디버그 출력(v2)
        assertThat(s.unchanged()).isEqualTo(1);    // size() 는 그대로
        assertThat(s.pending()).isEqualTo(1);      // v3 에 달린 새 코멘트
        assertThat(s.ratePercent()).isEqualTo(75);
        assertThat(s.remaining()).isEqualTo(2);

        var unchanged = fb.items().stream().filter(t -> t.status().equals("UNCHANGED")).findFirst().orElseThrow();
        assertThat(unchanged.body()).contains("size()");
        assertThat(unchanged.latestStart()).isGreaterThan(unchanged.startLine());   // addFirst 추가로 줄 번호가 밀림
        assertThat(fb.items().stream().filter(t -> t.status().equals("MODIFIED")).map(FeedbackService.TrackedComment::changedAtVersion))
                .containsExactlyInAnyOrder(2, 3);

        // 학생 본인도 볼 수 있다
        assertThat(feedbackService.forStudent(a1Id(), student1.getId(), student1).summary().remaining()).isEqualTo(2);
    }

    @Test
    void 표절_검사_결과에는_박서연과_최지호_쌍이_가장_위에_있다() {
        var run = similarityService.latest(a1Id(), prof());
        assertThat(run).isNotNull();
        assertThat(run.hasBaseline()).isTrue();
        assertThat(run.pairs()).isNotEmpty();
        var top = run.pairs().get(0);
        assertThat(java.util.Set.of(top.studentA(), top.studentB())).containsExactlyInAnyOrder("박서연", "최지호");
        assertThat(top.score()).isGreaterThan(0.8);
        if (run.pairs().size() > 1) {
            assertThat(run.pairs().get(1).score()).isLessThan(0.7);   // 나머지는 서로 다른 풀이
        }
        var view = similarityService.pair(top.id(), prof());
        assertThat(view.filesA().get(0).lines().stream().filter(SimilarityService.CodeLine::matched).count()).isGreaterThan(20);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    void 마감과_제출_시각이_발표용으로_깔끔하다() {
        for (var a : assignments.findAll()) {
            assertThat(a.getDueAt().toLocalTime()).as(a.getTitle() + " 마감").isEqualTo(java.time.LocalTime.of(23, 59));
            if (a.isAllowLate()) {
                assertThat(a.getLateUntil().toLocalTime()).as(a.getTitle() + " 지각 허용 기한").isEqualTo(java.time.LocalTime.of(23, 59));
                assertThat(a.getLateUntil().toLocalDate()).isAfter(a.getDueAt().toLocalDate());
            }
        }
        var all = submissionRepo.findAll();
        assertThat(all).isNotEmpty();
        for (var s : all) {
            assertThat(s.getSubmittedAt().getMinute() % 30).as("제출 시각 " + s.getSubmittedAt()).isZero();
            assertThat(s.getSubmittedAt().getSecond()).isZero();
            assertThat(s.getSubmittedAt()).isBeforeOrEqualTo(java.time.LocalDateTime.now());   // 미래 시각 없음
        }
        // 지각 제출 1건은 마감 이후, 나머지는 마감 이전
        assertThat(all.stream().filter(s -> s.isLate())).hasSize(1);
        assertThat(all.stream().filter(s -> s.isLate()).findFirst().orElseThrow().getSubmittedAt())
                .isAfter(all.stream().filter(s -> s.isLate()).findFirst().orElseThrow().getAssignment().getDueAt());
    }

    @Test
    void 새_코멘트만_있는_박서연은_퍼센트_없이_표기되고_평균은_2명_기준이다() {
        var stats = statsService.course(1L, prof());
        var row = stats.assignments().get(0).students().stream()
                .filter(r -> r.name().equals("박서연")).findFirst().orElseThrow();
        assertThat(row.feedback().hasRate()).isFalse();
        assertThat(row.feedback().rateLabel()).isEqualTo("새 코멘트 1개");

        assertThat(stats.averageFeedbackStudents()).isEqualTo(2);   // 이민준(75%), 강도윤(0%)
        assertThat(stats.averageFeedbackRate()).isEqualTo(38);
    }

    @Test
    void 학생별_성적과_이력이_들어_있다() {
        var history = submissionService.history(a1Id(), student(1).getId(), student(1));
        assertThat(history.versions()).hasSize(3);
        assertThat(history.versions().get(0).score()).isEqualTo(88);   // v3
        assertThat(history.versions().get(2).score()).isEqualTo(70);   // v1
        // 과제 1 에서 이민준 v2 는 직전 버전 대비 LinkedList.java 와 Main.java(first 명령 추가)가 수정됨
        assertThat(history.versions().get(1).modified()).isEqualTo(2);
    }

    @Autowired com.submithub.service.GradingService gradingService;

    @Test
    void 과제_1에는_자동_채점_설정과_테스트_케이스가_있고_가짜_판정은_없다() {
        var cfg = gradingService.config(a1Id(), prof());
        assertThat(cfg.configured()).isTrue();
        assertThat(cfg.cases()).hasSize(DemoGrading.CASES.size());
        assertThat(cfg.totalPoints()).isEqualTo(100);
        assertThat(cfg.cases().stream().filter(c -> c.publicCase()).count()).isEqualTo(2);
        // 시연 데이터가 채점 결과를 직접 넣지는 않는다: Docker 가 없으면 "채점 불가", 있으면 실제 채점 결과다
        var view = submissionService.history(a1Id(), student(1).getId(), prof());
        assertThat(view.versions()).hasSize(3);
        if (!gradingService.available()) {
            assertThat(gradingService.forSubmission(view.versions().get(0).id(), prof()).status()).isEqualTo("UNAVAILABLE");
        }
    }

    private Long a1Id() {
        return statsService.course(1L, prof()).assignments().get(0).id();
    }
}
