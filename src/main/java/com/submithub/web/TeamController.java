package com.submithub.web;

import com.submithub.domain.Assignment;
import com.submithub.domain.Course;
import com.submithub.domain.Team;
import com.submithub.security.AppUser;
import com.submithub.service.AssignmentService;
import com.submithub.service.TeamAuditService;
import com.submithub.service.TeamService;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class TeamController {

    @Getter
    @Setter
    public static class CreateTeamForm {
        @NotBlank(message = "팀명을 입력하세요.")
        private String name;
        private String repoUrl;
        private String branchName;
        private String roleTitle;
    }

    @Getter
    @Setter
    public static class JoinTeamForm {
        private String branchName;
        private String roleTitle;
    }

    private final TeamService teamService;
    private final TeamAuditService auditService;
    private final AssignmentService assignmentService;

    public TeamController(TeamService teamService, TeamAuditService auditService, AssignmentService assignmentService) {
        this.teamService = teamService;
        this.auditService = auditService;
        this.assignmentService = assignmentService;
    }

    @GetMapping("/assignments/{assignmentId}/teams")
    @PreAuthorize("isAuthenticated()")
    public String listTeams(@PathVariable Long assignmentId,
                            @AuthenticationPrincipal AppUser user,
                            Model model) {
        Assignment assignment = assignmentService.getAssignment(assignmentId, user);
        Course course = assignment.getCourse();
        List<Team> teams = teamService.getTeams(assignmentId);
        Team myTeam = user.isProfessor() ? null : teamService.getMyTeam(assignmentId, user.getId()).orElse(null);

        model.addAttribute("assignment", assignment);
        model.addAttribute("course", course);
        model.addAttribute("teams", teams);
        model.addAttribute("myTeam", myTeam);
        model.addAttribute("createTeamForm", new CreateTeamForm());
        model.addAttribute("joinTeamForm", new JoinTeamForm());
        return "teams/list";
    }

    @PostMapping("/assignments/{assignmentId}/teams")
    @PreAuthorize("hasRole('STUDENT')")
    public String createTeam(@PathVariable Long assignmentId,
                             @ModelAttribute CreateTeamForm form,
                             @AuthenticationPrincipal AppUser user,
                             RedirectAttributes ra) {
        try {
            teamService.createTeam(assignmentId, form.getName(), form.getRepoUrl(), user.getId(),
                    form.getBranchName(), form.getRoleTitle());
            ra.addFlashAttribute("successMessage", "팀 '" + form.getName() + "'이 성공적으로 생성되었습니다.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/assignments/" + assignmentId + "/teams";
    }

    @PostMapping("/teams/{teamId}/join")
    @PreAuthorize("hasRole('STUDENT')")
    public String joinTeam(@PathVariable Long teamId,
                           @ModelAttribute JoinTeamForm form,
                           @AuthenticationPrincipal AppUser user,
                           RedirectAttributes ra) {
        try {
            Team team = teamService.getTeam(teamId).orElseThrow();
            teamService.joinTeam(teamId, user.getId(), form.getBranchName(), form.getRoleTitle());
            ra.addFlashAttribute("successMessage", "'" + team.getName() + "' 팀에 참여했습니다.");
            return "redirect:/assignments/" + team.getAssignment().getId() + "/teams";
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/assignments";
        }
    }

    @GetMapping("/teams/{teamId}/audit")
    @PreAuthorize("isAuthenticated()")
    public String auditTeam(@PathVariable Long teamId,
                            @AuthenticationPrincipal AppUser user,
                            Model model) {
        Team team = teamService.getTeam(teamId)
                .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다: " + teamId));
        TeamAuditService.TeamAuditReport report = auditService.auditTeam(teamId, user);

        model.addAttribute("team", team);
        model.addAttribute("assignment", team.getAssignment());
        model.addAttribute("course", team.getAssignment().getCourse());
        model.addAttribute("report", report);
        return "teams/audit";
    }
}
