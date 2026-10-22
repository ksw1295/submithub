package com.submithub.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ReviewCommentRepository extends JpaRepository<ReviewComment, Long> {

    List<ReviewComment> findBySubmissionFileIdOrderByCreatedAtAscIdAsc(Long submissionFileId);

    /** 한 학생의 한 과제에 달린 모든 최상위 코멘트 (모든 버전) */
    @Query("""
            select c from ReviewComment c
            where c.parent is null
              and c.submissionFile.submission.assignment.id = :assignmentId
              and c.submissionFile.submission.student.id = :studentId
            order by c.submissionFile.submission.versionNo, c.startLine, c.id
            """)
    List<ReviewComment> findRootsForStudent(Long assignmentId, Long studentId);

    /** 과제 전체의 최상위 코멘트 (교수 통계용) */
    @Query("""
            select c from ReviewComment c
            where c.parent is null
              and c.submissionFile.submission.assignment.id = :assignmentId
            """)
    List<ReviewComment> findRootsForAssignment(Long assignmentId);
}
