package com.submithub.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface GradeRepository extends JpaRepository<Grade, Long> {
    Optional<Grade> findBySubmissionId(Long submissionId);

    @Query("select g from Grade g where g.submission.assignment.id = :assignmentId and g.submission.student.id = :studentId")
    List<Grade> findForStudent(Long assignmentId, Long studentId);
}
