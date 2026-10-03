package com.mockinterview.service.project;
import com.mockinterview.capability.evaluation.InvalidEvaluationException;
import org.springframework.stereotype.Component;
import org.slf4j.*;

@Component
public class ProjectEvaluationWorker {
    private static final Logger log=LoggerFactory.getLogger(ProjectEvaluationWorker.class);
    private final ProjectEvaluationStore store;private final ProjectAssessmentModel model;private final ProjectAssessmentParser parser;
    public ProjectEvaluationWorker(ProjectEvaluationStore store,ProjectAssessmentModel model,ProjectAssessmentParser parser){this.store=store;this.model=model;this.parser=parser;}
    public void run(String id){var work=store.claim(id);if(work==null)return;long start=System.nanoTime();
        String usedModel="unconfigured";
        try(var configuration=model.configurationScope()){usedModel=model.modelName();var result=parser.parse(model.assess(work.snapshot(),work.question(),work.answer()),work.snapshot(),work.answer());store.finish(id,result,null,usedModel,(System.nanoTime()-start)/1_000_000);}
        catch(RuntimeException e){log.warn("项目评估失败，id={},type={}",id,e.getClass().getSimpleName());store.finish(id,null,e instanceof InvalidEvaluationException?"INVALID_OUTPUT":"MODEL_UNAVAILABLE",usedModel,(System.nanoTime()-start)/1_000_000);}}
}
