package com.mockinterview.repository.question;

import com.mockinterview.domain.question.QuestionRevisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface QuestionRevisionRepository extends JpaRepository<QuestionRevisionEntity, Long> {
    Optional<QuestionRevisionEntity> findByQuestion_IdAndVersion(String questionId, int version);
    Optional<QuestionRevisionEntity> findFirstByQuestion_IdAndRubricVersion(String questionId, int rubricVersion);
}
