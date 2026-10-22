package com.submithub.service;

import com.submithub.AppProperties;
import com.submithub.domain.Role;
import com.submithub.domain.User;
import com.submithub.domain.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public UserService(UserRepository users, PasswordEncoder encoder, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    @Transactional
    public User signup(String loginId, String password, String name, Role role, String studentNo,
                       String department, String professorCode) {
        if (users.existsByLoginId(loginId)) {
            throw new BusinessException("이미 사용 중인 아이디입니다.");
        }
        if (role == Role.PROFESSOR && !props.professorSignupCode().isBlank()
                && !props.professorSignupCode().equals(professorCode)) {
            throw new BusinessException("교수 가입 코드가 올바르지 않습니다.");
        }
        String no = role == Role.STUDENT ? blankToNull(studentNo) : null;
        try {
            return users.saveAndFlush(new User(loginId, encoder.encode(password), name.trim(), role, no,
                    blankToNull(department)));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("이미 사용 중인 아이디입니다.");
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
