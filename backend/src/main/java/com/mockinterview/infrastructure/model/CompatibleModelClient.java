package com.mockinterview.infrastructure.model;

import com.mockinterview.service.model.ModelConnection;
import com.mockinterview.service.question.QuestionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.mockinterview.capability.evaluation.InvalidEvaluationException;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;

@Component
public class CompatibleModelClient {
    private final QuestionJson json;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ThreadPoolExecutor streams=new ThreadPoolExecutor(2,4,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->{var t=new Thread(r,"model-stream");t.setDaemon(true);return t;});
    private final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"model-deadline");t.setDaemon(true);return t;});
    public CompatibleModelClient(QuestionJson json){this.json=json;}
    public record Completion(String content,Map<String,Object> metrics) {}
    public Map<String,Object> body(ModelConnection c,List<Map<String,String>> messages,int maxTokens,boolean structured,boolean stream){
        var body=new LinkedHashMap<String,Object>();body.put("model",c.modelName());body.put("messages",messages);body.put("max_tokens",maxTokens);
        if(stream)body.put("stream",true);
        if(structured&&c.jsonMode())body.put("response_format",Map.of("type","json_object"));
        if("deepseek".equals(c.providerId())&&(c.modelName().startsWith("deepseek-flash")||c.modelName().startsWith("deepseek-pro"))){
            boolean reasoning=structured&&maxTokens>1024;
            body.put("thinking",Map.of("type",reasoning?"enabled":"disabled"));if(reasoning)body.put("reasoning_effort","low");
        }
        if("qwen".equals(c.providerId())&&(c.modelName().startsWith("qwen-plus")||c.modelName().startsWith("qwen-flash")||c.modelName().startsWith("qwen3")))body.put("enable_thinking",false);
        if("kimi".equals(c.providerId())&&"kimi-k3".equals(c.modelName()))body.put("reasoning_effort","low");
        if("minimax".equals(c.providerId())){body.put("reasoning_split",true);if("MiniMax-M3".equals(c.modelName()))body.put("thinking",Map.of("type","disabled"));}
        return body;
    }
    private HttpRequest request(ModelConnection c,Object body,Duration timeout){c.requireKey();
        try{return HttpRequest.newBuilder(URI.create(c.baseUrl().replaceAll("/+$","")+"/chat/completions"))
            .timeout(timeout).header("Authorization","Bearer "+c.apiKey()).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.write(body))).build();}
        catch(RuntimeException e){throw new ModelCallException("模型接口地址或配置无效，请检查模型设置。");}
    }
    private void status(int code){if(code==200)return;
        String message=switch(code){case 401,403->"模型鉴权失败，请检查 API Key 与模型权限。";case 404->"未找到接口或模型，请检查根地址和模型 ID。";
            case 429->"模型额度不足或请求频繁，请检查账户额度后重试。";case 400,422->"模型不支持当前参数，请检查模型 ID 或关闭 JSON 模式后重试。";
            default->"模型服务暂不可用（HTTP "+code+"），请稍后重试。";};throw new ModelCallException(message);
    }
    public Completion complete(ModelConnection c,List<Map<String,String>> messages,int maxTokens,boolean structured,Duration timeout){
        var request=request(c,body(c,messages,maxTokens,structured,false),timeout);
        try{var response=http.send(request,HttpResponse.BodyHandlers.ofString());status(response.statusCode());
            if(response.body().length()>200000)throw new InvalidEvaluationException("模型响应超过长度限制。");
            var payload=json.read(response.body(),JsonNode.class);var choice=payload.path("choices").path(0);
            if(!"stop".equals(choice.path("finish_reason").asText()))throw new InvalidEvaluationException("模型输出未完整结束，请调整模型后重试。");
            var node=choice.path("message").path("content");if(!node.isTextual()||node.asText().isBlank())throw new InvalidEvaluationException("模型没有返回有效正文。");
            var content=node.asText().replaceFirst("(?s)^\\s*<think>.*?</think>\\s*","");
            if(structured){try{var parsed=json.read(content,JsonNode.class);if(!parsed.isObject())throw new InvalidEvaluationException("模型没有返回有效 JSON 对象。");}catch(InvalidEvaluationException e){throw e;}catch(Exception e){throw new InvalidEvaluationException("模型没有返回有效 JSON 对象。");}}
            var metrics=new LinkedHashMap<String,Object>();metrics.put("responseModel",payload.path("model").asText(c.modelName()));metrics.put("providerId",c.providerId());metrics.put("configurationRevision",c.revision());
            for(String field:List.of("prompt_tokens","completion_tokens","total_tokens","prompt_cache_hit_tokens","prompt_cache_miss_tokens"))
                if(payload.path("usage").path(field).isNumber())metrics.put(field,payload.path("usage").path(field).asLong());
            return new Completion(content,Map.copyOf(metrics));
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new ModelCallException("模型调用已中断。");}
        catch(ModelCallException|InvalidEvaluationException e){throw e;}
        catch(Exception e){throw new ModelCallException("模型连接失败、超时或返回格式无效，请检查模型设置。");}
    }
    public List<Map<String,String>> messages(List<ChatMessage> input){return input.stream().map(m->{
        if(m instanceof SystemMessage s)return Map.of("role","system","content",s.text());
        if(m instanceof UserMessage u)return Map.of("role","user","content",u.singleText());
        if(m instanceof AiMessage a)return Map.of("role","assistant","content",a.text()==null?"":a.text());
        throw new ModelCallException("当前训练流程仅支持文本消息。");}).toList();}
    public void stream(ModelConnection c,List<ChatMessage> input,StreamingResponseHandler<AiMessage> handler){
        final HttpRequest request;try{request=request(c,body(c,messages(input),8192,false,true),Duration.ofSeconds(90));}
        catch(RuntimeException e){handler.onError(e);return;}
        try{streams.execute(()->{
            try{var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
                try(var in=response.body()){
                    status(response.statusCode());var timeout=deadlines.schedule(()->{try{in.close();}catch(IOException ignored){}},90,TimeUnit.SECONDS);
                    try(var reader=new BufferedReader(new InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8))){
                        var text=new StringBuilder();boolean finished=false;String line;int size=0;
                        while((line=reader.readLine())!=null){size+=line.length();if(size>400000)throw new ModelCallException("模型响应超过长度限制。");
                            if(!line.startsWith("data:"))continue;var data=line.substring(5).trim();if(data.equals("[DONE]")){break;}if(data.isEmpty())continue;
                            var payload=json.read(data,JsonNode.class);if(payload.has("error"))throw new ModelCallException("模型流式响应失败，请稍后重试。");
                            var choice=payload.path("choices").path(0);var delta=choice.path("delta").path("content");
                            if(delta.isTextual()&&!delta.asText().isEmpty()){text.append(delta.asText());handler.onNext(delta.asText());}
                            if(!choice.path("finish_reason").isMissingNode()&&!choice.path("finish_reason").isNull()){
                                if(!"stop".equals(choice.path("finish_reason").asText()))throw new ModelCallException("模型流式输出未完整结束。");finished=true;}
                        }
                        if(!finished||text.toString().isBlank())throw new ModelCallException("模型连接中断或未返回完整正文，请重试。");
                        handler.onComplete(Response.from(AiMessage.from(text.toString())));
                    }finally{timeout.cancel(false);}
                }
            }catch(InterruptedException e){Thread.currentThread().interrupt();handler.onError(new ModelCallException("模型调用已中断。"));}
            catch(Exception e){handler.onError(e instanceof ModelCallException?e:new ModelCallException("模型流式连接失败或超时，请检查模型设置。"));}
        });}catch(RejectedExecutionException e){handler.onError(new ModelCallException("模型任务较多，请稍后重试。"));}
    }
    @PreDestroy public void close(){streams.shutdownNow();deadlines.shutdownNow();}
}
