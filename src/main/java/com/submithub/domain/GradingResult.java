package com.submithub.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 테스트 케이스 하나의 채점 결과. 채점 시점의 케이스 이름·공개 여부·배점·입출력을 그대로 보관한다
 * (나중에 케이스를 고치거나 지워도 이 결과는 그때 기준으로 남는다).
 * 학생에게 보여줄 때 비공개 케이스의 입력·기대 출력·실제 출력은 서비스에서 가린다.
 */
@Entity
@Table(name = "grading_results", indexes = @Index(name = "idx_gr_run", columnList = "run_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GradingResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", updatable = false)
    private GradingRun run;

    @Column(name = "case_name", nullable = false, length = 100)
    private String caseName;

    @Column(name = "public_case", nullable = false)
    private boolean publicCase;

    @Column(nullable = false)
    private int points;

    @Column(nullable = false)
    private int awarded;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GradingVerdict verdict;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Lob
    @Column(name = "input_text")
    private String input;

    @Lob
    @Column(name = "expected_output")
    private String expected;

    @Lob
    @Column(name = "actual_output")
    private String actual;

    @Lob
    @Column(name = "error_output")
    private String error;

    public GradingResult(GradingRun run, String caseName, boolean publicCase, int points, GradingVerdict verdict,
                         long durationMs, String input, String expected, String actual, String error) {
        this.run = run;
        this.caseName = caseName;
        this.publicCase = publicCase;
        this.points = points;
        this.awarded = verdict == GradingVerdict.PASSED ? points : 0;
        this.verdict = verdict;
        this.durationMs = durationMs;
        this.input = input;
        this.expected = expected;
        this.actual = actual;
        this.error = error;
    }
}
