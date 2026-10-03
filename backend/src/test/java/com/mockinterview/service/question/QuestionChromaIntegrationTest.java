package com.mockinterview.service.question;

import com.mockinterview.capability.knowledge.*;
import com.mockinterview.capability.evaluation.EvaluationFixtures;
import com.mockinterview.config.KnowledgeProperties;
import com.mockinterview.infrastructure.chroma.ChromaKnowledgeStore;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15q.BgeSmallZhV15QuantizedEmbeddingModel;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "P1_CHROMA_TEST_URL", matches = ".+")
class QuestionChromaIntegrationTest {
    @Test
    void versionedQuestionDocumentsAreIndexedAndResolvable() {
        String baseUrl = System.getenv("P1_CHROMA_TEST_URL");
        var properties = new KnowledgeProperties();
        properties.setCollection("p1_test_" + UUID.randomUUID().toString().replace("-", ""));
        properties.setSeedOnStartup(false);
        var executor = Executors.newFixedThreadPool(2);
        var store = new ChromaKnowledgeStore(baseUrl, properties);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var json = new QuestionJson();
            var catalog = new QuestionCatalogLoader(json, new QuestionCatalogValidator(factory.getValidator())).loadBuiltin();
            var service = new TechKnowledgeService(properties, new KnowledgeChunker(properties, new com.fasterxml.jackson.databind.ObjectMapper()),
                    new KnowledgeEmbeddingService(new BgeSmallZhV15QuantizedEmbeddingModel(executor)),
                    store, new com.fasterxml.jackson.databind.ObjectMapper(), factory.getValidator());
            for (var source : catalog.sources()) assertTrue(service.upsert(source.document()).changed());
            long count = store.count();
            for (var source : catalog.sources()) assertFalse(service.upsert(source.document()).changed());
            assertEquals(count, store.count());
            assertEquals(catalog.sources().size(), count);
            assertReference(service, catalog, "Redis 锁要怎样避免删除别人的锁", "redis.lock", json);
            assertReference(service, catalog, "SQL 慢查询如何分析执行计划和实际扫描行数", "mysql.explain", json);
            assertReference(service, catalog, "无界队列为什么最大线程数不生效", "java.pool-unbounded", json);
        } finally {
            executor.shutdownNow();
            RestClient.create(baseUrl).delete().uri("/api/v2/tenants/default_tenant/databases/default_database/collections/"
                    + properties.getCollection()).retrieve().toBodilessEntity();
        }
    }

    private void assertReference(TechKnowledgeService service, com.mockinterview.domain.question.QuestionCatalog catalog,
            String query, String questionId, QuestionJson json) {
        var hits = service.search(query, 3);
        assertFalse(hits.isEmpty(), query);
        assertEquals("p1." + questionId + ".v1", hits.get(0).documentId(), query);
        var snapshot = EvaluationFixtures.snapshot(catalog, questionId, json);
        var reference = snapshot.sources().stream().filter(s -> s.documentId().equals(hits.get(0).documentId())).findFirst().orElseThrow();
        assertEquals(reference.sourceUrl(), hits.get(0).sourceUrl());
        assertEquals(64, reference.documentRevision().length());
        assertTrue(reference.content().contains(hits.get(0).content()));
        System.out.printf("P1 RAG top=%s score=%.3f%n", hits.get(0).documentId(), hits.get(0).score());
    }
}
