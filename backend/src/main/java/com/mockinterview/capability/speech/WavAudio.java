package com.mockinterview.capability.speech;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

/** The recording encoder and server use one canonical, bounded PCM format. Never trust client duration. */
public record WavAudio(long durationMs,String hash,int dataOffset,int samples) {
    public static WavAudio inspect(byte[] data,int maxSeconds) {
        if(data.length<44||data.length>44L+32000L*maxSeconds) throw new IllegalArgumentException("录音为空或超过大小上限");
        var b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if(!tag(data,0,"RIFF")||b.getInt(4)!=data.length-8||!tag(data,8,"WAVE")||!tag(data,12,"fmt ")
            ||b.getInt(16)!=16||b.getShort(20)!=1||b.getShort(22)!=1||b.getInt(24)!=16000
            ||b.getInt(28)!=32000||b.getShort(32)!=2||b.getShort(34)!=16||!tag(data,36,"data")
            ||b.getInt(40)!=data.length-44||(data.length-44)%2!=0)
            throw new IllegalArgumentException("请使用 16kHz 单声道 PCM WAV 录音");
        int samples=(data.length-44)/2;
        long ms=samples*1000L/16000;
        if(ms<1000||ms>maxSeconds*1000L) throw new IllegalArgumentException("录音需为 1–"+maxSeconds+" 秒");
        long energy=0;
        for(int i=44;i<data.length;i+=2) { int v=b.getShort(i); energy+=(long)v*v; }
        if(Math.sqrt((double)energy/samples)<20) throw new IllegalArgumentException("未检测到有效声音，请检查麦克风后重新录制");
        try { return new WavAudio(ms,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)),44,samples); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static boolean tag(byte[] b,int offset,String s) { return new String(b,offset,4,StandardCharsets.US_ASCII).equals(s); }
}
