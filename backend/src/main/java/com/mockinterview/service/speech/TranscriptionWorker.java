package com.mockinterview.service.speech;
import com.mockinterview.capability.speech.*;
import org.springframework.stereotype.Component;
import org.slf4j.*;

@Component
public class TranscriptionWorker {
    private static final Logger log=LoggerFactory.getLogger(TranscriptionWorker.class);
    private final TranscriptionStore store;private final SpeechTranscriber transcriber;private final SpeechFileStore files;
    public TranscriptionWorker(TranscriptionStore store,SpeechTranscriber transcriber,SpeechFileStore files) {this.store=store;this.transcriber=transcriber;this.files=files;}
    public void run(String id) {
        var work=store.claim(id);if(work==null)return;
        try { var text=transcriber.transcribe(files.read(work.recordingId()));
            if(text==null||text.isBlank()||text.length()>10000)throw new IllegalStateException("转写结果不合法");
            store.finish(id,text,transcriber.provider(),null);
        } catch(RuntimeException e) {store.finish(id,null,transcriber.provider(),"TRANSCRIPTION_FAILED");log.warn("转写失败，jobId={},type={}",id,e.getClass().getSimpleName());}
    }
}
