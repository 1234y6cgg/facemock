package com.mockinterview.service.model;

public record ModelConnection(String providerId, String baseUrl, String modelName, String apiKey, boolean jsonMode, long revision) {
    public boolean configured() { return apiKey != null && !apiKey.isBlank(); }
    public void requireKey() { if (!configured()) throw new IllegalStateException("请在模型设置中配置 API Key"); }
    public String identity() { return "deepseek".equals(providerId) ? modelName : providerId + ":" + modelName; }
    @Override public String toString() { return "ModelConnection[provider=" + providerId + ", model=" + modelName + ", key=REDACTED]"; }
    @FunctionalInterface public interface Scope extends AutoCloseable { @Override void close(); }
}
