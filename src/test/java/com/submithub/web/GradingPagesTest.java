package com.submithub.web;

import com.submithub.domain.Assignment;
import com.submithub.domain.Course;
import com.submithub.domain.GradingLanguage;
import com.submithub.security.AppUser;
import com.submithub.service.CourseService;
import com.submithub.service.GradingService;
import com.submithub.service.GradingService.CaseInput;
import com.submithub.service.GradingService.ConfigInput;
import com.submithub.service.SubmissionService;
import com.submithub.storage.UploadProcessor.Upload;
import com.submithub.support.FakeGradingRunner;
import com.submithub.support.GradingTest;
import com.submithub.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@GradingTest
class GradingPagesTest {

    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired FakeGradingRunner fake;
    @Autowired GradingService grading;
    @Autowired CourseService courseService;
    @Autowired SubmissionService submissionService;

    AppUser prof, otherProf, stu;
    Course course;
    Assignment assignment;

    @BeforeEach
    void setUp() {
        fake.reset();
        prof = data.professor();
        otherProf = data.professor();
        stu = data.student();
        course = data.course(prof);
        courseService.join(stu, course.getInviteCode());
        assignment = data.assignment(course, prof, Duration.ofDays(2), false, null);
    }

    String body(RequestPostProcessor who, String url) throws Exception {
        return mvc.perform(get(url).with(who)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    void enable() {
        grading.saveConfig(assignment.getId(), prof, new ConfigInput(GradingLanguage.JAVA, "Main.java", 5, 256));
        for (int i = 1; i <= 5; i++) {
            grading.addCase(assignment.getId(), prof, new CaseInput("케이스 " + i, String.valueOf(i), "out" + i, i <= 2, 20));
        }
    }

    Long submitAndWait(String level) throws Exception {
        var r = submissionService.submit(assignment.getId(), stu,
                List.of(new Upload("Main.java", ("//LEVEL:" + level + "\nclass Main {}").getBytes(StandardCharsets.UTF_8))), null);
        for (int i = 0; i < 100; i++) {
            var v = grading.forSubmission(r.id(), prof);
            if (v != null && (v.status().equals("DONE") || v.status().equals("ERROR") || v.status().equals("UNAVAILABLE"))) {
                break;
            }
            Thread.sleep(50);
        }
        return r.id();
    }

    @Test
    void 설정_화면은_교수만_열수_있고_실행기_상태를_알려준다() throws Exception {
        String ready = body(user(prof), "/assignments/" + assignment.getId() + "/grading");
        assertThat(ready).contains("자동 채점 설정").contains("채점 실행기를 쓸 수 있습니다").contains("먼저 실행 설정을 저장하세요");

        fake.available = false;
        String down = body(user(prof), "/assignments/" + assignment.getId() + "/grading");
        assertThat(down).contains("채점 사용 불가").contains("Docker").contains("나머지 기능은 그대로 동작합니다");

        mvc.perform(get("/assignments/" + assignment.getId() + "/grading").with(user(stu))).andExpect(status().isForbidden());
        mvc.perform(get("/assignments/" + assignment.getId() + "/grading").with(user(otherProf))).andExpect(status().isForbidden());
        mvc.perform(get("/assignments/" + assignment.getId() + "/grading")).andExpect(status().is3xxRedirection());
    }

    @Test
    void 설정과_케이스를_화면_폼으로_등록하고_지울수_있다() throws Exception {
        String url = "/assignments/" + assignment.getId() + "/grading";
        mvc.perform(post(url + "/config").with(user(prof)).with(csrf())
                        .param("language", "JAVA").param("mainFile", "Main.java").param("timeLimitSeconds", "5").param("memoryLimitMb", "256"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(url)).andExpect(flash().attributeExists("message"));
        mvc.perform(post(url + "/cases").with(user(prof)).with(csrf())
                        .param("name", "기본").param("input", "3\n4").param("expected", "7").param("publicCase", "true").param("points", "30"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("message"));

        String page = body(user(prof), url);
        assertThat(page).contains("기본").contains("공개").contains("30점").contains("1개 · 총 30점");
        Long caseId = grading.config(assignment.getId(), prof).cases().get(0).id();

        mvc.perform(post(url + "/cases/" + caseId + "/delete").with(user(prof)).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(grading.config(assignment.getId(), prof).cases()).isEmpty();
    }

    @Test
    void 잘못된_입력은_오류_메시지로_돌려보낸다() throws Exception {
        String url = "/assignments/" + assignment.getId() + "/grading";
        for (var params : List.of(
                new String[]{"JAVA", "Main.java", "abc", "256"},
                new String[]{"JAVA", "../Main.java", "5", "256"},
                new String[]{"RUBY", "Main.java", "5", "256"},
                new String[]{"JAVA", "Main.java", "99", "256"})) {
            mvc.perform(post(url + "/config").with(user(prof)).with(csrf()).header("Referer", "http://localhost" + url)
                            .param("language", params[0]).param("mainFile", params[1]).param("timeLimitSeconds", params[2]).param("memoryLimitMb", params[3]))
                    .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
        }
        assertThat(grading.config(assignment.getId(), prof).configured()).isFalse();
        grading.saveConfig(assignment.getId(), prof, new ConfigInput(GradingLanguage.JAVA, "Main.java", 5, 256));
        mvc.perform(post(url + "/cases").with(user(prof)).with(csrf()).header("Referer", "http://localhost" + url)
                        .param("name", "x").param("expected", "y").param("points", "열"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
    }

    @Test
    void 쓰기_요청은_담당_교수만_할수_있다() throws Exception {
        enable();
        String url = "/assignments/" + assignment.getId() + "/grading";
        Long caseId = grading.config(assignment.getId(), prof).cases().get(0).id();
        for (AppUser who : List.of(stu, otherProf)) {
            mvc.perform(post(url + "/config").with(user(who)).with(csrf()).param("language", "JAVA").param("mainFile", "Main.java")
                    .param("timeLimitSeconds", "5").param("memoryLimitMb", "256")).andExpect(status().isForbidden());
            mvc.perform(post(url + "/cases").with(user(who)).with(csrf()).param("name", "x").param("expected", "y").param("points", "1"))
                    .andExpect(status().isForbidden());
            mvc.perform(post(url + "/cases/" + caseId + "/delete").with(user(who)).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(post(url + "/regrade").with(user(who)).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(post("/submissions/1/regrade").with(user(who)).with(csrf())).andExpect(status().isForbidden());
        }
        assertThat(grading.config(assignment.getId(), prof).cases()).hasSize(5);
    }

    @Test
    void 제출_화면의_채점_결과는_학생에게_비공개_케이스를_숨긴다() throws Exception {
        enable();
        Long id = submitAndWait("1");

        String student = body(user(stu), "/submissions/" + id);
        assertThat(student).contains("자동 채점").contains("1 / 5 통과 (20%)").contains("20 / 100점").contains("비공개 케이스는 통과 여부만");
        assertThat(student).contains("out1").contains("공개");                 // 공개 케이스의 입출력
        assertThat(student).doesNotContain("out3").doesNotContain("out4").doesNotContain("out5");   // 비공개 케이스의 기대 출력
        assertThat(student).doesNotContain("다시 채점");

        String professor = body(user(prof), "/submissions/" + id);
        assertThat(professor).contains("out5").contains("out3").contains("다시 채점").contains("비공개 케이스: 교수에게만 보임");
    }

    @Test
    void 컴파일_오류와_채점_불가와_채점_전을_화면에_알려준다() throws Exception {
        enable();
        var bad = submissionService.submit(assignment.getId(), stu,
                List.of(new Upload("Main.java", "//COMPILE_ERROR\nclass Main {".getBytes(StandardCharsets.UTF_8))), null);
        for (int i = 0; i < 100 && !grading.forSubmission(bad.id(), prof).status().equals("DONE"); i++) {
            Thread.sleep(50);
        }
        String compile = body(user(stu), "/submissions/" + bad.id());
        assertThat(compile).contains("컴파일 오류").contains("컴파일(또는 문법) 오류 메시지").contains("error: &#39;;&#39; expected");      // 컴파일러 메시지는 HTML 이스케이프되어 나온다

        fake.available = false;
        Long unavailable = submitAndWait("5");
        String down = body(user(stu), "/submissions/" + unavailable);
        assertThat(down).contains("채점 불가").contains("Docker");
        assertThat(body(user(stu), "/assignments/" + assignment.getId() + "/students/" + stu.getId())).contains("채점 불가");
    }

    @Test
    void 통과율_추이가_학생_화면_교수_이력_통계에_보인다() throws Exception {
        enable();
        submitAndWait("1");
        submitAndWait("3");
        submitAndWait("5");

        String studentPage = body(user(stu), "/assignments/" + assignment.getId());
        assertThat(studentPage).contains("이 버전 테스트 통과율 변화").contains("20%").contains("60%").contains("100%")
                .contains("첫 채점 대비 ▲ +80%p").contains("제출할 때마다 자동으로 채점됩니다");
        assertThat(studentPage.indexOf("이 버전 테스트 통과율 변화")).as("반영 추적 카드 앞(옆)에 있다")
                .isLessThan(studentPage.indexOf("남은 피드백"));

        String history = body(user(prof), "/assignments/" + assignment.getId() + "/students/" + stu.getId());
        assertThat(history).contains("이 버전 테스트 통과율 변화").contains("<th>자동 채점</th>").contains("5/5 (100%)")
                .contains("3/5 (60%)").contains("1/5 (20%)");

        String stats = body(user(prof), "/courses/" + course.getId() + "/stats");
        assertThat(stats).contains("버전별 테스트 통과율").contains("20% → 60% → 100%").contains("버전별 평균 테스트 통과율")
                .contains("최신 버전 평균 100%").contains("id=\"pass-0\"");
    }

    @Test
    void 채점하지_않는_과제의_화면에는_자동_채점_표시가_없다() throws Exception {
        var r = submissionService.submit(assignment.getId(), stu,
                List.of(new Upload("Main.java", "class Main {}".getBytes(StandardCharsets.UTF_8))), null);
        assertThat(body(user(stu), "/submissions/" + r.id())).doesNotContain("자동 채점");
        assertThat(body(user(stu), "/assignments/" + assignment.getId())).doesNotContain("테스트 통과율").doesNotContain("자동으로 채점");
        assertThat(body(user(prof), "/assignments/" + assignment.getId())).contains("자동 채점").contains("꺼짐");
        assertThat(body(user(prof), "/courses/" + course.getId() + "/stats")).doesNotContain("버전별 테스트 통과율");
    }

    @Test
    void 교수는_화면에서_다시_채점할수_있다() throws Exception {
        enable();
        Long id = submitAndWait("2");
        int calls = fake.calls();
        String back = "/submissions/" + id;

        mvc.perform(post("/submissions/" + id + "/regrade").with(user(prof)).with(csrf()).param("back", back))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(back));
        for (int i = 0; i < 100 && fake.calls() == calls; i++) {
            Thread.sleep(50);
        }
        assertThat(fake.calls()).isGreaterThan(calls);

        mvc.perform(post("/assignments/" + assignment.getId() + "/grading/regrade").with(user(prof)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("message"));
        fake.available = false;
        mvc.perform(post("/assignments/" + assignment.getId() + "/grading/regrade").with(user(prof)).with(csrf())
                        .header("Referer", "http://localhost/assignments/" + assignment.getId() + "/grading"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
    }
}
