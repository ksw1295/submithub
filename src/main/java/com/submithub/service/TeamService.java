package com.submithub.service;

import com.submithub.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class TeamService {

    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final AssignmentRepository assignments;
    private final UserRepository users;

    public TeamService(TeamRepository teams, TeamMemberRepository members,
                       AssignmentRepository assignments, UserRepository users) {
        this.teams = teams;
        this.members = members;
        this.assignments = assignments;
        this.users = users;
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
                .orElseThrow(() -> new IllegalArgumentException("과제를 찾을 수 없습니다: " + assignmentId));
        User leader = users.findById(leaderId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + leaderId));

        if (teams.findByAssignmentIdAndStudentId(assignmentId, leaderId).isPresent()) {
            throw new IllegalStateException("이미 이 과제의 다른 팀에 참여하고 있습니다.");
        }
        if (teams.existsByAssignmentIdAndName(assignmentId, name)) {
            throw new IllegalArgumentException("이미 사용 중인 팀명입니다: " + name);
        }

        Team team = new Team(assignment, name.trim(), repoUrl == null ? null : repoUrl.trim(), leader);
        TeamMember leaderMember = new TeamMember(team, leader,
                leaderBranch == null || leaderBranch.isBlank() ? "main" : leaderBranch.trim(),
                leaderRole == null || leaderRole.isBlank() ? "팀장" : leaderRole.trim());
        team.addMember(leaderMember);
        return teams.save(team);
    }

    @Transactional
    public TeamMember joinTeam(Long teamId, Long studentId, String branchName, String roleTitle) {
        Team team = teams.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다: " + teamId));
        User student = users.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + studentId));

        Assignment a = team.getAssignment();
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
                .orElseThrow(() -> new IllegalArgumentException("팀원을 찾을 수 없습니다."));
        if (branchName != null && !branchName.isBlank()) {
            member.setBranchName(branchName.trim());
        }
        if (roleTitle != null && !roleTitle.isBlank()) {
            member.setRoleTitle(roleTitle.trim());
        }
    }

    @Transactional
    public void updateRepoUrl(Long teamId, String repoUrl) {
        Team team = teams.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다: " + teamId));
        team.setRepoUrl(repoUrl == null ? null : repoUrl.trim());
    }
}
