package com.submithub.service;

import com.submithub.domain.Assignment;
import com.submithub.domain.Course;
import com.submithub.security.AppUser;
import com.submithub.storage.UploadProcessor.Upload;
import com.submithub.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestData.class)
class StatsServiceTest {

    @Autowired TestData data;
    @Autowired StatsService statsService;
    @Autowired SubmissionService submissionService;
    @Autowired CourseService courseService;
    @Autowired ReviewService reviewService;

    AppUser prof, otherProf, a, b, c;
    Course course;

    @BeforeEach
    void setUp() {
        prof = data.professor();
        otherProf = data.professor();
        a = data.student();
        b = data.student();
        c = data.student();
        course = data.course(prof);
        for (AppUser s : List.of(a, b, c)) {
            courseService.join(s, course.getInviteCode());
        }
    }

    static Upload file(String content) {
        return new Upload("A.java", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 제출률_미제출자_버전_수와_시간대_분포가_계산된다() {
        Assignment asg = data.assignment(course, prof, Duration.ofHours(10), false, null);
        submissionService.submit(asg.getId(), a, List.of(file("1")), null);
        submissionService.submit(asg.getId(), a, List.of(file("2")), null);
        submissionService.submit(asg.getId(), b, List.of(file("3")), null);

        var stats = statsService.course(course.getId(), prof);
        assertThat(stats.enrolled()).isEqualTo(3);
        var s = stats.assignments().get(0);
        assertThat(s.enrolled()).isEqualTo(3);
        assertThat(s.submitted()).isEqualTo(2);
        assertThat(s.submitRatePercent()).isEqualTo(67);
        assertThat(s.missing()).containsExactly(c.getName());
        assertThat(s.totalSubmissions()).isEqualTo(3);
        assertThat(s.byHour()).hasSize(24);
        assertThat(s.byHour().stream().mapToInt(Integer::intValue).sum()).isEqualTo(3);
        assertThat(stats.hourTotal().stream().mapToInt(Integer::intValue).sum()).isEqualTo(3);
        var rowA = s.students().stream().filter(r -> r.studentId().equals(a.getId())).findFirst().orElseThrow();
        assertThat(rowA.versions()).isEqualTo(2);
        assertThat(rowA.submitted()).isTrue();
        assertThat(stats.averageSubmitRate()).isEqualTo(67);
    }

    @Test
    void 마감_직전_24시간_제출_비율과_지각_학생_수() {
        // 마감이 10시간 뒤: 지금 낸 제출은 모두 "마감 직전 24시간"
        Assignment soon = data.assignment(course, prof, Duration.ofHours(10), false, null);
        submissionService.submit(soon.getId(), a, List.of(file("1")), null);
        submissionService.submit(soon.getId(), b, List.of(file("2")), null);
        // 마감이 3일 뒤: 지금 낸 제출은 24시간 밖
        Assignment far = data.assignment(course, prof, Duration.ofDays(3), false, null);
        submissionService.submit(far.getId(), a, List.of(file("3")), null);
        // 마감이 지났지만 지각 허용: 지각 제출
        Assignment late = data.assignment(course, prof, Duration.ofHours(-2), true, Duration.ofDays(1));
        submissionService.submit(late.getId(), c, List.of(file("4")), null);

        var stats = statsService.course(course.getId(), prof);
        var byTitle = stats.assignments().stream().collect(java.util.stream.Collectors.toMap(
                x -> x.id(), x -> x));
        var soonStat = byTitle.get(soon.getId());
        assertThat(soonStat.lastDaySubmissions()).isEqualTo(2);
        assertThat(soonStat.lastDayPercent()).isEqualTo(100);
        assertThat(byTitle.get(far.getId()).lastDaySubmissions()).isZero();
        assertThat(byTitle.get(far.getId()).lastDayPercent()).isZero();
        var lateStat = byTitle.get(late.getId());
        assertThat(lateStat.lateStudents()).isEqualTo(1);
        assertThat(lateStat.pastDue()).isTrue();
        assertThat(lateStat.students().stream().filter(r -> r.late()).map(StatsService.StudentRow::studentId))
                .containsExactly(c.getId());
    }

    @Test
    void 피드백_반영률이_학생별과_평균으로_나온다() {
        Assignment asg = data.assignment(course, prof, Duration.ofDays(2), false, null);
        var v1 = submissionService.submit(asg.getId(), a, List.of(file("x\ny\nz\n")), null);
        Long fid = submissionService.view(v1.id(), null, prof).selectedFileId();
        reviewService.addComment(fid, 1, 1, "x 고쳐요", prof);
        reviewService.addComment(fid, 3, 3, "z 도 고쳐요", prof);
        submissionService.submit(asg.getId(), a, List.of(file("X\ny\nz\n")), null);     // x 만 수정

        var stats = statsService.course(course.getId(), prof);
        var row = stats.assignments().get(0).students().stream()
                .filter(r -> r.studentId().equals(a.getId())).findFirst().orElseThrow();
        assertThat(row.feedbackRate()).isEqualTo(50);
        assertThat(row.feedbackTotal()).isEqualTo(2);
        assertThat(row.feedbackUnchanged()).isEqualTo(1);
        assertThat(stats.averageFeedbackRate()).isEqualTo(50);
        var noComments = stats.assignments().get(0).students().stream()
                .filter(r -> r.studentId().equals(b.getId())).findFirst().orElseThrow();
        assertThat(noComments.feedbackRate()).isEqualTo(-1);
    }

    @Test
    void 과제가_없거나_제출이_없어도_통계가_계산되고_권한이_필요하다() {
        var empty = statsService.course(course.getId(), prof);
        assertThat(empty.assignments()).isEmpty();
        assertThat(empty.averageSubmitRate()).isZero();
        assertThat(empty.averageFeedbackRate()).isEqualTo(-1);

        Assignment asg = data.assignment(course, prof, Duration.ofDays(2), false, null);
        var s = statsService.course(course.getId(), prof).assignments().get(0);
        assertThat(s.submitted()).isZero();
        assertThat(s.submitRatePercent()).isZero();
        assertThat(s.lastDayPercent()).isZero();
        assertThat(s.missing()).hasSize(3);

        assertThatThrownBy(() -> statsService.course(course.getId(), otherProf)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> statsService.course(course.getId(), a)).isInstanceOf(ForbiddenException.class);
        assertThat(asg.getDueAt()).isAfter(LocalDateTime.now());
    }
}
