package com.submithub.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 과제의 자동 채점 실행 설정. 이 행이 있는 과제만 자동 채점한다. */
@Entity
@Table(name = "grading_configs", uniqueConstraints = @UniqueConstraint(name = "uq_grading_config_assignment", columnNames = "assignment_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GradingConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", updatable = false)
    private Assignment assignment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GradingLanguage language;

    /** 제출 파일 중 실행할 파일 (Java 는 Main.java 처럼 기본 패키지의 클래스 파일, Python 은 main.py) */
    @Column(name = "main_file", nullable = false, length = 200)
    private String mainFile;

    @Column(name = "time_limit_sec", nullable = false)
    private int timeLimitSeconds;

    @Column(name = "memory_limit_mb", nullable = false)
    private int memoryLimitMb;

    public GradingConfig(Assignment assignment, GradingLanguage language, String mainFile, int timeLimitSeconds,
                         int memoryLimitMb) {
        this.assignment = assignment;
        update(language, mainFile, timeLimitSeconds, memoryLimitMb);
    }

    public void update(GradingLanguage language, String mainFile, int timeLimitSeconds, int memoryLimitMb) {
        this.language = language;
        this.mainFile = mainFile;
        this.timeLimitSeconds = timeLimitSeconds;
        this.memoryLimitMb = memoryLimitMb;
    }
}
