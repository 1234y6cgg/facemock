package com.mockinterview.repository.project;
import com.mockinterview.domain.project.ProjectAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ProjectAttemptRepository extends JpaRepository<ProjectAttempt,String> {
    List<ProjectAttempt> findBySession_IdOrderByNumber(String id);
    Optional<ProjectAttempt> findBySession_IdAndRequestKey(String id,String key);
}
