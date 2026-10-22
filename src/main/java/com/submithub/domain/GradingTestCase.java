package com.submithub.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 채점용 테스트 케이스: 표준입력 → 기대 출력. 비공개 케이스의 입출력은 학생에게 보이지 않는다. */
@Entity
@Table(name = "grading_test_cases", indexes = @Index(name = "idx_gtc_assignment", columnList = "assignment_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GradingTestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", updatable = false)
    private Assignment assignment;

    @Column(nullable = false, length = 100)
    private String name;

    @Lob
    @Column(name = "input_text")
    private String input;

    @Lob
    @Column(name = "expected_output")
    private String expectedOutput;

    @Column(name = "public_case", nullable = false)
    private boolean publicCase;

    @Column(nullable = false)
    private int points;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    public GradingTestCase(Assignment assignment, String name, String input, String expectedOutput,
                           boolean publicCase, int points, int orderNo) {
        this.assignment = assignment;
        this.name = name;
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.publicCase = publicCase;
        this.points = points;
        this.orderNo = orderNo;
    }
}
