package com.mockinterview.service.model;

import com.mockinterview.config.DeepSeekProperties;
import com.mockinterview.service.question.QuestionJson;
import com.mockinterview.service.practice.PracticeConflictException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ModelSettingsTest {
    @TempDir Path directory;
    private ModelSettingsService service(DeepSeekProperties p){return new ModelSettingsService(p,new ModelSecretStore(directory,new QuestionJson()));}
    private ModelSettingsService.Update update(String provider,String url,String model,String key,long version){return new ModelSettingsService.Update(provider,url,model,key,false,version);}
    @Test void encryptPersistRestartRetainAndClearNeverReactivateEnvironmentKey()throws Exception{
        var p=new DeepSeekProperties();p.setApiKey("old-environment-key");var s=service(p);
        var saved=s.save(update("qwen","https://dashscope.aliyuncs.com/compatible-mode/v1/","qwen-plus","own-user-secret",0));
        assertTrue(saved.configured());assertEquals("user",saved.source());assertFalse(Files.readString(directory.resolve("model.json")).contains("own-user-secret"));
        assertFalse(saved.toString().contains("own-user-secret"));assertFalse(p.toString().contains("old-environment-key"));assertFalse(p.current().toString().contains("own-user-secret"));
        var restarted=new DeepSeekProperties();restarted.setApiKey("old-environment-key");var restored=service(restarted);
        assertEquals("own-user-secret",restarted.current().apiKey());assertEquals(saved,restored.view());
        var newer=restored.save(update("qwen",saved.baseUrl(),"qwen-flash","",saved.revision()));assertEquals("own-user-secret",restarted.current().apiKey());
        assertThrows(PracticeConflictException.class,()->restored.save(update("qwen",saved.baseUrl(),"qwen-plus","",saved.revision())));
        restored.clear(newer.revision());assertFalse(restored.view().configured());var afterClear=new DeepSeekProperties();afterClear.setApiKey("old-environment-key");
        assertFalse(service(afterClear).view().configured());assertFalse(Files.readString(directory.resolve("model.json")).contains("own-user-secret"));
    }
    @Test void changedDestinationRequiresExplicitNewKeyAndInvalidUrlsAreRejected(){
        var p=new DeepSeekProperties();p.setApiKey("environment-secret");var s=service(p);
        assertThrows(IllegalArgumentException.class,()->s.save(update("qwen","https://dashscope.aliyuncs.com/compatible-mode/v1","qwen-plus","",0)));
        for(String url:new String[]{"http://example.com/v1","https://user:secret@example.com/v1","https://example.com/v1?key=secret","https://example.com/v1#secret","https://example.com/v1/chat/completions"})
            assertThrows(IllegalArgumentException.class,()->s.save(update("custom",url,"m","new-secret",0)));
        assertEquals(0,s.view().revision());assertEquals("environment-secret",p.current().apiKey());
    }
    @Test void runningEvaluationKeepsItsSnapshotWhileOtherThreadsSeeSavedConfiguration(){
        var p=new DeepSeekProperties();var old=p.current();
        try(var scope=p.pin()){p.activate(new ModelConnection("qwen","https://example.com/v1","new","secret",false,1));assertEquals(old,p.current());}
        assertEquals("new",p.current().modelName());
    }
    @Test void missingMasterFailsClosedRatherThanSilentlyUsingEnvironmentKey()throws Exception{
        var p=new DeepSeekProperties();service(p).save(update("custom","https://example.com/v1","m","encrypted-secret",0));
        Files.delete(directory.resolve("master.key"));var next=new DeepSeekProperties();next.setApiKey("environment-secret");
        var error=assertThrows(IllegalStateException.class,()->service(next));assertFalse(error.getMessage().contains("encrypted-secret"));
    }
}
