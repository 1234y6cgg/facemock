package com.mockinterview.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "knowledge")
public class KnowledgeProperties {
    private boolean enabled = true;
    private boolean seedOnStartup = true;
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9._-]{1,61}[a-zA-Z0-9]")
    private String collection = "tech_knowledge_bge_zh_v1";
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9_-]{3,100}")
    private String tenant = "default_tenant";
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9_-]{3,100}")
    private String database = "default_database";
    @Min(1) @Max(10)
    private int topK = 3;
    @DecimalMin("0.0") @DecimalMax("1.0")
    private double minScore = 0.45;
    @Min(120) @Max(400)
    private int chunkSize = 360;
    @Min(0) @Max(100)
    private int chunkOverlap = 48;
    @Min(200) @Max(10000)
    private int maxContextChars = 2400;
    @Min(100) @Max(30000)
    private int connectTimeoutMs = 2000;
    @Min(100) @Max(60000)
    private int readTimeoutMs = 5000;
}
