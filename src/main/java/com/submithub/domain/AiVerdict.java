package com.submithub.domain;

/** AI 반영 판정 결과. FAILED 는 시간 초과·형식 오류·키 오류 등으로 판정하지 못한 경우 */
public enum AiVerdict {
    RESOLVED, PARTIAL, NOT_RESOLVED, FAILED
}
