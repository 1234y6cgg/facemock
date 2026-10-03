package com.mockinterview.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import com.mockinterview.service.model.ModelSettingsService;
import com.mockinterview.infrastructure.model.CompatibleModelClient;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import java.util.List;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DeepSeekProperties.class)
public class LangChain4jConfig {

    @Bean
    public ChatLanguageModel chatLanguageModel(DeepSeekProperties p,ModelSettingsService settings,CompatibleModelClient client) {
        return messages -> Response.from(AiMessage.from(client.complete(p.current(),client.messages(messages),8192,false,Duration.ofSeconds(90)).content()));
    }

    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel(DeepSeekProperties p,ModelSettingsService settings,CompatibleModelClient client) {
        return new StreamingChatLanguageModel(){
            @Override public void generate(List<ChatMessage> messages,StreamingResponseHandler<AiMessage> handler){client.stream(p.current(),messages,handler);}
        };
    }
}
