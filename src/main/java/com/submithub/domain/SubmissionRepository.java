package com.submithub.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    @Query("select coalesce(max(s.versionNo), 0) from Submission s where s.assignment.id = :assignmentId and s.student.id = :studentId")
    int maxVersion(Long assignmentId, Long studentId);

    Optional<Submission> findTopByAssignmentIdAndStudentIdOrderByVersionNoDesc(Long assignmentId, Long studentId);

    List<Submission> findByAssignmentIdAndStudentIdOrderByVersionNoAsc(Long assignmentId, Long studentId);

    /** 과제의 학생별 최신 버전 */
    @Query("""
            select s from Submission s
            where s.assignment.id = :assignmentId
              and s.versionNo = (select max(s2.versionNo) from Submission s2
                                 where s2.assignment.id = s.assignment.id and s2.student.id = s.student.id)
            """)
    List<Submission> findLatestPerStudent(Long assignmentId);

    long countByAssignmentIdAndStudentId(Long assignmentId, Long studentId);

    /** 통계용: 과제의 모든 제출(모든 학생, 모든 버전) */
    List<Submission> findByAssignmentIdOrderBySubmittedAtAsc(Long assignmentId);
}
