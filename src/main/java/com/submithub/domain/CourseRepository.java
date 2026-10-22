package com.submithub.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {
    Optional<Course> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);

    List<Course> findByProfessorIdOrderByCreatedAtDesc(Long professorId);

    @Query("select e.course from Enrollment e where e.student.id = :studentId order by e.joinedAt desc")
    List<Course> findByStudentId(Long studentId);
}
