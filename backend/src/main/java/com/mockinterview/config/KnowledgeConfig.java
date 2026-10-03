package com.mockinterview.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15q.BgeSmallZhV15QuantizedEmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableConfigurationProperties(KnowledgeProperties.class)
public class KnowledgeConfig {
    @Bean(destroyMethod = "shutdown")
    public ExecutorService knowledgeEmbeddingExecutor() {
        return Executors.newFixedThreadPool(2);
    }

    @Bean
    @Lazy
    public EmbeddingModel knowledgeEmbeddingModel(
            @Qualifier("knowledgeEmbeddingExecutor") ExecutorService executor) {
        return new BgeSmallZhV15QuantizedEmbeddingModel(executor);
    }
}
