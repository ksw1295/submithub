package com.submithub.web;

import com.submithub.security.AppUser;
import com.submithub.service.BusinessException;
import com.submithub.service.GradeService;
import com.submithub.service.ReviewService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 줄 코멘트, 답글, "고쳤어요" 표시, 점수. 처리 후에는 원래 보던 화면으로 돌아간다. */
@Controller
public class ReviewController {

    private final ReviewService reviewService;
    private final GradeService gradeService;

    public ReviewController(ReviewService reviewService, GradeService gradeService) {
        this.reviewService = reviewService;
        this.gradeService = gradeService;
    }

    @PostMapping("/files/{id}/comments")
    public String addComment(@PathVariable Long id, @RequestParam int startLine, @RequestParam int endLine,
                             @RequestParam String body, @RequestParam(required = false) String back,
                             @AuthenticationPrincipal AppUser user) {
        Long commentId = reviewService.addComment(id, startLine, endLine, body, user);
        return "redirect:" + safeBack(back) + "#c" + commentId;
    }

    @PostMapping("/comments/{id}/replies")
    public String reply(@PathVariable Long id, @RequestParam String body,
                        @RequestParam(required = false) String back, @AuthenticationPrincipal AppUser user) {
        Long replyId = reviewService.reply(id, body, user);
        return "redirect:" + safeBack(back) + "#c" + replyId;
    }

    @PostMapping("/comments/{id}/resolve")
    public String resolve(@PathVariable Long id, @RequestParam(required = false) String back,
                          @AuthenticationPrincipal AppUser user) {
        reviewService.toggleResolved(id, user);
        return "redirect:" + safeBack(back) + "#c" + id;
    }

    @PostMapping("/submissions/{id}/grade")
    public String grade(@PathVariable Long id, @RequestParam(required = false) String score,
                        @RequestParam(required = false) String summary, @RequestParam(required = false) String back,
                        @AuthenticationPrincipal AppUser user, RedirectAttributes redirect) {
        Integer value;
        try {
            value = score == null || score.isBlank() ? null : Integer.valueOf(score.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("점수는 0~100 사이의 숫자로 입력하세요.");
        }
        gradeService.save(id, value, summary, user);
        redirect.addFlashAttribute("message", "점수와 총평을 저장했습니다.");
        return "redirect:" + safeBack(back);
    }

    /** 같은 사이트의 경로만 돌아갈 곳으로 허용한다 (open redirect 방지) */
    static String safeBack(String back) {
        if (back == null || back.isBlank() || !back.startsWith("/") || back.startsWith("//")
                || back.contains("\\") || back.contains("\r") || back.contains("\n") || back.contains("#")) {
            return "/";
        }
        return back;
    }
}
