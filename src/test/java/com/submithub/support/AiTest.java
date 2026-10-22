package com.submithub.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 가짜 LLM(FakeJudgeProvider)을 쓰는 통합 테스트용. 이 어노테이션이 붙은 클래스들은 같은 스프링 컨텍스트를 공유하고,
 * 다른 테스트와 DB 가 섞이지 않도록 별도의 메모리 DB 를 쓴다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:ai-test;MODE=MariaDB;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import({TestData.class, FakeAiConfig.class})
public @interface AiTest {
}
