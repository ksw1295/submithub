package com.submithub.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AiJudgementRepository extends JpaRepository<AiJudgement, Long> {
    Optional<AiJudgement> findByCommentIdAndVersionNo(Long commentId, int versionNo);

    List<AiJudgement> findByCommentIdIn(Collection<Long> commentIds);
}
