package com.submithub.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    @EntityGraph(attributePaths = {"members", "members.student", "leader"})
    List<Team> findByAssignmentIdOrderByNameAsc(Long assignmentId);

    @Override
    @EntityGraph(attributePaths = {"members", "members.student", "leader", "assignment", "assignment.course"})
    @Query("SELECT t FROM Team t WHERE t.id = :id")
    Optional<Team> findById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"members", "members.student", "leader", "assignment", "assignment.course"})
    @Query("SELECT t FROM Team t WHERE t.id = :id")
    Optional<Team> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"members", "members.student", "leader"})
    @Query("SELECT t FROM Team t JOIN t.members m WHERE t.assignment.id = :assignmentId AND m.student.id = :studentId")
    Optional<Team> findByAssignmentIdAndStudentId(@Param("assignmentId") Long assignmentId, @Param("studentId") Long studentId);

    boolean existsByAssignmentIdAndName(Long assignmentId, String name);

    void deleteByAssignmentId(Long assignmentId);
}
