package com.submithub.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GradingRunRepository extends JpaRepository<GradingRun, Long> {
    Optional<GradingRun> findBySubmissionId(Long submissionId);

    List<GradingRun> findBySubmissionIdIn(Collection<Long> submissionIds);

    List<GradingRun> findBySubmissionAssignmentId(Long assignmentId);
}
