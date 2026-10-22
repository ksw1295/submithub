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

/**
 * 가짜 채점 실행기(FakeGradingRunner)를 쓰는 통합 테스트용. 같은 컨텍스트를 공유하고 별도의 메모리 DB 를 쓴다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:grading-test;MODE=MariaDB;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import({TestData.class, GradingTest.FakeGradingConfig.class})
public @interface GradingTest {

    @TestConfiguration
    class FakeGradingConfig {
        @Bean
        @Primary
        public FakeGradingRunner fakeGradingRunner() {
            return new FakeGradingRunner();
        }
    }
}
