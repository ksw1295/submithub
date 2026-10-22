package com.submithub.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "courses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professor_id")
    private User professor;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 30)
    private String semester;

    @Column(name = "invite_code", nullable = false, unique = true, length = 12)
    private String inviteCode;

    /**
     * AI 반영 판정 사용 여부 (기본 꺼짐). 켜면 코멘트 주변의 학생 코드가 외부 AI 서비스로 전송된다.
     * columnDefinition 의 default 는 이미 데이터가 있는 DB 에 컬럼을 추가할 때(ddl-auto=update)를 위한 것이다.
     */
    @Setter
    @Column(name = "ai_judge_enabled", nullable = false, columnDefinition = "boolean default false")
    private boolean aiJudgeEnabled;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Course(User professor, String name, String semester, String inviteCode) {
        this.professor = professor;
        this.name = name;
        this.semester = semester;
        this.inviteCode = inviteCode;
        this.createdAt = LocalDateTime.now();
    }
}
