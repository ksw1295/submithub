package com.submithub.web;

import com.submithub.domain.Assignment;
import com.submithub.domain.Course;
import com.submithub.security.AppUser;
import com.submithub.service.CourseService;
import com.submithub.service.ReviewService;
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
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 모든 화면이 실제로 렌더링되는지, 폼 처리 후 올바른 곳으로 가는지 확인한다 (템플릿 오류 방지). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestData.class)
class PagesRenderTest {

    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired CourseService courseService;
    @Autowired SubmissionService submissionService;
    @Autowired ReviewService reviewService;

    AppUser prof, stu, outsider;
    Course course;
    Assignment assignment;
    SubmissionService.SubmitResult v1, v2;
    Long fileId;

    @BeforeEach
    void setUp() {
        prof = data.professor();
        stu = data.student();
        outsider = data.student();
        course = data.course(prof);
        courseService.join(stu, course.getInviteCode());
        assignment = data.assignment(course, prof, Duration.ofDays(2), true, null);
        v1 = submissionService.submit(assignment.getId(), stu,
                List.of(new Upload("Main.java", "public class Main {\n    int a;\n    int b;\n}\n".getBytes(StandardCharsets.UTF_8))), "처음");
        v2 = submissionService.submit(assignment.getId(), stu,
                List.of(new Upload("Main.java", "public class Main {\n    int a;\n    long b;\n    int c;\n}\n".getBytes(StandardCharsets.UTF_8))), "수정");
        fileId = submissionService.view(v2.id(), null, prof).selectedFileId();
    }

    String body(org.springframework.test.web.servlet.request.RequestPostProcessor u, String url) throws Exception {
        MvcResult r = mvc.perform(get(url).with(u)).andExpect(status().isOk()).andReturn();
        return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void 교수_화면들이_렌더링된다() throws Exception {
        var p = user(prof);
        assertThat(body(p, "/courses")).contains("자료구조");
        assertThat(body(p, "/courses/" + course.getId())).contains(course.getInviteCode());
        assertThat(body(p, "/assignments/" + assignment.getId())).contains("제출 현황").contains("이력 보기");
        assertThat(body(p, "/assignments/" + assignment.getId() + "/edit")).contains("뼈대 코드");
        assertThat(body(p, "/courses/" + course.getId() + "/assignments/new")).contains("마감 일시");
        assertThat(body(p, "/assignments/" + assignment.getId() + "/students/" + stu.getId())).contains("버전 비교").contains("v2");
        String view = body(p, "/submissions/" + v2.id());
        assertThat(view).contains("점수 저장").contains("<template").contains("data-pick=\"2\"").contains("이전 버전과 비교");
    }

    @Test
    void 학생_화면들이_렌더링된다() throws Exception {
        var s = user(stu);
        assertThat(body(s, "/courses")).contains("자료구조");
        String course = body(s, "/courses/" + this.course.getId());
        assertThat(course).doesNotContain(this.course.getInviteCode()).contains("제출 완료");
        assertThat(body(s, "/assignments/" + assignment.getId())).contains("제출하기").contains("내 제출 이력");
        String view = body(s, "/submissions/" + v1.id());
        assertThat(view).doesNotContain("점수 저장").doesNotContain("<template");
        assertThat(body(s, "/courses/join")).contains("초대 코드");
    }

    @Test
    void diff_화면_모든_옵션_조합이_렌더링된다() throws Exception {
        String base = "/assignments/" + assignment.getId() + "/students/" + stu.getId() + "/diff";
        for (var u : List.of(user(prof), user(stu))) {
            for (String mode : List.of("split", "unified")) {
                for (String ws : List.of("0", "1")) {
                    for (String fold : List.of("0", "1")) {
                        String html = body(u, base + "?from=" + v1.id() + "&to=" + v2.id() + "&mode=" + mode + "&ws=" + ws
                                + "&fold=" + fold + "&file=Main.java");
                        assertThat(html).contains("long b;").contains("버전 비교");
                    }
                }
            }
        }
        assertThat(body(user(prof), base)).contains("Main.java");     // 기본값 (인자 없이)
        // 추가/삭제 줄에 색 클래스가 실제로 붙는다 (통합·분할 모두)
        for (String mode : List.of("split", "unified")) {
            String html = body(user(prof), base + "?from=" + v1.id() + "&to=" + v2.id() + "&mode=" + mode);
            assertThat(html).as(mode).contains("diff-add").contains("diff-del");
        }
    }

    @Test
    void 코멘트_작성_답글_점수_폼이_처리되고_원래_화면으로_돌아간다() throws Exception {
        String back = "/submissions/" + v2.id() + "?file=" + fileId;
        MvcResult r = mvc.perform(post("/files/" + fileId + "/comments").with(user(prof)).with(csrf())
                        .param("startLine", "2").param("endLine", "3").param("body", "타입을 확인하세요").param("back", back))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertThat(r.getResponse().getRedirectedUrl()).startsWith(back + "#c");

        var thread = reviewService.threads(fileId, prof).get(0);
        mvc.perform(post("/comments/" + thread.id() + "/replies").with(user(stu)).with(csrf())
                        .param("body", "고쳤습니다").param("back", back))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/comments/" + thread.id() + "/resolve").with(user(stu)).with(csrf()).param("back", back))
                .andExpect(status().is3xxRedirection());

        String html = body(user(stu), "/submissions/" + v2.id());
        assertThat(html).contains("타입을 확인하세요").contains("고쳤습니다").contains("수정함으로 표시").contains("줄 2~3");
        assertThat(body(user(prof), "/assignments/" + assignment.getId() + "/students/" + stu.getId() + "/diff?from="
                + v1.id() + "&to=" + v2.id() + "&file=Main.java")).contains("타입을 확인하세요");

        mvc.perform(post("/submissions/" + v2.id() + "/grade").with(user(prof)).with(csrf())
                        .param("score", "88").param("summary", "잘했습니다").param("back", back))
                .andExpect(redirectedUrl(back));
        assertThat(body(user(stu), "/submissions/" + v2.id())).contains("88점").contains("잘했습니다");
    }

    @Test
    void 반영_추적_화면이_렌더링된다() throws Exception {
        Long f1 = submissionService.view(v1.id(), null, prof).selectedFileId();
        reviewService.addComment(f1, 2, 2, "a 는 그대로 둔 줄", prof);       // v2 에서도 그대로 → 미반영
        reviewService.addComment(f1, 3, 3, "b 의 타입을 바꾸세요", prof);     // v2 에서 int→long → 반영됨

        String student = body(user(stu), "/assignments/" + assignment.getId());
        assertThat(student).contains("남은 피드백 1개").contains("미반영").contains("반영됨").contains("반영률 50%");
        assertThat(student).contains("최신 v2 2줄");

        String v1Page = body(user(stu), "/submissions/" + v1.id());
        assertThat(v1Page).contains("미반영").contains("에 그대로 있음").contains("v2에서 수정됨");

        String prof = body(user(this.prof), "/assignments/" + assignment.getId());
        assertThat(prof).contains("피드백 반영").contains("50%").contains("표절 검사");
        String history = body(user(this.prof), "/assignments/" + assignment.getId() + "/students/" + stu.getId());
        assertThat(history).contains("피드백 반영 현황").contains("반영률 50%");
    }

    @Test
    void 새_코멘트만_있는_학생은_제출_현황과_통계_화면에서_똑같이_표기된다() throws Exception {
        // 최신 버전(v2)에만 코멘트 1개 → 아직 고칠 기회가 없는 새 코멘트
        reviewService.addComment(fileId, 1, 1, "새 코멘트", prof);
        String statusPage = body(user(prof), "/assignments/" + assignment.getId());
        String statsPage = body(user(prof), "/courses/" + course.getId() + "/stats");
        for (String page : List.of(statusPage, statsPage)) {
            assertThat(page).contains("새 코멘트 1개").doesNotContain("미반영 0 / 전체").doesNotContain("미반영 0 / 추적");
        }
        // 추적 대상 코멘트가 있는 학생이 없으므로 평균 카드에는 그 안내가 나온다
        assertThat(statsPage).contains("추적 대상 코멘트가 있는 학생이 없습니다");

        // v1 에 단 코멘트(v2 에서 그대로) → 반영률 0%, 두 화면 모두 같은 형식
        Long f1 = submissionService.view(v1.id(), null, prof).selectedFileId();
        reviewService.addComment(f1, 1, 1, "첫 줄", prof);
        statusPage = body(user(prof), "/assignments/" + assignment.getId());
        statsPage = body(user(prof), "/courses/" + course.getId() + "/stats");
        for (String page : List.of(statusPage, statsPage)) {
            assertThat(page).contains("0%").contains("미반영 1 / 추적 1, 새 코멘트 1개");
        }
        assertThat(statsPage).contains("추적 대상 코멘트가 있는 학생 1명 기준");
    }

    @Test
    void 표절_검사_화면이_렌더링되고_학생은_접근할수_없다() throws Exception {
        String code = "import java.util.*;\npublic class Main {\n    public static void main(String[] a) {\n"
                + "        int total = 0;\n        for (int i = 0; i < 10; i++) {\n            if (i % 2 == 0) { total += i; } else { total -= 1; }\n"
                + "        }\n        System.out.println(total);\n        List<Integer> xs = new ArrayList<>();\n"
                + "        for (int i = 0; i < 5; i++) { xs.add(i * i); }\n        System.out.println(xs);\n    }\n}\n";
        AppUser other = data.student();
        courseService.join(other, course.getInviteCode());
        submissionService.submit(assignment.getId(), other, List.of(new Upload("Main.java",
                code.replace("total", "sum").replace("xs", "list").getBytes(StandardCharsets.UTF_8))), null);
        submissionService.submit(assignment.getId(), stu, List.of(new Upload("Main.java", code.getBytes(StandardCharsets.UTF_8))), "코드");

        String index = "/assignments/" + assignment.getId() + "/similarity";
        assertThat(body(user(prof), index)).contains("아직 검사를 실행하지 않았습니다").contains("검사 실행");

        MvcResult run = mvc.perform(post(index).with(user(prof)).with(csrf()).param("percent", "60"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(index)).andReturn();
        assertThat(run.getFlashMap().get("message").toString()).contains("의심 쌍");

        String result = body(user(prof), index);
        assertThat(result).contains("유사도 순위").contains("나란히 비교");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("/similarity/pairs/(\\d+)").matcher(result);
        assertThat(m.find()).isTrue();
        String pair = body(user(prof), "/similarity/pairs/" + m.group(1));
        assertThat(pair).contains("class=\"matched\"").contains("유사도");

        for (String url : List.of(index, "/similarity/pairs/" + m.group(1))) {
            mvc.perform(get(url).with(user(stu))).andExpect(status().isForbidden());
        }
        mvc.perform(post(index).with(user(stu)).with(csrf()).param("percent", "60")).andExpect(status().isForbidden());
        mvc.perform(post(index).with(user(prof)).with(csrf()).param("percent", "abc"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
    }

    @Test
    void 잘못된_점수_입력은_오류_메시지로_돌려보낸다() throws Exception {
        for (String bad : List.of("abc", "101", "-5", "")) {
            mvc.perform(post("/submissions/" + v1.id() + "/grade").with(user(prof)).with(csrf())
                            .header("Referer", "http://localhost/submissions/" + v1.id())
                            .param("score", bad).param("back", "/submissions/" + v1.id()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/submissions/" + v1.id()))
                    .andExpect(flash().attributeExists("error"));
        }
        assertThat(submissionService.view(v1.id(), null, prof).grade()).isNull();
    }

    @Test
    void back_파라미터로_외부_사이트로_보낼수_없다() throws Exception {
        for (String evil : List.of("https://evil.example/x", "//evil.example/x", "/\\evil.example", "javascript:alert(1)")) {
            MvcResult r = mvc.perform(post("/files/" + fileId + "/comments").with(user(prof)).with(csrf())
                    .param("startLine", "1").param("endLine", "1").param("body", "x").param("back", evil)).andReturn();
            assertThat(r.getResponse().getRedirectedUrl()).as(evil).startsWith("/#c");
        }
    }

    @Test
    void 권한_없는_화면은_403_없는_화면은_404() throws Exception {
        var o = user(outsider);
        mvc.perform(get("/courses/" + course.getId()).with(o)).andExpect(status().isForbidden());
        mvc.perform(get("/assignments/" + assignment.getId()).with(o)).andExpect(status().isForbidden());
        mvc.perform(get("/submissions/" + v1.id()).with(o)).andExpect(status().isForbidden());
        mvc.perform(get("/assignments/" + assignment.getId() + "/students/" + stu.getId()).with(o)).andExpect(status().isForbidden());
        mvc.perform(get("/assignments/" + assignment.getId() + "/students/" + stu.getId() + "/diff").with(o)).andExpect(status().isForbidden());
        mvc.perform(get("/files/" + fileId + "/download").with(o)).andExpect(status().isForbidden());
        mvc.perform(post("/files/" + fileId + "/comments").with(o).with(csrf())
                .param("startLine", "1").param("endLine", "1").param("body", "x")).andExpect(status().isForbidden());
        mvc.perform(post("/submissions/" + v1.id() + "/grade").with(user(stu)).with(csrf()).param("score", "100"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/submissions/999999").with(user(prof))).andExpect(status().isNotFound());
        mvc.perform(get("/courses/new").with(user(stu))).andExpect(status().isForbidden());
        mvc.perform(get("/courses/join").with(user(prof))).andExpect(status().isForbidden());
    }

    @Test
    void 학생이_폼으로_제출하면_새_버전이_생긴다() throws Exception {
        MockMultipartFile f = new MockMultipartFile("files", "Main.java", "text/plain",
                "class Main {}\n".getBytes(StandardCharsets.UTF_8));
        MvcResult r = mvc.perform(multipart("/assignments/" + assignment.getId() + "/submit").file(f)
                        .param("message", "폼 제출").with(user(stu)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertThat(r.getResponse().getRedirectedUrl()).startsWith("/submissions/");
        assertThat(submissionService.history(assignment.getId(), stu.getId(), stu).versions()).hasSize(3);

        // 파일 없이 제출하면 오류 메시지와 함께 돌려보낸다
        mvc.perform(multipart("/assignments/" + assignment.getId() + "/submit").param("message", "x")
                        .header("Referer", "http://localhost/assignments/" + assignment.getId()).with(user(stu)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
        assertThat(submissionService.history(assignment.getId(), stu.getId(), stu).versions()).hasSize(3);
    }

    @Test
    void 비로그인_사용자는_홈_소개화면이_보이고_로그인_사용자는_courses로_이동한다() throws Exception {
        // 비로그인 방문자: 소개 랜딩 페이지 렌더링
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("과제 제출도 Git처럼")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("로그인하고 시작하기")));

        // 별도 분리된 로그인 화면 렌더링
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("로그인")));

        // 로그인 사용자: 내 강의(/courses)로 리다이렉트
        mvc.perform(get("/").with(user(prof)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/courses"));
    }

    @Autowired com.submithub.service.TeamService teamService;

    @Test
    void 조별_과제_팀_목록과_기여도_감사_화면이_정상_렌더링된다() throws Exception {
        // 교수, 학생 둘 다 팀 목록 접근 가능
        mvc.perform(get("/assignments/" + assignment.getId() + "/teams").with(user(prof)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("조별 과제 / 팀 관리")));

        // 학생이 팀 생성
        com.submithub.domain.Team team = teamService.createTeam(assignment.getId(), "테스트조", "owner/repo", stu.getId(), "main", "팀장");

        // 팀 감사(Audit) 화면 렌더링
        mvc.perform(get("/teams/" + team.getId() + "/audit").with(user(prof)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("브랜치 기여도 감사")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("팀원별 실질 유효 코드 점유율")));
    }
}
