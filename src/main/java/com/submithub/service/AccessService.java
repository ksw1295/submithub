package com.submithub.service;

import com.submithub.domain.Assignment;
import com.submithub.domain.AssignmentRepository;
import com.submithub.domain.Course;
import com.submithub.domain.CourseRepository;
import com.submithub.domain.EnrollmentRepository;
import com.submithub.domain.ReviewComment;
import com.submithub.domain.ReviewCommentRepository;
import com.submithub.domain.Submission;
import com.submithub.domain.SubmissionFile;
import com.submithub.domain.SubmissionFileRepository;
import com.submithub.domain.SubmissionRepository;
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
    private final SubmissionRepository submissions;
    private final SubmissionFileRepository files;
    private final ReviewCommentRepository reviewComments;

    public AccessService(CourseRepository courses, EnrollmentRepository enrollments,
                         AssignmentRepository assignments, SubmissionRepository submissions,
                         SubmissionFileRepository files, ReviewCommentRepository reviewComments) {
        this.courses = courses;
        this.enrollments = enrollments;
        this.assignments = assignments;
        this.submissions = submissions;
        this.files = files;
        this.reviewComments = reviewComments;
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

    /** 제출물: 낸 학생 본인이거나 그 강의의 교수 */
    public Submission submission(Long submissionId, AppUser user) {
        Submission s = submissions.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("제출물을 찾을 수 없습니다."));
        requireOwnerOrProfessor(s.getAssignment(), s.getStudent().getId(), user);
        return s;
    }

    public SubmissionFile file(Long fileId, AppUser user) {
        SubmissionFile f = files.findById(fileId).orElseThrow(() -> new NotFoundException("파일을 찾을 수 없습니다."));
        requireOwnerOrProfessor(f.getSubmission().getAssignment(), f.getSubmission().getStudent().getId(), user);
        return f;
    }

    /** 코멘트가 달린 파일에 접근할 수 있는 사람만 코멘트에 접근할 수 있다 */
    public ReviewComment comment(Long commentId, AppUser user) {
        ReviewComment c = reviewComments.findById(commentId)
                .orElseThrow(() -> new NotFoundException("코멘트를 찾을 수 없습니다."));
        file(c.getSubmissionFile().getId(), user);
        return c;
    }

    /** 한 학생의 과제 제출 이력을 볼 수 있는지 확인하고 과제를 돌려준다 */
    public Assignment studentHistory(Long assignmentId, Long studentId, AppUser user) {
        Assignment a = assignments.findById(assignmentId)
                .orElseThrow(() -> new NotFoundException("과제를 찾을 수 없습니다."));
        requireOwnerOrProfessor(a, studentId, user);
        return a;
    }

    private void requireOwnerOrProfessor(Assignment assignment, Long ownerStudentId, AppUser user) {
        Long courseId = assignment.getCourse().getId();
        if (user.isProfessor()) {
            courseOfProfessor(courseId, user);
        } else if (!user.getId().equals(ownerStudentId)) {
            throw new ForbiddenException("다른 학생의 제출물입니다.");
        }
    }

    public Assignment assignmentOfProfessor(Long assignmentId, AppUser user) {
        Assignment a = assignments.findById(assignmentId)
                .orElseThrow(() -> new NotFoundException("과제를 찾을 수 없습니다."));
        courseOfProfessor(a.getCourse().getId(), user);
        return a;
    }
}
