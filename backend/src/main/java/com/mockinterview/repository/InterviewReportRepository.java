package com.mockinterview.repository;

import com.mockinterview.domain.InterviewReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InterviewReportRepository extends JpaRepository<InterviewReport, Long> {
    Optional<InterviewReport> findBySessionId(Long sessionId);
}
