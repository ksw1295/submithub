package com.submithub.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    boolean existsByCourseIdAndStudentId(Long courseId, Long studentId);

    List<Enrollment> findByCourseIdOrderByStudentNameAsc(Long courseId);

    long countByCourseId(Long courseId);
}
