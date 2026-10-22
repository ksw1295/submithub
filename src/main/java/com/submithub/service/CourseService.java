package com.submithub.service;

import com.submithub.domain.Course;
import com.submithub.domain.CourseRepository;
import com.submithub.domain.Enrollment;
import com.submithub.domain.EnrollmentRepository;
import com.submithub.domain.UserRepository;
import com.submithub.security.AppUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CourseService {

    /** 헷갈리기 쉬운 문자(0/O, 1/I)를 뺀 초대 코드 문자 */
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final UserRepository users;
    private final AccessService access;
    private final AssignmentService assignmentService;

    public CourseService(CourseRepository courses, EnrollmentRepository enrollments, UserRepository users,
                         AccessService access, AssignmentService assignmentService) {
        this.courses = courses;
        this.enrollments = enrollments;
        this.users = users;
        this.access = access;
        this.assignmentService = assignmentService;
    }

    public record CourseSummary(Long id, String name, String semester, String professorName, long studentCount) {
    }

    public record StudentRow(Long id, String name, String studentNo, String department, LocalDateTime joinedAt) {
    }

    public record CourseDetail(Long id, String name, String semester, String professorName,
                               String inviteCode, List<StudentRow> students,
                               List<AssignmentService.AssignmentRow> assignments) {
    }

    /** 교수는 자기가 개설한 강의, 학생은 수강 중인 강의 */
    @Transactional(readOnly = true)
    public List<CourseSummary> myCourses(AppUser user) {
        List<Course> list = user.isProfessor()
                ? courses.findByProfessorIdOrderByCreatedAtDesc(user.getId())
                : courses.findByStudentId(user.getId());
        return list.stream()
                .map(c -> new CourseSummary(c.getId(), c.getName(), c.getSemester(),
                        c.getProfessor().getName(), enrollments.countByCourseId(c.getId())))
                .toList();
    }

    @Transactional
    public Course create(AppUser professor, String name, String semester) {
        if (!professor.isProfessor()) {
            throw new ForbiddenException("교수만 강의를 개설할 수 있습니다.");
        }
        var owner = users.getReferenceById(professor.getId());
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = newInviteCode();
            if (courses.existsByInviteCode(code)) {
                continue;
            }
            try {
                return courses.saveAndFlush(new Course(owner, name.trim(), semester.trim(), code));
            } catch (DataIntegrityViolationException e) {
                // 동시에 같은 코드가 발급된 드문 경우: 다시 발급
            }
        }
        throw new BusinessException("초대 코드를 발급하지 못했습니다. 다시 시도해 주세요.");
    }

    @Transactional
    public Course join(AppUser student, String inviteCode) {
        if (student.isProfessor()) {
            throw new ForbiddenException("학생만 수강 등록을 할 수 있습니다.");
        }
        String code = inviteCode == null ? "" : inviteCode.trim().toUpperCase();
        Course course = courses.findByInviteCode(code)
                .orElseThrow(() -> new BusinessException("초대 코드가 올바르지 않습니다."));
        if (enrollments.existsByCourseIdAndStudentId(course.getId(), student.getId())) {
            throw new BusinessException("이미 등록한 강의입니다.");
        }
        try {
            enrollments.saveAndFlush(new Enrollment(course, users.getReferenceById(student.getId())));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("이미 등록한 강의입니다.");
        }
        return course;
    }

    @Transactional(readOnly = true)
    public CourseDetail detail(Long courseId, AppUser user) {
        Course course = access.course(courseId, user);
        List<StudentRow> students = user.isProfessor()
                ? enrollments.findByCourseIdOrderByStudentNameAsc(courseId).stream()
                .map(e -> new StudentRow(e.getStudent().getId(), e.getStudent().getName(),
                        e.getStudent().getStudentNo(), e.getStudent().getDepartment(), e.getJoinedAt()))
                .toList()
                : List.of();
        return new CourseDetail(course.getId(), course.getName(), course.getSemester(),
                course.getProfessor().getName(),
                user.isProfessor() ? course.getInviteCode() : null,
                students, assignmentService.rowsFor(course, user));
    }

    static String newInviteCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
