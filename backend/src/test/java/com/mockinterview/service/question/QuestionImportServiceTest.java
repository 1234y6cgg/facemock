package com.mockinterview.service.question;

import com.mockinterview.domain.question.*;
import com.mockinterview.repository.question.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {"spring.datasource.url=jdbc:h2:mem:p1;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = P1JpaConfiguration.class)
class QuestionImportServiceTest {
    @Autowired QuestionImportService importer;
    @Autowired QuestionCatalogLoader loader;
    @Autowired QuestionService service;
    @Autowired QuestionRepository questions;
    @Autowired QuestionRevisionRepository revisions;
    @Autowired QuestionSourceRevisionRepository sources;
    @Autowired RubricCriterionRepository criteria;
    @Autowired QuestionJson json;

    @Test
    void repeatImportIsIdempotentAndComplete() {
        var catalog = loader.loadBuiltin();
        var first = importer.importCatalog(catalog);
        var second = importer.importCatalog(catalog);
        assertEquals(catalog.questions().size(), first.newQuestions());
        assertEquals(catalog.questions().size(), first.newRevisions());
        assertEquals(catalog.knowledgePoints().size(), first.newKnowledgePoints());
        assertEquals(catalog.questions().size(), second.unchangedRevisions());
        assertEquals(0, second.newQuestions());
        assertEquals(0, second.newRevisions());
        assertEquals(catalog.questions().size(), questions.count());
        assertEquals(catalog.questions().size(), revisions.count());
        assertEquals(catalog.sources().size(), sources.count());
        assertEquals(catalog.questions().stream().mapToInt(q -> q.criteria().size()).sum(), criteria.count());
    }

    @Test
    void revisionIsAppendOnlyAndOlderImportCannotDowngradeCurrent() {
        var catalog = loader.loadBuiltin();
        importer.importCatalog(catalog);
        var original = service.snapshot("redis.lua-stock", 1);
        var revised = revisedCatalog(catalog, "redis.lua-stock", 2, "修订后的库存题");
        importer.importCatalog(revised);
        assertEquals("修订后的库存题", service.detail("redis.lua-stock", null).title());
        assertEquals(original, service.snapshot("redis.lua-stock", 1));
        importer.importCatalog(catalog);
        assertEquals(2, service.detail("redis.lua-stock", null).version());
        assertEquals(catalog.questions().size() + 1, revisions.count());
    }

    @Test
    void sameVersionCannotBeSilentlyRewritten() {
        var catalog = loader.loadBuiltin();
        importer.importCatalog(catalog);
        assertThrows(CatalogConflictException.class,
                () -> importer.importCatalog(revisedCatalog(catalog, "redis.lua-stock", 1, "偷改内容")));
    }

    @Test
    void sourceVersionCannotBeRewrittenOrReuseOldDocumentId() {
        var catalog = loader.loadBuiltin();
        importer.importCatalog(catalog);
        var tree = json.read(json.write(catalog), com.fasterxml.jackson.databind.node.ObjectNode.class);
        tree.withArray("sources").get(0).withObject("/document").put("content", "改变来源内容");
        assertThrows(CatalogConflictException.class,
                () -> importer.importCatalog(json.read(tree.toString(), QuestionCatalog.class)));
    }

    @Test
    void changedRubricRequiresNewRubricVersion() {
        var catalog = loader.loadBuiltin();
        importer.importCatalog(catalog);
        var revised = revisedCatalog(catalog, "redis.lua-stock", 2, "新版库存题");
        var tree = json.read(json.write(revised), com.fasterxml.jackson.databind.node.ObjectNode.class);
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.withArray("questions").get(0)
                .withArray("criteria").get(0)).put("expected", "修订后的评分要求");
        assertThrows(CatalogConflictException.class,
                () -> importer.importCatalog(json.read(tree.toString(), QuestionCatalog.class)));
    }

    @Test
    void filtersPaginationNoResultsAndValidation() {
        var catalog = loader.loadBuiltin();
        importer.importCatalog(catalog);
        var first = service.list(null, null, 0, 5);
        var second = service.list(null, null, 1, 5);
        assertEquals(catalog.questions().size(), first.totalElements());
        assertEquals(5, first.items().size());
        assertEquals((catalog.questions().size() + 4) / 5, first.totalPages());
        assertTrue(Collections.disjoint(first.items(), second.items()));
        var filtered = service.list(QuestionTopic.REDIS, 2, 0, 50);
        assertEquals(catalog.questions().stream().filter(q -> q.topic() == QuestionTopic.REDIS && q.difficulty() == 2).count(), filtered.totalElements());
        assertTrue(filtered.items().stream().allMatch(q -> q.topic() == QuestionTopic.REDIS && q.difficulty() == 2));
        assertTrue(service.list(QuestionTopic.JVM, 1, 0, 12).items().isEmpty());
        assertTrue(service.list(null, null, 100, 12).items().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.list(null, 6, 0, 12));
        assertThrows(IllegalArgumentException.class, () -> service.list(null, null, -1, 12));
        assertThrows(IllegalArgumentException.class, () -> service.list(null, null, 0, 51));
        assertThrows(com.mockinterview.controller.NotFoundException.class, () -> service.detail("missing", null));
    }

    @Test
    void expansionPreservesLegacySnapshotsAndOnlyAddsNewVersions() throws Exception {
        QuestionCatalog legacy;
        try (var in = new org.springframework.core.io.ClassPathResource("questions/catalog-v1.json").getInputStream()) {
            legacy = json.read(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), QuestionCatalog.class);
        }
        importer.importCatalog(legacy);
        var before = legacy.questions().stream().map(q -> service.snapshot(q.id(), 1)).toList();
        var expanded = loader.loadBuiltin();
        var result = importer.importCatalog(expanded);
        assertEquals(156, result.newQuestions());
        assertEquals(156, result.newRevisions());
        assertEquals(468, result.newKnowledgePoints());
        assertEquals(24, result.unchangedRevisions());
        assertEquals(before, legacy.questions().stream().map(q -> service.snapshot(q.id(), 1)).toList());
        assertEquals(180, importer.importCatalog(expanded).unchangedRevisions());
    }

    @Test
    void searchCombinesFiltersAndTreatsWildcardsAsLiteralText() {
        importer.importCatalog(loader.loadBuiltin());
        var hits = service.list(QuestionTopic.JAVA_COLLECTIONS, null, 0, 12, "  HASHMAP  ");
        assertFalse(hits.items().isEmpty());
        assertTrue(hits.items().stream().allMatch(q -> q.title().toLowerCase(Locale.ROOT).contains("hashmap")));
        assertEquals(0, service.list(null, null, 0, 12, "%").totalElements());
        assertEquals(List.of("network.tcp-close"), service.list(null, null, 0, 12, "_").items().stream().map(QuestionService.PublicQuestion::id).toList());
        assertThrows(IllegalArgumentException.class, () -> service.list(null, null, 0, 12, "a".repeat(81)));
        var summary = service.summary();
        assertEquals(180, summary.totalQuestions());
        assertEquals(18, summary.topics().size());
        assertEquals(12, summary.topics().stream().filter(t -> t.topic() == QuestionTopic.NETWORK).findFirst().orElseThrow().count());
    }

    static QuestionCatalog revisedCatalog(QuestionCatalog original, String id, int version, String title) {
        var qs = original.questions().stream().map(q -> q.id().equals(id) ?
                new QuestionCatalog.Question(q.id(), version, q.rubricVersion(), q.topic(), q.difficulty(),
                        title, q.prompt(), q.suggestedSeconds(), q.active(), q.criteria(), q.explanation(), q.followups()) : q).toList();
        return new QuestionCatalog(original.catalogVersion(), original.knowledgePoints(), original.sources(), qs);
    }
}
