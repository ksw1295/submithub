package com.submithub.web;

import com.submithub.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignupForm {

    @NotBlank(message = "아이디를 입력하세요.")
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,20}$", message = "아이디는 영문, 숫자, _ 4~20자입니다.")
    private String loginId;

    @NotBlank(message = "비밀번호를 입력하세요.")
    @Size(min = 8, max = 60, message = "비밀번호는 8자 이상이어야 합니다.")
    private String password;

    private String passwordConfirm;

    @NotBlank(message = "이름을 입력하세요.")
    @Size(max = 50, message = "이름은 50자 이하입니다.")
    private String name;

    @NotNull(message = "역할을 선택하세요.")
    private Role role = Role.STUDENT;

    @Size(max = 30, message = "학번은 30자 이하입니다.")
    private String studentNo;

    @Size(max = 100, message = "학과는 100자 이하입니다.")
    private String department;

    private String professorCode;
}
