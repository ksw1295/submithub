package com.submithub.web;

import com.submithub.domain.Role;
import com.submithub.service.BusinessException;
import com.submithub.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/signup")
    public String signupForm(Model model) {
        model.addAttribute("form", new SignupForm());
        return "signup";
    }

    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute("form") SignupForm form, BindingResult result,
                         RedirectAttributes redirect) {
        if (form.getPassword() != null && !form.getPassword().equals(form.getPasswordConfirm())) {
            result.rejectValue("passwordConfirm", "mismatch", "비밀번호 확인이 일치하지 않습니다.");
        }
        if (result.hasErrors()) {
            return "signup";
        }
        try {
            userService.signup(form.getLoginId(), form.getPassword(), form.getName(), form.getRole(),
                    form.getStudentNo(), form.getDepartment(), form.getProfessorCode());
        } catch (BusinessException e) {
            result.reject("signup", e.getMessage());
            return "signup";
        }
        redirect.addFlashAttribute("message", "가입이 완료되었습니다. 로그인하세요.");
        return "redirect:/login";
    }
}
