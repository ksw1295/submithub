package com.submithub.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GradingConfigRepository extends JpaRepository<GradingConfig, Long> {
    Optional<GradingConfig> findByAssignmentId(Long assignmentId);

    void deleteByAssignmentId(Long assignmentId);
}
