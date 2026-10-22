package com.submithub.service;

import com.submithub.domain.Assignment;
import com.submithub.domain.Course;
import com.submithub.domain.GradingLanguage;
import com.submithub.grading.GradingRunner.CaseOutcome;
import com.submithub.grading.GradingRunner.CaseStatus;
import com.submithub.grading.GradingRunner.GradingException;
import com.submithub.grading.GradingRunner.Outcome;
import com.submithub.security.AppUser;
import com.submithub.service.GradingService.CaseInput;
import com.submithub.service.GradingService.ConfigInput;
import com.submithub.storage.UploadProcessor.Upload;
import com.submithub.support.FakeGradingRunner;
import com.submithub.support.GradingTest;
import com.submithub.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@GradingTest
class GradingServiceTest {

    @Autowired TestData data;
    @Autowired FakeGradingRunner fake;
    @Autowired GradingService grading;
    @Autowired SubmissionService submissionService;
    @Autowired CourseService courseService;
    @Autowired StatsService statsService;

    AppUser prof, otherProf, stu, classmate;
    Course course;
    Assignment assignment;

    @BeforeEach
    void setUp() {
        fake.reset();
        prof = data.professor();
        otherProf = data.professor();
        stu = data.student();
        classmate = data.student();
        course = data.course(prof);
        courseService.join(stu, course.getInviteCode());
        courseService.join(classmate, course.getInviteCode());
        assignment = data.assignment(course, prof, Duration.ofDays(2), false, null);
    }

    /** 설정 + 케이스 5개 (입력 1~5, 기대 출력 out1~out5, 앞의 2개는 공개, 각 20점) */
    void enableGrading() {
        grading.saveConfig(assignment.getId(), prof, new ConfigInput(GradingLanguage.JAVA, "Main.java", 5, 256));
        for (int i = 1; i <= 5; i++) {
            grading.addCase(assignment.getId(), prof, new CaseInput("케이스 " + i, String.valueOf(i), "out" + i, i <= 2, 20));
        }
    }

    SubmissionService.SubmitResult submit(String code) {
        return submissionService.submit(assignment.getId(), stu,
                List.of(new Upload("Main.java", code.getBytes(StandardCharsets.UTF_8))), null);
    }

    GradingService.GradingView waitFinished(Long submissionId) throws InterruptedException {
        GradingService.GradingView v = null;
        for (int i = 0; i < 100; i++) {
            v = grading.forSubmission(submissionId, prof);
            if (v != null && !v.status().equals("NONE") && !v.status().equals("QUEUED") && !v.status().equals("RUNNING")) {
                return v;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("채점이 5초 안에 끝나야 한다: " + (v == null ? null : v.status()));
    }

    // ---------- 설정 ----------

    @Test
    void 실행_설정을_저장하고_고칠수_있다() {
        assertThat(grading.config(assignment.getId(), prof).configured()).isFalse();
        grading.saveConfig(assignment.getId(), prof, new ConfigInput(GradingLanguage.JAVA, "Main.java", 5, 256));
        var c = grading.config(assignment.getId(), prof);
        assertThat(c.configured()).isTrue();
        assertThat(c.language()).isEqualTo(GradingLanguage.JAVA);
        assertThat(c.timeLimitSeconds()).isEqualTo(5);

        grading.saveConfig(assignment.getId(), prof, new ConfigInput(GradingLanguage.PYTHON, "src/main.py", 10, 128));
        c = grading.config(assignment.getId(), prof);
        assertThat(c.language()).isEqualTo(GradingLanguage.PYTHON);
        assertThat(c.mainFile()).isEqualTo("src/main.py");
        assertThat(c.memoryLimitMb()).isEqualTo(128);
        assertThat(c.runnerName()).isEqualTo("fake");
    }

    @Test
    void 실행_설정_입력_검증() {
        for (var bad : List.of(
                new ConfigInput(null, "Main.java", 5, 256),
                new ConfigInput(GradingLanguage.JAVA, "", 5, 256),
                new ConfigInput(GradingLanguage.JAVA, "../Main.java", 5, 256),
                new ConfigInput(GradingLanguage.JAVA, "/etc/Main.java", 5, 256),
                new ConfigInput(GradingLanguage.JAVA, "Main.py", 5, 256),            // 언어와 확장자 불일치
                new ConfigInput(GradingLanguage.PYTHON, "main.java", 5, 256),
                new ConfigInput(GradingLanguage.JAVA, "My Main.java", 5, 256),        // 공백
                new ConfigInput(GradingLanguage.JAVA, "Main;rm.java", 5, 256),
                new ConfigInput(GradingLanguage.JAVA, "1Main.java", 5, 256),          // 클래스 이름으로 쓸 수 없음
                new ConfigInput(GradingLanguage.JAVA, "Main.java", 0, 256),
                new ConfigInput(GradingLanguage.JAVA, "Main.java", 31, 256),
                new ConfigInput(GradingLanguage.JAVA, "Main.java", 5, 63),
                new ConfigInput(GradingLanguage.JAVA, "Main.java", 5, 513))) {
            assertThatThrownBy(() -> grading.saveConfig(assignment.getId(), prof, bad)).as(bad.toString())
                    .isInstanceOf(BusinessException.class);
        }
        assertThat(grading.config(assignment.getId(), prof).configured()).isFalse();
    }

    @Test
    void 설정과_케이스는_담당_교수만_바꿀수_있다() {
        enableGrading();
        var caseId = grading.config(assignment.getId(), prof).cases().get(0).id();
        for (AppUser who : List.of(otherProf, stu)) {
            assertThatThrownBy(() -> grading.saveConfig(assignment.getId(), who, new ConfigInput(GradingLanguage.JAVA, "Main.java", 5, 256)))
                    .isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> grading.addCase(assignment.getId(), who, new CaseInput("x", "", "y", true, 1)))
                    .isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> grading.deleteCase(assignment.getId(), caseId, who)).isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> grading.config(assignment.getId(), who)).isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> grading.regradeAll(assignment.getId(), who)).isInstanceOf(ForbiddenException.class);
        }
        assertThat(grading.config(assignment.getId(), prof).cases()).hasSize(5);
    }

    @Test
    void 테스트_케이스_입력_검증과_삭제() {
        grading.saveConfig(assignment.getId(), prof, new ConfigInput(GradingLanguage.JAVA, "Main.java", 5, 256));
        for (var bad : List.of(
                new CaseInput(" ", "1", "x", true, 10),
                new CaseInput("a".repeat(101), "1", "x", true, 10),
                new CaseInput("n", "1", "  ", true, 10),
                new CaseInput("n", "1", null, true, 10),
                new CaseInput("n", "1", "x", true, -1),
                new CaseInput("n", "1", "x", true, 1001),
                new CaseInput("n", "i".repeat(20_001), "x", true, 10),
                new CaseInput("n", "1", "o".repeat(20_001), true, 10))) {
            assertThatThrownBy(() -> grading.addCase(assignment.getId(), prof, bad)).as(bad.name())
                    .isInstanceOf(BusinessException.class);
        }
        IntStream.range(0, 50).forEach(i -> grading.addCase(assignment.getId(), prof, new CaseInput("c" + i, "1", "x", false, 1)));
        assertThatThrownBy(() -> grading.addCase(assignment.getId(), prof, new CaseInput("c51", "1", "x", false, 1)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("50");

        Long first = grading.config(assignment.getId(), prof).cases().get(0).id();
        grading.deleteCase(assignment.getId(), first, prof);
        assertThat(grading.config(assignment.getId(), prof).cases()).hasSize(49);
        assertThatThrownBy(() -> grading.deleteCase(assignment.getId(), first, prof)).isInstanceOf(NotFoundException.class);
        // 다른 과제의 케이스는 지울 수 없다
        Assignment other = data.assignment(course, prof, Duration.ofDays(3), false, null);
        Long caseId = grading.config(assignment.getId(), prof).cases().get(0).id();
        assertThatThrownBy(() -> grading.deleteCase(other.getId(), caseId, prof)).isInstanceOf(NotFoundException.class);
    }

    // ---------- 채점 ----------

    @Test
    void 제출하면_비동기로_채점되고_케이스별_결과와_점수가_저장된다() throws Exception {
        enableGrading();
        var v1 = submit("//LEVEL:3\nclass Main {}");

        var view = waitFinished(v1.id());

        assertThat(view.status()).isEqualTo("DONE");
        assertThat(view.compileOk()).isTrue();
        assertThat(view.passed()).isEqualTo(3);
        assertThat(view.total()).isEqualTo(5);
        assertThat(view.percent()).isEqualTo(60);
        assertThat(view.score()).isEqualTo(60);
        assertThat(view.totalPoints()).isEqualTo(100);
        assertThat(view.runner()).isEqualTo("fake");
        assertThat(view.cases()).extracting(GradingService.CaseView::verdict)
                .containsExactly("PASSED", "PASSED", "PASSED", "FAILED", "FAILED");
        assertThat(view.cases().get(0).awarded()).isEqualTo(20);
        assertThat(view.cases().get(3).awarded()).isZero();
        assertThat(view.cases().get(0).durationMs()).isEqualTo(12);
    }

    @Test
    void 실행기에는_제출_파일과_설정_케이스_입력이_그대로_전달된다() throws Exception {
        enableGrading();
        var v1 = submit("//LEVEL:5\nclass Main {}");
        waitFinished(v1.id());

        var req = fake.last();
        assertThat(req.language()).isEqualTo(GradingLanguage.JAVA);
        assertThat(req.mainFile()).isEqualTo("Main.java");
        assertThat(req.timeLimitSeconds()).isEqualTo(5);
        assertThat(req.memoryLimitMb()).isEqualTo(256);
        assertThat(new String(req.files().get("Main.java"), StandardCharsets.UTF_8)).contains("class Main");
        assertThat(req.cases()).extracting(c -> c.input()).containsExactly("1", "2", "3", "4", "5");
    }

    @Test
    void 학생에게는_비공개_케이스의_입출력이_보이지_않고_교수에게는_전부_보인다() throws Exception {
        enableGrading();
        var v1 = submit("//LEVEL:1\nclass Main {}");
        waitFinished(v1.id());

        var forStudent = grading.forSubmission(v1.id(), stu);
        var pub = forStudent.cases().get(0);               // 공개 케이스
        assertThat(pub.publicCase()).isTrue();
        assertThat(pub.input()).isEqualTo("1");
        assertThat(pub.expected()).isEqualTo("out1");
        assertThat(pub.actual()).isEqualTo("out1");
        for (var hidden : forStudent.cases().subList(2, 5)) {   // 비공개 케이스
            assertThat(hidden.publicCase()).isFalse();
            assertThat(hidden.input()).isNull();
            assertThat(hidden.expected()).isNull();
            assertThat(hidden.actual()).isNull();
            assertThat(hidden.error()).isNull();
            assertThat(hidden.verdict()).isIn("PASSED", "FAILED");   // 통과 여부는 보인다
            assertThat(hidden.name()).startsWith("케이스");
        }
        var forProf = grading.forSubmission(v1.id(), prof);
        assertThat(forProf.cases().get(4).expected()).isEqualTo("out5");
        assertThat(forProf.cases().get(4).actual()).isEqualTo("wrong");
        assertThat(forProf.cases().get(4).input()).isEqualTo("5");

        // 다른 학생과 다른 교수는 결과를 볼 수 없다
        assertThatThrownBy(() -> grading.forSubmission(v1.id(), classmate)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> grading.forSubmission(v1.id(), otherProf)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 출력_비교와_결과_종류_분류() throws Exception {
        enableGrading();
        fake.behavior = r -> new Outcome(true, null, List.of(
                new CaseOutcome(r.cases().get(0).id(), CaseStatus.OK, "out1  \r\n\r\n", "", 3),      // 줄 끝 공백·CRLF 는 통과
                new CaseOutcome(r.cases().get(1).id(), CaseStatus.OK, "out 2", "", 3),                // 다른 출력은 실패
                new CaseOutcome(r.cases().get(2).id(), CaseStatus.TIMEOUT, "", "", 5000),
                new CaseOutcome(r.cases().get(3).id(), CaseStatus.RUNTIME_ERROR, "", "Exception in thread main", 4)));
        // 5번째 케이스 결과는 일부러 빠뜨린다
        var v1 = submit("class Main {}");

        var view = waitFinished(v1.id());

        assertThat(view.cases()).extracting(GradingService.CaseView::verdict)
                .containsExactly("PASSED", "FAILED", "TIMEOUT", "RUNTIME_ERROR", "RUNTIME_ERROR");
        assertThat(view.passed()).isEqualTo(1);
        assertThat(view.cases().get(3).error()).contains("Exception");
        assertThat(view.cases().get(4).error()).contains("결과가 없습니다");
    }

    @Test
    void 컴파일_오류는_모든_케이스를_컴파일_오류로_기록한다() throws Exception {
        enableGrading();
        var v1 = submit("//COMPILE_ERROR\nclass Main {");

        var view = waitFinished(v1.id());

        assertThat(view.status()).isEqualTo("DONE");
        assertThat(view.compileOk()).isFalse();
        assertThat(view.compileMessage()).contains("error");
        assertThat(view.passed()).isZero();
        assertThat(view.total()).isEqualTo(5);
        assertThat(view.score()).isZero();
        assertThat(view.cases()).extracting(GradingService.CaseView::verdict).containsOnly("COMPILE_ERROR");
        assertThat(grading.autoGrades(assignment.getId(), List.of(v1.id())).get(v1.id()).label()).isEqualTo("컴파일 오류");
    }

    @Test
    void 실행기를_쓸수_없으면_채점_불가로_기록하고_제출은_정상이며_나중에_다시_채점할수_있다() throws Exception {
        enableGrading();
        fake.available = false;

        var v1 = submit("//LEVEL:5\nclass Main {}");

        assertThat(v1.versionNo()).isEqualTo(1);
        var view = grading.forSubmission(v1.id(), stu);
        assertThat(view.status()).isEqualTo("UNAVAILABLE");
        assertThat(view.message()).contains("Docker");
        assertThat(view.runnerAvailable()).isFalse();
        assertThat(fake.calls()).isZero();
        assertThat(grading.autoGrades(assignment.getId(), List.of(v1.id())).get(v1.id()).label()).isEqualTo("채점 불가");
        assertThat(submissionService.history(assignment.getId(), stu.getId(), stu).versions()).hasSize(1);
        assertThatThrownBy(() -> grading.regradeAll(assignment.getId(), prof)).isInstanceOf(BusinessException.class);

        fake.available = true;
        grading.regrade(v1.id(), prof).join();
        assertThat(grading.forSubmission(v1.id(), stu).status()).isEqualTo("DONE");
        assertThat(grading.forSubmission(v1.id(), stu).percent()).isEqualTo(100);
    }

    @Test
    void 실행기_오류는_채점_오류로_기록되고_예외가_밖으로_나오지_않는다() throws Exception {
        enableGrading();
        fake.behavior = r -> {
            throw new GradingException("docker 실행 오류: boom");
        };
        var v1 = submit("class Main {}");
        var view = waitFinished(v1.id());
        assertThat(view.status()).isEqualTo("ERROR");
        assertThat(view.message()).contains("docker 실행 오류");

        fake.behavior = r -> {
            throw new IllegalStateException("secret internal detail");
        };
        var v2 = submit("class Main {}");
        var view2 = waitFinished(v2.id());
        assertThat(view2.status()).isEqualTo("ERROR");
        assertThat(view2.message()).doesNotContain("secret internal detail");
        assertThat(submissionService.history(assignment.getId(), stu.getId(), stu).versions()).hasSize(2);
    }

    @Test
    void 채점_설정이_없거나_케이스가_없으면_채점하지_않는다() throws Exception {
        var v0 = submit("class Main {}");
        assertThat(grading.forSubmission(v0.id(), stu)).isNull();
        assertThat(grading.trend(assignment.getId(), stu.getId(), stu)).isNull();
        assertThat(grading.configured(assignment.getId())).isFalse();

        grading.saveConfig(assignment.getId(), prof, new ConfigInput(GradingLanguage.JAVA, "Main.java", 5, 256));   // 케이스 없음
        var v1 = submit("class Main {}");
        Thread.sleep(150);
        assertThat(grading.configured(assignment.getId())).isFalse();
        assertThat(grading.forSubmission(v1.id(), stu)).isNull();
        assertThat(fake.calls()).isZero();
        assertThat(grading.autoGrades(assignment.getId(), List.of(v0.id(), v1.id()))).isEmpty();
    }

    // ---------- 추이, 이력, 통계 ----------

    @Test
    void 버전별_통과율_추이와_이력_통계가_계산된다() throws Exception {
        enableGrading();
        var v1 = submit("//LEVEL:1\nclass Main {}");
        waitFinished(v1.id());
        var v2 = submit("//LEVEL:3\nclass Main {}");
        waitFinished(v2.id());
        var v3 = submit("//LEVEL:5\nclass Main {}");
        waitFinished(v3.id());

        var trend = grading.trend(assignment.getId(), stu.getId(), stu);
        assertThat(trend.points()).extracting(GradingService.TrendPoint::percent).containsExactly(20, 60, 100);
        assertThat(trend.delta()).isEqualTo(80);
        assertThat(trend.graded()).isEqualTo(3);
        assertThat(trend.points().get(2).passed()).isEqualTo(5);

        var rows = submissionService.history(assignment.getId(), stu.getId(), stu).versions();   // 최신이 위
        assertThat(rows).extracting(SubmissionService.VersionRow::autoLabel)
                .containsExactly("5/5 (100%)", "3/5 (60%)", "1/5 (20%)");
        assertThat(rows).extracting(SubmissionService.VersionRow::autoPercent).containsExactly(100, 60, 20);

        var stat = statsService.course(course.getId(), prof).assignments().stream()
                .filter(a -> a.id().equals(assignment.getId())).findFirst().orElseThrow();
        assertThat(stat.graded()).isTrue();
        assertThat(stat.avgPassByVersion()).containsExactly(20, 60, 100);
        assertThat(stat.avgLatestPassPercent()).isEqualTo(100);
        var row = stat.students().stream().filter(s -> s.studentId().equals(stu.getId())).findFirst().orElseThrow();
        assertThat(row.passTrend()).isEqualTo("20% → 60% → 100%");
        assertThat(row.latestPassPercent()).isEqualTo(100);
        var noSubmission = stat.students().stream().filter(s -> s.studentId().equals(classmate.getId())).findFirst().orElseThrow();
        assertThat(noSubmission.passTrend()).isEmpty();
        assertThat(noSubmission.latestPassPercent()).isEqualTo(-1);

        // 다른 학생은 남의 추이를 볼 수 없다
        assertThatThrownBy(() -> grading.trend(assignment.getId(), stu.getId(), classmate)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 채점하지_않는_과제의_이력과_통계에는_자동_채점_항목이_없다() {
        submit("class Main {}");
        var rows = submissionService.history(assignment.getId(), stu.getId(), stu).versions();
        assertThat(rows.get(0).autoLabel()).isNull();
        assertThat(rows.get(0).autoPercent()).isEqualTo(-1);
        var stat = statsService.course(course.getId(), prof).assignments().stream()
                .filter(a -> a.id().equals(assignment.getId())).findFirst().orElseThrow();
        assertThat(stat.graded()).isFalse();
        assertThat(stat.avgPassByVersion()).isEmpty();
    }

    // ---------- 다시 채점 ----------

    @Test
    void 케이스를_고친_뒤_다시_채점하면_기존_제출에_반영되고_이전_결과는_그때_기준으로_남는다() throws Exception {
        enableGrading();
        var v1 = submit("//LEVEL:5\nclass Main {}");
        waitFinished(v1.id());
        assertThat(grading.forSubmission(v1.id(), prof).total()).isEqualTo(5);

        // 케이스를 하나 지우면, 이미 채점된 결과는 그대로 남는다 (채점 시점의 케이스 기준)
        Long removed = grading.config(assignment.getId(), prof).cases().get(4).id();
        grading.deleteCase(assignment.getId(), removed, prof);
        assertThat(grading.forSubmission(v1.id(), prof).total()).isEqualTo(5);

        // 다시 채점하면 현재 케이스(4개) 기준으로 바뀐다
        assertThat(grading.regradeAll(assignment.getId(), prof).get()).isEqualTo(1);
        var view = grading.forSubmission(v1.id(), prof);
        assertThat(view.total()).isEqualTo(4);
        assertThat(view.passed()).isEqualTo(4);
        assertThat(view.totalPoints()).isEqualTo(80);
    }

    @Test
    void 다시_채점은_제출_버전을_바꾸지_않는다() throws Exception {
        enableGrading();
        var v1 = submit("//LEVEL:2\nclass Main {}");
        var v2 = submit("//LEVEL:4\nclass Main {}");
        waitFinished(v1.id());
        waitFinished(v2.id());
        var before = submissionService.history(assignment.getId(), stu.getId(), prof).versions();

        grading.regradeAll(assignment.getId(), prof).get();

        var after = submissionService.history(assignment.getId(), stu.getId(), prof).versions();
        assertThat(after).hasSize(2);
        assertThat(after).extracting(SubmissionService.VersionRow::id).containsExactlyElementsOf(before.stream().map(SubmissionService.VersionRow::id).toList());
        assertThat(after).extracting(SubmissionService.VersionRow::totalSize).containsExactlyElementsOf(before.stream().map(SubmissionService.VersionRow::totalSize).toList());
        assertThat(submissionService.view(v1.id(), null, stu).selectedLines()).contains("//LEVEL:2");
    }

    @Test
    void 한_제출만_다시_채점하는_것도_담당_교수만_할수_있다() throws Exception {
        enableGrading();
        var v1 = submit("//LEVEL:5\nclass Main {}");
        waitFinished(v1.id());
        assertThatThrownBy(() -> grading.regrade(v1.id(), stu)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> grading.regrade(v1.id(), otherProf)).isInstanceOf(ForbiddenException.class);
        int calls = fake.calls();
        grading.regrade(v1.id(), prof).join();
        assertThat(fake.calls()).isEqualTo(calls + 1);
    }
}
