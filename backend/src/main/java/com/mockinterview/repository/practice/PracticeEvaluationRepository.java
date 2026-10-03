package com.mockinterview.repository.practice;
import com.mockinterview.domain.practice.PracticeEvaluation;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface PracticeEvaluationRepository extends JpaRepository<PracticeEvaluation,String> {
    List<PracticeEvaluation> findByAttempt_IdOrderByGeneration(String id);
    Optional<PracticeEvaluation> findFirstByAttempt_IdOrderByGenerationDesc(String id);
    Optional<PracticeEvaluation> findByAttempt_IdAndRequestKey(String id,String key);
    List<PracticeEvaluation> findTop6ByStatusOrderByCreatedAtAsc(String status);
    List<PracticeEvaluation> findByStatus(String status);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from PracticeEvaluation e where e.id=:id")
    Optional<PracticeEvaluation> lock(@Param("id") String id);
}
