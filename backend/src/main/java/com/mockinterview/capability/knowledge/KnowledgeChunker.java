package com.mockinterview.capability.knowledge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.config.KnowledgeProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Component
public class KnowledgeChunker {
    private final KnowledgeProperties properties;
    private final ObjectMapper mapper;

    public KnowledgeChunker(KnowledgeProperties properties, ObjectMapper mapper) {
        if (properties.getChunkOverlap() >= properties.getChunkSize()) {
            throw new IllegalArgumentException("knowledge.chunk-overlap 必须小于 chunk-size");
        }
        this.properties = properties;
        this.mapper = mapper;
    }

    public List<KnowledgeChunk> split(KnowledgeDocument document) {
        String text = document.content().replace("\r\n", "\n").replace('\r', '\n').strip();
        if (text.isBlank()) throw new IllegalArgumentException("知识文档内容不能为空");
        String revision = digest(document, text);
        String tags = document.tags() == null ? "" : String.join("、", document.tags());
        List<KnowledgeChunk> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + properties.getChunkSize(), text.length());
            if (end < text.length()) {
                int boundary = text.lastIndexOf('\n', end - 1);
                if (boundary > start + properties.getChunkSize() / 2) end = boundary + 1;
                if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
            }
            String content = text.substring(start, end).strip();
            if (!content.isBlank()) {
                String id = document.id() + ":" + revision + ":" + chunks.size();
                chunks.add(new KnowledgeChunk(id, document.id(), revision, document.title().strip(),
                        document.source().strip(), document.sourceUrl() == null ? "" : document.sourceUrl().strip(),
                        tags, content));
            }
            if (end == text.length()) break;
            int next = Math.max(start + 1, end - properties.getChunkOverlap());
            if (Character.isLowSurrogate(text.charAt(next))) next++;
            start = next;
        }
        return List.copyOf(chunks);
    }

    private String digest(KnowledgeDocument document, String text) {
        try {
            String canonical = mapper.writeValueAsString(List.of(document.id(), document.title().strip(),
                    text, document.source().strip(), document.sourceUrl() == null ? "" : document.sourceUrl().strip(),
                    document.tags() == null ? List.of() : document.tags(),
                    properties.getChunkSize(), properties.getChunkOverlap(), KnowledgeEmbeddingService.MODEL_ID));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("无法计算知识文档版本", e);
        }
    }
}
