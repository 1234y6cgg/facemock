package com.mockinterview.repository.project;
import com.mockinterview.domain.project.ProjectMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ProjectMaterialRepository extends JpaRepository<ProjectMaterial,String> {
    List<ProjectMaterial> findByProjectIdOrderByCreatedAtAsc(String id);
}
