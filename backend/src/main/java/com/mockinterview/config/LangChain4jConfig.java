package com.mockinterview.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DeepSeekProperties.class)
public class LangChain4jConfig {

    @Bean
    public ChatLanguageModel chatLanguageModel(DeepSeekProperties p) {
        return OpenAiChatModel.builder()
                .baseUrl(p.getBaseUrl())
                .apiKey(p.getApiKey())
                .modelName(p.getModelName())
                .build();
    }

    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel(DeepSeekProperties p) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(p.getBaseUrl())
                .apiKey(p.getApiKey())
                .modelName(p.getModelName())
                .build();
    }
}
