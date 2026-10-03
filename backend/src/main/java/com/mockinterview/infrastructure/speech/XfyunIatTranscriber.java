package com.mockinterview.infrastructure.speech;
import com.mockinterview.capability.speech.*;
import com.mockinterview.config.SpeechProperties;
import com.mockinterview.service.question.QuestionJson;
import org.springframework.stereotype.Component;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Component
public class XfyunIatTranscriber implements SpeechTranscriber {
    public static final int SEGMENT_BYTES=50*32000;
    private final SpeechProperties settings;private final QuestionJson json;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    public XfyunIatTranscriber(SpeechProperties settings,QuestionJson json){this.settings=settings;this.json=json;}
    public String provider(){return "xfyun-iat";}
    public String transcribe(byte[] wav) {
        if(!settings.configured())throw new IllegalStateException("语音服务未配置");
        WavAudio.inspect(wav,settings.getMaxSeconds());
        var text=new StringBuilder();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(settings.getTimeoutSeconds());
        for(int start=44;start<wav.length;start+=SEGMENT_BYTES){
            text.append(segment(Arrays.copyOfRange(wav,start,Math.min(wav.length,start+SEGMENT_BYTES)),deadline));
            if(text.length()>10000)throw new IllegalStateException("转写文本过长");
        }
        if(text.toString().isBlank())throw new IllegalStateException("未识别到有效语音");return text.toString();
    }
    private String segment(byte[] pcm,long deadline) {
        var listener=new Listener(json);WebSocket socket=null;
        try {
            socket=client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5)).buildAsync(IatProtocol.signedUri(settings,Instant.now()),listener).get(6,TimeUnit.SECONDS);
            for(int offset=0;offset<pcm.length;offset+=1280){
                if(System.nanoTime()>deadline)throw new TimeoutException();
                if(listener.result.isDone())throw new IllegalStateException("识别会话提前结束，未接收完整音频");
                socket.sendText(IatProtocol.frame(settings,json,offset==0?0:1,Arrays.copyOfRange(pcm,offset,Math.min(pcm.length,offset+1280))),true).get(5,TimeUnit.SECONDS);
                Thread.sleep(40);
            }
            socket.sendText(IatProtocol.frame(settings,json,2,new byte[0]),true).get(5,TimeUnit.SECONDS);
            long remaining=Math.min(TimeUnit.SECONDS.toNanos(10),deadline-System.nanoTime());
            if(remaining<=0)throw new TimeoutException();
            return listener.result.get(remaining,TimeUnit.NANOSECONDS);
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("转写被中断",e);}
        catch(ExecutionException|TimeoutException e){throw new IllegalStateException("讯飞识别连接失败或超时",e);}
        finally{if(socket!=null)socket.abort();}
    }
    private static final class Listener implements WebSocket.Listener {
        private final QuestionJson json;private final IatProtocol.Results parts=new IatProtocol.Results();
        private final CompletableFuture<String> result=new CompletableFuture<>();private final StringBuilder buffer=new StringBuilder();
        Listener(QuestionJson json){this.json=json;}
        public void onOpen(WebSocket socket){socket.request(1);}
        public CompletionStage<?> onText(WebSocket socket,CharSequence data,boolean last){
            try {buffer.append(data);if(buffer.length()>100000)throw new IllegalStateException("识别响应过长");
                if(last){parts.accept(json.read(buffer.toString(),com.fasterxml.jackson.databind.JsonNode.class));buffer.setLength(0);
                    if(parts.finished())result.complete(parts.text());}
            }catch(RuntimeException e){result.completeExceptionally(new IllegalStateException("识别响应校验失败"));}
            socket.request(1);return null;
        }
        public CompletionStage<?> onClose(WebSocket socket,int code,String reason){if(!result.isDone())result.completeExceptionally(new IllegalStateException("识别未完整结束"));return null;}
        public void onError(WebSocket socket,Throwable error){result.completeExceptionally(new IllegalStateException("语音连接中断"));}
    }
}
