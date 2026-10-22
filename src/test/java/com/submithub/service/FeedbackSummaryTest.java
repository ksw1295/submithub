package com.submithub.service;

import com.submithub.service.FeedbackService.FeedbackSummary;
import com.submithub.service.FeedbackService.TrackedComment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 반영률 계산과 표기는 FeedbackSummary 한 곳에서 한다 (과제 제출 현황·통계 화면 공용). */
class FeedbackSummaryTest {

    static TrackedComment c(String status) {
        return new TrackedComment(1L, 1, 1L, 1L, "A.java", 1, 1, "x", status, 0, 0, 0, 2, 2L, 2L, false, null, null, null, null);
    }

    static FeedbackSummary of(String... statuses) {
        return FeedbackSummary.of(java.util.Arrays.stream(statuses).map(FeedbackSummaryTest::c).toList());
    }

    @Test
    void 새_코멘트만_있으면_퍼센트_없이_새_코멘트_N개로_표기한다() {
        FeedbackSummary s = of("PENDING");
        assertThat(s.hasRate()).isFalse();
        assertThat(s.rateLabel()).isEqualTo("새 코멘트 1개");
        assertThat(s.rateDetail()).isEmpty();
        assertThat(of("PENDING", "PENDING").rateLabel()).isEqualTo("새 코멘트 2개");
    }

    @Test
    void 코멘트가_없으면_하이픈() {
        assertThat(FeedbackSummary.empty().rateLabel()).isEqualTo("-");
        assertThat(FeedbackSummary.of(List.of()).rateDetail()).isEmpty();
    }

    @Test
    void 반영률이_있으면_퍼센트와_미반영_추적_수를_보여준다() {
        FeedbackSummary s = of("MODIFIED", "MODIFIED", "DELETED", "UNCHANGED");
        assertThat(s.rateLabel()).isEqualTo("75%");
        assertThat(s.rateDetail()).isEqualTo("미반영 1 / 추적 4");
        assertThat(of("UNCHANGED").rateLabel()).isEqualTo("0%");
    }

    @Test
    void 반영률과_새_코멘트가_함께_있으면_둘_다_알려준다() {
        FeedbackSummary s = of("MODIFIED", "UNCHANGED", "PENDING");
        assertThat(s.rateLabel()).isEqualTo("50%");
        assertThat(s.rateDetail()).isEqualTo("미반영 1 / 추적 2, 새 코멘트 1개");
        assertThat(s.remaining()).isEqualTo(2);
    }
}
