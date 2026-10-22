package com.submithub.service;

import com.submithub.core.text.TextDecoder;
import com.submithub.domain.Assignment;
import com.submithub.domain.AssignmentRepository;
import com.submithub.domain.Course;
import com.submithub.domain.SubmissionRepository;
import com.submithub.security.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AssignmentService {

    /** 마감까지 이 시간 이하로 남으면 "마감 임박" */
    static final Duration DUE_SOON = Duration.ofHours(48);

    private final AssignmentRepository assignments;
    private final SubmissionRepository submissions;
    private final AccessService access;

    public AssignmentService(AssignmentRepository assignments, SubmissionRepository submissions,
                             AccessService access) {
        this.assignments = assignments;
        this.submissions = submissions;
        this.access = access;
    }

    /** 입력 값 묶음 (폼 → 서비스) */
    public record AssignmentInput(String title, String description, LocalDateTime dueAt, boolean allowLate,
                                  LocalDateTime lateUntil, String baselineCode, byte[] baselineFile) {
    }

    /**
     * 과제 목록 한 줄.
     *
     * @param state 학생 기준 상태: OPEN(진행 중) / DUE_SOON(마감 임박) / SUBMITTED(제출 완료) / LATE(지각 제출)
     *              / CLOSED(마감, 미제출)
     */
    public record AssignmentRow(Long id, String title, LocalDateTime dueAt, boolean allowLate,
                                LocalDateTime lateUntil, String state, int myVersions) {
    }

    /** 과제 상세 화면용 */
    public record AssignmentView(Long id, Long courseId, String courseName, String title, String description,
                                 LocalDateTime dueAt, boolean allowLate, LocalDateTime lateUntil,
                                 boolean hasBaseline, boolean open, boolean pastDue) {
    }

    @Transactional(readOnly = true)
    public AssignmentView view(Long assignmentId, AppUser user) {
        Assignment a = access.assignment(assignmentId, user);
        LocalDateTime now = LocalDateTime.now();
        return new AssignmentView(a.getId(), a.getCourse().getId(), a.getCourse().getName(), a.getTitle(),
                a.getDescription(), a.getDueAt(), a.isAllowLate(), a.getLateUntil(),
                a.getBaselineCode() != null, a.isOpen(now), a.isPastDue(now));
    }

    @Transactional(readOnly = true)
    public List<AssignmentRow> rowsFor(Course course, AppUser user) {
        LocalDateTime now = LocalDateTime.now();
        return assignments.findByCourseIdOrderByDueAtAsc(course.getId()).stream()
                .map(a -> row(a, user, now))
                .toList();
    }

    private AssignmentRow row(Assignment a, AppUser user, LocalDateTime now) {
        String state = "";
        int versions = 0;
        if (!user.isProfessor()) {
            state = stateOf(a, now);
            var latest = submissions.findTopByAssignmentIdAndStudentIdOrderByVersionNoDesc(a.getId(), user.getId());
            if (latest.isPresent()) {
                versions = latest.get().getVersionNo();
                state = latest.get().isLate() ? "LATE" : "SUBMITTED";
            }
        }
        return new AssignmentRow(a.getId(), a.getTitle(), a.getDueAt(), a.isAllowLate(), a.getLateUntil(),
                state, versions);
    }

    static String stateOf(Assignment a, LocalDateTime now) {
        if (a.isPastDue(now)) {
            return a.isOpen(now) ? "LATE_OPEN" : "CLOSED";
        }
        return Duration.between(now, a.getDueAt()).compareTo(DUE_SOON) <= 0 ? "DUE_SOON" : "OPEN";
    }

    @Transactional
    public Assignment create(Long courseId, AppUser professor, AssignmentInput in) {
        Course course = access.courseOfProfessor(courseId, professor);
        validate(in);
        return assignments.save(new Assignment(course, in.title().trim(), in.description(), in.dueAt(),
                in.allowLate(), in.lateUntil(), baseline(in)));
    }

    @Transactional
    public Assignment update(Long assignmentId, AppUser professor, AssignmentInput in) {
        Assignment a = access.assignmentOfProfessor(assignmentId, professor);
        validate(in);
        // 새 뼈대 입력이 없으면 기존 뼈대 코드를 유지한다
        String baseline = baseline(in);
        a.update(in.title().trim(), in.description(), in.dueAt(), in.allowLate(), in.lateUntil(),
                baseline != null ? baseline : a.getBaselineCode());
        return a;
    }

    @Transactional
    public void removeBaseline(Long assignmentId, AppUser professor) {
        Assignment a = access.assignmentOfProfessor(assignmentId, professor);
        a.update(a.getTitle(), a.getDescription(), a.getDueAt(), a.isAllowLate(), a.getLateUntil(), null);
    }

    /** 과제 등록 화면을 열 수 있는지 (강의를 연 교수만) */
    @Transactional(readOnly = true)
    public void requireCourseOwner(Long courseId, AppUser professor) {
        access.courseOfProfessor(courseId, professor);
    }

    @Transactional(readOnly = true)
    public Assignment editable(Long assignmentId, AppUser professor) {
        return access.assignmentOfProfessor(assignmentId, professor);
    }

    private static void validate(AssignmentInput in) {
        if (in.title() == null || in.title().isBlank()) {
            throw new BusinessException("과제 제목을 입력하세요.");
        }
        if (in.dueAt() == null) {
            throw new BusinessException("마감 일시를 입력하세요.");
        }
        if (in.allowLate() && in.lateUntil() != null && in.lateUntil().isBefore(in.dueAt())) {
            throw new BusinessException("지각 허용 기한은 마감 일시보다 늦어야 합니다.");
        }
    }

    /** 뼈대 코드: 직접 입력한 텍스트가 없으면 올린 파일을 (UTF-8/CP949 자동 인식으로) 읽는다 */
    private static String baseline(AssignmentInput in) {
        if (in.baselineCode() != null && !in.baselineCode().isBlank()) {
            return in.baselineCode();
        }
        if (in.baselineFile() != null && in.baselineFile().length > 0) {
            if (TextDecoder.looksBinary(in.baselineFile())) {
                throw new BusinessException("뼈대 코드는 텍스트 파일이어야 합니다.");
            }
            return TextDecoder.decode(in.baselineFile()).text();
        }
        return null;
    }
}
