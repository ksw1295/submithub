package com.submithub.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SimilarityPairRepository extends JpaRepository<SimilarityPair, Long> {
    List<SimilarityPair> findByRunIdOrderByScoreDesc(Long runId);

    @Modifying
    @Query("delete from SimilarityPair sp where sp.run.assignment.id = :assignmentId")
    void deleteByAssignmentId(@Param("assignmentId") Long assignmentId);
}
