package com.mockinterview.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResumeStructuredTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void roundTripsThroughJson() throws Exception {
        ResumeStructured rs = ResumeStructured.builder()
                .summary("后端工程师")
                .skills(List.of("Java", "MySQL"))
                .projects(List.of(ResumeStructured.Project.builder()
                        .name("订单系统")
                        .techStack(List.of("Spring Boot"))
                        .build()))
                .build();

        String json = mapper.writeValueAsString(rs);
        ResumeStructured back = mapper.readValue(json, ResumeStructured.class);

        assertEquals("后端工程师", back.getSummary());
        assertEquals("订单系统", back.getProjects().get(0).getName());
        assertEquals(List.of("Java", "MySQL"), back.getSkills());
    }
}
