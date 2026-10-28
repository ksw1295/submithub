package com.submithub.demo;

import com.submithub.domain.Course;
import com.submithub.domain.CourseRepository;
import com.submithub.domain.Role;
import com.submithub.domain.User;
import com.submithub.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "submithub.demo-data=true",
        "spring.datasource.url=jdbc:h2:mem:demo-reset-test;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "submithub.storage-dir=./target/demo-reset-uploads"
})
class DemoResetTest {

    @Autowired DemoDataLoader loader;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;

    @Test
    void resetDemoDataRestoresExactInitialCount() {
        long initialUsers = users.count();
        long initialCourses = courses.count();

        assertThat(initialUsers).isEqualTo(20);
        assertThat(initialCourses).isEqualTo(5);

        // 추가 임의 데이터 삽입
        User extraUser = users.save(new User("extrauser", "hash", "임시사용자", Role.STUDENT, "20269999", "소프트웨어학과"));
        courses.save(new Course(extraUser, "임시 강의", "2026-2", "TEMP9999"));

        assertThat(users.count()).isEqualTo(initialUsers + 1);
        assertThat(courses.count()).isEqualTo(initialCourses + 1);

        // 시연 데이터 초기화 실행
        loader.resetDemoData();

        // 초기화 후 시연 데이터 개수가 처음과 같은지 검증
        assertThat(users.count()).isEqualTo(initialUsers);
        assertThat(courses.count()).isEqualTo(initialCourses);
        assertThat(users.findByLoginId("extrauser")).isEmpty();
    }
}
