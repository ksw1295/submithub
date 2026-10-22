package com.submithub.web;

import com.submithub.security.AppUser;
import com.submithub.service.CourseService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping("/courses")
    public String list(@AuthenticationPrincipal AppUser user, Model model) {
        model.addAttribute("courses", courseService.myCourses(user));
        return "courses/list";
    }
}
