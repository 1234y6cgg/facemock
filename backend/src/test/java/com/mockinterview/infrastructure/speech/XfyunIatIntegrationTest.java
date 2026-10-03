package com.mockinterview.infrastructure.speech;
import com.mockinterview.config.SpeechProperties;
import com.mockinterview.capability.speech.WavAudio;
import com.mockinterview.service.question.QuestionJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import java.time.Instant;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in: real vendor traffic using a locally supplied real recording. No transcript is printed. */
@EnabledIfEnvironmentVariable(named="P3_ASR_TEST_WAV",matches=".+")
class XfyunIatIntegrationTest {
    @Test void transcribesRealRecording(){
        try {var p=new SpeechProperties();p.setEnabled(true);p.setAppId(System.getenv().getOrDefault("P3_ASR_APP_ID",""));
            p.setApiKey(System.getenv().getOrDefault("P3_ASR_API_KEY",""));p.setApiSecret(System.getenv().getOrDefault("P3_ASR_API_SECRET",""));assertTrue(p.configured(),"Set P3_ASR_* credentials locally");
            var wav=Files.readAllBytes(Path.of(System.getenv("P3_ASR_TEST_WAV")));var audio=WavAudio.inspect(wav,180);long start=System.nanoTime();
            var text=new XfyunIatTranscriber(p,new QuestionJson()).transcribe(wav);assertFalse(text.isBlank());
            var report=Map.of("verifiedAt",Instant.now().toString(),"provider","xfyun-iat","durationMs",audio.durationMs(),"audioHash",audio.hash(),
                "transcriptHash",QuestionJson.sha256(text),"characterCount",text.codePointCount(0,text.length()),"elapsedMs",(System.nanoTime()-start)/1_000_000,"realAsr",true);
            Files.writeString(Path.of("target/p3-real-asr.json"),new QuestionJson().write(report));
        }catch(Exception e){throw new AssertionError("Real ASR check failed: "+e.getClass().getSimpleName()+"; inspect configuration locally");}
    }
}
