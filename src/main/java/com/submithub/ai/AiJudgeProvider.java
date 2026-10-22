package com.submithub.ai;

import com.submithub.domain.AiVerdict;

import java.util.List;

/**
 * 코멘트가 실제로 반영됐는지 판정하는 AI 제공자. 기본 구현은 Gemini(GeminiJudgeProvider)이고,
 * 테스트에서는 가짜 구현으로 바꾼다.
 */
public interface AiJudgeProvider {

    /**
     * 판정 요청. 코드는 모두 "데이터"이고, 줄 번호는 파일 안의 실제 줄 번호이다.
     *
     * @param commentBody 교수가 남긴 코멘트
     * @param path        파일 경로
     * @param oldStart    이전 코드 조각의 첫 줄 번호
     * @param oldLines    코멘트가 달린 줄 주변의 이전 코드
     * @param newStart    새 코드 조각의 첫 줄 번호
     * @param newLines    같은 부분의 새 코드
     */
    record JudgeRequest(String commentBody, String path, int oldStart, List<String> oldLines,
                        int newStart, List<String> newLines) {
    }

    record JudgeResult(AiVerdict verdict, String reason, String model) {
    }

    /** 판정하지 못한 경우(시간 초과, 형식 오류, 키 오류 ...). 메시지는 화면에 그대로 보여줘도 되는 문장이다 */
    class AiJudgeException extends RuntimeException {
        public AiJudgeException(String message) {
            super(message);
        }

        public AiJudgeException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** 이 서버에서 사용할 수 있는지 (API 키가 설정돼 있는지). 없으면 AI 판정 기능을 화면에서 숨긴다 */
    boolean available();

    /** @throws AiJudgeException 판정 실패 */
    JudgeResult judge(JudgeRequest request);
}
