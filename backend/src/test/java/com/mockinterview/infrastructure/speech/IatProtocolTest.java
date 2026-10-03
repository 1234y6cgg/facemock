package com.mockinterview.infrastructure.speech;
import com.mockinterview.config.SpeechProperties;
import com.mockinterview.service.question.QuestionJson;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IatProtocolTest {
    private final QuestionJson json=new QuestionJson();
    @Test void signsOfficialWssAndKeepsKeysOutOfAudioFrames() {
        var p=new SpeechProperties();p.setAppId("test-app");p.setApiKey("test-key");p.setApiSecret("test-secret");
        var uri=IatProtocol.signedUri(p,Instant.parse("2026-10-03T00:00:00Z"));
        assertEquals("iat-api.xfyun.cn",uri.getHost());assertEquals("wss",uri.getScheme());
        String value=URLDecoder.decode(uri.getRawQuery().split("&")[0].substring("authorization=".length()),StandardCharsets.UTF_8);
        String auth=new String(Base64.getDecoder().decode(value),StandardCharsets.UTF_8);
        assertTrue(auth.contains("api_key=\"test-key\""));assertTrue(auth.contains("headers=\"host date request-line\""));
        var first=json.read(IatProtocol.frame(p,json,0,new byte[]{1,2}),JsonNode.class);
        assertEquals("test-app",first.path("common").path("app_id").asText());assertEquals(0,first.path("data").path("status").asInt());
        assertEquals("raw",first.path("data").path("encoding").asText());assertFalse(first.toString().contains("test-key"));
        assertFalse(json.read(IatProtocol.frame(p,json,1,new byte[]{1}),JsonNode.class).has("business"));
        p.setBaseUrl("wss://example.com/v2/iat");assertThrows(IllegalArgumentException.class,()->IatProtocol.signedUri(p,Instant.now()));
        assertEquals(4,(180*32000+XfyunIatTranscriber.SEGMENT_BYTES-1)/XfyunIatTranscriber.SEGMENT_BYTES);
    }
    private JsonNode response(int sn,String word,String extra,int status) {return json.read("{\"code\":0,\"data\":{\"status\":"+status+",\"result\":{\"sn\":"+sn+",\"ws\":[{\"cw\":[{\"w\":\""+word+"\"}]}]"+extra+"}}}",JsonNode.class);}
    @Test void correctionReplacesRangeRatherThanDuplicatingOriginalText() {
        var r=new IatProtocol.Results();r.accept(response(0,"先讲","",0));r.accept(response(1,"错误","",1));
        r.accept(response(2,"机制"," ,\"pgs\":\"rpl\",\"rg\":[1,1]",1));assertEquals("先讲机制",r.text());assertFalse(r.finished());
        r.accept(response(3,"。","",2));assertTrue(r.finished());assertEquals("先讲机制。",r.text());
    }
    @Test void rejectsProviderFailureMalformedWordsAndInvalidReplacement() {
        var r=new IatProtocol.Results();assertThrows(IllegalStateException.class,()->r.accept(json.read("{\"code\":11200}",JsonNode.class)));
        assertThrows(IllegalStateException.class,()->r.accept(response(0,"词"," ,\"pgs\":\"rpl\",\"rg\":[2,1]",1)));
        assertThrows(IllegalStateException.class,()->r.accept(json.read("{\"code\":0,\"data\":{\"result\":{\"sn\":1,\"ws\":[{}]}}}",JsonNode.class)));
    }
}
