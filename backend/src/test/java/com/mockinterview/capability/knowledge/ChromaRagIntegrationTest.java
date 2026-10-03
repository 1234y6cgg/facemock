package com.mockinterview.capability.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.config.KnowledgeProperties;
import com.mockinterview.infrastructure.chroma.ChromaKnowledgeStore;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15q.BgeSmallZhV15QuantizedEmbeddingModel;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import com.mockinterview.config.KnowledgeConfig;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in tests against a disposable, real Chroma v2 server and the packaged Chinese model. */
@EnabledIfEnvironmentVariable(named = "RAG_CHROMA_TEST_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChromaRagIntegrationTest {
    private final KnowledgeProperties properties = new KnowledgeProperties();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private ValidatorFactory validators;
    private ChromaKnowledgeStore store;
    private KnowledgeEmbeddingService embeddings;
    private TechKnowledgeService knowledge;

    @BeforeAll
    void setup() {
        properties.setCollection("rag_test_" + UUID.randomUUID().toString().replace("-", ""));
        properties.setSeedOnStartup(false);
        var mapper = new ObjectMapper();
        validators = Validation.buildDefaultValidatorFactory();
        store = new ChromaKnowledgeStore(System.getenv("RAG_CHROMA_TEST_URL"), properties);
        embeddings = new KnowledgeEmbeddingService(new BgeSmallZhV15QuantizedEmbeddingModel(executor));
        knowledge = new TechKnowledgeService(properties, new KnowledgeChunker(properties, mapper),
                embeddings, store, mapper, validators.getValidator());
        knowledge.seedBuiltin();
    }

    @AfterAll
    void cleanup() {
        try {
            if (store != null) RestClient.create(System.getenv("RAG_CHROMA_TEST_URL")).delete()
                    .uri("/api/v2/tenants/default_tenant/databases/default_database/collections/" + properties.getCollection())
                    .retrieve().toBodilessEntity();
        } finally {
            executor.shutdownNow();
            if (validators != null) validators.close();
        }
    }

    @Test
    void realChineseQueriesFindExpectedSources() {
        assertTopHit("秒杀活动很多人同时抢购，怎样避免库存被扣成负数？", "builtin.redis.lua-stock");
        assertTopHit("一条 SQL 特别慢，如何查看执行计划判断索引有没有被使用？", "builtin.mysql.explain");
        assertTopHit("消费者写入数据库后崩溃，消息重新消费怎么避免重复订单？", "builtin.kafka.delivery");
        assertTopHit("线程池用了无界队列，为什么最大线程数没有生效？", "builtin.java.threadpool");
        assertTrue(knowledge.search("古典芭蕾舞演员的足尖技巧训练", 3).isEmpty(), "无关问题不应硬凑技术知识");
    }

    @Test
    void reimportIsIdempotentAndUpdatesRemoveOldChunks() {
        long initial = store.count();
        assertTrue(knowledge.seedBuiltin().stream().noneMatch(TechKnowledgeService.ImportResult::changed));
        assertEquals(initial, store.count());
        var original = new KnowledgeDocument("custom.inventory", "自定义库存方案", "库存扣减方案。".repeat(110),
                "我的项目文档", "", List.of("库存"));
        var first = knowledge.upsert(original);
        assertTrue(first.chunks() > 1);
        assertTrue(first.changed());
        assertFalse(knowledge.upsert(original).changed());
        assertEquals(initial + first.chunks(), store.count());
        var edited = new KnowledgeDocument(original.id(), original.title(), "新方案使用数据库条件更新扣减库存。",
                original.source(), "", original.tags());
        var second = knowledge.upsert(edited);
        assertNotEquals(first.revision(), second.revision());
        assertEquals(initial + 1, store.count());
        knowledge.deleteDocument(original.id());
        assertEquals(initial, store.count());
    }

    @Test
    void embeddingsAreRealAndContextCarriesBoundedReferences() {
        var redis = embeddings.embedQuery("Redis 库存扣减");
        assertEquals(512, redis.size());
        assertTrue(redis.stream().anyMatch(v -> Math.abs(v) > 0.001));
        assertNotEquals(redis, embeddings.embedQuery("MySQL 慢查询"));
        properties.setMaxContextChars(500);
        try {
            String context = knowledge.retrieve("Redis Lua 库存如何原子扣减", 3);
            assertTrue(context.startsWith("[K1]"));
            assertTrue(context.contains("redis.io"));
            assertTrue(context.contains("库存"));
            assertTrue(context.length() <= 500);
        } finally {
            properties.setMaxContextChars(2400);
        }
    }

    @Test
    void springBeansResolveTheLazyModelAndValidatedProperties() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                    Map.of("knowledge.enabled", "false", "knowledge.collection", "test_spring_collection")));
            context.register(KnowledgeConfig.class, KnowledgeEmbeddingService.class);
            context.refresh();
            assertFalse(context.getBean(KnowledgeProperties.class).isEnabled());
            assertFalse(context.getBeanFactory().containsSingleton("knowledgeEmbeddingModel"));
            assertEquals(512, context.getBean(KnowledgeEmbeddingService.class).embedQuery("库存").size());
            assertTrue(context.getBeanFactory().containsSingleton("knowledgeEmbeddingModel"));
        }
    }

    private void assertTopHit(String query, String documentId) {
        var hits = knowledge.search(query, 3);
        assertFalse(hits.isEmpty(), query);
        System.out.printf("RAG query=%s top=%s score=%.3f%n", query, hits.get(0).documentId(), hits.get(0).score());
        assertEquals(documentId, hits.get(0).documentId(), query);
        assertTrue(hits.get(0).score() >= properties.getMinScore());
        assertFalse(hits.get(0).sourceUrl().isBlank());
    }
}
