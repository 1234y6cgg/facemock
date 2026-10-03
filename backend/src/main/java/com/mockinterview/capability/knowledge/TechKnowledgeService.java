package com.mockinterview.capability.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.config.KnowledgeProperties;
import com.mockinterview.infrastructure.chroma.ChromaKnowledgeStore;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class TechKnowledgeService {
    private static final Logger log = LoggerFactory.getLogger(TechKnowledgeService.class);
    private final KnowledgeProperties properties;
    private final KnowledgeChunker chunker;
    private final KnowledgeEmbeddingService embeddings;
    private final ChromaKnowledgeStore store;
    private final ObjectMapper mapper;
    private final Validator validator;
    private volatile boolean seeded;

    public TechKnowledgeService(KnowledgeProperties properties, KnowledgeChunker chunker,
                               KnowledgeEmbeddingService embeddings, ChromaKnowledgeStore store,
                               ObjectMapper mapper, Validator validator) {
        this.properties = properties;
        this.chunker = chunker;
        this.embeddings = embeddings;
        this.store = store;
        this.mapper = mapper;
        this.validator = validator;
    }

    public List<KnowledgeHit> search(String query, int topK) {
        requireEnabled();
        if (query == null || query.isBlank() || query.length() > 500) {
            throw new IllegalArgumentException("检索问题不能为空，且最多 500 个字符");
        }
        if (topK < 1 || topK > 10) throw new IllegalArgumentException("topK 必须在 1 到 10 之间");
        try {
            // Retry a failed startup import on the next search; successful imports are idempotent.
            if (properties.isSeedOnStartup() && !seeded) seedBuiltin();
            return store.query(embeddings.embedQuery(query.strip()), topK, properties.getMinScore());
        } catch (KnowledgeUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw unavailable("检索", e);
        }
    }

    /** Interview requests can continue when the knowledge service is unavailable. */
    public String retrieve(String query, int topK) {
        if (!properties.isEnabled() || query == null || query.isBlank()) return "";
        try {
            return context(search(query, topK));
        } catch (KnowledgeUnavailableException e) {
            log.warn("面试知识检索不可用，将继续面试：{}", e.getMessage());
            return "";
        }
    }

    public int defaultTopK() { return properties.getTopK(); }

    public synchronized ImportResult upsert(KnowledgeDocument document) {
        requireEnabled();
        if (document == null) throw new IllegalArgumentException("知识文档不能为空");
        var violations = validator.validate(document);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        List<KnowledgeChunk> chunks = chunker.split(document);
        try {
            boolean changed = !store.containsChunks(chunks);
            if (changed) store.upsert(chunks, embeddings.embedChunks(chunks));
            // Prune only after the new revision is safely stored, including after a partial retry.
            store.removeOldRevisions(document.id(), chunks.get(0).revision());
            return new ImportResult(document.id(), chunks.get(0).revision(), chunks.size(), changed);
        } catch (RuntimeException e) {
            throw unavailable("导入", e);
        }
    }

    public synchronized List<ImportResult> seedBuiltin() {
        requireEnabled();
        try (var input = new ClassPathResource("knowledge/java-backend.json").getInputStream()) {
            List<KnowledgeDocument> documents = mapper.readValue(input, new TypeReference<>() {});
            List<ImportResult> results = documents.stream().map(this::upsert).toList();
            seeded = true;
            log.info("知识库导入完成：{} 篇，{} 篇更新，collection={}", results.size(),
                    results.stream().filter(ImportResult::changed).count(), properties.getCollection());
            return results;
        } catch (KnowledgeUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw unavailable("内置知识导入", e);
        }
    }

    public synchronized void deleteDocument(String documentId) {
        requireEnabled();
        if (documentId == null || !documentId.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,99}")) {
            throw new IllegalArgumentException("文档 ID 格式不正确");
        }
        try {
            store.deleteDocument(documentId);
        } catch (RuntimeException e) {
            throw unavailable("删除", e);
        }
    }

    public Status status() {
        if (!properties.isEnabled()) return new Status(false, false, properties.getCollection(),
                KnowledgeEmbeddingService.MODEL_ID, KnowledgeEmbeddingService.DIMENSIONS, null, "知识库已禁用");
        try {
            return new Status(true, true, properties.getCollection(), KnowledgeEmbeddingService.MODEL_ID,
                    KnowledgeEmbeddingService.DIMENSIONS, store.count(), "正常");
        } catch (RuntimeException e) {
            log.warn("知识库状态检查失败", e);
            return new Status(true, false, properties.getCollection(), KnowledgeEmbeddingService.MODEL_ID,
                    KnowledgeEmbeddingService.DIMENSIONS, null, "ChromaDB 不可用或集合配置不匹配，请检查后端日志");
        }
    }

    private String context(List<KnowledgeHit> hits) {
        StringBuilder result = new StringBuilder();
        int reference = 1;
        for (KnowledgeHit hit : hits) {
            String header = String.format(Locale.ROOT, "[K%d] %s（来源：%s；相似度：%.3f）\n%s\n",
                    reference, hit.title(), hit.source(), hit.score(), hit.sourceUrl());
            int remaining = properties.getMaxContextChars() - result.length();
            if (remaining < header.length() + 80) break;
            result.append(header);
            int length = Math.min(hit.content().length(), remaining - header.length() - 2);
            if (length > 0 && Character.isHighSurrogate(hit.content().charAt(length - 1))) length--;
            result.append(hit.content(), 0, length).append("\n\n");
            reference++;
        }
        return result.toString().strip();
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) throw new KnowledgeUnavailableException("知识库已禁用");
    }

    private KnowledgeUnavailableException unavailable(String action, Exception e) {
        log.warn("知识库{}失败", action, e);
        return new KnowledgeUnavailableException("知识库" + action + "失败，请检查 ChromaDB 连接和集合配置", e);
    }

    public record ImportResult(String documentId, String revision, int chunks, boolean changed) {}
    public record Status(boolean enabled, boolean available, String collection, String embeddingModel,
                         int dimensions, Long totalChunks, String message) {}
}
