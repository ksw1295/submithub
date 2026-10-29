package com.submithub.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    List<Assignment> findByCourseIdOrderByDueAtAsc(Long courseId);

    boolean existsByCourseIdAndTitleAndCreatedAtAfter(Long courseId, String title, java.time.LocalDateTime createdAt);

    @Query("""
            select a from Assignment a
            join fetch a.course c
            where c.id in (select e.course.id from Enrollment e where e.student.id = :studentId)
            order by a.dueAt asc
            """)
    List<Assignment> findUpcomingForStudent(@org.springframework.data.repository.query.Param("studentId") Long studentId);

    @Query("""
            select a from Assignment a
            join fetch a.course c
            where c.id in (select e.course.id from Enrollment e where e.student.id = :studentId)
            order by a.createdAt desc
            """)
    List<Assignment> findRecentForStudent(@org.springframework.data.repository.query.Param("studentId") Long studentId);

    /** 제출 번호 부여 구간을 직렬화하기 위한 비관적 락 (SELECT ... FOR UPDATE) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Assignment a where a.id = :id")
    Optional<Assignment> findByIdForUpdate(Long id);
}
