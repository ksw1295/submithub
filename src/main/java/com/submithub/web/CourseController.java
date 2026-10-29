package com.submithub.web;

import com.submithub.domain.Course;
import com.submithub.security.AppUser;
import com.submithub.service.BusinessException;
import com.submithub.service.CourseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CourseController {

    @Getter
    @Setter
    public static class CourseForm {
        @NotBlank(message = "강의명을 입력하세요.")
        @Size(max = 100, message = "강의명은 100자 이하입니다.")
        private String name;

        @NotBlank(message = "학기를 입력하세요. (예: 2026-2)")
        @Size(max = 30, message = "학기는 30자 이하입니다.")
        private String semester;
    }

    @Getter
    @Setter
    public static class JoinForm {
        @NotBlank(message = "초대 코드를 입력하세요.")
        private String inviteCode;
    }

    private final CourseService courseService;
    private final com.submithub.service.AssignmentService assignmentService;

    public CourseController(CourseService courseService, com.submithub.service.AssignmentService assignmentService) {
        this.courseService = courseService;
        this.assignmentService = assignmentService;
    }

    @GetMapping("/courses")
    public String list(@AuthenticationPrincipal AppUser user, Model model) {
        model.addAttribute("courses", courseService.myCourses(user));
        if (user != null && !user.isProfessor()) {
            model.addAttribute("dueSoon", assignmentService.dueSoonForStudent(user));
            model.addAttribute("recent", assignmentService.recentForStudent(user));
        }
        return "courses/list";
    }

    @GetMapping("/courses/new")
    @PreAuthorize("hasRole('PROFESSOR')")
    public String newForm(Model model) {
        model.addAttribute("form", new CourseForm());
        return "courses/new";
    }

    @PostMapping("/courses/new")
    @PreAuthorize("hasRole('PROFESSOR')")
    public String create(@AuthenticationPrincipal AppUser user, @Valid @ModelAttribute("form") CourseForm form,
                         BindingResult result, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return "courses/new";
        }
        try {
            Course course = courseService.create(user, form.getName(), form.getSemester());
            redirect.addFlashAttribute("message", "강의를 개설했습니다. 초대 코드를 학생에게 알려주세요.");
            return "redirect:/courses/" + course.getId();
        } catch (BusinessException e) {
            result.reject("create", e.getMessage());
            return "courses/new";
        }
    }

    @GetMapping("/courses/join")
    @PreAuthorize("hasRole('STUDENT')")
    public String joinForm(Model model) {
        model.addAttribute("form", new JoinForm());
        return "courses/join";
    }

    @PostMapping("/courses/join")
    @PreAuthorize("hasRole('STUDENT')")
    public String join(@AuthenticationPrincipal AppUser user, @Valid @ModelAttribute("form") JoinForm form,
                       BindingResult result, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return "courses/join";
        }
        try {
            Course course = courseService.join(user, form.getInviteCode());
            redirect.addFlashAttribute("message", "수강 등록이 완료되었습니다.");
            return "redirect:/courses/" + course.getId();
        } catch (BusinessException e) {
            result.reject("join", e.getMessage());
            return "courses/join";
        }
    }

    @GetMapping("/courses/{id}")
    public String detail(@PathVariable Long id, @AuthenticationPrincipal AppUser user, Model model) {
        model.addAttribute("course", courseService.detail(id, user));
        return "courses/detail";
    }
}
