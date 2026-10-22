package com.submithub.grading;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutputComparatorTest {

    @Test
    void 줄바꿈_종류와_줄_끝_공백_끝의_빈_줄은_무시한다() {
        assertThat(OutputComparator.matches("5\n3\n", "5\r\n3\r\n")).isTrue();
        assertThat(OutputComparator.matches("hello world\n", "hello world   \n\n\n")).isTrue();
        assertThat(OutputComparator.matches("a\nb", "a \t\nb\n")).isTrue();
        assertThat(OutputComparator.matches("1\n2", "1\r2")).isTrue();      // 옛 Mac 줄바꿈
        assertThat(OutputComparator.matches("", "\n \n")).isTrue();
        assertThat(OutputComparator.matches(null, "")).isTrue();
    }

    @Test
    void 그_밖의_차이는_틀린_것이다() {
        assertThat(OutputComparator.matches("5", "5 6")).isFalse();
        assertThat(OutputComparator.matches("a b", "a  b")).isFalse();          // 줄 안의 공백은 그대로 비교
        assertThat(OutputComparator.matches(" a", "a")).isFalse();              // 줄 앞 공백은 의미가 있다
        assertThat(OutputComparator.matches("a\n\nb", "a\nb")).isFalse();       // 중간의 빈 줄
        assertThat(OutputComparator.matches("Hello", "hello")).isFalse();       // 대소문자
        assertThat(OutputComparator.matches("1\n2", "2\n1")).isFalse();
        assertThat(OutputComparator.matches("5\n3", "5")).isFalse();
    }

    @Test
    void 정규화_결과() {
        assertThat(OutputComparator.normalize("a  \r\nb\t\n\n\n")).isEqualTo("a\nb");
        assertThat(OutputComparator.normalize(null)).isEmpty();
    }
}
