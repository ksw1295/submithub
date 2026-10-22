package com.submithub.support;

import com.submithub.domain.Assignment;
import com.submithub.domain.Course;
import com.submithub.domain.Role;
import com.submithub.domain.User;
import com.submithub.domain.UserRepository;
import com.submithub.security.AppUser;
import com.submithub.service.AssignmentService;
import com.submithub.service.CourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/** 통합 테스트용 데이터 생성 도우미. 테스트끼리 겹치지 않도록 매번 새 아이디를 쓴다. */
@TestComponent
public class TestData {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired CourseService courseService;
    @Autowired AssignmentService assignmentService;

    public AppUser professor() {
        return user(Role.PROFESSOR, "prof");
    }

    public AppUser student() {
        return user(Role.STUDENT, "stu");
    }

    private AppUser user(Role role, String prefix) {
        String id = prefix + SEQ.incrementAndGet();
        User u = users.save(new User(id, encoder.encode("password1!"), id + "이름", role,
                role == Role.STUDENT ? "2026" + SEQ.get() : null, "컴퓨터공학과"));
        return new AppUser(u);
    }

    public Course course(AppUser professor) {
        return courseService.create(professor, "자료구조", "2026-2");
    }

    /** 마감이 dueFromNow 후인 과제 (음수면 이미 마감) */
    public Assignment assignment(Course course, AppUser professor, java.time.Duration dueFromNow,
                                 boolean allowLate, java.time.Duration lateFromNow) {
        LocalDateTime due = LocalDateTime.now().plus(dueFromNow);
        LocalDateTime lateUntil = lateFromNow == null ? null : LocalDateTime.now().plus(lateFromNow);
        return assignmentService.create(course.getId(), professor,
                new AssignmentService.AssignmentInput("과제-" + SEQ.incrementAndGet(), "설명", due, allowLate,
                        lateUntil, null, null));
    }
}
