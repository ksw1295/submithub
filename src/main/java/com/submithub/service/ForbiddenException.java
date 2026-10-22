package com.submithub.service;

/** 로그인은 했지만 이 자원에 접근할 권한이 없다 (403). */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
