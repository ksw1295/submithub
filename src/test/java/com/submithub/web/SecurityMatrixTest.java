package com.submithub.web;

import com.submithub.domain.Assignment;
import com.submithub.domain.Course;
import com.submithub.security.AppUser;
import com.submithub.service.CourseService;
import com.submithub.service.SimilarityService;
import com.submithub.service.SubmissionService;
import com.submithub.storage.UploadProcessor.Upload;
import com.submithub.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 권한 매트릭스: 모든 주요 주소를 (담당 교수 / 다른 교수 / 수강 학생 / 같은 강의 다른 학생 / 수강하지 않은 학생)으로
 * 호출해서 "본인 것이거나 해당 강의의 교수만" 규칙이 지켜지는지 확인한다 (IDOR 방지).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestData.class)
class SecurityMatrixTest {

    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired CourseService courseService;
    @Autowired SubmissionService submissionService;
    @Autowired SimilarityService similarityService;
    @Autowired com.submithub.service.TeamService teamService;
    @Autowired com.submithub.domain.AssignmentRepository assignmentRepository;

    AppUser prof, otherProf, stu, classmate, outsider;
    Course course;
    Assignment assignment;
    Long submissionId, fileId, pairId, teamId;

    @BeforeEach
    void setUp() {
        prof = data.professor();
        otherProf = data.professor();
        stu = data.student();
        classmate = data.student();
        outsider = data.student();
        course = data.course(prof);
        courseService.join(stu, course.getInviteCode());
        courseService.join(classmate, course.getInviteCode());
        assignment = data.assignment(course, prof, Duration.ofDays(2), false, null);
        assignment.configureTeam(true, 4);
        assignmentRepository.save(assignment);

        String code = "class A {\n  int f(int x) { for (int i = 0; i < x; i++) { x += i * 2; } return x; }\n"
                + "  int g(int y) { while (y > 0) { y -= 3; if (y % 2 == 0) { y++; } } return y; }\n}\n";
        submissionId = submissionService.submit(assignment.getId(), stu,
                List.of(new Upload("A.java", code.getBytes(StandardCharsets.UTF_8))), null).id();
        submissionService.submit(assignment.getId(), classmate,
                List.of(new Upload("A.java", code.getBytes(StandardCharsets.UTF_8))), null);
        fileId = submissionService.view(submissionId, null, prof).selectedFileId();
        pairId = similarityService.run(assignment.getId(), 0.5, prof).pairs().get(0).id();

        var t = teamService.createTeam(assignment.getId(), "보안팀", "https://github.com/owner/repo",
                stu.getId(), "main", "팀장");
        teamId = t.getId();
    }

    /** 주소 하나와, 그 주소를 열 수 있는 사람들의 이름표 */
    record Rule(String url, Set<String> allowed) {
    }

    List<Rule> rules() {
        String a = "/assignments/" + assignment.getId();
        return List.of(
                new Rule("/courses/" + course.getId(), Set.of("prof", "stu", "classmate")),
                new Rule(a, Set.of("prof", "stu", "classmate")),
                new Rule(a + "/students/" + stu.getId(), Set.of("prof", "stu")),
                new Rule(a + "/students/" + stu.getId() + "/diff", Set.of("prof", "stu")),
                new Rule("/submissions/" + submissionId, Set.of("prof", "stu")),
                new Rule("/files/" + fileId + "/download", Set.of("prof", "stu")),
                new Rule("/courses/" + course.getId() + "/stats", Set.of("prof")),
                new Rule(a + "/similarity", Set.of("prof")),
                new Rule("/similarity/pairs/" + pairId, Set.of("prof")),
                new Rule(a + "/edit", Set.of("prof")),
                new Rule(a + "/grading", Set.of("prof")),
                new Rule(a + "/teams", Set.of("prof", "stu", "classmate")),
                new Rule("/teams/" + teamId + "/audit", Set.of("prof", "stu")),
                new Rule("/courses/" + course.getId() + "/assignments/new", Set.of("prof")));
    }

    java.util.Map<String, AppUser> people() {
        var m = new java.util.LinkedHashMap<String, AppUser>();
        m.put("prof", prof);
        m.put("otherProf", otherProf);
        m.put("stu", stu);
        m.put("classmate", classmate);
        m.put("outsider", outsider);
        return m;
    }

    @Test
    void 로그인하지_않으면_모든_주소가_로그인_화면으로_보내진다() throws Exception {
        for (Rule r : rules()) {
            mvc.perform(get(r.url())).andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));
        }
    }

    @Test
    void 허용된_사람만_열수_있고_나머지는_403() throws Exception {
        for (Rule r : rules()) {
            for (var who : people().entrySet()) {
                int expected = r.allowed().contains(who.getKey()) ? 200 : 403;
                int actual = mvc.perform(get(r.url()).with(user(who.getValue()))).andReturn().getResponse().getStatus();
                assertThat(actual).as(r.url() + " / " + who.getKey()).isEqualTo(expected);
            }
        }
    }

    @Test
    void 쓰기_요청도_권한이_없으면_403_이고_데이터가_바뀌지_않는다() throws Exception {
        String a = "/assignments/" + assignment.getId();
        for (AppUser who : List.of(stu, otherProf, outsider)) {
            String label = who.getLoginId();
            mvc.perform(post("/files/" + fileId + "/comments").with(user(who)).with(csrf())
                            .param("startLine", "1").param("endLine", "1").param("body", "x"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/submissions/" + submissionId + "/grade").with(user(who)).with(csrf()).param("score", "1"))
                    .andExpect(status().isForbidden());
            mvc.perform(post(a + "/similarity").with(user(who)).with(csrf()).param("percent", "60"))
                    .andExpect(status().isForbidden());
            mvc.perform(post(a + "/edit").with(user(who)).with(csrf()).param("title", "해킹").param("dueAt", "2030-01-01T10:00"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/courses/" + course.getId() + "/assignments/new").with(user(who)).with(csrf())
                            .param("title", "해킹").param("dueAt", "2030-01-01T10:00"))
                    .andExpect(status().isForbidden());
            assertThat(label).isNotBlank();
        }
        // 수강하지 않은 학생과 교수는 팀을 생성하거나 가입할 수 없다 (403)
        for (AppUser who : List.of(otherProf, outsider, prof)) {
            mvc.perform(post(a + "/teams").with(user(who)).with(csrf()).param("name", "해킹팀"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/teams/" + teamId + "/join").with(user(who)).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        // 학생은 강의를 만들 수도 없다
        mvc.perform(post("/courses/new").with(user(stu)).with(csrf()).param("name", "x").param("semester", "y"))
                .andExpect(status().isForbidden());

        assertThat(submissionService.view(submissionId, null, prof).grade()).isNull();
        assertThat(courseService.myCourses(stu)).hasSize(1);
        assertThat(courseService.detail(course.getId(), prof).assignments()).hasSize(1);   // 해킹 과제가 생기지 않았다
    }

    @Test
    void 수강하지_않은_학생과_교수는_남의_강의에_제출할수_없다() throws Exception {
        for (AppUser who : List.of(outsider, prof, otherProf)) {
            mvc.perform(multipart(("/assignments/" + assignment.getId()) + "/submit")
                            .file(new MockMultipartFile("files", "A.java", "text/plain", "x".getBytes(StandardCharsets.UTF_8)))
                            .with(user(who)).with(csrf()))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/assignments/" + assignment.getId() + "/submit/my-branch")
                            .with(user(who)).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        assertThat(submissionService.history(assignment.getId(), outsider.getId(), prof).versions()).isEmpty();
    }

    @Test
    void 다른_학생의_제출을_내_이력_주소에_끼워도_비교되지_않는다() throws Exception {
        Long other = submissionService.history(assignment.getId(), classmate.getId(), prof).versions().get(0).id();
        mvc.perform(get("/assignments/" + assignment.getId() + "/students/" + stu.getId() + "/diff")
                        .param("from", String.valueOf(submissionId)).param("to", String.valueOf(other)).with(user(prof)))
                .andExpect(status().is3xxRedirection());   // BusinessException → 이전 화면으로 + 오류 메시지
    }
}
