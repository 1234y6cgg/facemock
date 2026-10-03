package com.mockinterview.service.speech;
import java.nio.*;
import java.nio.charset.StandardCharsets;
final class AudioRulesFixture {
    static byte[] wav(int seconds){var bytes=new byte[44+seconds*32000];var b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII));b.putInt(bytes.length-8);b.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));b.putInt(16);
        b.putShort((short)1);b.putShort((short)1);b.putInt(16000);b.putInt(32000);b.putShort((short)2);b.putShort((short)16);
        b.put("data".getBytes(StandardCharsets.US_ASCII));b.putInt(bytes.length-44);while(b.remaining()>0)b.putShort((short)(Math.sin(b.position()*.08)*4000));return bytes;}
}
