package com.mockinterview.controller;

import com.mockinterview.config.DeepSeekProperties;
import com.mockinterview.service.question.QuestionJson;
import com.mockinterview.service.model.*;
import com.mockinterview.infrastructure.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.*;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:modelsettings;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","knowledge.enabled=false","questions.index-knowledge=false",
    "practice.jobs.enabled=false","projects.jobs.enabled=false","speech.jobs.enabled=false","speech.enabled=false","deepseek.api-key="})
@AutoConfigureMockMvc
class ModelSettingsFlowTest {
    static Path directory;
    static{try{directory=Files.createTempDirectory("model-settings-test-");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("models.storage-path",()->directory.toString());}
    @Autowired MockMvc mvc;@Autowired QuestionJson json;@Autowired DeepSeekProperties properties;
    @Autowired ChatLanguageModel chat;@Autowired StreamingChatLanguageModel streaming;@Autowired CompatibleModelClient client;
    @Autowired com.mockinterview.agent.FeedbackAgent feedback;
    @Autowired com.mockinterview.agent.FollowUpAgent followup;
    @Test void noKeyStartupThenSaveTestSwitchSyncStreamAndExportWithoutSecrets()throws Exception{
        mvc.perform(get("/api/settings/model")).andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(false)).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/api/settings/model/presets")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(7));
        assertThrows(IllegalStateException.class,()->chat.generate("before-key"));
        var seen=new AtomicReference<JsonNode>();var auth=new AtomicReference<String>();var httpStatus=new AtomicInteger(200);
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/chat/completions",e->{var request=json.read(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8),JsonNode.class);seen.set(request);auth.set(e.getRequestHeaders().getFirst("Authorization"));
            String response=httpStatus.get()!=200?"private vendor secret: fake-test-key":request.path("stream").asBoolean()?"data: {\"choices\":[{\"delta\":{\"content\":\"你好\"},\"finish_reason\":null}]}\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"世界\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n":"{\"model\":\"fake-one\",\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}]}";
            e.getResponseHeaders().add("Content-Type",request.path("stream").asBoolean()?"text/event-stream":"application/json");var bytes=response.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(httpStatus.get(),bytes.length);e.getResponseBody().write(bytes);e.close();});
        server.start();try{
            var input=new ModelSettingsService.Update("custom","http://127.0.0.1:"+server.getAddress().getPort()+"/v1","fake-one","fake-test-key",false,0);
            mvc.perform(post("/api/settings/model/test").contentType("application/json").content(json.write(input))).andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
            mvc.perform(get("/api/settings/model")).andExpect(jsonPath("$.configured").value(false));
            var saved=mvc.perform(put("/api/settings/model").contentType("application/json").content(json.write(input))).andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(true)).andReturn().getResponse().getContentAsString();
            assertFalse(saved.contains("fake-test-key"));assertFalse(Files.readString(directory.resolve("model.json")).contains("fake-test-key"));
            assertEquals("{\"ok\":true}",chat.generate("sync"));assertEquals("Bearer fake-test-key",auth.get());assertFalse(seen.get().has("thinking"));
            assertEquals("{\"ok\":true}",feedback.comment("Java 后端","公开测试题","公开测试回答",1,1,1));
            var done=new CountDownLatch(1);var text=new StringBuilder();var failure=new AtomicReference<Throwable>();
            streaming.generate(List.of(UserMessage.from("stream")),new StreamingResponseHandler<AiMessage>(){
                public void onNext(String token){text.append(token);}public void onComplete(Response<AiMessage> result){assertEquals("你好世界",result.content().text());done.countDown();}
                public void onError(Throwable e){failure.set(e);done.countDown();}});
            assertTrue(done.await(10,TimeUnit.SECONDS));assertNull(failure.get());assertEquals("你好世界",text.toString());
            var agentDone=new CountDownLatch(1);var agentText=new StringBuilder();var agentFailure=new AtomicReference<Throwable>();
            followup.generateQuestion("Java 后端","","公开合成简历","合成项目","BACKGROUND",1,"","","")
                .onNext(agentText::append).onComplete(r->agentDone.countDown()).onError(e->{agentFailure.set(e);agentDone.countDown();}).start();
            assertTrue(agentDone.await(10,TimeUnit.SECONDS));assertNull(agentFailure.get());assertEquals("你好世界",agentText.toString());
            var change=new ModelSettingsService.Update("custom",input.baseUrl(),"fake-two","",false,1);
            mvc.perform(put("/api/settings/model").contentType("application/json").content(json.write(change))).andExpect(status().isOk());
            chat.generate("changed");assertEquals("fake-two",seen.get().path("model").asText());
            mvc.perform(put("/api/settings/model").contentType("application/json").content(json.write(input))).andExpect(status().isConflict());
            var invalid=new ModelSettingsService.Update("custom","https://other.example/v1","m","",false,2);
            mvc.perform(put("/api/settings/model").contentType("application/json").content(json.write(invalid))).andExpect(status().isBadRequest());
            httpStatus.set(401);var test=mvc.perform(post("/api/settings/model/test").contentType("application/json").content(json.write(new ModelSettingsService.Update("custom",input.baseUrl(),"fake-two","",false,2))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(false)).andReturn().getResponse().getContentAsString();assertFalse(test.contains("fake-test-key"));assertFalse(test.contains("private vendor"));
            var export=mvc.perform(get("/api/data/export")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertFalse(export.contains("fake-test-key"));assertFalse(export.contains("encryptedKey"));
            mvc.perform(delete("/api/settings/model?revision=2")).andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(false));
            assertThrows(IllegalStateException.class,()->chat.generate("after-clear"));
        }finally{server.stop(0);}
    }
    @Test void vendorSpecificFieldsAreNotSentToUnrelatedProviders(){
        for(var preset:ModelPresets.ALL){if(preset.id().equals("custom"))continue;var c=new ModelConnection(preset.id(),preset.baseUrl(),preset.models().get(0),"dummy",preset.jsonMode(),1);
            var body=client.body(c,List.of(Map.of("role","user","content","JSON")),8000,true,false);
            assertEquals(c.modelName(),body.get("model"));assertEquals(c.jsonMode(),body.containsKey("response_format"));
            assertEquals(preset.id().equals("deepseek")||preset.id().equals("kimi"),body.containsKey("reasoning_effort"));assertEquals(preset.id().equals("qwen"),body.containsKey("enable_thinking"));
            if(preset.id().equals("minimax"))assertEquals(true,body.get("reasoning_split"));
        }
    }
}
