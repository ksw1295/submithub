package com.submithub.support;

import com.submithub.ai.AiJudgeProvider;
import com.submithub.domain.AiVerdict;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** 테스트용 가짜 LLM. 요청을 기록하고, 미리 정한 결과(또는 예외)를 돌려준다. */
public class FakeJudgeProvider implements AiJudgeProvider {

    public volatile boolean available = true;
    public volatile Function<JudgeRequest, JudgeResult> behavior = defaultBehavior();
    public final List<JudgeRequest> requests = new ArrayList<>();

    public static Function<JudgeRequest, JudgeResult> defaultBehavior() {
        return r -> new JudgeResult(AiVerdict.RESOLVED, "가짜 판정: 해결됨", "fake-model");
    }

    public void reset() {
        available = true;
        behavior = defaultBehavior();
        synchronized (requests) {
            requests.clear();
        }
    }

    public void always(AiVerdict verdict, String reason) {
        behavior = r -> new JudgeResult(verdict, reason, "fake-model");
    }

    public void failWith(String message) {
        behavior = r -> {
            throw new AiJudgeException(message);
        };
    }

    public int calls() {
        synchronized (requests) {
            return requests.size();
        }
    }

    public JudgeRequest last() {
        synchronized (requests) {
            return requests.get(requests.size() - 1);
        }
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public JudgeResult judge(JudgeRequest request) {
        synchronized (requests) {
            requests.add(request);
        }
        return behavior.apply(request);
    }
}
