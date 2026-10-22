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

    /** GitHub 커밋으로 제출한 경우: "owner/repo" 와 커밋 SHA (파일 업로드로 낸 버전은 null) */
    @Column(name = "source_repo", length = 200, updatable = false)
    private String sourceRepo;

    @Column(name = "source_commit", length = 40, updatable = false)
    private String sourceCommit;

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

    /** GitHub 커밋 출처를 기록한다 (저장 전에만 호출) */
    public void setSource(String repo, String commitSha) {
        this.sourceRepo = repo;
        this.sourceCommit = commitSha;
    }

    public boolean fromGitHub() {
        return sourceCommit != null;
    }

    public String sourceUrl() {
        return fromGitHub() ? "https://github.com/" + sourceRepo + "/commit/" + sourceCommit : null;
    }

    public String sourceLabel() {
        return fromGitHub() ? sourceRepo + "@" + sourceCommit.substring(0, Math.min(7, sourceCommit.length())) : null;
    }

    public void addFile(SubmissionFile file) {
        files.add(file);
    }
}
