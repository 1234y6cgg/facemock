package com.mockinterview.config;
import com.mockinterview.service.project.*;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.*;
import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.*;

@Component @EnableScheduling @ConditionalOnProperty(name="projects.jobs.enabled",havingValue="true",matchIfMissing=true)
public class ProjectJobDispatcher {
    private final ProjectStore projects;private final ProjectIndex index;private final ProjectEvaluationStore evaluations;private final ProjectEvaluationWorker worker;
    private final ThreadPoolExecutor executor=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(2),r->{var t=new Thread(r,"project-training");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private final Set<String> dispatched=ConcurrentHashMap.newKeySet();private volatile boolean ready;
    public ProjectJobDispatcher(ProjectStore projects,ProjectIndex index,ProjectEvaluationStore evaluations,ProjectEvaluationWorker worker){this.projects=projects;this.index=index;this.evaluations=evaluations;this.worker=worker;}
    @EventListener(ApplicationReadyEvent.class) public void ready(){evaluations.recover();ready=true;}
    @Scheduled(fixedDelay=1000) public void dispatch(){if(!ready)return;
        for(var id:projects.pendingIndex())submit("index:"+id,()->index.sync(id));
        for(var id:evaluations.pending())submit("evaluate:"+id,()->worker.run(id));}
    private void submit(String id,Runnable work){if(!dispatched.add(id))return;try{executor.execute(()->{try{work.run();}finally{dispatched.remove(id);}});}catch(RejectedExecutionException e){dispatched.remove(id);}}
    @PreDestroy public void close(){ready=false;executor.shutdownNow();}
}
