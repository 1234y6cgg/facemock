package com.mockinterview.capability.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.mockinterview.domain.question.*;
import com.mockinterview.service.question.*;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class EvaluationFixtures {
    private EvaluationFixtures() {}

    public static JsonNode cases(QuestionJson json) throws Exception {
        try (var input = new ClassPathResource("questions/answer-cases-v1.json").getInputStream()) {
            return json.read(new String(input.readAllBytes(), StandardCharsets.UTF_8), JsonNode.class).path("cases");
        }
    }

    public static QuestionSnapshot snapshot(QuestionCatalog catalog, String id, QuestionJson json) {
        var q = catalog.questions().stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
        var sourceIds = q.criteria().stream().flatMap(c -> c.sourceIds().stream()).collect(java.util.stream.Collectors.toSet());
        var refs = catalog.sources().stream().filter(s -> sourceIds.contains(s.id())).map(s -> {
            var d = s.document();
            return new QuestionSnapshot.SourceSnapshot(s.id(), s.version(), d.id(), QuestionJson.sha256(json.write(s)),
                    d.title(), d.source(), d.sourceUrl(), d.content(), s.verifiedOn());
        }).toList();
        var pointIds = q.criteria().stream().map(QuestionCatalog.Criterion::knowledgePointId)
                .collect(java.util.stream.Collectors.toSet());
        return new QuestionSnapshot(q, catalog.knowledgePoints().stream().filter(p -> pointIds.contains(p.id())).toList(), refs);
    }

    public static TrainingEvaluation expected(JsonNode sample, QuestionSnapshot snapshot) {
        List<TrainingEvaluation.CriterionAssessment> results = new ArrayList<>();
        for (var expected : sample.path("expected")) {
            var criterion = snapshot.question().criteria().stream().filter(c -> c.id()
                    .equals(expected.path("criterionId").asText())).findFirst().orElseThrow();
            List<TrainingEvaluation.SourceCitation> citations = new ArrayList<>();
            for (var sourceId : expected.path("sourceIds")) {
                var source = snapshot.sources().stream().filter(s -> s.sourceId().equals(sourceId.asText())).findFirst().orElseThrow();
                citations.add(new TrainingEvaluation.SourceCitation(source.sourceId(), source.documentRevision(), criterion.expected()));
            }
            var quotes = new ArrayList<String>();
            expected.path("candidateQuotes").forEach(q -> quotes.add(q.asText()));
            results.add(new TrainingEvaluation.CriterionAssessment(criterion.id(),
                    CriterionStatus.valueOf(expected.path("status").asText()), quotes,
                    expected.path("reason").asText(), citations));
        }
        var q = snapshot.question();
        return new TrainingEvaluation(q.id(), q.version(), q.rubricVersion(),
                QuestionJson.sha256(sample.path("answer").asText()), results, List.of());
    }

    public static Set<String> available(JsonNode sample) {
        Set<String> ids = new HashSet<>();
        sample.path("availableSourceIds").forEach(id -> ids.add(id.asText()));
        return ids;
    }
}
