package com.submithub.service;

import com.submithub.core.similarity.CodeTokenizer;
import com.submithub.core.similarity.SimilarityChecker;
import com.submithub.domain.*;
import com.submithub.github.GitHubRepoRef;
import com.submithub.security.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class TeamAuditService {

    private static final Set<String> SOURCE_EXTENSIONS = Set.of(
            ".java", ".py", ".js", ".ts", ".c", ".h", ".cpp", ".hpp",
            ".cs", ".kt", ".go", ".rs", ".rb", ".php", ".html", ".css", ".sql"
    );

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
            String healthBadgeClass,
            String branchStatus,
            String branchStatusClass,
            boolean duplicateWarning,
            int duplicatePercent
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

    @Value("${submithub.team.lead-threshold:40}")
    private int leadThreshold;

    @Value("${submithub.team.low-threshold:15}")
    private int lowThreshold;

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
        Map<String, String> memberCodeForSimilarity = new LinkedHashMap<>();

        int totalLines = 0;
        for (TeamMember m : teamMembers) {
            Long studentId = m.getStudent().getId();
            List<Submission> subs = submissions.findByAssignmentIdAndStudentIdOrderByVersionNoAsc(assignment.getId(), studentId);
            memberSubmissions.put(studentId, subs);

            int lines = 0;
            if (!subs.isEmpty()) {
                Submission latest = subs.get(subs.size() - 1);
                lines = calculateValidCodeLines(latest);

                StringBuilder sb = new StringBuilder();
                if (latest.getFiles() != null) {
                    for (SubmissionFile f : latest.getFiles()) {
                        if (isSourceFile(f.getPath()) && f.getContentText() != null) {
                            sb.append(f.getContentText()).append("\n");
                        }
                    }
                }
                if (!sb.isEmpty()) {
                    memberCodeForSimilarity.put(String.valueOf(studentId), sb.toString());
                }
            }
            memberCodeLines.put(studentId, lines);
            totalLines += lines;
        }

        // P3-A: 팀원 간 최신 제출물 중복도 계산 (70% 이상 시 경고)
        Map<Long, Integer> peerDuplicatePercents = new HashMap<>();
        if (memberCodeForSimilarity.size() >= 2) {
            var pairs = SimilarityChecker.compareAll(memberCodeForSimilarity, assignment.getBaselineCode(), 0.70);
            for (var p : pairs) {
                Long idA = Long.parseLong(p.idA());
                Long idB = Long.parseLong(p.idB());
                int pctA = (int) Math.round(p.coverageA() * 100);
                int pctB = (int) Math.round(p.coverageB() * 100);
                peerDuplicatePercents.merge(idA, pctA, Math::max);
                peerDuplicatePercents.merge(idB, pctB, Math::max);
            }
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
            String branchStatus;
            String branchStatusClass;

            if (!subs.isEmpty()) {
                Submission latest = subs.get(subs.size() - 1);
                latestSubId = latest.getId();
                latestCommit = latest.getSourceCommit();
                score = grades.findBySubmissionId(latestSubId).map(Grade::getScore).orElse(null);

                // P2-1: 브랜치/저장소 일치 검사
                if (!latest.fromGitHub()) {
                    branchStatus = "출처 미확인 (직접 업로드)";
                    branchStatusClass = "text-bg-secondary";
                } else {
                    String subRepo = normalizeRepo(latest.getSourceRepo());
                    String teamRepo = normalizeRepo(team.getRepoUrl());
                    if (!subRepo.isEmpty() && subRepo.equals(teamRepo)) {
                        String ref = latest.getSourceRef();
                        if (ref == null || ref.isBlank() || "HEAD".equals(ref) || GitHubRepoRef.isSha(ref)) {
                            // SHA 로만 냈거나 기본 브랜치(이름을 모름)로 낸 경우: 어느 브랜치인지 알 수 없다
                            branchStatus = "브랜치 확인 불가";
                            branchStatusClass = "text-bg-secondary";
                        } else if (ref.equals(m.getBranchName())) {
                            branchStatus = "브랜치 일치";
                            branchStatusClass = "text-bg-success";
                        } else {
                            branchStatus = "브랜치 불일치";
                            branchStatusClass = "text-bg-danger";
                        }
                    } else {
                        branchStatus = "저장소 불일치";
                        branchStatusClass = "text-bg-danger";
                    }
                }
            } else {
                branchStatus = "미제출";
                branchStatusClass = "text-bg-light text-muted border";
            }

            // 학생이 조회할 때 다른 팀원의 점수는 숨긴다 (null)
            if (score != null && !isCourseProfessor && !sId.equals(currentUser.getId())) {
                score = null;
            }

            int lines = memberCodeLines.get(sId);
            // P2-4: 총합이 0이면 모든 팀원 기여율 0%
            int contribPercent = (totalLines > 0) ? Math.round((float) lines * 100 / totalLines) : 0;

            var fb = feedbackService.computeUnchecked(assignment.getId(), sId);
            int fbTotal = fb.summary().total();
            int fbRem = fb.summary().remaining();
            int fbRate = fb.summary().ratePercent();

            if (fb.summary().hasRate()) {
                totalFeedbackRateSum += fbRate;
                feedbackCountedMembers++;
            }

            // P2-3: Health badge logic (leadThreshold=40, lowThreshold=15)
            String badge;
            String badgeClass;
            if (versionCount == 0) {
                badge = "미제출 (활동 없음)";
                badgeClass = "text-bg-danger";
                hasImbalance = true;
            } else if (teamMembers.size() >= 2 && contribPercent < lowThreshold) {
                badge = "기여도 저조 (주의)";
                badgeClass = "text-bg-warning";
                hasImbalance = true;
            } else if (teamMembers.size() >= 3 && contribPercent >= leadThreshold) {
                badge = "주도적 핵심 기여";
                badgeClass = "text-bg-primary";
            } else {
                badge = "정상 균형 기여";
                badgeClass = "text-bg-success";
            }

            int dupPct = peerDuplicatePercents.getOrDefault(sId, 0);
            boolean dupWarning = dupPct >= 70;

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
                    badgeClass,
                    branchStatus,
                    branchStatusClass,
                    dupWarning,
                    dupPct
            ));
        }

        int avgFeedbackRate = (feedbackCountedMembers > 0) ? Math.round((float) totalFeedbackRateSum / feedbackCountedMembers) : -1;

        String collabStatus;
        String collabClass;
        String collabSummary;

        // P3-A: 완화된 표현 적용 ("무임승차 의심" -> "활동 저조 — 확인 필요")
        if (hasImbalance) {
            collabStatus = "불균형 감지 (활동 저조 — 확인 필요)";
            collabClass = "text-bg-warning";
            collabSummary = "특정 팀원의 코드 분량 또는 제출 활동이 저조합니다. 브랜치 활동 이력을 확인하세요.";
        } else {
            collabStatus = "매우 원활 (협업 우수)";
            collabClass = "text-bg-success";
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

    private boolean isSourceFile(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase();
        return SOURCE_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private String normalizeRepo(String urlOrRepo) {
        if (urlOrRepo == null || urlOrRepo.isBlank()) return "";
        String s = urlOrRepo.trim();
        s = s.replaceAll("^https?://(www\\.)?github\\.com/", "");
        s = s.replaceAll("\\.git$", "");
        s = s.replaceAll("^/+|/+$", "");
        return s.toLowerCase();
    }

    private int calculateValidCodeLines(Submission s) {
        if (s == null || s.getFiles() == null) {
            return 0;
        }
        int count = 0;
        for (SubmissionFile f : s.getFiles()) {
            if (!isSourceFile(f.getPath())) {
                continue;
            }
            String text = f.getContentText();
            if (text == null || text.isBlank()) {
                continue;
            }
            var tokens = CodeTokenizer.tokenize(text);
            count += (int) tokens.stream().mapToInt(CodeTokenizer.Token::line).distinct().count();
        }
        return count;
    }
}
