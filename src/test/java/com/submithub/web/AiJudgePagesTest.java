package com.submithub.web;

import com.submithub.domain.AiDecision;
import com.submithub.domain.AiVerdict;
import com.submithub.domain.Assignment;
import com.submithub.domain.Course;
import com.submithub.security.AppUser;
import com.submithub.service.AiJudgeService;
import com.submithub.service.CourseService;
import com.submithub.service.FeedbackService;
import com.submithub.service.ReviewService;
import com.submithub.service.SubmissionService;
import com.submithub.storage.UploadProcessor.Upload;
import com.submithub.support.AiTest;
import com.submithub.support.FakeJudgeProvider;
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

@AiTest
class AiJudgePagesTest {

    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired FakeJudgeProvider fake;
    @Autowired AiJudgeService aiJudge;
    @Autowired CourseService courseService;
    @Autowired SubmissionService submissionService;
    @Autowired ReviewService reviewService;
    @Autowired FeedbackService feedbackService;

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
        String v1 = "class A {\n  void f() {\n    int x = 1;\n    int y = 2;\n  }\n}\n";
        var s1 = submissionService.submit(assignment.getId(), stu, List.of(new Upload("A.java", v1.getBytes(StandardCharsets.UTF_8))), null);
        reviewService.addComment(submissionService.view(s1.id(), null, prof).selectedFileId(), 4, 4, "y 를 쓰기 전에 검사하세요", prof);
        submissionService.submit(assignment.getId(), stu, List.of(new Upload("A.java",
                "class A {\n  void f() {\n    int x = 1;\n    int y = x + 2;\n  }\n}\n".getBytes(StandardCharsets.UTF_8))), null);
    }

    String body(RequestPostProcessor who, String url) throws Exception {
        return mvc.perform(get(url).with(who)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    String history() {
        return "/assignments/" + assignment.getId() + "/students/" + stu.getId();
    }

    @Test
    void 키가_없으면_AI_관련_화면이_보이지_않는다() throws Exception {
        fake.available = false;
        String courseProf = body(user(prof), "/courses/" + course.getId());
        assertThat(courseProf).doesNotContain("AI 반영 판정").doesNotContain("aiSettings");
        assertThat(body(user(prof), history())).doesNotContain("AI 판정 실행").doesNotContain("AI 판정");
        assertThat(body(user(stu), "/courses/" + course.getId())).doesNotContain("AI 반영 판정");
    }

    @Test
    void 키가_있으면_교수에게_설정_카드와_전송_안내가_보이고_기본은_꺼짐이다() throws Exception {
        String page = body(user(prof), "/courses/" + course.getId());
        assertThat(page).contains("AI 반영 판정").contains("꺼짐").contains("외부 AI 서비스로 전송됩니다");
        assertThat(body(user(stu), "/courses/" + course.getId())).doesNotContain("AI 반영 판정");     // 꺼져 있으면 학생에게 안내 없음
        assertThat(body(user(prof), history())).doesNotContain("AI 판정 실행");                        // 꺼져 있으면 버튼 없음
    }

    @Test
    void 켜면_학생에게도_안내가_보이고_교수_화면에_실행_버튼이_생긴다() throws Exception {
        mvc.perform(post("/courses/" + course.getId() + "/ai-judge").with(user(prof)).with(csrf()).param("enabled", "true"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/courses/" + course.getId()));

        assertThat(body(user(prof), "/courses/" + course.getId())).contains("사용 중");
        String studentPage = body(user(stu), "/courses/" + course.getId());
        assertThat(studentPage).contains("AI 반영 판정").contains("외부 AI 서비스(Google Gemini)로 전송되어");
        assertThat(body(user(prof), history())).contains("AI 판정 실행");
        assertThat(body(user(stu), "/assignments/" + assignment.getId())).doesNotContain("AI 판정 실행");

        mvc.perform(post("/courses/" + course.getId() + "/ai-judge").with(user(prof)).with(csrf()).param("enabled", "false"))
                .andExpect(status().is3xxRedirection());
        assertThat(body(user(stu), "/courses/" + course.getId())).doesNotContain("AI 반영 판정");
    }

    @Test
    void 설정_실행_수락_요청은_담당_교수만_할수_있다() throws Exception {
        for (AppUser who : List.of(stu, otherProf)) {
            mvc.perform(post("/courses/" + course.getId() + "/ai-judge").with(user(who)).with(csrf()).param("enabled", "true"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/assignments/" + assignment.getId() + "/students/" + stu.getId() + "/ai-judge").with(user(who)).with(csrf()))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/ai-judgements/1/decision").with(user(who)).with(csrf()).param("decision", "ACCEPTED"))
                    .andExpect(status().isForbidden());
        }
        // 강의가 꺼져 있으면 교수의 실행 요청도 오류 메시지로 돌려보낸다
        mvc.perform(post("/assignments/" + assignment.getId() + "/students/" + stu.getId() + "/ai-judge").with(user(prof)).with(csrf())
                        .header("Referer", "http://localhost" + history()).param("back", history()))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
    }

    @Test
    void AI_판정은_교수에게_전부_보이고_학생에게는_수락한_것만_보인다() throws Exception {
        aiJudge.setEnabled(course.getId(), true, prof);
        fake.always(AiVerdict.PARTIAL, "x 만 쓰고 음수 경우는 막지 않았습니다.");
        aiJudge.judgeStudent(assignment.getId(), stu.getId());
        Long jid = feedbackService.forStudent(assignment.getId(), stu.getId(), prof).items().get(0).aiJudgementId();

        String profPage = body(user(prof), history());
        assertThat(profPage).contains("AI 판정").contains("부분 해결").contains("음수 경우는").contains("교수 확인 대기")
                .contains("수락").contains("무시");
        assertThat(body(user(stu), "/assignments/" + assignment.getId())).doesNotContain("부분 해결").doesNotContain("음수 경우는");

        mvc.perform(post("/ai-judgements/" + jid + "/decision").with(user(prof)).with(csrf())
                        .param("decision", "ACCEPTED").param("back", history()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(history()));

        String studentPage = body(user(stu), "/assignments/" + assignment.getId());
        assertThat(studentPage).contains("AI 판정").contains("부분 해결").contains("교수가 수락함")
                .contains("남은 피드백으로 셉니다").contains("남은 피드백 1개");
        assertThat(studentPage).doesNotContain(">수락<").doesNotContain(">무시<");         // 학생에게는 버튼이 없다

        mvc.perform(post("/ai-judgements/" + jid + "/decision").with(user(prof)).with(csrf()).param("decision", "IGNORED"))
                .andExpect(status().is3xxRedirection());
        assertThat(body(user(stu), "/assignments/" + assignment.getId())).doesNotContain("부분 해결").contains("남은 피드백 0개");
        mvc.perform(post("/ai-judgements/" + jid + "/decision").with(user(prof)).with(csrf()).param("decision", "BOGUS"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
    }

    @Test
    void 판정_실패는_화면에_판정_실패로_표시되고_수락_버튼이_없다() throws Exception {
        aiJudge.setEnabled(course.getId(), true, prof);
        fake.failWith("AI 응답 시간이 초과되었습니다 (10초).");
        aiJudge.judgeStudent(assignment.getId(), stu.getId());

        String page = body(user(prof), history());
        assertThat(page).contains("판정 실패").contains("시간이 초과");
        assertThat(page).doesNotContain("교수 확인 대기");
        assertThat(feedbackService.forStudent(assignment.getId(), stu.getId(), prof).items().get(0).aiDecision()).isEqualTo(AiDecision.PENDING.name());
        assertThat(body(user(stu), "/assignments/" + assignment.getId())).doesNotContain("판정 실패");
    }
}
