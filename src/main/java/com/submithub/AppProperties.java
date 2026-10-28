package com.submithub;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 submithub.* 설정.
 *
 * @param storageDir          업로드 파일 저장 폴더 (UUID 이름으로 저장)
 * @param demoData            true 이고 DB 가 비어 있으면 시연용 샘플 데이터를 넣는다
 * @param professorSignupCode 비어 있지 않으면 교수 회원가입 시 이 코드를 입력해야 한다
 * @param maxFileBytes        파일 하나의 최대 크기
 * @param maxTotalBytes       한 번의 제출(zip 풀었을 때 포함)의 최대 총 크기
 * @param maxFiles            한 번의 제출의 최대 파일 수
 * @param diff                diff 계산 옵션
 */
@ConfigurationProperties(prefix = "submithub")
public record AppProperties(String storageDir, boolean demoData, String professorSignupCode,
                            long maxFileBytes, long maxTotalBytes, int maxFiles,
                            Diff diff) {

    public record Diff(int maxEditDistance) {
        public Diff {
            if (maxEditDistance <= 0) {
                maxEditDistance = 5000;
            }
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
    }
}
