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

    @Test
    void 유효코드줄계산_README만_있는_제출은_0줄_계산된다() {
        AppUser s = data.student();
        courseService.join(s, course.getInviteCode());
        Team t = teamService.createTeam(assignment.getId(), "문서팀", null, s.getId(), "main", "팀장");

        submissionService.submit(assignment.getId(), s,
                List.of(new Upload("README.md", "# 프로젝트 설명\n이것은 리드미입니다.\n".getBytes(StandardCharsets.UTF_8))), "v1");

        var report = teamAuditService.auditTeam(t.getId(), prof);
        var audit = report.members().get(0);
        assertThat(audit.validCodeLines()).isEqualTo(0);
    }

    @Test
    void 유효코드줄계산_포인터연산_포함되고_여러줄주석과_파이썬docstring_제외된다() {
        AppUser s = data.student();
        courseService.join(s, course.getInviteCode());
        Team t = teamService.createTeam(assignment.getId(), "C언어팀", null, s.getId(), "main", "팀장");

        // C 코드: 포인터 역참조 *p = 1; 포함, /* ... */ 여러 줄 주석 포함
        String cCode = """
                #include <stdio.h>
                /*
                 * 여러 줄 주석
                 * 주석 라인 2
                 */
                void test(int *p) {
                    *p = 1;
                }
                """;
        // 파이썬 코드: 삼중 따옴표 docstring 포함
        String pyCode = """
                def hello():
                    \"\"\"
                    이것은 여러 줄에 걸친
                    파이썬 docstring 입니다.
                    \"\"\"
                    return 42
                """;

        submissionService.submit(assignment.getId(), s,
                List.of(
                        new Upload("main.c", cCode.getBytes(StandardCharsets.UTF_8)),
                        new Upload("app.py", pyCode.getBytes(StandardCharsets.UTF_8))
                ), "v1");

        var report = teamAuditService.auditTeam(t.getId(), prof);
        var audit = report.members().get(0);
        // C: #include <stdio.h> (line 1), void test(int *p) { (line 7), *p = 1; (line 8), } (line 9) -> 4줄
        // Py: def hello(): (line 1), """...""" start (line 2), return 42 (line 7) -> 3줄
        // 합계 7줄 (주석 내부 줄 제외)
        assertThat(audit.validCodeLines()).isGreaterThanOrEqualTo(5);
        assertThat(audit.validCodeLines()).isLessThan(15);
    }

    @Test
    void 제출이_0건이면_기여율은_0퍼센트() {
        AppUser s = data.student();
        courseService.join(s, course.getInviteCode());
        Team t = teamService.createTeam(assignment.getId(), "미제출팀", null, s.getId(), "main", "팀장");

        var report = teamAuditService.auditTeam(t.getId(), prof);
        assertThat(report.totalValidCodeLines()).isEqualTo(0);
        assertThat(report.members().get(0).contributionPercent()).isEqualTo(0);
        assertThat(report.members().get(0).healthBadge()).contains("미제출");
    }

    @Test
    void 출처검증_직접업로드와_저장소일치_구분() {
        var report = teamAuditService.auditTeam(team.getId(), prof);
        var m1 = report.members().stream().filter(m -> m.studentId().equals(member1.getId())).findFirst().orElseThrow();
        // member1은 upload로 제출 -> 출처 미확인 (직접 업로드)
        assertThat(m1.branchStatus()).contains("출처 미확인");
    }

    @Test
    void 팀원간_코드_중복도_70퍼센트_이상시_경고() {
        AppUser s1 = data.student();
        AppUser s2 = data.student();
        courseService.join(s1, course.getInviteCode());
        courseService.join(s2, course.getInviteCode());

        Team t = teamService.createTeam(assignment.getId(), "중복의심팀", null, s1.getId(), "main", "팀장");
        teamService.joinTeam(t.getId(), s2.getId(), "feature/s2", "팀원");

        String identicalCode = """
                public class Service {
                    public int calculate(int a, int b) {
                        int x = a * 10;
                        int y = b * 20;
                        int z = x + y;
                        for (int i = 0; i < 10; i++) {
                            z += i * 2;
                        }
                        return z;
                    }
                }
                """;

        submissionService.submit(assignment.getId(), s1,
                List.of(new Upload("Service.java", identicalCode.getBytes(StandardCharsets.UTF_8))), "v1");
        submissionService.submit(assignment.getId(), s2,
                List.of(new Upload("Service.java", identicalCode.getBytes(StandardCharsets.UTF_8))), "v1");

        var report = teamAuditService.auditTeam(t.getId(), prof);
        var a1 = report.members().stream().filter(m -> m.studentId().equals(s1.getId())).findFirst().orElseThrow();
        var a2 = report.members().stream().filter(m -> m.studentId().equals(s2.getId())).findFirst().orElseThrow();

        assertThat(a1.duplicateWarning()).isTrue();
        assertThat(a2.duplicateWarning()).isTrue();
        assertThat(a1.duplicatePercent()).isGreaterThanOrEqualTo(70);
    }

    @Test
    void 배지_경계값_14_15_39_40_판정() {
        AppUser sA = data.student();
        AppUser sB = data.student();
        AppUser sC = data.student();
        AppUser sD = data.student();
        courseService.join(sA, course.getInviteCode());
        courseService.join(sB, course.getInviteCode());
        courseService.join(sC, course.getInviteCode());
        courseService.join(sD, course.getInviteCode());

        Team t = teamService.createTeam(assignment.getId(), "경계값팀", null, sA.getId(), "main", "팀장");
        teamService.joinTeam(t.getId(), sB.getId(), "feature/b", "팀원");
        teamService.joinTeam(t.getId(), sC.getId(), "feature/c", "팀원");
        teamService.joinTeam(t.getId(), sD.getId(), "feature/d", "팀원");

        // Helper to generate N lines of valid Java code
        java.util.function.IntFunction<String> genLines = count -> {
            StringBuilder sb = new StringBuilder("public class T {\n");
            for (int i = 1; i <= count - 2; i++) {
                sb.append("int x").append(i).append(" = ").append(i).append(";\n");
            }
            sb.append("}\n");
            return sb.toString();
        };

        // 40 + 31 + 15 + 14 = 100 lines
        submissionService.submit(assignment.getId(), sA,
                List.of(new Upload("A.java", genLines.apply(40).getBytes(StandardCharsets.UTF_8))), "v1");
        submissionService.submit(assignment.getId(), sB,
                List.of(new Upload("B.java", genLines.apply(31).getBytes(StandardCharsets.UTF_8))), "v1");
        submissionService.submit(assignment.getId(), sC,
                List.of(new Upload("C.java", genLines.apply(15).getBytes(StandardCharsets.UTF_8))), "v1");
        submissionService.submit(assignment.getId(), sD,
                List.of(new Upload("D.java", genLines.apply(14).getBytes(StandardCharsets.UTF_8))), "v1");

        var report = teamAuditService.auditTeam(t.getId(), prof);
        assertThat(report.totalValidCodeLines()).isEqualTo(100);

        var auditA = report.members().stream().filter(m -> m.studentId().equals(sA.getId())).findFirst().orElseThrow();
        var auditB = report.members().stream().filter(m -> m.studentId().equals(sB.getId())).findFirst().orElseThrow();
        var auditC = report.members().stream().filter(m -> m.studentId().equals(sC.getId())).findFirst().orElseThrow();
        var auditD = report.members().stream().filter(m -> m.studentId().equals(sD.getId())).findFirst().orElseThrow();

        assertThat(auditA.contributionPercent()).isEqualTo(40);
        assertThat(auditA.healthBadge()).isEqualTo("주도적 핵심 기여"); // >= 40%

        assertThat(auditB.contributionPercent()).isEqualTo(31);
        assertThat(auditB.healthBadge()).isEqualTo("정상 균형 기여"); // 15% <= x < 40%

        assertThat(auditC.contributionPercent()).isEqualTo(15);
        assertThat(auditC.healthBadge()).isEqualTo("정상 균형 기여"); // >= 15%

        assertThat(auditD.contributionPercent()).isEqualTo(14);
        assertThat(auditD.healthBadge()).isEqualTo("기여도 저조 (주의)"); // < 15%
    }
}
