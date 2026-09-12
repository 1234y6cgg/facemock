package com.mockinterview.service.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.agent.*;
import com.mockinterview.domain.ResumeStructured;
import dev.langchain4j.service.TokenStream;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class InterviewOrchestrator {

    private final InterviewStateMachine stateMachine;
    private final AssessmentAgent assessmentAgent;
    private final AssessmentParser assessmentParser;
    private final FollowUpAgent followUpAgent;
    private final ObjectMapper objectMapper;

    public InterviewOrchestrator(InterviewStateMachine stateMachine,
                                 AssessmentAgent assessmentAgent,
                                 AssessmentParser assessmentParser,
                                 FollowUpAgent followUpAgent,
                                 ObjectMapper objectMapper) {
        this.stateMachine = stateMachine;
        this.assessmentAgent = assessmentAgent;
        this.assessmentParser = assessmentParser;
        this.followUpAgent = followUpAgent;
        this.objectMapper = objectMapper;
    }

    public AnswerAssessment assess(String history, String lastAnswer) {
        String raw = assessmentAgent.assessAnswer(history, lastAnswer);
        return assessmentParser.parse(raw);
    }

    public InterviewState nextState(InterviewState state, AnswerAssessment assessment) {
        return stateMachine.apply(state, assessment);
    }

    public TokenStream generateQuestion(ResumeStructured resume, int projectIdx, Layer layer, String history) {
        return followUpAgent.generateQuestion(resumeContext(resume), projectContext(resume, projectIdx),
                layer.label(), history);
    }

    private String resumeContext(ResumeStructured resume) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "summary", resume.getSummary() == null ? "" : resume.getSummary(),
                    "skills", resume.getSkills() == null ? List.of() : resume.getSkills()));
        } catch (Exception e) {
            return "{}";
        }
    }

    private String projectContext(ResumeStructured resume, int idx) {
        if (resume.getProjects() == null || resume.getProjects().isEmpty()) {
            return "{}";
        }
        int i = Math.min(idx, resume.getProjects().size() - 1);
        try {
            return objectMapper.writeValueAsString(resume.getProjects().get(i));
        } catch (Exception e) {
            return "{}";
        }
    }
}
