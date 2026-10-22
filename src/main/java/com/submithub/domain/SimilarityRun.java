package com.submithub.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 표절 검사 한 번의 실행 기록 */
@Entity
@Table(name = "similarity_runs", indexes = @Index(name = "idx_sr_assignment", columnList = "assignment_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SimilarityRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id")
    private Assignment assignment;

    @Column(nullable = false)
    private double threshold;

    /** 비교에 포함된 제출물 수 (학생별 최신 버전 중 텍스트가 있는 것) */
    @Column(nullable = false)
    private int compared;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public SimilarityRun(Assignment assignment, double threshold, int compared) {
        this.assignment = assignment;
        this.threshold = threshold;
        this.compared = compared;
        this.createdAt = LocalDateTime.now();
    }
}
