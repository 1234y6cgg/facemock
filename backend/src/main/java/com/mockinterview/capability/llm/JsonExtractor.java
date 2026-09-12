package com.mockinterview.capability.llm;

public final class JsonExtractor {

    private JsonExtractor() {
    }

    /** 从 LLM 输出中截取第一个 '{' 到最后一个 '}' 之间的 JSON 对象。 */
    public static String extractJsonObject(String text) {
        if (text == null) {
            return "";
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) {
            return "";
        }
        return text.substring(start, end + 1);
    }
}
