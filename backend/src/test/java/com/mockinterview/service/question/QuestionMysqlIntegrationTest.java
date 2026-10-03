package com.mockinterview.service.question;

import com.mockinterview.domain.question.*;
import com.mockinterview.repository.question.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = P1JpaConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "P1_MYSQL_TEST_URL", matches = ".+")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class QuestionMysqlIntegrationTest {
    @Autowired QuestionCatalogLoader loader;
    @Autowired QuestionImportService importer;
    @Autowired QuestionService service;
    @Autowired QuestionRepository questions;
    @Autowired QuestionRevisionRepository revisions;
    @Autowired QuestionSourceRevisionRepository sources;
    @Autowired RubricCriterionRepository criteria;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("P1_MYSQL_TEST_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("P1_MYSQL_TEST_USER", "root"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("P1_MYSQL_TEST_PASSWORD", ""));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Test
    void committedImportsRevisionsRollbackAndQueriesWorkOnRealMysql() {
        var catalog = loader.loadBuiltin();
        assertEquals(catalog.questions().size(), importer.importCatalog(catalog).newRevisions());
        assertEquals(catalog.questions().size(), importer.importCatalog(catalog).unchangedRevisions());
        assertEquals(catalog.questions().size(), questions.count());
        assertEquals(catalog.sources().size(), sources.count());
        assertEquals(catalog.knowledgePoints().size(), criteria.count());
        var old = service.snapshot("redis.lua-stock", 1);
        importer.importCatalog(QuestionImportServiceTest.revisedCatalog(catalog, "redis.lua-stock", 2, "新版库存题"));
        assertEquals(old, service.snapshot("redis.lua-stock", 1));
        assertEquals(2, service.detail("redis.lua-stock", null).version());
        // A valid new revision followed by a conflict must roll back the entire catalog.
        var bad = QuestionImportServiceTest.revisedCatalog(catalog, "redis.lock", 2, "本不应提交的新锁题");
        bad = QuestionImportServiceTest.revisedCatalog(bad, "redis.eviction", 1, "同版本冲突");
        final var failedCatalog = bad;
        assertThrows(CatalogConflictException.class, () -> importer.importCatalog(failedCatalog));
        assertEquals(catalog.questions().size() + 1, revisions.count());
        assertEquals(1, service.detail("redis.lock", null).version());
        assertEquals(catalog.questions().stream().filter(q -> q.topic() == QuestionTopic.REDIS).count(), service.list(QuestionTopic.REDIS, null, 0, 10).totalElements());
        assertEquals(12, service.list(QuestionTopic.NETWORK, null, 0, 50).totalElements());
        assertFalse(service.list(QuestionTopic.JAVA_COLLECTIONS, null, 0, 12, "hashmap").items().isEmpty());
        assertEquals(18, service.summary().topics().size());
        assertEquals(0, service.list(QuestionTopic.REDIS, 5, 0, 10).totalElements());
        assertTrue(service.list(null, null, 999, 10).items().isEmpty());
        importer.importCatalog(catalog);
        assertEquals(2, service.detail("redis.lua-stock", null).version());
    }
}
