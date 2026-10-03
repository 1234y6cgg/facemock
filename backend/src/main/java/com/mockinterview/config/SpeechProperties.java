package com.mockinterview.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.*;

@Component @ConfigurationProperties(prefix="speech") @Validated @Data
public class SpeechProperties {
    private boolean enabled=false;
    private String baseUrl="wss://iat-api.xfyun.cn/v2/iat";
    private String appId="";
    private String apiKey="";
    private String apiSecret="";
    private String language="zh_cn";
    private String storagePath="data/recordings";
    @Min(1) @Max(365) private int retentionDays=30;
    @Min(1) @Max(180) private int maxSeconds=180;
    @Min(30) @Max(600) private int timeoutSeconds=240;
    public boolean configured() {return enabled&&!appId.isBlank()&&!apiKey.isBlank()&&!apiSecret.isBlank();}
}
