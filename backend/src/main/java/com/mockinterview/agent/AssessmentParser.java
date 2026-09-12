package com.mockinterview.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.capability.llm.JsonExtractor;
import org.springframework.stereotype.Component;

@Component
public class AssessmentParser {

    private final ObjectMapper objectMapper;

    public AssessmentParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AnswerAssessment parse(String json) {
        try {
            JsonNode node = objectMapper.readTree(JsonExtractor.extractJsonObject(json));
            return new AnswerAssessment(
                    node.path("depth").asInt(0),
                    node.path("accuracy").asInt(0),
                    node.path("completeness").asInt(0),
                    node.path("stuck").asBoolean(false));
        } catch (Exception e) {
            return new AnswerAssessment(0, 0, 0, false);
        }
    }
}
