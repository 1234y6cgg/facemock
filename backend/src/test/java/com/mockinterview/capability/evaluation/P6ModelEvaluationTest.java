package com.mockinterview.capability.evaluation;
import com.fasterxml.jackson.databind.JsonNode;
import com.mockinterview.config.DeepSeekProperties;
import com.mockinterview.infrastructure.deepseek.DeepSeekPracticeAssessmentModel;
import com.mockinterview.service.practice.PracticeReferences;
import com.mockinterview.service.question.*;
import com.mockinterview.capability.knowledge.KnowledgeHit;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="P6_MODEL_KEY",matches=".+")
class P6ModelEvaluationTest {
    @Test void reproducibleRealModelAndOptionalRagBenchmark()throws Exception{
        var json=new QuestionJson();var data=P6DatasetTest.dataset(json);var catalog=P6DatasetTest.catalog(json);
        var properties=new DeepSeekProperties();properties.setApiKey(System.getenv("P6_MODEL_KEY"));
        properties.setBaseUrl(System.getenv().getOrDefault("P6_MODEL_BASE_URL","https://api.deepseek.com"));properties.setModelName(System.getenv().getOrDefault("P6_MODEL_NAME","deepseek-flash"));
        var prompt=new PracticeAssessmentPrompt(json);var model=new DeepSeekPracticeAssessmentModel(properties,prompt,json);
        var validator=Validation.buildDefaultValidatorFactory();var engine=new PracticeEvaluationEngine(model,new TrainingEvaluationParser(json,validator.getValidator()),json);
        var pool=Executors.newFixedThreadPool(2);var futures=new ArrayList<Future<Map<String,Object>>>();
        var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        try{
            for(var sample:data.path("cases")){
                if(!sample.path("id").asText().matches(System.getenv().getOrDefault("P6_CASE_FILTER",".*")))continue;
                futures.add(pool.submit(()->{
                    var snapshot=EvaluationFixtures.snapshot(catalog,sample.path("questionId").asText(),json);
                    var allowed=EvaluationFixtures.available(sample);var sources=snapshot.sources().stream().filter(s->allowed.contains(s.sourceId())).toList();
                    var row=new LinkedHashMap<String,Object>();row.put("caseId",sample.path("id").asText());row.put("category",sample.path("category").asText());row.put("expected",sample.path("expected"));row.put("questionId",snapshot.question().id());
                    row.put("questionVersion",snapshot.question().version());row.put("rubricVersion",snapshot.question().rubricVersion());row.put("sourceSnapshots",sources);row.put("modelCalled",!sources.isEmpty());
                    var bound=new ArrayList<KnowledgeHit>();var rag=System.getenv("P6_RAG_URL");long retrievalStart=System.nanoTime();
                    if(rag!=null&&!rag.isBlank())try{
                        String query=snapshot.question().prompt()+" "+sample.path("answer").asText();query=query.substring(0,Math.min(500,query.length()));
                        var response=http.send(HttpRequest.newBuilder(URI.create(rag.replaceAll("/+$","")+"/api/knowledge/search?topK=5&query="+URLEncoder.encode(query,java.nio.charset.StandardCharsets.UTF_8))).timeout(Duration.ofSeconds(20)).GET().build(),HttpResponse.BodyHandlers.ofString());
                        if(response.statusCode()!=200)throw new IllegalStateException("RAG_HTTP_"+response.statusCode());
                        var hits=json.read(response.body(),KnowledgeHit[].class);row.put("retrievedCount",hits.length);
                        for(var hit:hits)if(sources.stream().anyMatch(s->s.documentId().equals(hit.documentId())&&s.content().contains(hit.content())&&s.sourceUrl().equals(hit.sourceUrl())))bound.add(hit);
                        row.put("retrievalValid",true);row.put("boundHitCount",bound.size());
                    }catch(Exception e){row.put("retrievalValid",false);row.put("retrievalErrorType",e.getClass().getSimpleName());}
                    row.put("retrievalDurationMs",(System.nanoTime()-retrievalStart)/1_000_000);
                    var context=new PracticeReferences.Context(sources.isEmpty()?"UNAVAILABLE":bound.isEmpty()?"NO_MATCH":"AVAILABLE","固定集：仅允许已核验题目来源和经核验检索片段",sources,List.copyOf(bound));
                    long start=System.nanoTime();
                    try{var result=engine.evaluate(snapshot,sample.path("answer").asText(),context);row.put("validOutput",true);row.put("result",result);
                        var predicted=new HashMap<String,String>();result.criteria().forEach(c->predicted.put(c.criterionId(),c.status().name()));var diffs=new ArrayList<Map<String,String>>();
                        for(var e:sample.path("expected"))if(!e.path("status").asText().equals(predicted.get(e.path("criterionId").asText())))diffs.add(Map.of("criterionId",e.path("criterionId").asText(),"expected",e.path("status").asText(),"actual",predicted.get(e.path("criterionId").asText())));
                        row.put("differences",diffs);
                    }catch(RuntimeException e){row.put("validOutput",false);row.put("errorType",e.getClass().getSimpleName());}
                    row.put("durationMs",(System.nanoTime()-start)/1_000_000);if(!sources.isEmpty())row.put("usage",model.telemetry());
                    return row;
                }));
            }
            var rows=new ArrayList<Map<String,Object>>();for(var f:futures)rows.add(f.get(10,TimeUnit.MINUTES));
            var report=new LinkedHashMap<String,Object>();report.put("verifiedAt",Instant.now());report.put("actualModel",true);report.put("model",model.modelName());report.put("promptVersion",PracticeAssessmentPrompt.VERSION);
            report.put("promptHash",QuestionJson.sha256(prompt.system()));report.put("datasetHash",QuestionJson.sha256(json.write(data)));report.put("catalogHash",QuestionJson.sha256(json.write(catalog)));
            report.put("annotationStatus",data.path("annotationStatus").asText());report.put("settings",Map.of("thinking","enabled","reasoningEffort","low","maxTokens",8000,"concurrency",2));
            report.put("cases",rows);var path=Path.of(System.getenv().getOrDefault("P6_REPORT_PATH","target/p6-model-evaluation.json"));Files.createDirectories(path.toAbsolutePath().getParent());Files.writeString(path,json.write(report));
            assertFalse(rows.isEmpty());assertEquals(futures.size(),rows.size());
        }finally{pool.shutdownNow();validator.close();}
    }
}
