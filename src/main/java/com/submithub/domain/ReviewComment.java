package com.submithub.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 줄 단위 코드 리뷰 코멘트. 특정 버전의 특정 파일의 줄(범위)에 달린다.
 * 최상위 코멘트는 교수가 쓰고, 답글(parent 가 있는 것)은 교수와 해당 학생이 쓴다.
 */
@Entity
@Table(name = "review_comments", indexes = {
        @Index(name = "idx_rc_file", columnList = "submission_file_id"),
        @Index(name = "idx_rc_parent", columnList = "parent_id")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_file_id", updatable = false)
    private SubmissionFile submissionFile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", updatable = false)
    private User author;

    @Column(name = "start_line", nullable = false, updatable = false)
    private int startLine;

    @Column(name = "end_line", nullable = false, updatable = false)
    private int endLine;

    @Column(nullable = false, length = 2000, updatable = false)
    private String body;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", updatable = false)
    private ReviewComment parent;

    /** 학생이 "고쳤어요"로 표시했는지 (최상위 코멘트에만 의미 있음) */
    @Setter
    @Column(name = "resolved_by_student", nullable = false)
    private boolean resolvedByStudent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ReviewComment(SubmissionFile file, User author, int startLine, int endLine, String body,
                         ReviewComment parent) {
        this.submissionFile = file;
        this.author = author;
        this.startLine = startLine;
        this.endLine = endLine;
        this.body = body;
        this.parent = parent;
        this.createdAt = LocalDateTime.now();
    }
}
