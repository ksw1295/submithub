package com.submithub.service;

import com.submithub.domain.Assignment;
import com.submithub.domain.AssignmentRepository;
import com.submithub.domain.Course;
import com.submithub.domain.CourseRepository;
import com.submithub.domain.EnrollmentRepository;
import com.submithub.security.AppUser;
import org.springframework.stereotype.Component;

/**
 * 모든 조회·수정의 권한 검사를 한곳에서 한다 (IDOR 방지).
 * 규칙: 본인 것이거나, 해당 강의의 교수만 접근할 수 있다.
 * 반드시 트랜잭션 안에서 호출해야 한다 (반환 엔티티의 지연 로딩을 쓰기 때문).
 */
@Component
public class AccessService {

    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final AssignmentRepository assignments;

    public AccessService(CourseRepository courses, EnrollmentRepository enrollments,
                         AssignmentRepository assignments) {
        this.courses = courses;
        this.enrollments = enrollments;
        this.assignments = assignments;
    }

    /** 강의를 연 교수이거나 그 강의를 수강 중인 학생 */
    public Course course(Long courseId, AppUser user) {
        Course course = courses.findById(courseId).orElseThrow(() -> new NotFoundException("강의를 찾을 수 없습니다."));
        if (user.isProfessor()) {
            if (!course.getProfessor().getId().equals(user.getId())) {
                throw new ForbiddenException("다른 교수의 강의입니다.");
            }
        } else if (!enrollments.existsByCourseIdAndStudentId(courseId, user.getId())) {
            throw new ForbiddenException("수강 중인 강의가 아닙니다.");
        }
        return course;
    }

    /** 강의를 연 교수만 */
    public Course courseOfProfessor(Long courseId, AppUser user) {
        Course course = courses.findById(courseId).orElseThrow(() -> new NotFoundException("강의를 찾을 수 없습니다."));
        if (!user.isProfessor() || !course.getProfessor().getId().equals(user.getId())) {
            throw new ForbiddenException("강의를 연 교수만 할 수 있습니다.");
        }
        return course;
    }

    public Assignment assignment(Long assignmentId, AppUser user) {
        Assignment a = assignments.findById(assignmentId)
                .orElseThrow(() -> new NotFoundException("과제를 찾을 수 없습니다."));
        course(a.getCourse().getId(), user);
        return a;
    }

    public Assignment assignmentOfProfessor(Long assignmentId, AppUser user) {
        Assignment a = assignments.findById(assignmentId)
                .orElseThrow(() -> new NotFoundException("과제를 찾을 수 없습니다."));
        courseOfProfessor(a.getCourse().getId(), user);
        return a;
    }
}
