package com.submithub.service;

import com.submithub.domain.Assignment;
import com.submithub.domain.AssignmentRepository;
import com.submithub.domain.Course;
import com.submithub.domain.Team;
import com.submithub.domain.TeamMember;
import com.submithub.security.AppUser;
import com.submithub.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(TestData.class)
class TeamServiceTest {

    @Autowired TestData data;
    @Autowired TeamService teamService;
    @Autowired CourseService courseService;
    @Autowired AssignmentRepository assignmentRepository;

    AppUser prof, student1, student2, nonEnrolledStudent;
    Course course;
    Assignment teamAssignment, individualAssignment, closedAssignment;

    @BeforeEach
    void setUp() {
        prof = data.professor();
        student1 = data.student();
        student2 = data.student();
        nonEnrolledStudent = data.student();

        course = data.course(prof);
        courseService.join(student1, course.getInviteCode());
        courseService.join(student2, course.getInviteCode());

        teamAssignment = data.assignment(course, prof, Duration.ofDays(7), false, null);
        teamAssignment.configureTeam(true, 3);
        assignmentRepository.save(teamAssignment);

        individualAssignment = data.assignment(course, prof, Duration.ofDays(7), false, null);
        individualAssignment.configureTeam(false, null);
        assignmentRepository.save(individualAssignment);

        closedAssignment = data.assignment(course, prof, Duration.ofDays(-2), false, null);
        closedAssignment.configureTeam(true, 3);
        assignmentRepository.save(closedAssignment);
    }

    // --- createTeam Tests ---

    @Test
    void 팀생성_정상() {
        Team team = teamService.createTeam(teamAssignment.getId(), "1조", "https://github.com/test-owner/test-repo",
                student1.getId(), "feature/leader", "팀장");
        assertThat(team.getId()).isNotNull();
        assertThat(team.getName()).isEqualTo("1조");
        assertThat(team.getMembers()).hasSize(1);
        assertThat(team.getMembers().get(0).getBranchName()).isEqualTo("feature/leader");
    }

    @Test
    void 팀생성_수강하지_않은_학생은_Forbidden() {
        assertThatThrownBy(() -> teamService.createTeam(teamAssignment.getId(), "1조", null,
                nonEnrolledStudent.getId(), "main", "팀장"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("수강 중인 강의가 아닙니다");
    }

    @Test
    void 팀생성_개인과제인_경우_예외() {
        assertThatThrownBy(() -> teamService.createTeam(individualAssignment.getId(), "1조", null,
                student1.getId(), "main", "팀장"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("조별 과제가 아닙니다");
    }

    @Test
    void 팀생성_마감된_과제인_경우_예외() {
        assertThatThrownBy(() -> teamService.createTeam(closedAssignment.getId(), "1조", null,
                student1.getId(), "main", "팀장"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("마감");
    }

    @Test
    void 팀생성_브랜치이름_형식_오류_예외() {
        assertThatThrownBy(() -> teamService.createTeam(teamAssignment.getId(), "1조", null,
                student1.getId(), "feature/..hack", "팀장"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("올바른 브랜치 이름 형식이 아닙니다");

        assertThatThrownBy(() -> teamService.createTeam(teamAssignment.getId(), "1조", null,
                student1.getId(), "feature with spaces", "팀장"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("올바른 브랜치 이름 형식이 아닙니다");
    }

    @Test
    void 팀생성_팀명_공백이거나_100자초과_예외() {
        assertThatThrownBy(() -> teamService.createTeam(teamAssignment.getId(), "   ", null,
                student1.getId(), "main", "팀장"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("팀명을 입력하세요");

        String longName = "a".repeat(101);
        assertThatThrownBy(() -> teamService.createTeam(teamAssignment.getId(), longName, null,
                student1.getId(), "main", "팀장"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 팀생성_잘못된_repoUrl_예외() {
        assertThatThrownBy(() -> teamService.createTeam(teamAssignment.getId(), "1조", "https://notgithub.com/a/b",
                student1.getId(), "main", "팀장"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- joinTeam Tests ---

    @Test
    void 팀가입_정상() {
        Team team = teamService.createTeam(teamAssignment.getId(), "1조", null,
                student1.getId(), "main", "팀장");

        TeamMember member = teamService.joinTeam(team.getId(), student2.getId(), "feature/sub", "개발");
        assertThat(member.getId()).isNotNull();
        assertThat(member.getBranchName()).isEqualTo("feature/sub");
        assertThat(member.getStudent().getId()).isEqualTo(student2.getId());
    }

    @Test
    void 팀가입_수강하지_않은_학생은_Forbidden() {
        Team team = teamService.createTeam(teamAssignment.getId(), "1조", null,
                student1.getId(), "main", "팀장");

        assertThatThrownBy(() -> teamService.joinTeam(team.getId(), nonEnrolledStudent.getId(), "main", "팀원"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("수강 중인 강의가 아닙니다");
    }

    @Test
    void 팀가입_개인과제인_경우_예외() {
        Team team = teamService.createTeam(teamAssignment.getId(), "1조", null,
                student1.getId(), "main", "팀장");
        teamAssignment.configureTeam(false, null);
        assignmentRepository.save(teamAssignment);

        assertThatThrownBy(() -> teamService.joinTeam(team.getId(), student2.getId(), "main", "팀원"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("조별 과제가 아닙니다");
    }

    @Test
    void 팀가입_마감된_과제인_경우_예외() {
        Team team = teamService.createTeam(teamAssignment.getId(), "1조", null,
                student1.getId(), "main", "팀장");
        teamAssignment.update("제목", "설명", java.time.LocalDateTime.now().minusDays(1), false, null, null);
        assignmentRepository.save(teamAssignment);

        assertThatThrownBy(() -> teamService.joinTeam(team.getId(), student2.getId(), "main", "팀원"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("마감");
    }

    @Test
    void 팀가입_브랜치이름_형식_오류_예외() {
        Team team = teamService.createTeam(teamAssignment.getId(), "1조", null,
                student1.getId(), "main", "팀장");

        assertThatThrownBy(() -> teamService.joinTeam(team.getId(), student2.getId(), "feature/..hack", "팀원"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("올바른 브랜치 이름 형식이 아닙니다");
    }
}
