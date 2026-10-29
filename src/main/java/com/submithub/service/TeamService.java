package com.submithub.service;

import com.submithub.domain.*;
import com.submithub.github.GitHubRepoRef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class TeamService {

    private static final Pattern BRANCH_PATTERN = Pattern.compile("^[A-Za-z0-9._/-]{1,100}$");

    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final AssignmentRepository assignments;
    private final UserRepository users;
    private final AccessService accessService;

    public TeamService(TeamRepository teams, TeamMemberRepository members,
                       AssignmentRepository assignments, UserRepository users,
                       AccessService accessService) {
        this.teams = teams;
        this.members = members;
        this.assignments = assignments;
        this.users = users;
        this.accessService = accessService;
    }

    public List<Team> getTeams(Long assignmentId) {
        return teams.findByAssignmentIdOrderByNameAsc(assignmentId);
    }

    public Optional<Team> getTeam(Long teamId) {
        return teams.findById(teamId);
    }

    public Optional<Team> getMyTeam(Long assignmentId, Long studentId) {
        return teams.findByAssignmentIdAndStudentId(assignmentId, studentId);
    }

    @Transactional
    public Team createTeam(Long assignmentId, String name, String repoUrl, Long leaderId,
                           String leaderBranch, String leaderRole) {
        Assignment assignment = assignments.findById(assignmentId)
                .orElseThrow(() -> new NotFoundException("과제를 찾을 수 없습니다: " + assignmentId));
        User leader = users.findById(leaderId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + leaderId));

        // 1. 수강 중인 학생인지 검사
        accessService.requireEnrolled(assignment.getCourse().getId(), leaderId);

        // 2. 조별 과제 여부 검사
        if (!assignment.isTeamProject()) {
            throw new IllegalStateException("조별 과제가 아닙니다.");
        }

        // 3. 마감 기한 검사
        if (!assignment.isOpen(LocalDateTime.now())) {
            throw new IllegalStateException("과제 제출 기한이 마감되었습니다.");
        }

        // 4. 브랜치 이름 형식 검사
        if (leaderBranch != null && !leaderBranch.isBlank()) {
            validateBranchName(leaderBranch);
        }

        // 5. 팀명 검사
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("팀명을 입력하세요.");
        }
        String trimmedName = name.trim();
        if (trimmedName.length() > 100) {
            throw new IllegalArgumentException("팀명은 1~100자 사이여야 합니다.");
        }

        // 6. repoUrl 검사 (비어 있거나 https://github.com/{owner}/{repo} 형식)
        String trimmedRepoUrl = (repoUrl == null || repoUrl.isBlank()) ? null : repoUrl.trim();
        if (trimmedRepoUrl != null) {
            try {
                GitHubRepoRef.parse(trimmedRepoUrl, "HEAD");
            } catch (BusinessException e) {
                throw new IllegalArgumentException(e.getMessage());
            }
        }

        if (teams.findByAssignmentIdAndStudentId(assignmentId, leaderId).isPresent()) {
            throw new IllegalStateException("이미 이 과제의 다른 팀에 참여하고 있습니다.");
        }
        if (teams.existsByAssignmentIdAndName(assignmentId, trimmedName)) {
            throw new IllegalArgumentException("이미 사용 중인 팀명입니다: " + trimmedName);
        }

        Team team = new Team(assignment, trimmedName, trimmedRepoUrl, leader);
        TeamMember leaderMember = new TeamMember(team, leader,
                leaderBranch == null || leaderBranch.isBlank() ? "main" : leaderBranch.trim(),
                leaderRole == null || leaderRole.isBlank() ? "팀장" : leaderRole.trim());
        team.addMember(leaderMember);
        return teams.save(team);
    }

    @Transactional
    public TeamMember joinTeam(Long teamId, Long studentId, String branchName, String roleTitle) {
        Team team = teams.findById(teamId)
                .orElseThrow(() -> new NotFoundException("팀을 찾을 수 없습니다: " + teamId));
        User student = users.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + studentId));

        Assignment a = team.getAssignment();

        // 1. 수강 중인 학생인지 검사
        accessService.requireEnrolled(a.getCourse().getId(), studentId);

        // 2. 조별 과제 여부 검사
        if (!a.isTeamProject()) {
            throw new IllegalStateException("조별 과제가 아닙니다.");
        }

        // 3. 마감 기한 검사
        if (!a.isOpen(LocalDateTime.now())) {
            throw new IllegalStateException("과제 제출 기한이 마감되었습니다.");
        }

        // 4. 브랜치 이름 형식 검사
        if (branchName != null && !branchName.isBlank()) {
            validateBranchName(branchName);
        }

        if (teams.findByAssignmentIdAndStudentId(a.getId(), studentId).isPresent()) {
            throw new IllegalStateException("이미 이 과제의 팀에 등록되어 있습니다.");
        }
        if (a.getMaxTeamSize() != null && team.getMembers().size() >= a.getMaxTeamSize()) {
            throw new IllegalStateException("팀 최대 인원(" + a.getMaxTeamSize() + "명)을 초과할 수 없습니다.");
        }

        TeamMember member = new TeamMember(team, student,
                branchName == null || branchName.isBlank() ? "feature/" + student.getLoginId() : branchName.trim(),
                roleTitle == null || roleTitle.isBlank() ? "팀원" : roleTitle.trim());
        team.addMember(member);
        return members.save(member);
    }

    @Transactional
    public void updateMember(Long teamId, Long studentId, String branchName, String roleTitle) {
        TeamMember member = members.findByTeamIdAndStudentId(teamId, studentId)
                .orElseThrow(() -> new NotFoundException("팀원을 찾을 수 없습니다."));
        if (branchName != null && !branchName.isBlank()) {
            validateBranchName(branchName);
            member.setBranchName(branchName.trim());
        }
        if (roleTitle != null && !roleTitle.isBlank()) {
            member.setRoleTitle(roleTitle.trim());
        }
    }

    @Transactional
    public void updateRepoUrl(Long teamId, String repoUrl) {
        Team team = teams.findById(teamId)
                .orElseThrow(() -> new NotFoundException("팀을 찾을 수 없습니다: " + teamId));
        String trimmed = (repoUrl == null || repoUrl.isBlank()) ? null : repoUrl.trim();
        if (trimmed != null) {
            try {
                GitHubRepoRef.parse(trimmed, "HEAD");
            } catch (BusinessException e) {
                throw new IllegalArgumentException(e.getMessage());
            }
        }
        team.setRepoUrl(trimmed);
    }

    private void validateBranchName(String branchName) {
        if (branchName == null || branchName.isBlank()) {
            return;
        }
        String trimmed = branchName.trim();
        if (!BRANCH_PATTERN.matcher(trimmed).matches() || trimmed.contains("..")) {
            throw new IllegalArgumentException("올바른 브랜치 이름 형식이 아닙니다.");
        }
    }
}
