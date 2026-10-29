package com.submithub.web;

import com.submithub.security.AppUser;
import com.submithub.service.AiJudgeService;
import com.submithub.service.BusinessException;
import com.submithub.service.DiffService;
import com.submithub.service.FeedbackService;
import com.submithub.service.GradingService;
import com.submithub.service.SubmissionService;
import com.submithub.storage.UploadProcessor.Upload;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Controller
public class SubmissionController {

    private final SubmissionService submissionService;
    private final DiffService diffService;
    private final FeedbackService feedbackService;
    private final AiJudgeService aiJudge;
    private final GradingService gradingService;

    public SubmissionController(SubmissionService submissionService, DiffService diffService,
                                FeedbackService feedbackService, AiJudgeService aiJudge,
                                GradingService gradingService) {
        this.submissionService = submissionService;
        this.diffService = diffService;
        this.feedbackService = feedbackService;
        this.aiJudge = aiJudge;
        this.gradingService = gradingService;
    }

    @PostMapping("/assignments/{id}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public String submit(@PathVariable Long id, @AuthenticationPrincipal AppUser user,
                         @RequestParam(value = "files", required = false) List<MultipartFile> files,
                         @RequestParam(value = "message", required = false) String message,
                         RedirectAttributes redirect) {
        List<Upload> uploads = new ArrayList<>();
        try {
            for (MultipartFile f : files == null ? List.<MultipartFile>of() : files) {
                if (!f.isEmpty()) {
                    uploads.add(new Upload(f.getOriginalFilename(), f.getBytes()));
                }
            }
        } catch (IOException e) {
            throw new BusinessException("업로드한 파일을 읽지 못했습니다.");
        }
        var result = submissionService.submit(id, user, uploads, message);
        redirect.addFlashAttribute("message", "v" + result.versionNo() + " 제출이 완료되었습니다."
                + (result.late() ? " (지각 제출)" : ""));
        return "redirect:/submissions/" + result.id();
    }

    @PostMapping("/assignments/{id}/submit/github")
    @PreAuthorize("hasRole('STUDENT')")
    public String submitGitHub(@PathVariable Long id, @AuthenticationPrincipal AppUser user,
                               @RequestParam(value = "repoUrl", required = false) String repoUrl,
                               @RequestParam(value = "ref", required = false) String ref,
                               @RequestParam(value = "message", required = false) String message,
                               RedirectAttributes redirect) {
        var result = submissionService.submitFromGitHub(id, user, repoUrl, ref, message);
        redirect.addFlashAttribute("message", "GitHub 커밋을 v" + result.versionNo() + " 로 제출했습니다."
                + (result.late() ? " (지각 제출)" : ""));
        return "redirect:/submissions/" + result.id();
    }

    @PostMapping("/assignments/{id}/submit/my-branch")
    @PreAuthorize("hasRole('STUDENT')")
    public String submitMyBranch(@PathVariable Long id, @AuthenticationPrincipal AppUser user,
                                 @RequestParam(value = "message", required = false) String message,
                                 RedirectAttributes redirect) {
        var result = submissionService.submitFromMyBranch(id, user, message);
        redirect.addFlashAttribute("message", "내 브랜치 최신 커밋을 v" + result.versionNo() + " 로 제출했습니다."
                + (result.late() ? " (지각 제출)" : ""));
        return "redirect:/submissions/" + result.id();
    }

    @GetMapping("/assignments/{aid}/students/{sid}")
    public String history(@PathVariable Long aid, @PathVariable Long sid, @AuthenticationPrincipal AppUser user,
                          Model model, HttpServletRequest request) {
        model.addAttribute("history", submissionService.history(aid, sid, user));
        model.addAttribute("feedback", feedbackService.forStudent(aid, sid, user));
        model.addAttribute("canComment", user.isProfessor());
        model.addAttribute("aiEnabled", user.isProfessor() && aiJudge.enabledForAssignment(aid));
        model.addAttribute("trend", gradingService.trend(aid, sid, user));
        model.addAttribute("back", currentUrl(request));
        return "submissions/history";
    }

    @GetMapping("/submissions/{id}")
    public String view(@PathVariable Long id, @RequestParam(value = "file", required = false) Long fileId,
                       @AuthenticationPrincipal AppUser user, Model model, HttpServletRequest request) {
        model.addAttribute("sub", submissionService.view(id, fileId, user));
        model.addAttribute("grading", gradingService.forSubmission(id, user));
        model.addAttribute("canComment", user.isProfessor());
        model.addAttribute("back", currentUrl(request));
        return "submissions/view";
    }

    /** 임의의 두 버전 비교: from/to 는 제출 ID, mode=split|unified, ws=1 공백 무시, fold=0 이면 전체 펼치기 */
    @GetMapping("/assignments/{aid}/students/{sid}/diff")
    public String diff(@PathVariable Long aid, @PathVariable Long sid,
                       @RequestParam(required = false) Long from, @RequestParam(required = false) Long to,
                       @RequestParam(required = false) String file, @RequestParam(required = false) String mode,
                       @RequestParam(required = false) String ws, @RequestParam(required = false) String fold,
                       @AuthenticationPrincipal AppUser user, Model model, HttpServletRequest request) {
        model.addAttribute("diff", diffService.compare(aid, sid, from, to, file, mode,
                "1".equals(ws), !"0".equals(fold), user));
        model.addAttribute("canComment", user.isProfessor());
        model.addAttribute("back", currentUrl(request));
        return "submissions/diff";
    }

    private static String currentUrl(HttpServletRequest request) {
        String q = request.getQueryString();
        return request.getRequestURI() + (q == null ? "" : "?" + q);
    }

    @GetMapping("/files/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id, @AuthenticationPrincipal AppUser user) {
        var d = submissionService.download(id, user);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(d.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(d.bytes());
    }
}
