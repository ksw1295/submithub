package com.submithub.web;

import com.submithub.domain.GradingLanguage;
import com.submithub.security.AppUser;
import com.submithub.service.AssignmentService;
import com.submithub.service.BusinessException;
import com.submithub.service.GradingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 자동 채점 설정(테스트 케이스, 실행 설정)과 재채점. 모두 해당 강의 교수만 */
@Controller
@PreAuthorize("hasRole('PROFESSOR')")
public class GradingController {

    private final GradingService gradingService;
    private final AssignmentService assignmentService;

    public GradingController(GradingService gradingService, AssignmentService assignmentService) {
        this.gradingService = gradingService;
        this.assignmentService = assignmentService;
    }

    @GetMapping("/assignments/{id}/grading")
    public String page(@PathVariable Long id, @AuthenticationPrincipal AppUser user, Model model) {
        model.addAttribute("assignment", assignmentService.view(id, user));
        model.addAttribute("cfg", gradingService.config(id, user));
        model.addAttribute("languages", GradingLanguage.values());
        return "grading/config";
    }

    @PostMapping("/assignments/{id}/grading/config")
    public String saveConfig(@PathVariable Long id, @RequestParam String language, @RequestParam String mainFile,
                             @RequestParam String timeLimitSeconds, @RequestParam String memoryLimitMb,
                             @AuthenticationPrincipal AppUser user, RedirectAttributes redirect) {
        GradingLanguage lang;
        try {
            lang = GradingLanguage.valueOf(language);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("지원하지 않는 언어입니다.");
        }
        gradingService.saveConfig(id, user, new GradingService.ConfigInput(lang, mainFile,
                number(timeLimitSeconds, "시간 제한"), number(memoryLimitMb, "메모리 제한")));
        redirect.addFlashAttribute("message", "실행 설정을 저장했습니다.");
        return "redirect:/assignments/" + id + "/grading";
    }

    @PostMapping("/assignments/{id}/grading/cases")
    public String addCase(@PathVariable Long id, @RequestParam String name,
                          @RequestParam(required = false) String input, @RequestParam String expected,
                          @RequestParam(defaultValue = "false") boolean publicCase, @RequestParam String points,
                          @AuthenticationPrincipal AppUser user, RedirectAttributes redirect) {
        gradingService.addCase(id, user, new GradingService.CaseInput(name, input, expected, publicCase, number(points, "배점")));
        redirect.addFlashAttribute("message", "테스트 케이스를 추가했습니다.");
        return "redirect:/assignments/" + id + "/grading";
    }

    @PostMapping("/assignments/{id}/grading/cases/{caseId}/delete")
    public String deleteCase(@PathVariable Long id, @PathVariable Long caseId, @AuthenticationPrincipal AppUser user,
                             RedirectAttributes redirect) {
        gradingService.deleteCase(id, caseId, user);
        redirect.addFlashAttribute("message", "테스트 케이스를 삭제했습니다. 이미 채점된 결과는 그대로 남고, 다시 채점하면 반영됩니다.");
        return "redirect:/assignments/" + id + "/grading";
    }

    /** 과제의 모든 제출을 다시 채점한다 (비동기) */
    @PostMapping("/assignments/{id}/grading/regrade")
    public String regradeAll(@PathVariable Long id, @AuthenticationPrincipal AppUser user, RedirectAttributes redirect) {
        gradingService.regradeAll(id, user);
        redirect.addFlashAttribute("message", "모든 제출을 다시 채점하고 있습니다. 잠시 뒤 화면을 새로 고치면 결과가 보입니다.");
        return "redirect:/assignments/" + id + "/grading";
    }

    @PostMapping("/submissions/{id}/regrade")
    public String regrade(@PathVariable Long id, @RequestParam(required = false) String back,
                          @AuthenticationPrincipal AppUser user, RedirectAttributes redirect) {
        gradingService.regrade(id, user);
        redirect.addFlashAttribute("message", "다시 채점하고 있습니다. 잠시 뒤 화면을 새로 고치면 결과가 보입니다.");
        return "redirect:" + ReviewController.safeBack(back);
    }

    private static int number(String text, String label) {
        try {
            return Integer.parseInt(text == null ? "" : text.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(label + "은(는) 숫자로 입력하세요.");
        }
    }
}
