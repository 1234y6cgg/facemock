package com.mockinterview.repository.project;
import com.mockinterview.domain.project.ProjectEvaluation;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface ProjectEvaluationRepository extends JpaRepository<ProjectEvaluation,String> {
    List<ProjectEvaluation> findByAttempt_IdOrderByGeneration(String id);
    Optional<ProjectEvaluation> findByAttempt_IdAndRequestKey(String id,String key);
    List<ProjectEvaluation> findTop3ByStatusOrderByCreatedAtAsc(String status);
    List<ProjectEvaluation> findByStatus(String status);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from ProjectEvaluation e where e.id=:id")
    Optional<ProjectEvaluation> lock(@Param("id") String id);
}
