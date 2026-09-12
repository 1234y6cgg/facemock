package com.mockinterview.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosisParserTest {

    private final DiagnosisParser parser = new DiagnosisParser(new ObjectMapper());

    @Test
    void parsesReport() {
        DiagnosisReport r = parser.parse("""
                {"scores":{"技术深度":7,"总体":6},
                 "weaknesses":["Redis 主从理解浅"],
                 "suggestions":["深入学习分布式缓存"]}
                """);

        assertEquals(7, r.scores().get("技术深度"));
        assertEquals(1, r.weaknesses().size());
        assertEquals("Redis 主从理解浅", r.weaknesses().get(0));
    }

    @Test
    void defaultsOnInvalidJson() {
        DiagnosisReport r = parser.parse("garbage");
        assertTrue(r.scores().isEmpty());
        assertTrue(r.weaknesses().isEmpty());
    }
}
