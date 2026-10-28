package com.submithub;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 운영(prod) 프로필 시작 시 보안 및 필수 설정 검증.
 * 데이터베이스나 다른 빈이 초기화되기 전에 교수 가입 코드가 비어 있는지 검사하여,
 * 비어 있으면 명확한 오류 메시지와 함께 서버 시작을 차단한다.
 */
@Component
@Profile("prod")
public class ProdProfileValidator implements BeanFactoryPostProcessor, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        String code = environment.getProperty("submithub.professor-signup-code");
        if (code == null || code.isBlank()) {
            throw new IllegalStateException(
                    "운영 환경(prod)에서는 교수 가입 코드(submithub.professor-signup-code) 설정이 필수입니다. " +
                    "환경변수 PROFESSOR_SIGNUP_CODE 또는 submithub.professor-signup-code 설정을 지정하세요."
            );
        }
    }
}
