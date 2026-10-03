package com.mockinterview.infrastructure.chroma;

import com.fasterxml.jackson.databind.JsonNode;
import com.mockinterview.capability.knowledge.KnowledgeChunk;
import com.mockinterview.capability.knowledge.KnowledgeEmbeddingService;
import com.mockinterview.capability.knowledge.KnowledgeHit;
import com.mockinterview.config.KnowledgeProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Component
public class ChromaKnowledgeStore {
    private final RestClient http;
    private final KnowledgeProperties properties;
    private volatile String collectionId;

    public ChromaKnowledgeStore(@Value("${chroma.base-url:http://localhost:8000}") String baseUrl,
                                KnowledgeProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public synchronized String ensureCollection() {
        if (collectionId != null) return collectionId;
        JsonNode collection = http.post().uri(collectionsPath())
                .body(Map.of("name", properties.getCollection(), "get_or_create", true,
                        "configuration", Map.of("hnsw", Map.of("space", "cosine")),
                        "metadata", Map.of("embedding_model", KnowledgeEmbeddingService.MODEL_ID,
                                "managed_by", "mock-interview")))
                .retrieve().body(JsonNode.class);
        if (collection == null || collection.path("id").asText().isBlank()) {
            throw new IllegalStateException("ChromaDB 未返回有效的 collection ID");
        }
        if (!KnowledgeEmbeddingService.MODEL_ID.equals(collection.path("metadata").path("embedding_model").asText())) {
            throw new IllegalStateException("知识集合的向量模型不匹配，请配置独立的 knowledge.collection");
        }
        JsonNode dimension = collection.path("dimension");
        if (dimension.isNumber() && dimension.asInt() != KnowledgeEmbeddingService.DIMENSIONS) {
            throw new IllegalStateException("知识集合的向量维度不匹配");
        }
        if (!"cosine".equals(collection.path("configuration_json").path("hnsw").path("space").asText())) {
            throw new IllegalStateException("知识集合必须使用 cosine 距离");
        }
        collectionId = collection.path("id").asText();
        return collectionId;
    }

    public boolean containsChunks(List<KnowledgeChunk> chunks) {
        List<String> ids = chunks.stream().map(KnowledgeChunk::id).toList();
        JsonNode result = post("get", Map.of("ids", ids, "include", List.of("metadatas")));
        if (!result.path("ids").isArray()) throw new IllegalStateException("ChromaDB get 响应格式不正确");
        var found = new HashSet<String>();
        result.path("ids").forEach(id -> found.add(id.asText()));
        return found.containsAll(ids);
    }

    public void upsert(List<KnowledgeChunk> chunks, List<List<Float>> vectors) {
        if (chunks.size() != vectors.size()) throw new IllegalArgumentException("知识分块和向量数量不匹配");
        List<Map<String, String>> metadata = chunks.stream().map(c -> Map.of(
                "document_id", c.documentId(), "revision", c.revision(), "title", c.title(),
                "source", c.source(), "source_url", c.sourceUrl(), "tags", c.tags())).toList();
        post("upsert", Map.of("ids", chunks.stream().map(KnowledgeChunk::id).toList(),
                "embeddings", vectors, "documents", chunks.stream().map(KnowledgeChunk::content).toList(),
                "metadatas", metadata));
    }

    public void removeOldRevisions(String documentId, String revision) {
        post("delete", Map.of("where", Map.of("$and", List.of(
                Map.of("document_id", Map.of("$eq", documentId)),
                Map.of("revision", Map.of("$ne", revision))))));
    }

    public void deleteDocument(String documentId) {
        post("delete", Map.of("where", Map.of("document_id", Map.of("$eq", documentId))));
    }

    public long count() {
        try {
            Long count = http.get().uri(collectionPath() + "/count").retrieve().body(Long.class);
            if (count == null) throw new IllegalStateException("ChromaDB 未返回知识分块数量");
            return count;
        } catch (HttpClientErrorException.NotFound e) {
            collectionId = null;
            throw e;
        }
    }

    public List<KnowledgeHit> query(List<Float> embedding, int topK, double minScore) {
        JsonNode result = post("query", Map.of("query_embeddings", List.of(embedding),
                "n_results", topK, "include", List.of("documents", "metadatas", "distances")));
        JsonNode ids = row(result, "ids"), documents = row(result, "documents");
        JsonNode metadata = row(result, "metadatas"), distances = row(result, "distances");
        if (!ids.isArray() || !documents.isArray() || !metadata.isArray() || !distances.isArray()
                || ids.size() != documents.size() || ids.size() != metadata.size() || ids.size() != distances.size()) {
            throw new IllegalStateException("ChromaDB query 响应格式不正确");
        }
        List<KnowledgeHit> hits = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            JsonNode distance = distances.path(i), document = documents.path(i), m = metadata.path(i);
            if (!distance.isNumber() || !Double.isFinite(distance.asDouble()) || !document.isTextual()) continue;
            double score = Math.max(0, Math.min(1, 1 - distance.asDouble()));
            if (score < minScore || document.asText().isBlank()) continue;
            hits.add(new KnowledgeHit(ids.get(i).asText(), m.path("document_id").asText(),
                    m.path("title").asText(), m.path("source").asText(), m.path("source_url").asText(),
                    document.asText(), score));
        }
        return List.copyOf(hits);
    }

    private JsonNode post(String action, Object body) {
        try {
            JsonNode result = http.post().uri(collectionPath() + "/" + action).body(body)
                    .retrieve().body(JsonNode.class);
            // Successful write endpoints may return JSON null or an empty response.
            return result == null ? com.fasterxml.jackson.databind.node.MissingNode.getInstance() : result;
        } catch (HttpClientErrorException.NotFound e) {
            collectionId = null;
            throw e;
        }
    }

    private JsonNode row(JsonNode result, String name) {
        return result.path(name).path(0);
    }

    private String collectionsPath() {
        // Tenant/database identifiers are configuration, not untrusted URL input.
        return "/api/v2/tenants/" + properties.getTenant() + "/databases/"
                + properties.getDatabase() + "/collections";
    }

    private String collectionPath() {
        return collectionsPath() + "/" + ensureCollection();
    }
}
