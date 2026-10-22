package com.submithub.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 가짜 GitHub 클라이언트(FakeGitHubClient)와 가짜 채점 실행기를 쓰는 통합 테스트용. 별도의 메모리 DB 를 쓴다. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:github-test;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "submithub.storage-dir=./target/github-test-uploads"})
@AutoConfigureMockMvc
@Import({TestData.class, GitHubTest.FakeGitHubConfig.class})
public @interface GitHubTest {

    @TestConfiguration
    class FakeGitHubConfig {
        @Bean
        @Primary
        public FakeGitHubClient fakeGitHubClient() {
            return new FakeGitHubClient();
        }

        @Bean
        @Primary
        public FakeGradingRunner fakeGradingRunner() {
            return new FakeGradingRunner();
        }
    }
}
