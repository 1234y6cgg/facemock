package com.mockinterview.infrastructure.speech;
import com.mockinterview.config.SpeechProperties;
import com.mockinterview.service.question.QuestionJson;
import com.fasterxml.jackson.databind.JsonNode;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class IatProtocol {
    private IatProtocol() {}
    public static URI signedUri(SpeechProperties p,Instant now) {
        var uri=URI.create(p.getBaseUrl());
        if(!"wss".equals(uri.getScheme())||!"iat-api.xfyun.cn".equals(uri.getHost())||!"/v2/iat".equals(uri.getPath())||uri.getQuery()!=null)
            throw new IllegalArgumentException("IAT 地址必须为讯飞官方 WSS 地址");
        String date=DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'",Locale.US).withZone(ZoneOffset.UTC).format(now);
        String raw="host: "+uri.getHost()+"\ndate: "+date+"\nGET "+uri.getPath()+" HTTP/1.1";
        try {var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(p.getApiSecret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            var signature=Base64.getEncoder().encodeToString(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
            var auth="api_key=\""+p.getApiKey()+"\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\""+signature+"\"";
            return URI.create(p.getBaseUrl()+"?authorization="+encode(Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8)))+"&date="+encode(date)+"&host="+encode(uri.getHost()));
        }catch(java.security.GeneralSecurityException e){throw new IllegalStateException("语音认证失败",e);}
    }
    private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    public static String frame(SpeechProperties p,QuestionJson json,int status,byte[] pcm) {
        var message=new LinkedHashMap<String,Object>();
        if(status==0){message.put("common",Map.of("app_id",p.getAppId()));message.put("business",Map.of("language",p.getLanguage(),"domain","iat","accent","mandarin","eos",10000));}
        message.put("data",Map.of("status",status,"format","audio/L16;rate=16000","encoding","raw","audio",Base64.getEncoder().encodeToString(pcm)));
        return json.write(message);
    }
    public static final class Results {
        private final TreeMap<Integer,String> parts=new TreeMap<>();
        private boolean finished;
        public void accept(JsonNode payload) {
            if(!payload.path("code").isInt()||payload.path("code").asInt()!=0)throw new IllegalStateException("讯飞识别失败");
            var data=payload.path("data");var result=data.path("result");
            if(!result.isMissingNode()&&!result.isNull()) {
                if(!result.path("sn").isInt()||!result.path("ws").isArray())throw new IllegalStateException("讯飞返回结构不正确");
                int sn=result.path("sn").asInt();if(sn<0||sn>10000)throw new IllegalStateException("讯飞结果序号不正确");
                if("rpl".equals(result.path("pgs").asText())) {var rg=result.path("rg");
                    if(!rg.isArray()||rg.size()!=2||!rg.get(0).isInt()||!rg.get(1).isInt()||rg.get(0).asInt()>rg.get(1).asInt())throw new IllegalStateException("讯飞修正范围不正确");
                    parts.subMap(rg.get(0).asInt(),true,rg.get(1).asInt(),true).clear(); }
                var text=new StringBuilder();for(var word:result.path("ws")){var w=word.path("cw").path(0).path("w");if(!w.isTextual())throw new IllegalStateException("讯飞词语结构不正确");text.append(w.asText());}
                parts.put(sn,text.toString());if(text().length()>10000)throw new IllegalStateException("转写文本过长");
            }
            if(data.path("status").asInt(-1)==2)finished=true;
        }
        public boolean finished(){return finished;}
        public String text(){return String.join("",parts.values());}
    }
}
