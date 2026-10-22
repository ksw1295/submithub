package com.submithub.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 버전 단위 점수·총평. 제출물(submissions)은 그대로 두고 평가만 따로 저장한다. */
@Entity
@Table(name = "grades", uniqueConstraints = @UniqueConstraint(name = "uq_grade_submission", columnNames = "submission_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Grade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", updatable = false)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professor_id")
    private User professor;

    @Column(nullable = false)
    private int score;

    @Column(length = 2000)
    private String summary;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Grade(Submission submission, User professor, int score, String summary) {
        this.submission = submission;
        this.createdAt = LocalDateTime.now();
        update(professor, score, summary);
    }

    public void update(User professor, int score, String summary) {
        this.professor = professor;
        this.score = score;
        this.summary = summary;
        this.createdAt = LocalDateTime.now();
    }
}
