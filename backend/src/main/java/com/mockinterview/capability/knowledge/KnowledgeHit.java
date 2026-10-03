package com.mockinterview.capability.knowledge;

public record KnowledgeHit(
        String id, String documentId, String title, String source,
        String sourceUrl, String content, double score) {
}
