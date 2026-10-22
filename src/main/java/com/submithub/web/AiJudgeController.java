package com.submithub.web;

import com.submithub.domain.AiDecision;
import com.submithub.security.AppUser;
import com.submithub.service.AiJudgeService;
import com.submithub.service.BusinessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** AI 반영 판정: 강의 설정, 교수의 실행 버튼, 수락/무시. 모두 해당 강의 교수만 */
@Controller
@PreAuthorize("hasRole('PROFESSOR')")
public class AiJudgeController {

    private final AiJudgeService aiJudge;

    public AiJudgeController(AiJudgeService aiJudge) {
        this.aiJudge = aiJudge;
    }

    @PostMapping("/courses/{id}/ai-judge")
    public String setEnabled(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean enabled,
                             @AuthenticationPrincipal AppUser user, RedirectAttributes redirect) {
        aiJudge.setEnabled(id, enabled, user);
        redirect.addFlashAttribute("message", enabled
                ? "AI 판정을 켰습니다. 이제 코멘트 주변의 학생 코드가 외부 AI 서비스로 전송됩니다."
                : "AI 판정을 껐습니다.");
        return "redirect:/courses/" + id;
    }

    /** 한 학생의 한 과제에 대해 비동기로 AI 판정을 시작한다. 결과는 잠시 뒤 화면을 새로 고치면 보인다 */
    @PostMapping("/assignments/{aid}/students/{sid}/ai-judge")
    public String run(@PathVariable Long aid, @PathVariable Long sid, @RequestParam(required = false) String back,
                      @AuthenticationPrincipal AppUser user, RedirectAttributes redirect) {
        aiJudge.request(aid, sid, user);
        redirect.addFlashAttribute("message", "AI 판정을 시작했습니다. 잠시 뒤 화면을 새로 고치면 결과가 보입니다.");
        return "redirect:" + ReviewController.safeBack(back);
    }

    @PostMapping("/ai-judgements/{id}/decision")
    public String decide(@PathVariable Long id, @RequestParam String decision,
                         @RequestParam(required = false) String back, @AuthenticationPrincipal AppUser user) {
        AiDecision d;
        try {
            d = AiDecision.valueOf(decision);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("알 수 없는 결정입니다.");
        }
        aiJudge.decide(id, d, user);
        return "redirect:" + ReviewController.safeBack(back);
    }
}
