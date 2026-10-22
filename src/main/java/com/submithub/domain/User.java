package com.submithub.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, unique = true, length = 30)
    private String loginId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "student_no", length = 30)
    private String studentNo;

    @Column(length = 100)
    private String department;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public User(String loginId, String passwordHash, String name, Role role, String studentNo, String department) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role;
        this.studentNo = studentNo;
        this.department = department;
        this.createdAt = LocalDateTime.now();
    }

    public boolean isProfessor() {
        return role == Role.PROFESSOR;
    }
}
