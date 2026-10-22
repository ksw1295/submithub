package com.submithub.grading;

import com.submithub.domain.GradingLanguage;

import java.util.List;
import java.util.Map;

/**
 * 제출 코드를 실제로 실행해서 결과를 돌려주는 "격리된 실행기".
 *
 * 학생 코드를 이 서버(호스트)에서 직접 실행하는 구현은 만들지 않는다. 운영 구현(DockerGradingRunner)은
 * 반드시 Docker 격리 컨테이너 안에서만 실행하고, 테스트에서는 가짜 구현으로 바꾼다.
 * 출력 비교와 점수 계산은 실행기가 아니라 GradingService 가 한다.
 */
public interface GradingRunner {

    /** 테스트 케이스 하나의 입력 */
    record Case(Long id, String input) {
    }

    /**
     * @param files            제출 파일 (zip 안 경로 → 내용). 경로는 이미 정규화된 상대 경로다
     * @param mainFile         실행할 파일 (files 의 경로)
     * @param timeLimitSeconds 테스트 케이스 하나당 시간 제한
     * @param memoryLimitMb    프로그램 하나가 쓸 수 있는 메모리 제한
     */
    record Request(GradingLanguage language, String mainFile, Map<String, byte[]> files, List<Case> cases,
                   int timeLimitSeconds, int memoryLimitMb) {
    }

    enum CaseStatus { OK, TIMEOUT, RUNTIME_ERROR }

    record CaseOutcome(Long id, CaseStatus status, String stdout, String stderr, long durationMs) {
    }

    /** compiled=false 이면 cases 는 비어 있고 compileMessage 에 컴파일러 출력이 들어 있다 */
    record Outcome(boolean compiled, String compileMessage, List<CaseOutcome> cases) {
    }

    /** 실행기를 지금 쓸 수 있는지 (Docker 가 설치돼 있고 응답하는지) */
    boolean available();

    /** 화면에 보여주는 실행기 이름 */
    String name();

    /** @throws GradingException 실행기 자체의 문제(Docker 오류 등). 학생 코드의 실패는 예외가 아니라 결과로 돌려준다 */
    Outcome run(Request request);

    class GradingException extends RuntimeException {
        public GradingException(String message) {
            super(message);
        }

        public GradingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
