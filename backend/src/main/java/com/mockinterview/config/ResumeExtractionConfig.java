package com.mockinterview.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

@Configuration
@EnableConfigurationProperties(OcrProperties.class)
public class ResumeExtractionConfig {
    @Bean("resumeExecutor")
    public Executor resumeExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(4);
        executor.setThreadNamePrefix("resume-extraction-");
        executor.initialize();
        return executor;
    }
}
