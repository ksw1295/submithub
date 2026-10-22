package com.submithub.web;

import com.submithub.security.AppUser;
import com.submithub.service.AssignmentService;
import com.submithub.service.BusinessException;
import com.submithub.service.SimilarityService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 표절 검사: 교수만. 실행 → 유사도 순위 → 쌍 비교 화면 */
@Controller
@PreAuthorize("hasRole('PROFESSOR')")
public class SimilarityController {

    private final SimilarityService similarityService;
    private final AssignmentService assignmentService;

    public SimilarityController(SimilarityService similarityService, AssignmentService assignmentService) {
        this.similarityService = similarityService;
        this.assignmentService = assignmentService;
    }

    @GetMapping("/assignments/{id}/similarity")
    public String index(@PathVariable Long id, @AuthenticationPrincipal AppUser user, Model model) {
        model.addAttribute("assignment", assignmentService.view(id, user));
        model.addAttribute("run", similarityService.latest(id, user));
        model.addAttribute("defaultPercent", Math.round(SimilarityService.DEFAULT_THRESHOLD * 100));
        return "similarity/index";
    }

    @PostMapping("/assignments/{id}/similarity")
    public String run(@PathVariable Long id, @RequestParam(defaultValue = "60") String percent,
                      @AuthenticationPrincipal AppUser user, RedirectAttributes redirect) {
        double threshold;
        try {
            threshold = Double.parseDouble(percent.trim()) / 100.0;
        } catch (NumberFormatException e) {
            throw new BusinessException("기준 유사도는 숫자(5~100)로 입력하세요.");
        }
        var result = similarityService.run(id, threshold, user);
        redirect.addFlashAttribute("message", "표절 검사를 실행했습니다. 의심 쌍 " + result.pairs().size() + "개");
        return "redirect:/assignments/" + id + "/similarity";
    }

    @GetMapping("/similarity/pairs/{id}")
    public String pair(@PathVariable Long id, @AuthenticationPrincipal AppUser user, Model model) {
        model.addAttribute("pair", similarityService.pair(id, user));
        return "similarity/pair";
    }
}
