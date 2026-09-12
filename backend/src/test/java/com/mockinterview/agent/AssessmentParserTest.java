package com.mockinterview.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AssessmentParserTest {

    private final AssessmentParser parser = new AssessmentParser(new ObjectMapper());

    @Test
    void parsesAssessment() {
        AnswerAssessment a = parser.parse(
                "```json\n{\"depth\":4,\"accuracy\":3,\"completeness\":4,\"stuck\":false}\n```");
        assertEquals(4, a.depth());
        assertEquals(3, a.accuracy());
        assertFalse(a.stuck());
    }

    @Test
    void defaultsOnInvalidJson() {
        AnswerAssessment a = parser.parse("这不是 JSON");
        assertEquals(0, a.depth());
        assertFalse(a.stuck());
    }
}
