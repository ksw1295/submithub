package com.submithub.security;

import com.submithub.AppProperties;
import com.submithub.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "submithub.demo-data=false",
        "spring.datasource.url=jdbc:h2:mem:ratelimit-test;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "submithub.rate-limit.login-per-minute=10",
        "submithub.rate-limit.submit-per-minute=5",
        "submithub.rate-limit.ai-judge-per-minute=3"
})
@AutoConfigureMockMvc
@Import(TestData.class)
class RateLimitTest {

    @Autowired MockMvc mvc;
    @Autowired RateLimiterService rateLimiterService;
    @Autowired AppProperties properties;
    @Autowired TestData data;

    @BeforeEach
    void setUp() {
        rateLimiterService.reset();
    }

    @Test
    void loginRateLimitRejectsAfterTenAttemptsPerIp() throws Exception {
        String clientIp = "192.168.1.100";

        // 10회 로그인 시도는 허용 (자격 증명 불일치로 302 리다이렉트)
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/login")
                            .with(csrf())
                            .with(req -> { req.setRemoteAddr(clientIp); return req; })
                            .param("username", "testuser")
                            .param("password", "wrongpassword"))
                    .andExpect(status().is3xxRedirection());
        }

        // 11번째 시도는 429 Too Many Requests 반환
        mvc.perform(post("/login")
                        .with(csrf())
                        .with(req -> { req.setRemoteAddr(clientIp); return req; })
                        .param("username", "testuser")
                        .param("password", "wrongpassword"))
                .andExpect(status().is(429));
    }

    @Test
    void submissionRateLimitRejectsAfterFiveAttemptsPerUser() throws Exception {
        AppUser student = data.student();

        // 5회 제출 시도는 통과 (파라미터 누락이어도 429는 아님)
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/assignments/1/submit")
                            .with(user(student))
                            .with(csrf()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(429));
        }

        // 6번째 제출 시도는 429 반환
        mvc.perform(post("/assignments/1/submit")
                        .with(user(student))
                        .with(csrf()))
                .andExpect(status().is(429));
    }

    @Test
    void aiJudgeRateLimitRejectsAfterThreeAttemptsPerUser() throws Exception {
        AppUser prof = data.professor();

        // 3회 AI 판정 요청 통과
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/assignments/1/students/1/ai-judge")
                            .with(user(prof))
                            .with(csrf()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(429));
        }

        // 4번째 요청은 429 반환
        mvc.perform(post("/assignments/1/students/1/ai-judge")
                        .with(user(prof))
                        .with(csrf()))
                .andExpect(status().is(429));
    }

    @Test
    void trustedProxyUsesXForwardedForWhileUntrustedIgnoresIt() {
        // 1) 신뢰하는 프록시(127.0.0.1)에서 보낸 X-Forwarded-For 채택
        MockHttpServletRequest trustedReq = new MockHttpServletRequest();
        trustedReq.setRemoteAddr("127.0.0.1");
        trustedReq.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");
        assertThat(rateLimiterService.resolveClientIp(trustedReq)).isEqualTo("203.0.113.10");

        // 2) 신뢰하지 않는 외부 IP(203.0.113.50)에서 변조한 X-Forwarded-For 는 무시하고 원격 IP 채택
        MockHttpServletRequest untrustedReq = new MockHttpServletRequest();
        untrustedReq.setRemoteAddr("203.0.113.50");
        untrustedReq.addHeader("X-Forwarded-For", "1.1.1.1");
        assertThat(rateLimiterService.resolveClientIp(untrustedReq)).isEqualTo("203.0.113.50");
    }
}
