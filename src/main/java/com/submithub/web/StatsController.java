package com.submithub.web;

import com.submithub.security.AppUser;
import com.submithub.service.StatsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/courses/{id}/stats")
    @PreAuthorize("hasRole('PROFESSOR')")
    public String stats(@PathVariable Long id, @AuthenticationPrincipal AppUser user, Model model) {
        model.addAttribute("stats", statsService.course(id, user));
        return "courses/stats";
    }
}
