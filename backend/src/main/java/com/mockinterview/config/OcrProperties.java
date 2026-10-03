package com.mockinterview.config;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "resume.ocr")
public class OcrProperties {
    private boolean enabled = true;
    @NotBlank private String baseUrl = "http://localhost:8001";
    @Min(5) @Max(300) private int timeoutSeconds = 120;
    @Min(1) @Max(20) private int maxPdfPages = 10;
    @Min(30) @Max(300) private int minPageTextChars = 80;
}
