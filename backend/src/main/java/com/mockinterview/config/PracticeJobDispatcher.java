package com.mockinterview.config;
import com.mockinterview.service.practice.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.*;

@Component
@EnableScheduling
@ConditionalOnProperty(name="practice.jobs.enabled",havingValue="true",matchIfMissing=true)
public class PracticeJobDispatcher {
    private final PracticeEvaluationStore store;
    private final PracticeEvaluationWorker worker;
    private final ThreadPoolExecutor executor=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(4),r->{ var t=new Thread(r,"practice-evaluation"); t.setDaemon(true); return t; },
        new ThreadPoolExecutor.AbortPolicy());
    private final Set<String> dispatched=ConcurrentHashMap.newKeySet();
    private volatile boolean ready;
    public PracticeJobDispatcher(PracticeEvaluationStore store,PracticeEvaluationWorker worker) { this.store=store; this.worker=worker; }
    @EventListener(ApplicationReadyEvent.class) public void ready() { store.recoverInterrupted(); ready=true; }
    @Scheduled(fixedDelay=1000) public void dispatch() {
        if(!ready) return;
        for(String id:store.pending()) {
            if(!dispatched.add(id)) continue;
            try { executor.execute(()->{ try { worker.run(id); } finally { dispatched.remove(id); } }); }
            catch(RejectedExecutionException e) { dispatched.remove(id); break; }
        }
    }
    @PreDestroy public void close() { ready=false; executor.shutdownNow(); }
}
