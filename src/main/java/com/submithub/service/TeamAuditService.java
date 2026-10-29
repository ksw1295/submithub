package com.submithub.service;

import com.submithub.domain.*;
import com.submithub.security.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class TeamAuditService {

    public record MemberAudit(
            Long studentId,
            String studentName,
            String studentNum,
            String loginId,
            boolean leader,
            String roleTitle,
            String branchName,
            int versionCount,
            Long latestSubmissionId,
            String latestCommitSha,
            int validCodeLines,
            int contributionPercent,
            Integer score,
            int feedbackTotal,
            int feedbackRemaining,
            int feedbackRatePercent,
            String healthBadge,
            String healthBadgeClass
    ) {}

    public record TeamAuditReport(
            Long teamId,
            String teamName,
            Long assignmentId,
            String assignmentTitle,
            String repoUrl,
            int memberCount,
            int totalValidCodeLines,
            int avgFeedbackRate,
            String collaborationStatus,
            String collaborationClass,
            String collaborationSummary,
            List<MemberAudit> members
    ) {}

    private final TeamRepository teams;
    private final SubmissionRepository submissions;
    private final FeedbackService feedbackService;
    private final GradeRepository grades;

    public TeamAuditService(TeamRepository teams, SubmissionRepository submissions,
                            FeedbackService feedbackService, GradeRepository grades) {
        this.teams = teams;
        this.submissions = submissions;
        this.feedbackService = feedbackService;
        this.grades = grades;
    }

    public TeamAuditReport auditTeam(Long teamId, AppUser currentUser) {
        Team team = teams.findById(teamId)
                .orElseThrow(() -> new NotFoundException("팀을 찾을 수 없습니다: " + teamId));
        Assignment assignment = team.getAssignment();
        Course course = assignment.getCourse();

        if (currentUser == null) {
            throw new ForbiddenException("로그인이 필요합니다.");
        }

        boolean isCourseProfessor = currentUser.isProfessor() && course.getProfessor().getId().equals(currentUser.getId());
        boolean isTeamMember = !currentUser.isProfessor() && team.getMembers().stream()
                .anyMatch(m -> m.getStudent().getId().equals(currentUser.getId()));

        if (!isCourseProfessor && !isTeamMember) {
            if (currentUser.isProfessor()) {
                throw new ForbiddenException("다른 교수의 강의입니다.");
            } else {
                throw new ForbiddenException("해당 팀의 팀원만 감사 리포트를 조회할 수 있습니다.");
            }
        }

        List<TeamMember> teamMembers = team.getMembers();

        Map<Long, List<Submission>> memberSubmissions = new LinkedHashMap<>();
        Map<Long, Integer> memberCodeLines = new LinkedHashMap<>();

        int totalLines = 0;
        for (TeamMember m : teamMembers) {
            Long studentId = m.getStudent().getId();
            List<Submission> subs = submissions.findByAssignmentIdAndStudentIdOrderByVersionNoAsc(assignment.getId(), studentId);
            memberSubmissions.put(studentId, subs);

            int lines = 0;
            if (!subs.isEmpty()) {
                Submission latest = subs.get(subs.size() - 1);
                lines = calculateValidCodeLines(latest);
            }
            memberCodeLines.put(studentId, lines);
            totalLines += lines;
        }

        List<MemberAudit> memberAudits = new ArrayList<>();
        int totalFeedbackRateSum = 0;
        int feedbackCountedMembers = 0;
        boolean hasImbalance = false;

        for (TeamMember m : teamMembers) {
            User student = m.getStudent();
            Long sId = student.getId();
            boolean isLeader = team.getLeader().getId().equals(sId);
            List<Submission> subs = memberSubmissions.get(sId);

            int versionCount = subs.size();
            Long latestSubId = null;
            String latestCommit = null;
            Integer score = null;

            if (!subs.isEmpty()) {
                Submission latest = subs.get(subs.size() - 1);
                latestSubId = latest.getId();
                latestCommit = latest.getSourceCommit();
                score = grades.findBySubmissionId(latestSubId).map(Grade::getScore).orElse(null);
            }

            // 학생이 조회할 때 다른 팀원의 점수는 숨긴다 (null)
            if (score != null && !isCourseProfessor && !sId.equals(currentUser.getId())) {
                score = null;
            }

            int lines = memberCodeLines.get(sId);
            int contribPercent = (totalLines > 0) ? Math.round((float) lines * 100 / totalLines) : (100 / Math.max(1, teamMembers.size()));

            var fb = feedbackService.computeUnchecked(assignment.getId(), sId);
            int fbTotal = fb.summary().total();
            int fbRem = fb.summary().remaining();
            int fbRate = fb.summary().ratePercent();

            if (fb.summary().hasRate()) {
                totalFeedbackRateSum += fbRate;
                feedbackCountedMembers++;
            }

            // Health badge logic
            String badge;
            String badgeClass;
            if (versionCount == 0) {
                badge = "미제출 (활동 없음)";
                badgeClass = "badge-danger";
                hasImbalance = true;
            } else if (teamMembers.size() >= 2 && contribPercent < 15) {
                badge = "기여도 저조 (주의)";
                badgeClass = "badge-warning";
                hasImbalance = true;
            } else if (teamMembers.size() >= 3 && contribPercent >= 45) {
                badge = "주도적 핵심 기여";
                badgeClass = "badge-primary";
            } else {
                badge = "정상 균형 기여";
                badgeClass = "badge-success";
            }

            memberAudits.add(new MemberAudit(
                    sId,
                    student.getName(),
                    student.getStudentNo(),
                    student.getLoginId(),
                    isLeader,
                    m.getRoleTitle(),
                    m.getBranchName(),
                    versionCount,
                    latestSubId,
                    latestCommit,
                    lines,
                    contribPercent,
                    score,
                    fbTotal,
                    fbRem,
                    fbRate,
                    badge,
                    badgeClass
            ));
        }

        int avgFeedbackRate = (feedbackCountedMembers > 0) ? Math.round((float) totalFeedbackRateSum / feedbackCountedMembers) : -1;

        String collabStatus;
        String collabClass;
        String collabSummary;

        if (hasImbalance) {
            collabStatus = "불균형 감지 (무임승차 주의)";
            collabClass = "badge-warning";
            collabSummary = "특정 팀원의 코드 분량 또는 제출 활동이 저조합니다. 브랜치 활동 이력을 확인하세요.";
        } else {
            collabStatus = "매우 원활 (협업 우수)";
            collabClass = "badge-success";
            collabSummary = "모든 팀원이 담당 브랜치에서 균형 있게 코드를 개발하고 피드백을 충실히 반영하고 있습니다.";
        }

        return new TeamAuditReport(
                team.getId(),
                team.getName(),
                assignment.getId(),
                assignment.getTitle(),
                team.getRepoUrl(),
                teamMembers.size(),
                totalLines,
                avgFeedbackRate,
                collabStatus,
                collabClass,
                collabSummary,
                memberAudits
        );
    }

    private int calculateValidCodeLines(Submission s) {
        if (s == null || s.getFiles() == null) {
            return 0;
        }
        int count = 0;
        for (SubmissionFile f : s.getFiles()) {
            String text = f.getContentText();
            if (text == null || text.isBlank()) {
                continue;
            }
            String[] lines = text.split("\r?\n");
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("//") || line.startsWith("#") || line.startsWith("/*") || line.startsWith("*")) {
                    continue;
                }
                count++;
            }
        }
        return count;
    }
}
