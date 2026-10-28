package com.submithub;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

/**
 * application.yml 의 submithub.* 설정.
 */
@ConfigurationProperties(prefix = "submithub")
public record AppProperties(String storageDir, boolean demoData, String professorSignupCode,
                            long maxFileBytes, long maxTotalBytes, int maxFiles,
                            Diff diff, RateLimit rateLimit, Ai ai, Demo demo) {

    public record Diff(int maxEditDistance) {
        public Diff {
            if (maxEditDistance <= 0) {
                maxEditDistance = 5000;
            }
        }
    }

    public record RateLimit(int loginPerMinute, int submitPerMinute, int aiJudgePerMinute, List<String> trustedProxies) {
        public RateLimit {
            if (loginPerMinute <= 0) loginPerMinute = 10;
            if (submitPerMinute <= 0) submitPerMinute = 5;
            if (aiJudgePerMinute <= 0) aiJudgePerMinute = 3;
            if (trustedProxies == null) trustedProxies = List.of();
        }
    }

    public record Ai(int dailyLimit) {
        public Ai {
            if (dailyLimit <= 0) dailyLimit = 100;
        }
    }

    public record Demo(String resetCron) {
        public Demo {
            if (resetCron == null) resetCron = "";
        }
    }

    public AppProperties {
        if (storageDir == null || storageDir.isBlank()) {
            storageDir = "./uploads";
        }
        if (maxFileBytes <= 0) {
            maxFileBytes = 5L * 1024 * 1024;
        }
        if (maxTotalBytes <= 0) {
            maxTotalBytes = 20L * 1024 * 1024;
        }
        if (maxFiles <= 0) {
            maxFiles = 200;
        }
        if (professorSignupCode == null) {
            professorSignupCode = "";
        }
        if (diff == null) {
            diff = new Diff(5000);
        }
        if (rateLimit == null) {
            rateLimit = new RateLimit(10, 5, 3, List.of());
        }
        if (ai == null) {
            ai = new Ai(100);
        }
        if (demo == null) {
            demo = new Demo("");
        }
    }
}
