package com.mockinterview.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeStructured {

    private String summary;
    private List<String> skills;
    private List<Project> projects;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Project {
        private String name;
        private String desc;
        private String role;
        private List<String> techStack;
        private List<String> responsibilities;
        private List<String> highlights;
    }
}
