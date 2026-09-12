package com.mockinterview.service.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.domain.ResumeStructured;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResumeParserTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesStructuredJson() {
        ResumeStructuringModel model = raw -> """
                {"summary":"后端工程师","skills":["Java","MySQL"],
                 "projects":[{"name":"订单系统","techStack":["Spring Boot"],
                              "responsibilities":["负责下单流程"]}]}
                """;
        ResumeParser parser = new ResumeParser(model, mapper);

        ResumeStructured r = parser.parse("任意简历文本");

        assertEquals("后端工程师", r.getSummary());
        assertEquals(1, r.getProjects().size());
        assertEquals("订单系统", r.getProjects().get(0).getName());
    }

    @Test
    void throwsWhenModelReturnsInvalidJson() {
        ResumeStructuringModel model = raw -> "这不是 JSON";
        ResumeParser parser = new ResumeParser(model, mapper);

        assertThrows(IllegalStateException.class, () -> parser.parse("文本"));
    }
}
