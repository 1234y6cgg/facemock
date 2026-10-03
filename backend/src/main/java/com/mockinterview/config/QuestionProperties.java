package com.mockinterview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "questions")
public class QuestionProperties {
    private boolean seedOnStartup = true;
    private boolean indexKnowledge = true;
}
