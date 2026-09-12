package com.mockinterview.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.capability.llm.JsonExtractor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DiagnosisParser {

    private final ObjectMapper objectMapper;

    public DiagnosisParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DiagnosisReport parse(String json) {
        try {
            JsonNode node = objectMapper.readTree(JsonExtractor.extractJsonObject(json));
            Map<String, Object> scores = objectMapper.convertValue(node.path("scores"),
                    new TypeReference<Map<String, Object>>() {
                    });
            if (scores == null) {
                scores = Map.of();
            }
            List<String> weaknesses = new ArrayList<>();
            node.path("weaknesses").forEach(n -> weaknesses.add(n.asText()));
            List<String> suggestions = new ArrayList<>();
            node.path("suggestions").forEach(n -> suggestions.add(n.asText()));
            return new DiagnosisReport(scores, weaknesses, suggestions);
        } catch (Exception e) {
            return new DiagnosisReport(Map.of(), List.of(), List.of());
        }
    }
}
