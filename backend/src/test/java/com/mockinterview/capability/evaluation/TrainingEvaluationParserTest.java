package com.mockinterview.capability.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mockinterview.domain.question.*;
import com.mockinterview.service.question.*;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class TrainingEvaluationParserTest {
    private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private final QuestionJson json = new QuestionJson();
    private final QuestionCatalog catalog = new QuestionCatalogLoader(json,
            new QuestionCatalogValidator(factory.getValidator())).loadBuiltin();
    private final TrainingEvaluationParser parser = new TrainingEvaluationParser(json, factory.getValidator());
    @AfterEach void close() { factory.close(); }

    @TestFactory
    Stream<DynamicTest> fixedAuthorLabelledCasesValidateOriginalQuotesAndSources() throws Exception {
        var cases = EvaluationFixtures.cases(json);
        assertEquals(24, cases.size());
        Map<String, Integer> counts = new HashMap<>();
        cases.forEach(c -> counts.merge(c.path("category").asText(), 1, Integer::sum));
        assertEquals(6, counts.size());
        assertTrue(counts.values().stream().allMatch(n -> n == 4));
        List<DynamicTest> tests = new ArrayList<>();
        for (var sample : cases) {
            tests.add(DynamicTest.dynamicTest(sample.path("id").asText() + " " + sample.path("category").asText(), () -> {
                var snapshot = EvaluationFixtures.snapshot(catalog, sample.path("questionId").asText(), json);
                var expected = EvaluationFixtures.expected(sample, snapshot);
                var parsed = parser.parse(json.write(expected), snapshot, sample.path("answer").asText(), EvaluationFixtures.available(sample));
                assertEquals(expected, parsed);
                assertEquals(sample.path("questionVersion").asInt(), parsed.questionVersion());
            }));
        }
        return tests.stream();
    }

    @TestFactory
    Stream<DynamicTest> fabricatedOrIncompleteEvaluationsAreRejected() throws Exception {
        var sample = EvaluationFixtures.cases(json).get(0);
        var snapshot = EvaluationFixtures.snapshot(catalog, sample.path("questionId").asText(), json);
        String valid = json.write(EvaluationFixtures.expected(sample, snapshot));
        Map<String, Consumer<ObjectNode>> bad = new LinkedHashMap<>();
        bad.put("unknown field", t -> t.put("score", 100));
        bad.put("wrong question", t -> t.put("questionId", "another-question"));
        bad.put("wrong question version", t -> t.put("questionVersion", 99));
        bad.put("wrong rubric version", t -> t.put("rubricVersion", 99));
        bad.put("wrong answer", t -> t.put("answerHash", "a".repeat(64)));
        bad.put("missing criterion", t -> t.withArray("criteria").remove(0));
        bad.put("duplicate criterion", t -> t.withArray("criteria").add(t.withArray("criteria").get(0)));
        bad.put("unknown criterion", t -> first(t).put("criterionId", "fake"));
        bad.put("unknown status", t -> first(t).put("status", "PERFECT"));
        bad.put("numeric status", t -> first(t).put("status", 1));
        bad.put("coerced version", t -> t.put("questionVersion", "1"));
        bad.put("fractional version", t -> t.put("questionVersion", 1.5));
        bad.put("numeric reason", t -> first(t).put("reason", 42));
        bad.put("fabricated original quote", t -> first(t).withArray("candidateQuotes").set(0,
                com.fasterxml.jackson.databind.node.TextNode.valueOf("我从未说过的话")));
        bad.put("missing original quote", t -> first(t).withArray("candidateQuotes").removeAll());
        bad.put("missing source", t -> first(t).withArray("references").removeAll());
        bad.put("fabricated source", t -> citation(t).put("sourceId", "fake.official"));
        bad.put("stale source", t -> citation(t).put("documentRevision", "b".repeat(64)));
        bad.put("invented source quote", t -> citation(t).put("quote", "来源里面没有写过这句话"));
        bad.put("fabricated expression quote", t -> t.withArray("expressionFeedback").add(json.read(
                "{\"quote\":\"没说过\",\"issue\":\"不清晰\",\"suggestion\":\"具体解释\"}", ObjectNode.class)));
        bad.put("missing with original quote", t -> first(t).put("status", "MISSING"));
        var tests = bad.entrySet().stream().map(entry -> DynamicTest.dynamicTest(entry.getKey(), () -> {
            var tree = json.read(valid, ObjectNode.class);
            entry.getValue().accept(tree);
            assertThrows(InvalidEvaluationException.class, () -> parser.parse(tree.toString(), snapshot,
                    sample.path("answer").asText(), EvaluationFixtures.available(sample)));
        })).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        tests.add(DynamicTest.dynamicTest("trailing JSON", () -> assertThrows(InvalidEvaluationException.class,
                () -> parser.parse(valid + " {}", snapshot, sample.path("answer").asText(), EvaluationFixtures.available(sample)))));
        tests.add(DynamicTest.dynamicTest("duplicate JSON field", () -> assertThrows(InvalidEvaluationException.class,
                () -> parser.parse(valid.replaceFirst("\\{", "{\"questionId\":\"spoof\","),
                        snapshot, sample.path("answer").asText(), EvaluationFixtures.available(sample)))));
        tests.add(DynamicTest.dynamicTest("unavailable sources", () -> assertThrows(InvalidEvaluationException.class,
                () -> parser.parse(valid, snapshot, sample.path("answer").asText(), Set.of()))));
        return tests.stream();
    }

    @Test
    void partialAndUncertainAreAllowedWithoutInventingNumericScores() throws Exception {
        var sample = EvaluationFixtures.cases(json).get(0);
        var snapshot = EvaluationFixtures.snapshot(catalog, sample.path("questionId").asText(), json);
        var tree = json.read(json.write(EvaluationFixtures.expected(sample, snapshot)), ObjectNode.class);
        first(tree).put("status", "PARTIAL");
        assertEquals(CriterionStatus.PARTIAL, parser.parse(tree.toString(), snapshot, sample.path("answer").asText(),
                EvaluationFixtures.available(sample)).criteria().get(0).status());
        for (var criterion : tree.withArray("criteria")) {
            ((ObjectNode) criterion).put("status", "UNCERTAIN");
            ((ObjectNode) criterion).withArray("candidateQuotes").removeAll();
            ((ObjectNode) criterion).withArray("references").removeAll();
        }
        assertTrue(parser.parse(tree.toString(), snapshot, sample.path("answer").asText(), Set.of()).criteria()
                .stream().allMatch(c -> c.status() == CriterionStatus.UNCERTAIN));
    }

    private ObjectNode first(ObjectNode tree) { return (ObjectNode) tree.withArray("criteria").get(0); }
    private ObjectNode citation(ObjectNode tree) { return (ObjectNode) first(tree).withArray("references").get(0); }
}
