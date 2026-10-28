package com.submithub.demo;

import com.submithub.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 시연 데이터 매일 초기화 스케줄러.
 * submithub.demo.reset-cron 설정이 켜져 있을 때 정해진 시각에 초기화를 실행한다.
 */
@Component
@ConditionalOnExpression("!'${submithub.demo.reset-cron:}'.isBlank() && !'${submithub.demo.reset-cron:}'.equals('-')")
public class DemoDataResetScheduler {

    private static final Logger log = LoggerFactory.getLogger(DemoDataResetScheduler.class);

    private final ObjectProvider<DemoDataLoader> loaderProvider;
    private final AppProperties properties;

    public DemoDataResetScheduler(ObjectProvider<DemoDataLoader> loaderProvider, AppProperties properties) {
        this.loaderProvider = loaderProvider;
        this.properties = properties;
    }

    @Scheduled(cron = "${submithub.demo.reset-cron:-}")
    public void scheduleReset() {
        String cron = properties.demo() != null ? properties.demo().resetCron() : null;
        if (cron == null || cron.isBlank() || "-".equals(cron)) {
            return;
        }
        DemoDataLoader loader = loaderProvider.getIfAvailable();
        if (loader == null) {
            log.warn("시연 데이터 초기화 스케줄이 실행되었으나 DemoDataLoader 빈이 비활성화 상태입니다 (submithub.demo-data=false).");
            return;
        }
        log.info("시연 데이터 정기 초기화 스케줄 실행 (cron: {})", cron);
        loader.resetDemoData();
    }
}
