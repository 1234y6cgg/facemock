package com.mockinterview.capability.evaluation;
import com.mockinterview.domain.question.QuestionSnapshot;
import com.mockinterview.service.practice.PracticeReferences.Context;
public interface PracticeAssessmentModel {
    default com.mockinterview.service.model.ModelConnection.Scope configurationScope(){return () -> {};}
    String assess(QuestionSnapshot snapshot,String answer,Context context);
    String modelName();
    default java.util.Map<String,Object> telemetry(){return java.util.Map.of();}
}
