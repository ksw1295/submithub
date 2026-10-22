package com.submithub.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GradingTestCaseRepository extends JpaRepository<GradingTestCase, Long> {
    List<GradingTestCase> findByAssignmentIdOrderByOrderNoAscIdAsc(Long assignmentId);

    long countByAssignmentId(Long assignmentId);
}
