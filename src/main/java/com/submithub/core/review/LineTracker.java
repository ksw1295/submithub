package com.submithub.core.review;

import com.submithub.core.diff.DiffOptions;
import com.submithub.core.diff.MyersDiff;
import com.submithub.core.diff.SideBySide;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 피드백 반영 추적.
 *
 * 교수가 v2 의 12~14번째 줄에 코멘트를 남긴 뒤 학생이 v3, v4 를 제출하면,
 * 그 줄들이 이후 버전에서 어떻게 되었는지 diff 로 따라가서 자동으로 판정한다.
 *
 * - UNCHANGED : 코멘트 달린 줄이 그대로 있음 → "미반영" (최신 버전의 줄 번호도 알려줌)
 * - MODIFIED  : 줄이 수정됨 → "반영됨(확인 필요)"
 * - DELETED   : 줄이 삭제됨 → "삭제됨"
 *
 * GitHub PR 에서 코드가 바뀌면 리뷰 코멘트가 "Outdated" 로 표시되는 것과 같은 개념이다.
 * 들여쓰기만 바꾼 것은 반영으로 보지 않도록 공백 무시 비교를 쓴다.
 */
public final class LineTracker {

    public enum Status { UNCHANGED, MODIFIED, DELETED }

    /**
     * @param status          최종 판정
     * @param startLine       최신 버전에서의 시작 줄 (UNCHANGED 일 때만 의미 있음, 아니면 0)
     * @param endLine         최신 버전에서의 끝 줄 (UNCHANGED 일 때만 의미 있음, 아니면 0)
     * @param changedAtIndex  versions 목록에서 처음 바뀐 버전의 인덱스. 안 바뀌었으면 -1
     */
    public record Result(Status status, int startLine, int endLine, int changedAtIndex) {
    }

    private record Step(Status status, int newLine) {
    }

    private LineTracker() {
    }

    /**
     * @param versions  코멘트를 단 버전부터 최신 버전까지의 텍스트 (오래된 순, 첫 번째가 코멘트 단 버전)
     * @param startLine 코멘트 범위 시작 줄 (1부터)
     * @param endLine   코멘트 범위 끝 줄 (포함)
     */
    public static Result track(List<String> versions, int startLine, int endLine) {
        if (versions == null || versions.isEmpty()) {
            throw new IllegalArgumentException("versions 가 비어 있습니다");
        }
        if (startLine < 1 || endLine < startLine) {
            throw new IllegalArgumentException("잘못된 줄 범위: " + startLine + "~" + endLine);
        }

        int[] lines = new int[endLine - startLine + 1];
        for (int i = 0; i < lines.length; i++) {
            lines[i] = startLine + i;
        }

        for (int v = 1; v < versions.size(); v++) {
            Map<Integer, Step> mapping = map(versions.get(v - 1), versions.get(v));
            int deleted = 0;
            boolean changed = false;
            for (int i = 0; i < lines.length; i++) {
                Step step = mapping.get(lines[i]);
                if (step == null || step.status() == Status.DELETED) {
                    deleted++;
                    changed = true;
                } else if (step.status() == Status.MODIFIED) {
                    changed = true;
                } else {
                    lines[i] = step.newLine();
                }
            }
            if (changed) {
                Status status = deleted == lines.length ? Status.DELETED : Status.MODIFIED;
                return new Result(status, 0, 0, v);
            }
        }
        return new Result(Status.UNCHANGED, lines[0], lines[lines.length - 1], -1);
    }

    public static Result track(List<String> versions, int line) {
        return track(versions, line, line);
    }

    /** 이전 텍스트의 줄 번호 → 다음 텍스트에서의 상태 */
    private static Map<Integer, Step> map(String oldText, String newText) {
        Map<Integer, Step> mapping = new HashMap<>();
        var rows = SideBySide.build(MyersDiff.diff(oldText, newText, DiffOptions.IGNORE_WHITESPACE));
        for (SideBySide.Row row : rows) {
            if (row.leftLineNo() == 0) {
                continue;
            }
            switch (row.type()) {
                case EQUAL -> mapping.put(row.leftLineNo(), new Step(Status.UNCHANGED, row.rightLineNo()));
                case MODIFIED -> mapping.put(row.leftLineNo(), new Step(Status.MODIFIED, row.rightLineNo()));
                case REMOVED -> mapping.put(row.leftLineNo(), new Step(Status.DELETED, 0));
                default -> {
                }
            }
        }
        return mapping;
    }
}
