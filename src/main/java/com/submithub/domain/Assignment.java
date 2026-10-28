package com.submithub.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "assignments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 4000)
    private String description;

    @Column(name = "due_at", nullable = false)
    private LocalDateTime dueAt;

    @Column(name = "allow_late", nullable = false)
    private boolean allowLate;

    /** 지각 허용일 때 이 시각까지만 제출 가능. null 이면 기한 없이 허용 */
    @Column(name = "late_until")
    private LocalDateTime lateUntil;

    /** 교수가 나눠준 뼈대 코드. 표절 검사에서 이 부분은 제외한다 */
    @Lob
    @Column(name = "baseline_code", columnDefinition = "LONGTEXT")
    private String baselineCode;

    /** 조별(팀) 프로젝트 여부 */
    @Column(name = "team_project", nullable = false, columnDefinition = "boolean default false")
    private boolean teamProject;

    /** 최대 팀 인원 수 (null 이면 무제한) */
    @Column(name = "max_team_size")
    private Integer maxTeamSize;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Assignment(Course course, String title, String description, LocalDateTime dueAt,
                      boolean allowLate, LocalDateTime lateUntil, String baselineCode) {
        this(course, title, description, dueAt, allowLate, lateUntil, baselineCode, false, null);
    }

    public Assignment(Course course, String title, String description, LocalDateTime dueAt,
                      boolean allowLate, LocalDateTime lateUntil, String baselineCode,
                      boolean teamProject, Integer maxTeamSize) {
        this.course = course;
        this.createdAt = LocalDateTime.now();
        this.teamProject = teamProject;
        this.maxTeamSize = maxTeamSize;
        update(title, description, dueAt, allowLate, lateUntil, baselineCode);
    }

    public void configureTeam(boolean teamProject, Integer maxTeamSize) {
        this.teamProject = teamProject;
        this.maxTeamSize = maxTeamSize;
    }

    public void update(String title, String description, LocalDateTime dueAt,
                       boolean allowLate, LocalDateTime lateUntil, String baselineCode) {
        this.title = title;
        this.description = description;
        this.dueAt = dueAt;
        this.allowLate = allowLate;
        this.lateUntil = allowLate ? lateUntil : null;
        this.baselineCode = (baselineCode == null || baselineCode.isBlank()) ? null : baselineCode;
    }

    public boolean isPastDue(LocalDateTime now) {
        return now.isAfter(dueAt);
    }

    /** 지금 제출할 수 있는지: 마감 전이거나, 지각 허용 기간 안 */
    public boolean isOpen(LocalDateTime now) {
        if (!isPastDue(now)) {
            return true;
        }
        return allowLate && (lateUntil == null || !now.isAfter(lateUntil));
    }
}
