package com.submithub.support;

import com.submithub.grading.GradingRunner;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 테스트용 가짜 채점 실행기 (코드를 실제로 실행하지 않는다).
 * 기본 동작: 제출 파일의 "//LEVEL:n" 표시를 읽어서, 입력이 숫자 1..n 인 케이스는 기대 출력("out"+입력)을 내고
 * 나머지 케이스는 틀린 출력("wrong")을 낸다. 표시가 없으면 모두 맞게 낸다.
 */
public class FakeGradingRunner implements GradingRunner {

    public volatile boolean available = true;
    public volatile Function<Request, Outcome> behavior = FakeGradingRunner::levelBehavior;
    public final List<Request> requests = new ArrayList<>();

    public static Outcome levelBehavior(Request r) {
        String code = r.files().values().stream().map(b -> new String(b, StandardCharsets.UTF_8))
                .reduce("", (a, b) -> a + "\n" + b);
        int level = Integer.MAX_VALUE;
        int i = code.indexOf("//LEVEL:");
        if (i >= 0) {
            int j = i + "//LEVEL:".length();
            int k = j;
            while (k < code.length() && Character.isDigit(code.charAt(k))) {
                k++;
            }
            level = Integer.parseInt(code.substring(j, k));
        }
        if (code.contains("//COMPILE_ERROR")) {
            return new Outcome(false, "Main.java:1: error: ';' expected", List.of());
        }
        List<CaseOutcome> out = new ArrayList<>();
        for (Case c : r.cases()) {
            int n;
            try {
                n = Integer.parseInt(c.input().trim());
            } catch (NumberFormatException e) {
                n = 0;
            }
            out.add(new CaseOutcome(c.id(), CaseStatus.OK, n <= level ? "out" + c.input().trim() : "wrong", "", 12));
        }
        return new Outcome(true, null, out);
    }

    public void reset() {
        available = true;
        behavior = FakeGradingRunner::levelBehavior;
        synchronized (requests) {
            requests.clear();
        }
    }

    public int calls() {
        synchronized (requests) {
            return requests.size();
        }
    }

    public Request last() {
        synchronized (requests) {
            return requests.get(requests.size() - 1);
        }
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public Outcome run(Request request) {
        synchronized (requests) {
            requests.add(request);
        }
        return behavior.apply(request);
    }
}
