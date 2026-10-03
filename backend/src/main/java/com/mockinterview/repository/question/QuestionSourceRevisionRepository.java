package com.mockinterview.repository.question;

import com.mockinterview.domain.question.QuestionSourceRevisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface QuestionSourceRevisionRepository extends JpaRepository<QuestionSourceRevisionEntity, Long> {
    Optional<QuestionSourceRevisionEntity> findBySourceIdAndVersion(String sourceId, int version);
    Optional<QuestionSourceRevisionEntity> findByDocumentId(String documentId);
}
