package com.mockinterview.service.model;

import com.mockinterview.config.DeepSeekProperties;
import com.mockinterview.service.question.QuestionJson;
import com.mockinterview.service.practice.PracticeConflictException;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;

@Service
public class ModelSettingsService {
    private final DeepSeekProperties properties;
    private final ModelSecretStore storage;
    public record View(String providerId,String baseUrl,String modelName,boolean jsonMode,boolean configured,String source,long revision,String scope) {}
    public record Update(@NotBlank String providerId,@NotBlank @Size(max=2048) String baseUrl,@NotBlank @Size(max=120) String modelName,
        @Size(max=4096) String apiKey,boolean jsonMode,@Min(0) long revision) {
        @Override public String toString(){return "ModelUpdate[provider="+providerId+",key=REDACTED]";}
    }
    public record Check(boolean ok,String message,long durationMs,String modelName,String checkedAt) {}
    @org.springframework.beans.factory.annotation.Autowired
    public ModelSettingsService(DeepSeekProperties properties,QuestionJson json,@Value("${models.storage-path:data/model-settings}") String path){
        this(properties,new ModelSecretStore(Path.of(path),json));
    }
    public ModelSettingsService(DeepSeekProperties properties,ModelSecretStore storage){
        this.properties=properties;this.storage=storage;
        storage.read().ifPresent(s->properties.activate(new ModelConnection(s.providerId(),s.baseUrl(),s.modelName(),storage.decrypt(s.encryptedKey()),s.jsonMode(),s.revision())));
    }
    public synchronized View view(){var c=properties.current();return new View(c.providerId(),c.baseUrl(),c.modelName(),c.jsonMode(),c.configured(),c.revision()==0?"environment":"user",c.revision(),"personal-instance");}
    private String endpoint(String value){
        try{var u=URI.create(value.trim());var local="localhost".equalsIgnoreCase(u.getHost())||"127.0.0.1".equals(u.getHost())||"[::1]".equals(u.getHost());
            if(u.getHost()==null||u.getUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null
                ||!("https".equalsIgnoreCase(u.getScheme())||local&&"http".equalsIgnoreCase(u.getScheme())))throw new IllegalArgumentException();
            String normalized=value.trim().replaceAll("/+$","");if(normalized.endsWith("/chat/completions"))throw new IllegalArgumentException();return normalized;
        }catch(Exception e){throw new IllegalArgumentException("填写 HTTPS 接口根地址，不含密钥、查询参数或 /chat/completions；本机 localhost 可用 HTTP");}
    }
    private ModelConnection candidate(Update input){
        ModelPresets.get(input.providerId());var current=properties.current();
        if(input.revision()!=current.revision())throw new PracticeConflictException("模型配置已更新，请刷新后重新填写");
        var url=endpoint(input.baseUrl());var model=input.modelName().trim();
        if(model.isEmpty()||model.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("模型 ID 格式不正确");
        var key=input.apiKey()==null?"":input.apiKey().trim();
        if(key.isEmpty()){
            if(!current.providerId().equals(input.providerId())||!current.baseUrl().equals(url))throw new IllegalArgumentException("切换服务商或接口地址时，请重新填写该服务的 API Key");
            key=current.apiKey();
        }
        if(key==null||key.isBlank()||key.chars().anyMatch(Character::isWhitespace)||key.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("请填写有效的 API Key，不能包含空白字符");
        return new ModelConnection(input.providerId(),url,model,key,input.jsonMode(),current.revision()+1);
    }
    public synchronized View save(Update input){var next=candidate(input);storage.write(next);properties.activate(next);return view();}
    public synchronized View clear(long revision){var current=properties.current();if(current.revision()!=revision)throw new PracticeConflictException("模型配置已更新，请刷新后重试");
        var empty=new ModelConnection(current.providerId(),current.baseUrl(),current.modelName(),"",current.jsonMode(),revision+1);storage.write(empty);properties.activate(empty);return view();}
    public Check check(Update input,com.mockinterview.infrastructure.model.CompatibleModelClient client){
        final ModelConnection c; synchronized(this){c=candidate(input);}long start=System.nanoTime();
        try{client.complete(c,java.util.List.of(java.util.Map.of("role","user","content","只返回 JSON 对象 {\"ok\":true}，不要解释。")),1024,true,java.time.Duration.ofSeconds(30));
            return new Check(true,"连接成功，模型能够返回有效 JSON；保存后才会用于练习。",(System.nanoTime()-start)/1_000_000,c.modelName(),Instant.now().toString());
        }catch(RuntimeException e){return new Check(false,e instanceof com.mockinterview.infrastructure.model.ModelCallException?e.getMessage():"模型连接失败或返回格式不符合要求，请检查地址、模型权限和 Key。",(System.nanoTime()-start)/1_000_000,c.modelName(),Instant.now().toString());}
    }
}
