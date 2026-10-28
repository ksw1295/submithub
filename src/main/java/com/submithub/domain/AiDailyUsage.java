package com.submithub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Gemini LLM 일일 호출 사용량 기록.
 * 서버가 재시작되어도 그날 카운트가 유지된다.
 */
@Entity
@Table(name = "ai_daily_usage")
public class AiDailyUsage {

    @Id
    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "call_count", nullable = false)
    private int callCount;

    protected AiDailyUsage() {
    }

    public AiDailyUsage(LocalDate usageDate, int callCount) {
        this.usageDate = usageDate;
        this.callCount = callCount;
    }

    public LocalDate getUsageDate() {
        return usageDate;
    }

    public int getCallCount() {
        return callCount;
    }

    public void setCallCount(int callCount) {
        this.callCount = callCount;
    }

    public void increment() {
        this.callCount++;
    }
}
