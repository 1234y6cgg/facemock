package com.mockinterview.repository.question;

import com.mockinterview.domain.question.KnowledgePointEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgePointRepository extends JpaRepository<KnowledgePointEntity, String> {}
