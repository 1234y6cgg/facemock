package com.mockinterview.capability.evaluation;
import com.fasterxml.jackson.databind.JsonNode;
import com.mockinterview.config.DeepSeekProperties;
import com.mockinterview.domain.question.*;
import com.mockinterview.infrastructure.deepseek.DeepSeekPracticeAssessmentModel;
import com.mockinterview.service.practice.PracticeReferences;
import com.mockinterview.service.question.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="P2_MODEL_TEST_KEY",matches=".+")
class PracticeModelIntegrationTest {
    @Test void recordRealModelPredictionsAgainst24FixedCases() throws Exception {
        var json=new QuestionJson();
        QuestionCatalog catalog;
        try(var in=new ClassPathResource("questions/catalog-v1.json").getInputStream()) {
            catalog=json.read(new String(in.readAllBytes(),StandardCharsets.UTF_8),QuestionCatalog.class);
        }
        var properties=new DeepSeekProperties(); properties.setApiKey(System.getenv("P2_MODEL_TEST_KEY"));
        properties.setBaseUrl(System.getenv().getOrDefault("P2_MODEL_TEST_BASE_URL","https://api.deepseek.com"));
        properties.setModelName(System.getenv().getOrDefault("P2_MODEL_TEST_NAME","deepseek-flash"));
        var model=new DeepSeekPracticeAssessmentModel(properties,new PracticeAssessmentPrompt(json),json);
        var validatorFactory=Validation.buildDefaultValidatorFactory();
        var parser=new TrainingEvaluationParser(json,validatorFactory.getValidator());
        var engine=new PracticeEvaluationEngine(model,parser,json);
        var pool=Executors.newFixedThreadPool(2);
        var futures=new ArrayList<Future<Map<String,Object>>>();
        try {
            for(JsonNode sample:EvaluationFixtures.cases(json)) {
                if(!sample.path("id").asText().matches(System.getenv().getOrDefault("P2_MODEL_CASE_FILTER",".*"))) continue;
                final var current=sample;
                futures.add(pool.submit(()->{
                    var snapshot=EvaluationFixtures.snapshot(catalog,current.path("questionId").asText(),json);
                    var allowed=EvaluationFixtures.available(current);
                    var sources=snapshot.sources().stream().filter(s->allowed.contains(s.sourceId())).toList();
                    var context=new PracticeReferences.Context(sources.isEmpty()?"UNAVAILABLE":"NO_MATCH","固定样例：仅提供指定核验来源",sources,List.of());
                    var row=new LinkedHashMap<String,Object>();
                    row.put("caseId",current.path("id").asText()); row.put("category",current.path("category").asText());
                    row.put("questionId",snapshot.question().id()); row.put("expected",current.path("expected"));
                    row.put("modelCalled",!sources.isEmpty());
                    long start=System.nanoTime();
                    String raw=null;
                    try {
                        var result=engine.evaluate(snapshot,current.path("answer").asText(),context);
                        var predicted=new HashMap<String,String>(); result.criteria().forEach(c->predicted.put(c.criterionId(),c.status().name()));
                        var differences=new ArrayList<Map<String,String>>();
                        for(var expected:current.path("expected")) {
                            String id=expected.path("criterionId").asText(), status=expected.path("status").asText();
                            if(!status.equals(predicted.get(id))) differences.add(Map.of("criterionId",id,"expected",status,"actual",predicted.get(id)));
                        }
                        row.put("result",result); row.put("differences",differences); row.put("valid",true);
                    } catch(RuntimeException e) {
                        row.put("valid",false); row.put("errorType",e.getClass().getSimpleName());
                        if(e instanceof InvalidEvaluationException) { row.put("validationError",e.getMessage()); row.put("rawOutput",raw); }
                    }
                    row.put("durationMs",(System.nanoTime()-start)/1_000_000);
                    return row;
                }));
            }
            var results=new ArrayList<Map<String,Object>>();
            for(var future:futures) results.add(future.get(15,TimeUnit.MINUTES));
            var report=new LinkedHashMap<String,Object>();
            report.put("verifiedAt",Instant.now().toString()); report.put("model",properties.getModelName());
            report.put("promptVersion",PracticeAssessmentPrompt.VERSION); report.put("cases",results);
            Files.createDirectories(Path.of("target"));
            Files.writeString(Path.of("target/p2-model-evaluation.json"),json.write(report),StandardCharsets.UTF_8);
            assertEquals(futures.size(),results.size());
            assertTrue(results.stream().allMatch(r->Boolean.TRUE.equals(r.get("valid"))),"Some outputs failed strict validation; see target/p2-model-evaluation.json");
        } finally { pool.shutdownNow();validatorFactory.close(); }
    }
}
