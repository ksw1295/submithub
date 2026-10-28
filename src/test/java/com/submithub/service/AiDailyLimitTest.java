package com.submithub.service;

import com.submithub.domain.AiDailyUsage;
import com.submithub.domain.AiDailyUsageRepository;
import com.submithub.domain.AiJudgementRepository;
import com.submithub.domain.AiVerdict;
import com.submithub.domain.Assignment;
import com.submithub.domain.Course;
import com.submithub.security.AppUser;
import com.submithub.storage.UploadProcessor.Upload;
import com.submithub.support.FakeJudgeProvider;
import com.submithub.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@com.submithub.support.AiTest
@SpringBootTest(properties = {
        "submithub.ai.daily-limit=1",
        "spring.datasource.url=jdbc:h2:mem:ai-daily-test;MODE=MariaDB;DB_CLOSE_DELAY=-1"
})
class AiDailyLimitTest {

    @Autowired TestData data;
    @Autowired FakeJudgeProvider fake;
    @Autowired AiJudgeService aiJudge;
    @Autowired SubmissionService submissionService;
    @Autowired CourseService courseService;
    @Autowired ReviewService reviewService;
    @Autowired AiJudgementRepository judgements;
    @Autowired AiDailyUsageRepository dailyUsages;

    AppUser prof, stu;
    Course course;
    Assignment assignment;

    @BeforeEach
    void setUp() {
        dailyUsages.deleteAll();
        fake.reset();
        prof = data.professor();
        stu = data.student();
        course = data.course(prof);
        courseService.join(stu, course.getInviteCode());
        assignment = data.assignment(course, prof, Duration.ofDays(2), false, null);
    }

    @Test
    void whenDailyLimitExceededDoesNotCallLlmAndRecordsFailureReason() {
        // v1 제출
        var v1 = submissionService.submit(assignment.getId(), stu,
                List.of(new Upload("Main.java", "int a = 1;\nint b = 2;\n".getBytes(StandardCharsets.UTF_8))), "v1");
        Long fileId = submissionService.view(v1.id(), null, prof).selectedFileId();
        // 코멘트 작성
        reviewService.addComment(fileId, 2, 2, "b 변수를 수정하세요", prof);

        // v2 제출 (줄 2 수정 -> MODIFIED -> AI 판정 대상)
        submissionService.submit(assignment.getId(), stu,
                List.of(new Upload("Main.java", "int a = 1;\nlong b = 2;\n".getBytes(StandardCharsets.UTF_8))), "v2");

        // DB에 오늘 호출 횟수를 이미 한도(1)에 도달한 상태로 기록
        dailyUsages.save(new AiDailyUsage(LocalDate.now(), 1));

        // 판정 실행
        int done = aiJudge.judgeStudent(assignment.getId(), stu.getId());
        assertThat(done).isEqualTo(1);

        // LLM 은 호출되지 않았어야 함
        assertThat(fake.calls()).isEqualTo(0);

        // 저장된 판정의 이유가 "오늘 AI 판정 한도 초과" 이어야 함
        var allJudgements = judgements.findAll();
        assertThat(allJudgements).hasSize(1);
        var j = allJudgements.get(0);
        assertThat(j.getVerdict()).isEqualTo(AiVerdict.FAILED);
        assertThat(j.getReason()).isEqualTo("오늘 AI 판정 한도 초과");
    }
}
