package com.submithub;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProdProfileTest {

    @Test
    void prodProfileFailsToStartWithoutProfessorSignupCode() {
        var thrown = assertThrows(Exception.class, () -> {
            new SpringApplicationBuilder(SubmitHubApplication.class)
                    .profiles("prod")
                    .properties(
                            "server.port=0",
                            "spring.datasource.url=jdbc:h2:mem:prodtest1;MODE=MariaDB;DB_CLOSE_DELAY=-1",
                            "spring.datasource.driver-class-name=org.h2.Driver",
                            "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                            "spring.datasource.username=sa",
                            "spring.datasource.password="
                    )
                    .run();
        });

        Throwable cause = thrown;
        boolean foundMessage = false;
        while (cause != null) {
            if (cause.getMessage() != null && cause.getMessage().contains("교수 가입 코드")) {
                foundMessage = true;
                break;
            }
            cause = cause.getCause();
        }
        assertThat(foundMessage)
                .withFailMessage("Expected failure cause containing '교수 가입 코드' but got: " + thrown)
                .isTrue();
    }

    @Test
    void prodProfileStartsSuccessfullyWithProfessorSignupCode() {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(SubmitHubApplication.class)
                .profiles("prod")
                .properties(
                        "server.port=0",
                        "spring.datasource.url=jdbc:h2:mem:prodtest2;MODE=MariaDB;DB_CLOSE_DELAY=-1",
                        "spring.datasource.driver-class-name=org.h2.Driver",
                        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                        "spring.datasource.username=sa",
                        "spring.datasource.password="
                )
                .run("--submithub.professor-signup-code=secret-code-1234");

        try {
            assertThat(ctx.isRunning()).isTrue();
            AppProperties props = ctx.getBean(AppProperties.class);
            assertThat(props.professorSignupCode()).isEqualTo("secret-code-1234");
            assertThat(props.demoData()).isFalse();
        } finally {
            ctx.close();
        }
    }
}
