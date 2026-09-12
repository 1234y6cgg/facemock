package com.mockinterview.service.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.capability.llm.JsonExtractor;
import com.mockinterview.domain.ResumeStructured;
import org.springframework.stereotype.Component;

@Component
public class ResumeParser {

    private final ResumeStructuringModel model;
    private final ObjectMapper objectMapper;

    public ResumeParser(ResumeStructuringModel model, ObjectMapper objectMapper) {
        this.model = model;
        this.objectMapper = objectMapper;
    }

    public ResumeStructured parse(String rawText) {
        String raw = model.extractJson(rawText);
        String json = JsonExtractor.extractJsonObject(raw);
        try {
            return objectMapper.readValue(json, ResumeStructured.class);
        } catch (Exception e) {
            throw new IllegalStateException("简历结构化解析失败", e);
        }
    }
}
