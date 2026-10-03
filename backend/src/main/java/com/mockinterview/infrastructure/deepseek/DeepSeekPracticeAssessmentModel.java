package com.mockinterview.infrastructure.deepseek;

import com.mockinterview.capability.evaluation.*;
import com.mockinterview.config.DeepSeekProperties;
import com.mockinterview.domain.question.QuestionSnapshot;
import com.mockinterview.service.practice.PracticeReferences.Context;
import com.mockinterview.service.question.QuestionJson;
import com.mockinterview.infrastructure.model.CompatibleModelClient;
import com.mockinterview.service.model.ModelConnection;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Duration;
import java.util.*;

@Component
public class DeepSeekPracticeAssessmentModel implements PracticeAssessmentModel {
    private final DeepSeekProperties properties;
    private final PracticeAssessmentPrompt prompt;
    private final CompatibleModelClient client;
    private final ThreadLocal<Map<String,Object>> lastTelemetry=ThreadLocal.withInitial(Map::of);
    public DeepSeekPracticeAssessmentModel(DeepSeekProperties p,PracticeAssessmentPrompt prompt,QuestionJson json){
        this(p,prompt,json,new CompatibleModelClient(json));
    }
    @Autowired public DeepSeekPracticeAssessmentModel(DeepSeekProperties p,PracticeAssessmentPrompt prompt,QuestionJson json,CompatibleModelClient client){
        this.properties=p;this.prompt=prompt;this.client=client;
    }
    @Override public String assess(QuestionSnapshot snapshot,String answer,Context context){
        lastTelemetry.remove();var c=properties.current();
        var result=client.complete(c,List.of(Map.of("role","system","content",prompt.system()),Map.of("role","user","content",prompt.user(snapshot,answer,context))),
            8000,true,Duration.ofSeconds(60));
        lastTelemetry.set(result.metrics());return result.content();
    }
    @Override public String modelName(){return properties.current().identity();}
    @Override public ModelConnection.Scope configurationScope(){return properties.pin();}
    @Override public Map<String,Object> telemetry(){var result=lastTelemetry.get();lastTelemetry.remove();return result;}
}
