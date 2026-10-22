package com.submithub.service;

import com.submithub.domain.Course;
import com.submithub.domain.CourseRepository;
import com.submithub.security.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CourseService {

    private final CourseRepository courses;

    public CourseService(CourseRepository courses) {
        this.courses = courses;
    }

    /** 교수는 자기가 개설한 강의, 학생은 수강 중인 강의 */
    @Transactional(readOnly = true)
    public List<Course> myCourses(AppUser user) {
        return user.isProfessor()
                ? courses.findByProfessorIdOrderByCreatedAtDesc(user.getId())
                : courses.findByStudentId(user.getId());
    }
}
