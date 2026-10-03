package com.mockinterview.repository.project;
import com.mockinterview.domain.project.ProjectRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ProjectRevisionRepository extends JpaRepository<ProjectRevision,String> {
    Optional<ProjectRevision> findByProjectIdAndRevision(String id,int revision);
}
