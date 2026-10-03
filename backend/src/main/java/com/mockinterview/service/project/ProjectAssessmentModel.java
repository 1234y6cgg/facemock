package com.mockinterview.service.project;
import com.mockinterview.service.project.ProjectDtos.Snapshot;
public interface ProjectAssessmentModel {
    default com.mockinterview.service.model.ModelConnection.Scope configurationScope(){return () -> {};}
    String assess(Snapshot snapshot,String question,String answer);
    String modelName();
}
