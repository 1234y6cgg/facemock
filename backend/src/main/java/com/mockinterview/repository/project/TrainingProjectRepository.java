package com.mockinterview.repository.project;
import com.mockinterview.domain.project.TrainingProject;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface TrainingProjectRepository extends JpaRepository<TrainingProject,String> {
    Optional<TrainingProject> findByResumeIdAndResumeProjectIndex(Long resumeId,Integer index);
    List<TrainingProject> findByDeletedFalseOrderByCreatedAtDesc();
    List<TrainingProject> findTop3ByIndexStatusInOrderByCreatedAtAsc(Collection<String> statuses);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from TrainingProject p where p.id=:id")
    Optional<TrainingProject> lock(@Param("id") String id);
}
