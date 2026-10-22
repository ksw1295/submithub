package com.submithub.demo;

import com.submithub.domain.GradingLanguage;

import java.util.List;

/**
 * 시연용 과제 1(연결 리스트)의 자동 채점 설정. 표준입력으로 명령(add N / first N / get I / remove / size / sum)을
 * 읽는 Main 의 출력을 비교한다. 이민준의 v1 → v2 → v3 는 실제로 실행하면 2/5 → 4/5 → 5/5 로 통과율이 오르도록 만들었다
 * (v1: 디버그 출력과 first 명령 없음, v2: get 범위 오류는 아직 NPE, v3: IndexOutOfBoundsException 처리).
 */
public final class DemoGrading {

    public record Case(String name, String input, String expected, boolean publicCase, int points) {
    }

    public static final GradingLanguage LANGUAGE = GradingLanguage.JAVA;
    public static final String MAIN_FILE = "Main.java";
    public static final int TIME_LIMIT_SECONDS = 5;
    public static final int MEMORY_LIMIT_MB = 256;

    public static final List<Case> CASES = List.of(
            new Case("기본 동작: add, get, size", "add 3\nadd 5\nadd 8\nget 1\nsize\n", "5\n3\n", true, 20),
            new Case("합계: sum", "add 1\nadd 2\nadd 3\nadd 4\nsum\n", "10\n", true, 20),
            new Case("삭제 후 크기: remove", "add 7\nadd 9\nremove\nsize\n", "1\n", false, 20),
            new Case("앞에 추가: first", "add 2\nfirst 1\nget 0\nget 1\nsize\n", "1\n2\n2\n", false, 20),
            new Case("범위를 벗어난 get", "add 1\nget 5\nsize\n", "ERROR\n1\n", false, 20));

    public static final String DESCRIPTION = "단일 연결 리스트를 구현하세요. add, get, remove, size 가 동작해야 합니다.\n"
            + "제공한 뼈대 코드(LinkedList.java)를 시작점으로 사용하세요.\n\n"
            + "자동 채점: Main.java 는 표준입력의 명령을 한 줄씩 처리합니다.\n"
            + "  add N (뒤에 추가), first N (앞에 추가), get I (I번째 값 출력, 범위를 벗어나면 ERROR 출력),\n"
            + "  remove (맨 앞 삭제), size (크기 출력), sum (합계 출력)";

    private DemoGrading() {
    }
}
