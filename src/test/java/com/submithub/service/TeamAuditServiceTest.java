package com.submithub.service;

import com.submithub.domain.Assignment;
import com.submithub.domain.AssignmentRepository;
import com.submithub.domain.Course;
import com.submithub.domain.Team;
import com.submithub.security.AppUser;
import com.submithub.storage.UploadProcessor.Upload;
import com.submithub.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(TestData.class)
class TeamAuditServiceTest {

    @Autowired TestData data;
    @Autowired TeamService teamService;
    @Autowired TeamAuditService teamAuditService;
    @Autowired CourseService courseService;
    @Autowired SubmissionService submissionService;
    @Autowired GradeService gradeService;
    @Autowired AssignmentRepository assignmentRepository;

    AppUser prof, otherProf, member1, member2, classmate, outsider;
    Course course;
    Assignment assignment;
    Team team;

    @BeforeEach
    void setUp() {
        prof = data.professor();
        otherProf = data.professor();
        member1 = data.student();
        member2 = data.student();
        classmate = data.student();
        outsider = data.student();

        course = data.course(prof);
        courseService.join(member1, course.getInviteCode());
        courseService.join(member2, course.getInviteCode());
        courseService.join(classmate, course.getInviteCode());

        assignment = data.assignment(course, prof, Duration.ofDays(7), false, null);
        assignment.configureTeam(true, 4);
        assignmentRepository.save(assignment);

        team = teamService.createTeam(assignment.getId(), "1조", "https://github.com/org/repo",
                member1.getId(), "feature/m1", "팀장");
        teamService.joinTeam(team.getId(), member2.getId(), "feature/m2", "팀원");

        // member1 제출 및 채점 (95점)
        String code1 = "public class A { int x = 1; }";
        var sub1 = submissionService.submit(assignment.getId(), member1,
                List.of(new Upload("A.java", code1.getBytes(StandardCharsets.UTF_8))), "v1");
        gradeService.save(sub1.id(), 95, "좋은 코드", prof);

        // member2 제출 및 채점 (80점)
        String code2 = "public class B { int y = 2; }";
        var sub2 = submissionService.submit(assignment.getId(), member2,
                List.of(new Upload("B.java", code2.getBytes(StandardCharsets.UTF_8))), "v1");
        gradeService.save(sub2.id(), 80, "양호", prof);
    }

    @Test
    void 담당교수는_모든_팀원의_점수를_포함하여_감사_조회_가능() {
        var report = teamAuditService.auditTeam(team.getId(), prof);
        assertThat(report).isNotNull();
        assertThat(report.members()).hasSize(2);

        var m1 = report.members().stream().filter(m -> m.studentId().equals(member1.getId())).findFirst().orElseThrow();
        var m2 = report.members().stream().filter(m -> m.studentId().equals(member2.getId())).findFirst().orElseThrow();

        assertThat(m1.score()).isEqualTo(95);
        assertThat(m2.score()).isEqualTo(80);
    }

    @Test
    void 팀원은_본인_점수만_보고_다른_팀원의_점수는_숨김_처리() {
        var report = teamAuditService.auditTeam(team.getId(), member1);
        assertThat(report).isNotNull();

        var m1 = report.members().stream().filter(m -> m.studentId().equals(member1.getId())).findFirst().orElseThrow();
        var m2 = report.members().stream().filter(m -> m.studentId().equals(member2.getId())).findFirst().orElseThrow();

        assertThat(m1.score()).isEqualTo(95);   // 본인 점수는 보임
        assertThat(m2.score()).isNull();        // 다른 팀원 점수는 숨김(null)
    }

    @Test
    void 같은_강의지만_다른_팀_학생은_403() {
        assertThatThrownBy(() -> teamAuditService.auditTeam(team.getId(), classmate))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 다른_강의_학생은_403() {
        assertThatThrownBy(() -> teamAuditService.auditTeam(team.getId(), outsider))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 다른_교수는_403() {
        assertThatThrownBy(() -> teamAuditService.auditTeam(team.getId(), otherProf))
                .isInstanceOf(ForbiddenException.class);
    }
}
