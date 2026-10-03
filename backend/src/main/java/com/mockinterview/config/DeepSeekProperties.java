package com.mockinterview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import com.mockinterview.service.model.ModelConnection;

@Data
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekProperties {
    @lombok.ToString.Exclude
    private String apiKey = "";
    private String baseUrl = "https://api.deepseek.com";
    private String modelName = "deepseek-flash";
    @lombok.Getter(lombok.AccessLevel.NONE) @lombok.Setter(lombok.AccessLevel.NONE) @lombok.ToString.Exclude
    private volatile ModelConnection runtime;
    @lombok.Getter(lombok.AccessLevel.NONE) @lombok.Setter(lombok.AccessLevel.NONE) @lombok.ToString.Exclude
    private final ThreadLocal<ModelConnection> pinned = new ThreadLocal<>();
    public ModelConnection current() {
        var pin = pinned.get(); if (pin != null) return pin;
        var active = runtime;
        return active != null ? active : new ModelConnection("deepseek", baseUrl.replaceAll("/+$", ""), modelName, apiKey, true, 0);
    }
    public void activate(ModelConnection connection) { runtime = connection; }
    public ModelConnection.Scope pin() {
        var previous = pinned.get(); pinned.set(current());
        return () -> { if (previous == null) pinned.remove(); else pinned.set(previous); };
    }
}
