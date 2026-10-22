package com.submithub.domain;

/** 제출 하나의 채점 진행 상태. UNAVAILABLE 은 Docker 를 쓸 수 없어 채점하지 못한 경우 */
public enum GradingStatus {
    QUEUED, RUNNING, DONE, UNAVAILABLE, ERROR
}
