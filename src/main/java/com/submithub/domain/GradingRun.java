package com.submithub.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 제출 하나의 채점 기록. 제출물(submissions)은 그대로 두고 채점 결과만 따로 저장한다.
 * 재채점하면 이 행의 결과를 새로 채운다 (제출 버전은 바뀌지 않는다).
 */
@Entity
@Table(name = "grading_runs", uniqueConstraints = @UniqueConstraint(name = "uq_grading_run_submission", columnNames = "submission_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GradingRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", updatable = false)
    private Submission submission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GradingStatus status;

    /** 컴파일 성공 여부. 아직 모르면 null */
    @Column(name = "compile_ok")
    private Boolean compileOk;

    @Lob
    @Column(name = "compile_message")
    private String compileMessage;

    @Column(nullable = false)
    private int score;

    @Column(name = "total_points", nullable = false)
    private int totalPoints;

    @Column(name = "passed_count", nullable = false)
    private int passedCount;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(length = 60)
    private String runner;

    @Column(length = 500)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<GradingResult> results = new ArrayList<>();

    public GradingRun(Submission submission) {
        this.submission = submission;
        this.createdAt = LocalDateTime.now();
        reset(GradingStatus.QUEUED, null);
    }

    /** 새 채점을 시작한다: 이전 결과를 지운다 */
    public void reset(GradingStatus status, String message) {
        this.status = status;
        this.message = message;
        this.compileOk = null;
        this.compileMessage = null;
        this.score = 0;
        this.totalPoints = 0;
        this.passedCount = 0;
        this.totalCount = 0;
        this.runner = null;
        this.finishedAt = null;
        this.results.clear();
    }

    public void markRunning(String runner) {
        this.status = GradingStatus.RUNNING;
        this.runner = runner;
    }

    public void markFailed(GradingStatus status, String message) {
        this.status = status;
        this.message = message;
        this.finishedAt = LocalDateTime.now();
    }

    public void finish(boolean compileOk, String compileMessage, List<GradingResult> newResults) {
        this.compileOk = compileOk;
        this.compileMessage = compileMessage;
        this.results.clear();
        this.results.addAll(newResults);
        this.totalCount = newResults.size();
        this.passedCount = (int) newResults.stream().filter(r -> r.getVerdict() == GradingVerdict.PASSED).count();
        this.totalPoints = newResults.stream().mapToInt(GradingResult::getPoints).sum();
        this.score = newResults.stream().mapToInt(GradingResult::getAwarded).sum();
        this.status = GradingStatus.DONE;
        this.message = null;
        this.finishedAt = LocalDateTime.now();
    }

    /** 케이스 기준 통과율(%). 케이스가 없으면 -1 */
    public int percent() {
        return totalCount == 0 ? -1 : (int) Math.round(100.0 * passedCount / totalCount);
    }
}
