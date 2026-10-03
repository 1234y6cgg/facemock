package com.mockinterview.repository;

import com.mockinterview.domain.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from InterviewSession s where s.id = :id")
    java.util.Optional<InterviewSession> lock(@org.springframework.data.repository.query.Param("id") Long id);

    List<InterviewSession> findAllByOrderByStartedAtDesc();

    long countByResumeId(Long resumeId);
}
