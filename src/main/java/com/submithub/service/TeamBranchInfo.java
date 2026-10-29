package com.submithub.service;

import com.submithub.domain.Team;
import com.submithub.domain.TeamMember;

/**
 * 조별 과제에서 학생의 팀 및 본인 담당 브랜치 등록 정보 DTO
 */
public record TeamBranchInfo(
        boolean hasTeam,
        String teamName,
        String repoUrl,
        String branchName,
        boolean readyToSubmit
) {
    public static TeamBranchInfo none() {
        return new TeamBranchInfo(false, null, null, null, false);
    }

    public static TeamBranchInfo of(Team team, TeamMember member) {
        boolean hasRepo = team != null && team.getRepoUrl() != null && !team.getRepoUrl().isBlank();
        boolean hasBranch = member != null && member.getBranchName() != null && !member.getBranchName().isBlank();
        return new TeamBranchInfo(
                team != null,
                team != null ? team.getName() : null,
                team != null ? team.getRepoUrl() : null,
                member != null ? member.getBranchName() : null,
                hasRepo && hasBranch
        );
    }
}
