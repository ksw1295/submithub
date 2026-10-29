package com.submithub.service;

import com.submithub.domain.Assignment;
import com.submithub.domain.Course;
import com.submithub.security.AppUser;
import com.submithub.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestData.class)
class CourseAssignmentServiceTest {

    @Autowired TestData data;
    @Autowired CourseService courseService;
    @Autowired AssignmentService assignmentService;

    @Test
    void 강의를_개설하면_초대코드가_발급된다() {
        AppUser prof = data.professor();
        Course a = data.course(prof);
        Course b = data.course(prof);

        assertThat(a.getInviteCode()).matches("[A-Z2-9]{8}");
        assertThat(a.getInviteCode()).isNotEqualTo(b.getInviteCode());
        assertThat(courseService.myCourses(prof)).hasSize(2);
    }

    @Test
    void 학생은_강의를_개설할수_없다() {
        assertThatThrownBy(() -> courseService.create(data.student(), "강의", "2026-2"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 초대코드로_수강등록하면_내_강의에_나타난다() {
        AppUser prof = data.professor();
        AppUser stu = data.student();
        Course course = data.course(prof);

        courseService.join(stu, course.getInviteCode().toLowerCase() + " ");   // 소문자·공백도 허용

        assertThat(courseService.myCourses(stu)).extracting(CourseService.CourseSummary::id).containsExactly(course.getId());
        assertThat(courseService.detail(course.getId(), prof).students()).hasSize(1);
    }

    @Test
    void 틀린_코드와_중복_등록은_거부된다() {
        AppUser prof = data.professor();
        AppUser stu = data.student();
        Course course = data.course(prof);

        assertThatThrownBy(() -> courseService.join(stu, "ZZZZZZZZ")).isInstanceOf(BusinessException.class);
        courseService.join(stu, course.getInviteCode());
        assertThatThrownBy(() -> courseService.join(stu, course.getInviteCode()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("이미");
    }

    @Test
    void 교수는_수강등록할수_없다() {
        Course course = data.course(data.professor());
        assertThatThrownBy(() -> courseService.join(data.professor(), course.getInviteCode()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 수강하지_않은_학생과_다른_교수는_강의를_볼수_없다() {
        AppUser prof = data.professor();
        Course course = data.course(prof);

        assertThatThrownBy(() -> courseService.detail(course.getId(), data.student()))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> courseService.detail(course.getId(), data.professor()))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> courseService.detail(999999L, prof)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void 학생_화면에는_초대코드가_보이지_않는다() {
        AppUser prof = data.professor();
        AppUser stu = data.student();
        Course course = data.course(prof);
        courseService.join(stu, course.getInviteCode());

        assertThat(courseService.detail(course.getId(), stu).inviteCode()).isNull();
        assertThat(courseService.detail(course.getId(), prof).inviteCode()).isEqualTo(course.getInviteCode());
    }

    @Test
    void 과제를_등록하고_수정할수_있다_다른_교수는_수정할수_없다() {
        AppUser prof = data.professor();
        Course course = data.course(prof);
        Assignment a = data.assignment(course, prof, Duration.ofDays(3), true, Duration.ofDays(5));

        LocalDateTime newDue = LocalDateTime.now().plusDays(10);
        assignmentService.update(a.getId(), prof, new AssignmentService.AssignmentInput(
                "새 제목", "새 설명", newDue, false, null, "int main() {}", null));

        Assignment updated = assignmentService.editable(a.getId(), prof);
        assertThat(updated.getTitle()).isEqualTo("새 제목");
        assertThat(updated.isAllowLate()).isFalse();
        assertThat(updated.getLateUntil()).isNull();       // 지각 불허면 기한 제거
        assertThat(updated.getBaselineCode()).isEqualTo("int main() {}");

        assertThatThrownBy(() -> assignmentService.update(a.getId(), data.professor(),
                new AssignmentService.AssignmentInput("해킹", "", newDue, false, null, null, null)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> assignmentService.create(course.getId(), data.professor(),
                new AssignmentService.AssignmentInput("해킹", "", newDue, false, null, null, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 조별과제를_등록하고_수정할수_있다() {
        AppUser prof = data.professor();
        Course course = data.course(prof);
        LocalDateTime due = LocalDateTime.now().plusDays(7);

        Assignment created = assignmentService.create(course.getId(), prof, new AssignmentService.AssignmentInput(
                "RAG 팀 과제", "팀 프로젝트 설명", due, true, due.plusDays(2), null, null, true, 4));

        assertThat(created.isTeamProject()).isTrue();
        assertThat(created.getMaxTeamSize()).isEqualTo(4);

        assignmentService.update(created.getId(), prof, new AssignmentService.AssignmentInput(
                "RAG 팀 과제 수정", "설명 수정", due, true, due.plusDays(2), null, null, true, 5));

        Assignment updated = assignmentService.editable(created.getId(), prof);
        assertThat(updated.isTeamProject()).isTrue();
        assertThat(updated.getMaxTeamSize()).isEqualTo(5);
    }

    @Test
    void 과제_입력값_검증() {
        AppUser prof = data.professor();
        Course course = data.course(prof);
        LocalDateTime due = LocalDateTime.now().plusDays(1);

        assertThatThrownBy(() -> assignmentService.create(course.getId(), prof,
                new AssignmentService.AssignmentInput(" ", "", due, false, null, null, null)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> assignmentService.create(course.getId(), prof,
                new AssignmentService.AssignmentInput("제목", "", null, false, null, null, null)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> assignmentService.create(course.getId(), prof,
                new AssignmentService.AssignmentInput("제목", "", due, true, due.minusHours(1), null, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("지각");
    }

    @Test
    void 뼈대코드는_파일로도_올릴수_있고_CP949도_읽는다() throws Exception {
        AppUser prof = data.professor();
        Course course = data.course(prof);
        byte[] cp949 = "// 뼈대 코드\nint main() { return 0; }\n".getBytes("MS949");

        Assignment a = assignmentService.create(course.getId(), prof, new AssignmentService.AssignmentInput(
                "제목", "", LocalDateTime.now().plusDays(1), false, null, null, cp949));

        assertThat(a.getBaselineCode()).contains("뼈대 코드").contains("int main()");
        assertThatThrownBy(() -> assignmentService.create(course.getId(), prof, new AssignmentService.AssignmentInput(
                "제목", "", LocalDateTime.now().plusDays(1), false, null, null, new byte[]{0, 1, 2, 0, 5})))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 마감_상태_판정() {
        LocalDateTime now = LocalDateTime.now();
        Course dummy = null;
        Assignment open = new Assignment(dummy, "t", "", now.plusDays(10), false, null, null);
        Assignment soon = new Assignment(dummy, "t", "", now.plusHours(5), false, null, null);
        Assignment lateOpen = new Assignment(dummy, "t", "", now.minusHours(1), true, now.plusDays(1), null);
        Assignment lateExpired = new Assignment(dummy, "t", "", now.minusHours(3), true, now.minusHours(1), null);
        Assignment closed = new Assignment(dummy, "t", "", now.minusHours(1), false, null, null);

        assertThat(AssignmentService.stateOf(open, now)).isEqualTo("OPEN");
        assertThat(AssignmentService.stateOf(soon, now)).isEqualTo("DUE_SOON");
        assertThat(AssignmentService.stateOf(lateOpen, now)).isEqualTo("LATE_OPEN");
        assertThat(AssignmentService.stateOf(lateExpired, now)).isEqualTo("CLOSED");
        assertThat(AssignmentService.stateOf(closed, now)).isEqualTo("CLOSED");
        assertThat(lateOpen.isOpen(now)).isTrue();
        assertThat(lateExpired.isOpen(now)).isFalse();
    }
}
