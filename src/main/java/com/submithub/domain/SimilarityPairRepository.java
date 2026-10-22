package com.submithub.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SimilarityPairRepository extends JpaRepository<SimilarityPair, Long> {
    List<SimilarityPair> findByRunIdOrderByScoreDesc(Long runId);
}
