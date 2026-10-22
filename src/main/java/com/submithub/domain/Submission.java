package com.submithub.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 제출 한 번 = 버전 하나. INSERT 만 한다 (@Immutable): 기존 버전을 수정하거나 지우지 않는다.
 */
@Entity
@Immutable
@Table(name = "submissions", uniqueConstraints = @UniqueConstraint(
        name = "uq_submission_version", columnNames = {"assignment_id", "student_id", "version_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", updatable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", updatable = false)
    private User student;

    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo;

    /** 제출 메모 (커밋 메시지 같은 것) */
    @Column(length = 500, updatable = false)
    private String message;

    @Column(nullable = false, updatable = false)
    private boolean late;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.PERSIST)
    @OrderBy("path ASC")
    private List<SubmissionFile> files = new ArrayList<>();

    public Submission(Assignment assignment, User student, int versionNo, String message, boolean late,
                      LocalDateTime submittedAt) {
        this.assignment = assignment;
        this.student = student;
        this.versionNo = versionNo;
        this.message = message;
        this.late = late;
        this.submittedAt = submittedAt;
    }

    public void addFile(SubmissionFile file) {
        files.add(file);
    }
}
