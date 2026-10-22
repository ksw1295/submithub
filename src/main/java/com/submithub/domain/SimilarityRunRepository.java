package com.submithub.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SimilarityRunRepository extends JpaRepository<SimilarityRun, Long> {
    Optional<SimilarityRun> findTopByAssignmentIdOrderByIdDesc(Long assignmentId);
}
