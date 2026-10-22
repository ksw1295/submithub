package com.submithub.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "submission_files", indexes = @Index(name = "idx_sf_submission", columnList = "submission_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubmissionFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", updatable = false)
    private Submission submission;

    /** zip 안의 경로 (정규화됨). 서버 디스크 경로와는 무관하다 */
    @Column(nullable = false, length = 300, updatable = false)
    private String path;

    @Column(nullable = false, updatable = false)
    private long size;

    @Column(nullable = false, length = 64, updatable = false)
    private String sha256;

    /** 텍스트일 때 인식한 문자 집합 (UTF-8, MS949 ...). 바이너리면 null */
    @Column(length = 30, updatable = false)
    private String charset;

    /** 텍스트 파일의 내용. 바이너리면 null */
    @Lob
    @Column(name = "content_text", updatable = false)
    private String contentText;

    /** 서버 디스크에 저장된 원본 파일 이름 (UUID) */
    @Column(name = "storage_path", nullable = false, length = 60, updatable = false)
    private String storagePath;

    public SubmissionFile(Submission submission, String path, long size, String sha256, String charset,
                          String contentText, String storagePath) {
        this.submission = submission;
        this.path = path;
        this.size = size;
        this.sha256 = sha256;
        this.charset = charset;
        this.contentText = contentText;
        this.storagePath = storagePath;
    }

    public boolean isBinary() {
        return contentText == null;
    }
}
