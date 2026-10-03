package com.mockinterview.repository;

import com.mockinterview.domain.Resume;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeRepository extends JpaRepository<Resume, Long> {
    java.util.List<Resume> findByStatus(com.mockinterview.domain.ResumeStatus status);
}
