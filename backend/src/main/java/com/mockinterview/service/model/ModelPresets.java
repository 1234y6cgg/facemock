package com.mockinterview.service.model;

import java.util.List;

public final class ModelPresets {
    private ModelPresets() {}
    public record Preset(String id, String name, String baseUrl, List<String> models, boolean jsonMode, String note, String docsUrl) {}
    public static final List<Preset> ALL = List.of(
        new Preset("deepseek", "DeepSeek", "https://api.deepseek.com", List.of("deepseek-flash", "deepseek-pro"), true,
            "使用 DeepSeek 开放平台的 API Key。", "https://api-docs.deepseek.com/"),
        new Preset("qwen", "通义千问 · 阿里云百炼", "https://dashscope.aliyuncs.com/compatible-mode/v1", List.of("qwen-plus", "qwen-flash", "qwen-max"), true,
            "预设为北京地域；其他地域或业务空间专属域名请修改接口地址。", "https://help.aliyun.com/zh/model-studio/qwen-api-via-openai-chat-completions"),
        new Preset("doubao", "豆包 · 火山方舟", "https://ark.cn-beijing.volces.com/api/v3", List.of("doubao-seed-2-1-pro-260628"), false,
            "需开通模型；可填写方舟控制台提供的 Model ID 或 ep- 开头的接入点 ID。", "https://docs.volcengine.com/docs/ark/compatible-with-openai-sdk?lang=zh"),
        new Preset("glm", "智谱 · GLM", "https://open.bigmodel.cn/api/paas/v4", List.of("glm-5.3", "glm-5.3-flash"), false,
            "预设为通用 API；Coding Plan 专属端点与适用范围请以平台说明为准。", "https://docs.bigmodel.cn/cn/guide/start/quick-start"),
        new Preset("kimi", "Kimi · 月之暗面", "https://api.moonshot.cn/v1", List.of("kimi-k3", "kimi-k2.6"), true,
            "使用开放平台 Key；模型名称可以按控制台可用列表修改。", "https://platform.kimi.com/docs/models"),
        new Preset("minimax", "MiniMax", "https://api.minimax.cn/v1", List.of("MiniMax-M3", "MiniMax-M2.7", "MiniMax-M2.5"), false,
            "使用通用 OpenAI 兼容接口，模型权限以开放平台为准。", "https://platform.minimax.cn/docs/api-reference/text-openai-api"),
        new Preset("custom", "自定义兼容服务", "", List.of(), false,
            "填写 OpenAI Chat Completions 兼容的接口根地址和模型 ID。", "")
    );
    public static Preset get(String id) { return ALL.stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("请选择有效的模型服务商")); }
}
