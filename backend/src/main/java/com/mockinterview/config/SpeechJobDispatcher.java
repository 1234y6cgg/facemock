package com.mockinterview.config;
import com.mockinterview.service.speech.*;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.*;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.*;
import org.slf4j.*;

@Component @EnableScheduling @ConditionalOnProperty(name="speech.jobs.enabled",havingValue="true",matchIfMissing=true)
public class SpeechJobDispatcher {
    private static final Logger log=LoggerFactory.getLogger(SpeechJobDispatcher.class);
    private final TranscriptionStore store;private final TranscriptionWorker worker;private final SpeechStore speech;
    private final ThreadPoolExecutor executor=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(2),
        r->{var t=new Thread(r,"speech-transcription");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private final Set<String> dispatched=ConcurrentHashMap.newKeySet();private volatile boolean ready;
    public SpeechJobDispatcher(TranscriptionStore store,TranscriptionWorker worker,SpeechStore speech) {this.store=store;this.worker=worker;this.speech=speech;}
    @EventListener(ApplicationReadyEvent.class) public void ready() {store.recover();ready=true;}
    @Scheduled(fixedDelay=1000) public void dispatch() {if(!ready)return;
        for(var id:store.pending()) {if(!dispatched.add(id))continue;
            try {executor.execute(()->{try{worker.run(id);}finally{dispatched.remove(id);}});}catch(RejectedExecutionException e){dispatched.remove(id);break;} }
    }
    @Scheduled(fixedDelay=3600000,initialDelay=10000) public void expire() {if(!ready)return;try{speech.expire();}catch(RuntimeException e){log.warn("录音到期清理失败，将再次尝试");}}
    @PreDestroy public void close(){ready=false;executor.shutdownNow();}
}
