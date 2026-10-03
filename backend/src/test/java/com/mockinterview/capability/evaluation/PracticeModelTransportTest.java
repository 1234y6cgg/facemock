package com.mockinterview.capability.evaluation;
import com.fasterxml.jackson.databind.JsonNode;
import com.mockinterview.config.DeepSeekProperties;
import com.mockinterview.infrastructure.deepseek.DeepSeekPracticeAssessmentModel;
import com.mockinterview.service.practice.PracticeReferences;
import com.mockinterview.service.question.*;
import com.sun.net.httpserver.HttpServer;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class PracticeModelTransportTest {
    @Test void sendsBoundedReasoningJsonRequestAndNeverReturnsEmptyOrTruncatedContent() throws Exception {
        var json=new QuestionJson();
        var seen=new AtomicReference<JsonNode>(); var response=new AtomicReference<String>();
        var status=new java.util.concurrent.atomic.AtomicInteger(200);
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/chat/completions",exchange->{
            seen.set(json.read(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8),JsonNode.class));
            var bytes=response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(),bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var catalog=new QuestionCatalogLoader(json,new QuestionCatalogValidator(factory.getValidator())).loadBuiltin();
            var snapshot=EvaluationFixtures.snapshot(catalog,"redis.lua-stock",json);
            var props=new DeepSeekProperties(); props.setApiKey("test-not-a-real-key");
            props.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort()+"/v1/");
            var model=new DeepSeekPracticeAssessmentModel(props,new PracticeAssessmentPrompt(json),json);
            var ctx=new PracticeReferences.Context("NO_MATCH","test",snapshot.sources(),List.of());
            response.set("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{}\"}}]}");
            assertEquals("{}",model.assess(snapshot,"回答",ctx));
            assertEquals("enabled",seen.get().path("thinking").path("type").asText());
            assertEquals("low",seen.get().path("reasoning_effort").asText());
            assertEquals("json_object",seen.get().path("response_format").path("type").asText());
            assertEquals(8000,seen.get().path("max_tokens").asInt());
            response.set("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"\"}}]}");
            assertThrows(InvalidEvaluationException.class,()->model.assess(snapshot,"回答",ctx));
            response.set("{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"{}\"}}]}");
            assertThrows(InvalidEvaluationException.class,()->model.assess(snapshot,"回答",ctx));
            status.set(401); response.set("private upstream response");
            var failure=assertThrows(IllegalStateException.class,()->model.assess(snapshot,"回答",ctx));
            assertFalse(failure.getMessage().contains("private")); assertFalse(failure.getMessage().contains("key"));
        } finally { server.stop(0); }
    }
}
