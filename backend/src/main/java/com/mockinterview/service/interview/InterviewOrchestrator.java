package com.mockinterview.service.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.agent.*;
import com.mockinterview.capability.knowledge.TechKnowledgeService;
import com.mockinterview.capability.knowledge.InterviewKnowledgeQuery;
import com.mockinterview.capability.memory.LongTermMemoryService;
import com.mockinterview.domain.ResumeStructured;
import dev.langchain4j.service.TokenStream;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class InterviewOrchestrator {

    private final InterviewStateMachine stateMachine;
    private final AssessmentAgent assessmentAgent;
    private final AssessmentParser assessmentParser;
    private final FollowUpAgent followUpAgent;
    private final PressureAgent pressureAgent;
    private final ScenarioAgent scenarioAgent;
    private final FeedbackAgent feedbackAgent;
    private final TechKnowledgeService knowledgeService;
    private final LongTermMemoryService memoryService;
    private final ObjectMapper objectMapper;

    public InterviewOrchestrator(InterviewStateMachine stateMachine,
                                 AssessmentAgent assessmentAgent,
                                 AssessmentParser assessmentParser,
                                 FollowUpAgent followUpAgent,
                                 PressureAgent pressureAgent,
                                 ScenarioAgent scenarioAgent,
                                 FeedbackAgent feedbackAgent,
                                 TechKnowledgeService knowledgeService,
                                 LongTermMemoryService memoryService,
                                 ObjectMapper objectMapper) {
        this.stateMachine = stateMachine;
        this.assessmentAgent = assessmentAgent;
        this.assessmentParser = assessmentParser;
        this.followUpAgent = followUpAgent;
        this.pressureAgent = pressureAgent;
        this.scenarioAgent = scenarioAgent;
        this.feedbackAgent = feedbackAgent;
        this.knowledgeService = knowledgeService;
        this.memoryService = memoryService;
        this.objectMapper = objectMapper;
    }

    public AnswerAssessment assess(String history, String lastAnswer) {
        String knowledge = knowledgeService.retrieve(InterviewKnowledgeQuery.assessment(history, lastAnswer),
                knowledgeService.defaultTopK());
        String raw = assessmentAgent.assessAnswer(history, lastAnswer, knowledgeOrEmpty(knowledge));
        return assessmentParser.parse(raw);
    }

    public InterviewState nextState(InterviewState state, AnswerAssessment assessment) {
        return stateMachine.apply(state, assessment);
    }

    public String feedback(String role, String question, String answer, AnswerAssessment a) {
        try {
            return feedbackAgent.comment(role, question, answer, a.depth(), a.accuracy(), a.completeness());
        } catch (Exception e) {
            return "";
        }
    }

    public TokenStream nextQuestion(ResumeStructured resume, InterviewState state,
                                    String history, Long sessionId, String role, String jobDetail) {
        String job = jobDetail == null || jobDetail.isBlank() ? "（未提供）" : jobDetail;
        String knowledge = knowledgeOrEmpty(knowledgeService.retrieve(
                InterviewKnowledgeQuery.question(resume, state, history, role), knowledgeService.defaultTopK()));
        if (state.getStage() == Stage.EXTENSION) {
            return scenarioAgent.pose(
                    role, job, skillsContext(resume), projectsContext(resume), history, state.getDifficulty(), knowledge);
        }
        if (state.isPressureUsed() && state.getLayer() == Layer.L5_TRADEOFF
                && state.getQuestionCount() > 0 && justEnteredPressure(state)) {
            return pressureAgent.press(role, job,
                    projectContext(resume, state.getCurrentProjectIdx()),
                    state.getLayer().label(), history, state.getDifficulty(), knowledge);
        }
        Set<String> asked = memoryService.askedTopics(sessionId);
        return followUpAgent.generateQuestion(
                role, job,
                resumeContext(resume),
                projectContext(resume, state.getCurrentProjectIdx()),
                state.getLayer().label(),
                state.getDifficulty(),
                asked.isEmpty() ? "（暂无）" : String.join("、", asked),
                knowledge,
                history);
    }

    private boolean justEnteredPressure(InterviewState state) {
        return state.getConsecutiveStuck() == 0;
    }

    private String knowledgeOrEmpty(String knowledge) {
        return knowledge == null || knowledge.isBlank() ? "（无相关知识，不要编造来源）" : knowledge;
    }

    private String resumeContext(ResumeStructured resume) {
        try {
            Set<String> weak = memoryService.knownWeaknesses();
            return objectMapper.writeValueAsString(Map.of(
                    "summary", resume.getSummary() == null ? "" : resume.getSummary(),
                    "skills", resume.getSkills() == null ? List.of() : resume.getSkills(),
                    "historicalWeaknesses", weak == null ? List.of() : weak));
        } catch (Exception e) {
            return "{}";
        }
    }

    private String projectContext(ResumeStructured resume, int idx) {
        if (resume.getProjects() == null || resume.getProjects().isEmpty()) {
            return "{}";
        }
        int i = Math.max(0, Math.min(idx, resume.getProjects().size() - 1));
        try {
            return objectMapper.writeValueAsString(resume.getProjects().get(i));
        } catch (Exception e) {
            return "{}";
        }
    }

    private String skillsContext(ResumeStructured resume) {
        return resume.getSkills() == null ? "" : String.join("、", resume.getSkills());
    }

    private String projectsContext(ResumeStructured resume) {
        if (resume.getProjects() == null) return "";
        return resume.getProjects().stream()
                .map(p -> p.getName() + "（" + String.join(",", p.getTechStack() == null ? List.of() : p.getTechStack()) + "）")
                .collect(Collectors.joining("\n"));
    }
}
