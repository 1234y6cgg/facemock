package com.mockinterview.capability.knowledge;

public record KnowledgeChunk(
        String id, String documentId, String revision, String title,
        String source, String sourceUrl, String tags, String content) {
    public String embeddingText() {
        return prefix(title, 40) + "\n主题：" + prefix(tags, 32) + "\n" + content;
    }

    private static String prefix(String value, int max) {
        int end = Math.min(value.length(), max);
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end);
    }
}
