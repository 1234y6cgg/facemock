package com.mockinterview.repository.project;
import com.mockinterview.domain.project.ProjectSession;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface ProjectSessionRepository extends JpaRepository<ProjectSession,String> {
    Optional<ProjectSession> findByRequestKey(String key);
    List<ProjectSession> findByProjectIdOrderByCreatedAtDesc(String id);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from ProjectSession s where s.id=:id")
    Optional<ProjectSession> lock(@Param("id") String id);
}
