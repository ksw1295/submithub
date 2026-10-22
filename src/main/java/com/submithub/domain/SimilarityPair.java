package com.submithub.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 표절 검사에서 기준 이상으로 비슷하게 나온 제출물 쌍 */
@Entity
@Table(name = "similarity_pairs", indexes = @Index(name = "idx_sp_run", columnList = "run_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SimilarityPair {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id")
    private SimilarityRun run;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_a_id")
    private Submission submissionA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_b_id")
    private Submission submissionB;

    @Column(nullable = false)
    private double score;

    @Column(name = "coverage_a", nullable = false)
    private double coverageA;

    @Column(name = "coverage_b", nullable = false)
    private double coverageB;

    public SimilarityPair(SimilarityRun run, Submission a, Submission b, double score, double coverageA,
                          double coverageB) {
        this.run = run;
        this.submissionA = a;
        this.submissionB = b;
        this.score = score;
        this.coverageA = coverageA;
        this.coverageB = coverageB;
    }
}
