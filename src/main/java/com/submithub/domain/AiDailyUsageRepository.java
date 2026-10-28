package com.submithub.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface AiDailyUsageRepository extends JpaRepository<AiDailyUsage, LocalDate> {
}
