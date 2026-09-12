package com.mockinterview.capability.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonExtractorTest {

    @Test
    void stripsMarkdownFence() {
        String in = "```json\n{\"summary\":\"x\"}\n```";
        assertEquals("{\"summary\":\"x\"}", JsonExtractor.extractJsonObject(in));
    }

    @Test
    void extractsEmbeddedJson() {
        String in = "好的，结果如下：{\"a\":1} 请查收";
        assertEquals("{\"a\":1}", JsonExtractor.extractJsonObject(in));
    }

    @Test
    void emptyWhenNoJson() {
        assertEquals("", JsonExtractor.extractJsonObject("没有 JSON"));
    }
}
