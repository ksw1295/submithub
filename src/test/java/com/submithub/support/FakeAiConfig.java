package com.submithub.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 실제 Gemini 대신 가짜 LLM 을 쓰게 하는 테스트 설정. 이 설정을 Import 한 테스트만 가짜를 쓴다. */
@TestConfiguration
public class FakeAiConfig {

    @Bean
    @Primary
    public FakeJudgeProvider fakeJudgeProvider() {
        return new FakeJudgeProvider();
    }
}
