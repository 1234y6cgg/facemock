package com.mockinterview.config;

import com.mockinterview.agent.AssessmentAgent;
import com.mockinterview.agent.DiagnosisAgent;
import com.mockinterview.agent.FeedbackAgent;
import com.mockinterview.agent.FollowUpAgent;
import com.mockinterview.agent.PressureAgent;
import com.mockinterview.agent.ScenarioAgent;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    @Bean
    public FollowUpAgent followUpAgent(StreamingChatLanguageModel streamingModel) {
        return AiServices.builder(FollowUpAgent.class)
                .streamingChatLanguageModel(streamingModel)
                .build();
    }

    @Bean
    public AssessmentAgent assessmentAgent(ChatLanguageModel model) {
        return AiServices.builder(AssessmentAgent.class)
                .chatLanguageModel(model)
                .build();
    }

    @Bean
    public DiagnosisAgent diagnosisAgent(ChatLanguageModel model) {
        return AiServices.builder(DiagnosisAgent.class)
                .chatLanguageModel(model)
                .build();
    }

    @Bean
    public PressureAgent pressureAgent(StreamingChatLanguageModel streamingModel) {
        return AiServices.builder(PressureAgent.class)
                .streamingChatLanguageModel(streamingModel)
                .build();
    }

    @Bean
    public ScenarioAgent scenarioAgent(StreamingChatLanguageModel streamingModel) {
        return AiServices.builder(ScenarioAgent.class)
                .streamingChatLanguageModel(streamingModel)
                .build();
    }

    @Bean
    public FeedbackAgent feedbackAgent(ChatLanguageModel model) {
        return AiServices.builder(FeedbackAgent.class)
                .chatLanguageModel(model)
                .build();
    }
}
