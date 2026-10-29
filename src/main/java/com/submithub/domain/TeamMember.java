package com.submithub.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "team_members", uniqueConstraints = {
        @UniqueConstraint(name = "uk_team_member_assignment_student", columnNames = {"assignment_id", "student_id"}),
        @UniqueConstraint(name = "uk_team_member_team_student", columnNames = {"team_id", "student_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id")
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id")
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private User student;

    @Setter
    @Column(name = "branch_name", length = 100)
    private String branchName;

    @Setter
    @Column(name = "role_title", length = 100)
    private String roleTitle;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    public TeamMember(Team team, User student, String branchName, String roleTitle) {
        this.team = team;
        this.assignment = team.getAssignment();
        this.student = student;
        this.branchName = branchName;
        this.roleTitle = roleTitle;
        this.joinedAt = LocalDateTime.now();
    }
}
