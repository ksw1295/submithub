package com.submithub.web;

import com.submithub.domain.Role;
import com.submithub.domain.User;
import com.submithub.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    @Test
    void 로그인하지_않으면_로그인_페이지로_이동한다() throws Exception {
        mvc.perform(get("/courses"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void 로그인_회원가입_페이지는_누구나_열수_있다() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/signup")).andExpect(status().isOk());
    }

    @Test
    void 회원가입하면_BCrypt_해시로_저장되고_로그인할수_있다() throws Exception {
        mvc.perform(post("/signup").with(csrf())
                        .param("loginId", "kim2026").param("password", "password1!").param("passwordConfirm", "password1!")
                        .param("name", "김학생").param("role", "STUDENT").param("studentNo", "20261234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        User saved = users.findByLoginId("kim2026").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo("password1!").startsWith("$2");
        assertThat(encoder.matches("password1!", saved.getPasswordHash())).isTrue();
        assertThat(saved.getRole()).isEqualTo(Role.STUDENT);

        mvc.perform(post("/login").with(csrf()).param("username", "kim2026").param("password", "password1!"))
                .andExpect(authenticated().withUsername("kim2026"));
    }

    @Test
    void 비밀번호가_틀리면_로그인되지_않는다() throws Exception {
        mvc.perform(post("/signup").with(csrf())
                .param("loginId", "park2026").param("password", "password1!").param("passwordConfirm", "password1!")
                .param("name", "박교수").param("role", "PROFESSOR"));
        mvc.perform(post("/login").with(csrf()).param("username", "park2026").param("password", "wrong-password"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void 중복_아이디_짧은_비밀번호_확인불일치는_가입되지_않는다() throws Exception {
        mvc.perform(post("/signup").with(csrf())
                .param("loginId", "dup2026").param("password", "password1!").param("passwordConfirm", "password1!")
                .param("name", "가").param("role", "STUDENT"));

        mvc.perform(post("/signup").with(csrf())
                        .param("loginId", "dup2026").param("password", "password1!").param("passwordConfirm", "password1!")
                        .param("name", "나").param("role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(view().name("signup"));

        mvc.perform(post("/signup").with(csrf())
                        .param("loginId", "short2026").param("password", "123").param("passwordConfirm", "123")
                        .param("name", "다").param("role", "STUDENT"))
                .andExpect(view().name("signup"))
                .andExpect(model().attributeHasFieldErrors("form", "password"));

        mvc.perform(post("/signup").with(csrf())
                        .param("loginId", "mis2026").param("password", "password1!").param("passwordConfirm", "different1!")
                        .param("name", "라").param("role", "STUDENT"))
                .andExpect(model().attributeHasFieldErrors("form", "passwordConfirm"));

        assertThat(users.existsByLoginId("short2026")).isFalse();
        assertThat(users.existsByLoginId("mis2026")).isFalse();
    }

    @Test
    void CSRF_토큰이_없는_POST_는_거부된다() throws Exception {
        mvc.perform(post("/signup")
                        .param("loginId", "csrf2026").param("password", "password1!").param("passwordConfirm", "password1!")
                        .param("name", "마").param("role", "STUDENT"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 없는_페이지는_404_오류_페이지를_보여준다() throws Exception {
        mvc.perform(post("/login").with(csrf()).param("username", "nobody").param("password", "x"));
        mvc.perform(get("/no-such-page")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user(new com.submithub.security.AppUser(
                                        new User("tmp1", "x", "임시", Role.STUDENT, null, null)))))
                .andExpect(status().isNotFound());
    }
}
