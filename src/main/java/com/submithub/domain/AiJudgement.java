package com.submithub.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 코멘트 하나에 대한 AI 반영 판정. 같은 코멘트 + 같은 버전(판정 시점의 최신 버전)은 한 번만 저장하고 재사용한다.
 * 판정에 실패한 행(FAILED)은 다시 실행할 때 결과를 덮어쓴다.
 */
@Entity
@Table(name = "ai_judgements",
        uniqueConstraints = @UniqueConstraint(name = "uq_ai_comment_version", columnNames = {"comment_id", "version_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiJudgement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", updatable = false)
    private ReviewComment comment;

    /** 판정에 쓴 "새 코드"의 버전 번호 (판정 시점의 최신 버전) */
    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiVerdict verdict;

    @Column(length = 1000)
    private String reason;

    @Column(length = 60)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiDecision decision = AiDecision.PENDING;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    public AiJudgement(ReviewComment comment, int versionNo, AiVerdict verdict, String reason, String model) {
        this.comment = comment;
        this.versionNo = versionNo;
        this.createdAt = LocalDateTime.now();
        setResult(verdict, reason, model);
    }

    /** 새 결과로 바꾼다. 교수의 이전 결정은 초기화한다 */
    public void setResult(AiVerdict verdict, String reason, String model) {
        this.verdict = verdict;
        this.reason = reason;
        this.model = model;
        this.decision = AiDecision.PENDING;
        this.decidedAt = null;
        this.createdAt = LocalDateTime.now();
    }

    public void decide(AiDecision decision) {
        this.decision = decision;
        this.decidedAt = decision == AiDecision.PENDING ? null : LocalDateTime.now();
    }
}
