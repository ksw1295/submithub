package com.submithub.web;

import com.submithub.domain.Assignment;
import com.submithub.security.AppUser;
import com.submithub.service.AssignmentService;
import com.submithub.service.AssignmentService.AssignmentInput;
import com.submithub.service.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDateTime;

@Controller
public class AssignmentController {

    @Getter
    @Setter
    public static class AssignmentForm {
        @NotBlank(message = "과제 제목을 입력하세요.")
        @Size(max = 200, message = "제목은 200자 이하입니다.")
        private String title;

        @Size(max = 4000, message = "설명은 4000자 이하입니다.")
        private String description;

        @NotNull(message = "마감 일시를 입력하세요.")
        @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
        private LocalDateTime dueAt;

        private boolean allowLate;

        @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
        private LocalDateTime lateUntil;

        private String baselineCode;

        private MultipartFile baselineFile;
    }

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping("/assignments/{id}")
    public String detail(@PathVariable Long id, @AuthenticationPrincipal AppUser user, Model model) {
        model.addAttribute("assignment", assignmentService.view(id, user));
        return "assignments/detail";
    }

    @GetMapping("/courses/{courseId}/assignments/new")
    @PreAuthorize("hasRole('PROFESSOR')")
    public String newForm(@PathVariable Long courseId, Model model) {
        AssignmentForm form = new AssignmentForm();
        form.setDueAt(LocalDateTime.now().plusDays(7).withMinute(0).withSecond(0).withNano(0));
        model.addAttribute("form", form);
        model.addAttribute("courseId", courseId);
        model.addAttribute("assignmentId", null);
        return "assignments/form";
    }

    @PostMapping("/courses/{courseId}/assignments/new")
    @PreAuthorize("hasRole('PROFESSOR')")
    public String create(@PathVariable Long courseId, @AuthenticationPrincipal AppUser user,
                         @Valid @ModelAttribute("form") AssignmentForm form, BindingResult result,
                         Model model, RedirectAttributes redirect) {
        model.addAttribute("courseId", courseId);
        model.addAttribute("assignmentId", null);
        if (result.hasErrors()) {
            return "assignments/form";
        }
        try {
            Assignment a = assignmentService.create(courseId, user, toInput(form));
            redirect.addFlashAttribute("message", "과제를 등록했습니다.");
            return "redirect:/assignments/" + a.getId();
        } catch (BusinessException e) {
            result.reject("create", e.getMessage());
            return "assignments/form";
        }
    }

    @GetMapping("/assignments/{id}/edit")
    @PreAuthorize("hasRole('PROFESSOR')")
    public String editForm(@PathVariable Long id, @AuthenticationPrincipal AppUser user, Model model) {
        Assignment a = assignmentService.editable(id, user);
        AssignmentForm form = new AssignmentForm();
        form.setTitle(a.getTitle());
        form.setDescription(a.getDescription());
        form.setDueAt(a.getDueAt());
        form.setAllowLate(a.isAllowLate());
        form.setLateUntil(a.getLateUntil());
        model.addAttribute("form", form);
        model.addAttribute("courseId", a.getCourse().getId());
        model.addAttribute("assignmentId", id);
        model.addAttribute("hasBaseline", a.getBaselineCode() != null);
        return "assignments/form";
    }

    @PostMapping("/assignments/{id}/edit")
    @PreAuthorize("hasRole('PROFESSOR')")
    public String edit(@PathVariable Long id, @AuthenticationPrincipal AppUser user,
                       @Valid @ModelAttribute("form") AssignmentForm form, BindingResult result,
                       Model model, RedirectAttributes redirect) {
        Assignment current = assignmentService.editable(id, user);
        model.addAttribute("courseId", current.getCourse().getId());
        model.addAttribute("assignmentId", id);
        model.addAttribute("hasBaseline", current.getBaselineCode() != null);
        if (result.hasErrors()) {
            return "assignments/form";
        }
        try {
            assignmentService.update(id, user, toInput(form));
            redirect.addFlashAttribute("message", "과제를 수정했습니다.");
            return "redirect:/assignments/" + id;
        } catch (BusinessException e) {
            result.reject("update", e.getMessage());
            return "assignments/form";
        }
    }

    @PostMapping("/assignments/{id}/baseline/delete")
    @PreAuthorize("hasRole('PROFESSOR')")
    public String removeBaseline(@PathVariable Long id, @AuthenticationPrincipal AppUser user,
                                 RedirectAttributes redirect) {
        assignmentService.removeBaseline(id, user);
        redirect.addFlashAttribute("message", "뼈대 코드를 삭제했습니다.");
        return "redirect:/assignments/" + id + "/edit";
    }

    private static AssignmentInput toInput(AssignmentForm f) {
        byte[] file = null;
        try {
            if (f.getBaselineFile() != null && !f.getBaselineFile().isEmpty()) {
                file = f.getBaselineFile().getBytes();
            }
        } catch (IOException e) {
            throw new BusinessException("뼈대 코드 파일을 읽지 못했습니다.");
        }
        return new AssignmentInput(f.getTitle(), f.getDescription(), f.getDueAt(), f.isAllowLate(),
                f.getLateUntil(), f.getBaselineCode(), file);
    }
}
