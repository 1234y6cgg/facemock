package com.mockinterview.service.question;

import com.mockinterview.domain.question.*;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class QuestionCatalogTest {
    private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private final QuestionJson json = new QuestionJson();
    private final QuestionCatalogValidator validator = new QuestionCatalogValidator(factory.getValidator());
    private final QuestionCatalogLoader loader = new QuestionCatalogLoader(json, validator);
    @AfterEach void close() { factory.close(); }

    @Test
    void builtinCoversAllEighteenThemesWithTraceableRubrics() {
        var catalog = loader.loadBuiltin();
        assertEquals(180, catalog.questions().size());
        assertEquals(540, catalog.knowledgePoints().size());
        assertEquals(180, catalog.sources().size());
        var counts = catalog.questions().stream().collect(Collectors.groupingBy(
                QuestionCatalog.Question::topic, Collectors.counting()));
        assertEquals(EnumSet.allOf(QuestionTopic.class), counts.keySet());
        assertTrue(counts.values().stream().allMatch(v -> v >= 6));
        for (var q : catalog.questions()) {
            assertEquals(1, q.version());
            assertEquals(3, q.criteria().size());
            for (var c : q.criteria()) {
                var source = catalog.sources().stream().filter(s -> c.sourceIds().contains(s.id())).findFirst().orElseThrow();
                assertTrue(source.document().content().contains(c.expected()));
                assertFalse(c.acceptedExpressions().isEmpty());
                assertFalse(c.commonMistakes().isEmpty());
            }
        }
    }

    @Test
    void duplicateIdsUnknownSourcesAndCrossTopicPointsAreRejected() {
        var original = loader.loadBuiltin();
        var tree = json.read(json.write(original), com.fasterxml.jackson.databind.node.ObjectNode.class);
        tree.withArray("questions").add(tree.withArray("questions").get(0));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(json.read(tree.toString(), QuestionCatalog.class)));
        var unknown = json.read(json.write(original), com.fasterxml.jackson.databind.node.ObjectNode.class);
        ((com.fasterxml.jackson.databind.node.ObjectNode) unknown.withArray("questions").get(0)
                .withArray("criteria").get(0)).withArray("sourceIds").set(0,
                com.fasterxml.jackson.databind.node.TextNode.valueOf("fake-source"));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(json.read(unknown.toString(), QuestionCatalog.class)));
        var cross = json.read(json.write(original), com.fasterxml.jackson.databind.node.ObjectNode.class);
        cross.withArray("knowledgePoints").get(0).withObject("").put("topic", "JVM");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(json.read(cross.toString(), QuestionCatalog.class)));
    }
}
