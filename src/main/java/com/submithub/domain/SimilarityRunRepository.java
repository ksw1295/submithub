package com.submithub.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SimilarityRunRepository extends JpaRepository<SimilarityRun, Long> {
    Optional<SimilarityRun> findTopByAssignmentIdOrderByIdDesc(Long assignmentId);

    @Modifying
    @Query("delete from SimilarityRun sr where sr.assignment.id = :assignmentId")
    void deleteByAssignmentId(@Param("assignmentId") Long assignmentId);
}
