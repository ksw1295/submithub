package com.submithub.domain;

/** AI 판정에 대한 교수의 결정. 수락한 판정만 반영률·남은 피드백에 영향을 주고, 학생에게도 보인다 */
public enum AiDecision {
    PENDING, ACCEPTED, IGNORED
}
