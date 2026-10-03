package com.mockinterview.capability.evaluation;
import com.mockinterview.service.question.*;
import com.mockinterview.domain.question.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.io.ClassPathResource;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class P6DatasetTest {
    static JsonNode dataset(QuestionJson json)throws Exception{try(var in=new ClassPathResource("questions/answer-cases-v2.json").getInputStream()){return json.read(new String(in.readAllBytes(),StandardCharsets.UTF_8),JsonNode.class);}}
    static QuestionCatalog catalog(QuestionJson json)throws Exception{try(var in=new ClassPathResource("questions/catalog-v1.json").getInputStream()){return json.read(new String(in.readAllBytes(),StandardCharsets.UTF_8),QuestionCatalog.class);}}
    @Test void sixtyDistinctFixedAnswersHaveValidExpectedQuotesAndBoundSourceVersions()throws Exception{
        var json=new QuestionJson();var data=dataset(json);var catalog=catalog(json);var ids=new HashSet<String>();var answers=new HashSet<String>();
        try(var factory=Validation.buildDefaultValidatorFactory()){var parser=new TrainingEvaluationParser(json,factory.getValidator());
            assertEquals(60,data.path("cases").size());for(var sample:data.path("cases")){
                assertTrue(ids.add(sample.path("id").asText()));assertTrue(answers.add(sample.path("questionId").asText()+"\n"+sample.path("answer").asText()));
                var snapshot=EvaluationFixtures.snapshot(catalog,sample.path("questionId").asText(),json);var expected=EvaluationFixtures.expected(sample,snapshot);
                assertEquals(3,expected.criteria().size());parser.parse(json.write(expected),snapshot,sample.path("answer").asText(),EvaluationFixtures.available(sample));
            }}
        assertTrue(data.path("annotationStatus").asText().startsWith("Rubric-derived expectations"));
    }
}
