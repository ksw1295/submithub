package com.submithub.service;

/** 대상이 없거나, 존재 여부를 숨기고 싶은 경우(권한 없음) 404 로 처리한다. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
