package com.mockinterview.capability.speech;
import org.junit.jupiter.api.Test;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class AudioRulesTest {
    public static byte[] wav(int seconds,boolean sound) {
        var bytes=new byte[44+seconds*32000];var b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII));b.putInt(bytes.length-8);b.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        b.putInt(16);b.putShort((short)1);b.putShort((short)1);b.putInt(16000);b.putInt(32000);b.putShort((short)2);b.putShort((short)16);
        b.put("data".getBytes(StandardCharsets.US_ASCII));b.putInt(bytes.length-44);
        while(b.remaining()>0)b.putShort(sound?(short)(Math.sin(b.position()*.08)*4000):(short)0);
        return bytes;
    }
    @Test void derivesDurationAndRejectsSpoofedHeadersSilenceAndLimits() {
        assertEquals(2000,WavAudio.inspect(wav(2,true),180).durationMs());
        assertEquals(180000,WavAudio.inspect(wav(180,true),180).durationMs());
        assertThrows(IllegalArgumentException.class,()->WavAudio.inspect(wav(181,true),180));
        assertThrows(IllegalArgumentException.class,()->WavAudio.inspect(wav(2,false),180));
        assertThrows(IllegalArgumentException.class,()->WavAudio.inspect(wav(0,true),180));
        var bad=wav(2,true);bad[24]=0;assertThrows(IllegalArgumentException.class,()->WavAudio.inspect(bad,180));
    }
    @Test void oralObservationsAreReproduciblePositionsNotAKnowledgeScore() {
        String text="嗯，那个 Redis 脚本不会覆盖订单数据库。";
        var feedback=OralFeedback.describe(text,8000,120);
        assertEquals(text.codePointCount(0,text.length()),feedback.characterCount());assertEquals(2,feedback.observations().size());
        for(var word:feedback.observations())assertEquals(word.word(),text.substring(word.start(),word.end()));
        assertTrue(feedback.suggestions().get(0).contains("较短"));
        assertTrue(OralFeedback.describe(text,180000,60).suggestions().get(0).contains("超过"));
    }
}
