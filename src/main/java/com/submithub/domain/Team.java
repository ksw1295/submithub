package com.submithub.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "teams")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id")
    private Assignment assignment;

    @Setter
    @Column(nullable = false, length = 100)
    private String name;

    @Setter
    @Column(name = "repo_url", length = 300)
    private String repoUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leader_id")
    private User leader;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("joinedAt ASC")
    private List<TeamMember> members = new ArrayList<>();

    public Team(Assignment assignment, String name, String repoUrl, User leader) {
        this.assignment = assignment;
        this.name = name;
        this.repoUrl = repoUrl;
        this.leader = leader;
        this.createdAt = LocalDateTime.now();
    }

    public void addMember(TeamMember member) {
        this.members.add(member);
    }

    public boolean hasMember(Long studentId) {
        return members.stream().anyMatch(m -> m.getStudent().getId().equals(studentId));
    }
}
