package com.submithub.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/courses";
    }

    @RequestMapping("/error/429")
    public String error429(Model model, HttpServletResponse response) {
        response.setStatus(429);
        model.addAttribute("status", 429);
        return "error/4xx";
    }
}
