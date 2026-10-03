package com.mockinterview.service.practice;
import com.mockinterview.capability.evaluation.*;
import org.slf4j.*;
import org.springframework.stereotype.Component;

@Component
public class PracticeEvaluationWorker {
    private static final Logger log=LoggerFactory.getLogger(PracticeEvaluationWorker.class);
    private final PracticeEvaluationStore store;
    private final PracticeReferences references;
    private final PracticeAssessmentModel model;
    private final PracticeEvaluationEngine engine;
    public PracticeEvaluationWorker(PracticeEvaluationStore store,PracticeReferences references,
        PracticeAssessmentModel model,PracticeEvaluationEngine engine) {
        this.store=store; this.references=references; this.model=model; this.engine=engine;
    }
    public void run(String id) {
        var work=store.claim(id);
        if(work==null) return;
        long start=System.nanoTime();
        boolean called=false;
        try (var configuration=model.configurationScope()) {
            var context=references.resolve(work.snapshot(),work.answer());
            called=!context.sources().isEmpty();
            store.context(id,context,context.sources().isEmpty()?"not-called":model.modelName(),PracticeAssessmentPrompt.VERSION);
            var result=engine.evaluate(work.snapshot(),work.answer(),context);
            store.success(id,result,elapsed(start));
        } catch(InvalidEvaluationException e) {
            store.failure(id,"INVALID_OUTPUT","模型返回的结构或引用未通过校验，回答已保留，可以重试。",elapsed(start));
            log.warn("练习评估输出校验失败，evaluationId={}",id);
        } catch(RuntimeException e) {
            store.failure(id,"MODEL_UNAVAILABLE","评分服务暂时不可用或超时，回答已保留，可以重试。",elapsed(start));
            // Do not log response bodies, answer text or exception messages that may contain credentials.
            log.warn("练习评估执行失败，evaluationId={}, type={}",id,e.getClass().getSimpleName());
        } finally {
            if(called)store.telemetry(id,model.telemetry());
        }
    }
    private long elapsed(long start) { return (System.nanoTime()-start)/1_000_000; }
}
