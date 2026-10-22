package com.submithub.service;

/** 사용자가 고칠 수 있는 입력/상태 오류. 화면에 메시지로 보여주고 이전 화면으로 돌려보낸다. */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
