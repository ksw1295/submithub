package com.submithub.service;

import com.submithub.domain.Assignment;
import com.submithub.domain.AssignmentRepository;
import com.submithub.domain.Course;
import com.submithub.domain.Enrollment;
import com.submithub.domain.EnrollmentRepository;
import com.submithub.domain.Submission;
import com.submithub.domain.SubmissionRepository;
import com.submithub.security.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 교수용 제출 통계: 제출률, 미제출자, 지각, 마감 직전 24시간 비율, 시간대별 분포, 학생별 버전 수·반영률 */
@Service
public class StatsService {

    public record StudentRow(Long studentId, String name, String studentNo, int versions, boolean submitted,
                             boolean late, Integer score, FeedbackService.FeedbackSummary feedback) {
        // 반영률 계산과 표기는 FeedbackSummary 한 곳에서 한다 (과제 제출 현황 화면과 같은 값)
        public int feedbackRate() {
            return feedback.ratePercent();
        }

        public int feedbackTotal() {
            return feedback.total();
        }

        public int feedbackUnchanged() {
            return feedback.unchanged();
        }
    }

    /**
     * @param lastDayPercent 전체 제출(모든 버전) 중 마감 직전 24시간 안에 낸 비율
     * @param byHour         시간대(0~23시)별 제출 수 (모든 버전)
     */
    public record AssignmentStat(Long id, String title, LocalDateTime dueAt, boolean pastDue, int enrolled,
                                 int submitted, int submitRatePercent, int lateStudents, List<String> missing,
                                 int totalSubmissions, int lastDaySubmissions, int lastDayPercent,
                                 List<Integer> byHour, List<StudentRow> students) {
    }

    /**
     * @param averageFeedbackRate     반영률이 계산되는(추적 대상 코멘트가 있는) 학생들의 평균(%), 없으면 -1.
     *                                학생마다 과제별 반영률을 평균낸 값을 다시 학생 수로 평균한다
     * @param averageFeedbackStudents 그 평균에 들어간 학생 수
     */
    public record CourseStats(Long courseId, String courseName, int enrolled, List<AssignmentStat> assignments,
                              List<Integer> hourTotal, int averageFeedbackRate, int averageFeedbackStudents) {
        public List<String> assignmentTitles() {
            return assignments.stream().map(AssignmentStat::title).toList();
        }

        public List<Integer> submitRates() {
            return assignments.stream().map(AssignmentStat::submitRatePercent).toList();
        }

        /** 과제별 제출률의 평균(%) */
        public int averageSubmitRate() {
            return assignments.isEmpty() ? 0
                    : (int) Math.round(assignments.stream().mapToInt(AssignmentStat::submitRatePercent).average().orElse(0));
        }

        public List<Integer> lateCounts() {
            return assignments.stream().map(AssignmentStat::lateStudents).toList();
        }

        /** 과제별 시간대 분포 (차트에 그대로 넘기려고 단순한 리스트로 제공) */
        public List<List<Integer>> hoursByAssignment() {
            return assignments.stream().map(AssignmentStat::byHour).toList();
        }
    }

    private final AssignmentRepository assignments;
    private final SubmissionRepository submissions;
    private final EnrollmentRepository enrollments;
    private final FeedbackService feedback;
    private final GradeService grades;
    private final AccessService access;

    public StatsService(AssignmentRepository assignments, SubmissionRepository submissions,
                        EnrollmentRepository enrollments, FeedbackService feedback, GradeService grades,
                        AccessService access) {
        this.assignments = assignments;
        this.submissions = submissions;
        this.enrollments = enrollments;
        this.feedback = feedback;
        this.grades = grades;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public CourseStats course(Long courseId, AppUser professor) {
        Course course = access.courseOfProfessor(courseId, professor);
        List<Enrollment> students = enrollments.findByCourseIdOrderByStudentNameAsc(courseId);
        LocalDateTime now = LocalDateTime.now();

        int[] hourTotal = new int[24];
        List<AssignmentStat> stats = new ArrayList<>();
        Map<Long, List<Integer>> ratesByStudent = new HashMap<>();
        for (Assignment a : assignments.findByCourseIdOrderByDueAtAsc(courseId)) {
            AssignmentStat s = assignment(a, students, professor, now);
            stats.add(s);
            for (int h = 0; h < 24; h++) {
                hourTotal[h] += s.byHour().get(h);
            }
            s.students().stream().filter(r -> r.feedback().hasRate())
                    .forEach(r -> ratesByStudent.computeIfAbsent(r.studentId(), k -> new ArrayList<>()).add(r.feedbackRate()));
        }
        int avg = ratesByStudent.isEmpty() ? -1 : (int) Math.round(ratesByStudent.values().stream()
                .mapToDouble(l -> l.stream().mapToInt(Integer::intValue).average().orElse(0)).average().orElse(0));
        return new CourseStats(course.getId(), course.getName(), students.size(), stats,
                java.util.Arrays.stream(hourTotal).boxed().toList(), avg, ratesByStudent.size());
    }

    private AssignmentStat assignment(Assignment a, List<Enrollment> students, AppUser professor, LocalDateTime now) {
        List<Submission> all = submissions.findByAssignmentIdOrderBySubmittedAtAsc(a.getId());
        Map<Long, Submission> latest = new HashMap<>();
        Map<Long, Integer> versions = new HashMap<>();
        int[] byHour = new int[24];
        int lastDay = 0;
        for (Submission s : all) {
            Long sid = s.getStudent().getId();
            versions.merge(sid, 1, Integer::sum);
            Submission prev = latest.get(sid);
            if (prev == null || prev.getVersionNo() < s.getVersionNo()) {
                latest.put(sid, s);
            }
            byHour[s.getSubmittedAt().getHour()]++;
            LocalDateTime windowStart = a.getDueAt().minus(Duration.ofHours(24));
            if (!s.getSubmittedAt().isBefore(windowStart) && !s.getSubmittedAt().isAfter(a.getDueAt())) {
                lastDay++;
            }
        }
        Map<Long, FeedbackService.FeedbackSummary> fb = feedback.summaries(a.getId(), professor);

        List<StudentRow> rows = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        int late = 0;
        for (Enrollment e : students) {
            Long sid = e.getStudent().getId();
            Submission s = latest.get(sid);
            var summary = fb.get(sid);
            if (s == null) {
                missing.add(e.getStudent().getName());
            } else if (s.isLate()) {
                late++;
            }
            rows.add(new StudentRow(sid, e.getStudent().getName(), e.getStudent().getStudentNo(),
                    versions.getOrDefault(sid, 0), s != null, s != null && s.isLate(),
                    s == null ? null : grades.score(s.getId()),
                    summary == null ? FeedbackService.FeedbackSummary.empty() : summary));
        }
        int submitted = students.size() - missing.size();
        int rate = students.isEmpty() ? 0 : (int) Math.round(100.0 * submitted / students.size());
        int lastDayPercent = all.isEmpty() ? 0 : (int) Math.round(100.0 * lastDay / all.size());
        return new AssignmentStat(a.getId(), a.getTitle(), a.getDueAt(), a.isPastDue(now), students.size(),
                submitted, rate, late, missing, all.size(), lastDay, lastDayPercent,
                java.util.Arrays.stream(byHour).boxed().toList(), rows);
    }
}
