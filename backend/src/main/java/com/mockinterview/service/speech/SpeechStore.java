package com.mockinterview.service.speech;
import com.mockinterview.capability.speech.*;
import com.mockinterview.config.SpeechProperties;
import com.mockinterview.controller.NotFoundException;
import com.mockinterview.domain.speech.*;
import com.mockinterview.repository.speech.*;
import com.mockinterview.repository.practice.*;
import com.mockinterview.repository.InterviewSessionRepository;
import com.mockinterview.repository.InterviewMessageRepository;
import com.mockinterview.domain.InterviewStatus;
import com.mockinterview.domain.MessageRole;
import com.mockinterview.service.practice.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;
import static com.mockinterview.service.speech.SpeechDtos.*;

@Service @Transactional
public class SpeechStore {
    private final SpeechRecordingRepository recordings;
    private final TranscriptionJobRepository jobs;
    private final PracticeSessionRepository sessions;
    private final PracticeStore practice;
    private final SpeechFileStore files;
    private final SpeechProperties settings;
    private final SpeechTranscriber provider;
    private final InterviewSessionRepository interviews;
    private final InterviewMessageRepository interviewMessages;
    public SpeechStore(SpeechRecordingRepository recordings,TranscriptionJobRepository jobs,PracticeSessionRepository sessions,
        PracticeStore practice,SpeechFileStore files,SpeechProperties settings,SpeechTranscriber provider,
        InterviewSessionRepository interviews,InterviewMessageRepository interviewMessages) {
        this.recordings=recordings;this.jobs=jobs;this.sessions=sessions;this.practice=practice;this.files=files;this.settings=settings;this.provider=provider;
        this.interviews=interviews;this.interviewMessages=interviewMessages;
    }
    public Status status() { return new Status(settings.configured(),provider.provider(),settings.getMaxSeconds(),settings.getRetentionDays(),
        settings.configured()?"录音上传后由科大讯飞转写；核对文本后再提交回答。":"尚未配置科大讯飞语音识别，文字回答仍可使用。"); }
    private void key(String key) { if(key==null||!key.matches("[a-zA-Z0-9_-]{8,64}")) throw new IllegalArgumentException("请求标识为 8–64 位字母、数字、下划线或短横线"); }
    private void lockSession(String id) {
        if(id.startsWith("interview:")) interviews.lock(Long.valueOf(id.substring(10))).orElseThrow(()->new NotFoundException("面试不存在"));
        else sessions.lock(id).orElseThrow(()->new NotFoundException("练习不存在"));
    }
    private void activeSession(String id) {
        if(id.startsWith("interview:")) { var s=interviews.lock(Long.valueOf(id.substring(10))).orElseThrow(()->new NotFoundException("面试不存在"));
            if(s.getStatus()!=InterviewStatus.IN_PROGRESS) throw new PracticeConflictException("面试已结束"); return; }
        var s=sessions.lock(id).orElseThrow(()->new NotFoundException("练习不存在"));
        if(!"ACTIVE".equals(s.getStatus())) throw new PracticeConflictException("练习已结束"); }
    public RecordingView upload(String sessionId,String parent,String key,byte[] wav) {
        if(sessionId.startsWith("interview:")) throw new IllegalArgumentException("模拟面试请使用专用录音接口");
        key(key); activeSession(sessionId);
        if(!settings.configured()) throw new PracticeConflictException("尚未配置语音识别服务，请使用文字练习");
        var audio=WavAudio.inspect(wav,settings.getMaxSeconds());
        var old=recordings.findBySessionIdAndClientRequestId(sessionId,key);
        if(old.isPresent()) { var r=old.get(); if(!r.getAudioHash().equals(audio.hash())||!Objects.equals(r.getParentAttemptId(),parent))
            throw new PracticeConflictException("相同请求标识的录音不一致"); requireAudio(r); return view(r); }
        var current=practice.get(sessionId).attempts();
        if(current.isEmpty()?parent!=null:!Objects.equals(parent,current.get(current.size()-1).id())
            ||!"SUCCEEDED".equals(current.get(current.size()-1).evaluation().status())) throw new PracticeConflictException("请引用当前成功评估的回答后重录");
        return saveAudio(sessionId,parent,key,wav,audio);
    }
    public RecordingView uploadInterview(Long id,int turn,String key,byte[] wav) {
        String scope="interview:"+id;key(key);activeSession(scope);
        if(!settings.configured()) throw new PracticeConflictException("尚未配置科大讯飞语音识别，请使用文字回答");
        var messages=interviewMessages.findBySessionIdOrderByCreatedAtAsc(id);
        if(turn!=messages.size()||messages.isEmpty()||messages.get(messages.size()-1).getRole()!=MessageRole.INTERVIEWER)
            throw new PracticeConflictException("当前问题已变化，请重新加载对话后录音");
        var audio=WavAudio.inspect(wav,settings.getMaxSeconds());String parent="turn-"+turn;
        var old=recordings.findBySessionIdAndClientRequestId(scope,key);
        if(old.isPresent()) { var r=old.get(); if(!r.getAudioHash().equals(audio.hash())||!Objects.equals(r.getParentAttemptId(),parent))
            throw new PracticeConflictException("相同请求标识的录音不一致");requireAudio(r);return view(r); }
        return saveAudio(scope,parent,key,wav,audio);
    }
    @Transactional(readOnly=true) public List<RecordingView> listInterview(Long id) {
        interviews.findById(id).orElseThrow(()->new NotFoundException("面试不存在"));
        return recordings.findBySessionIdOrderByCreatedAtAsc("interview:"+id).stream().map(this::view).toList();
    }
    public void deleteInterview(Long id) {
        lockSession("interview:"+id);
        for(var r:recordings.findBySessionIdOrderByCreatedAtAsc("interview:"+id)) {
            clear(lock(r.getId()),"DELETED");jobs.deleteAll(jobs.findByRecording_IdOrderByGeneration(r.getId()));recordings.delete(r);
        }
    }
    private RecordingView saveAudio(String sessionId,String parent,String key,byte[] wav,WavAudio audio) {
        var r=new SpeechRecording(); r.setId(UUID.randomUUID().toString()); r.setSessionId(sessionId); r.setClientRequestId(key);
        r.setParentAttemptId(parent);r.setAudioHash(audio.hash());r.setDurationMs(audio.durationMs());r.setByteSize(wav.length);
        r.setCreatedAt(Instant.now());r.setExpiresAt(r.getCreatedAt().plus(settings.getRetentionDays(),java.time.temporal.ChronoUnit.DAYS));r.setState("ACTIVE");
        files.write(r.getId(),wav);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) { if(status!=STATUS_COMMITTED) files.delete(r.getId()); }
        });
        recordings.save(r); enqueue(r,1,key); return view(r);
    }
    private void enqueue(SpeechRecording r,int generation,String key) { var j=new TranscriptionJob();j.setId(UUID.randomUUID().toString());
        j.setRecording(r);j.setGeneration(generation);j.setRequestKey(key);j.setStatus("PENDING");j.setCreatedAt(Instant.now());jobs.save(j); }
    public RecordingView retry(String id,String key) {
        key(key); var base=recording(id);activeSession(base.getSessionId());var r=lock(id);requireAudio(r);
        if(r.getSessionId().startsWith("interview:")) {
            var messages=interviewMessages.findBySessionIdOrderByCreatedAtAsc(Long.valueOf(r.getSessionId().substring(10)));
            if(!Objects.equals(r.getParentAttemptId(),"turn-"+messages.size())||messages.isEmpty()||messages.get(messages.size()-1).getRole()!=MessageRole.INTERVIEWER)
                throw new PracticeConflictException("当前问题已变化，请为当前问题重新录音");
        }
        if(r.getAttemptId()!=null) throw new PracticeConflictException("已确认的回答不能重新转写");
        if(!settings.configured()) throw new PracticeConflictException("请配置语音识别服务后再重试");
        if(jobs.findByRecording_IdAndRequestKey(id,key).isPresent()) return view(r);
        var latest=latest(id);
        if("PENDING".equals(latest.getStatus())||"RUNNING".equals(latest.getStatus())) return view(r);
        if(!"FAILED".equals(latest.getStatus())||latest.getGeneration()>=3) throw new PracticeConflictException("本录音不需要重试或已达到三次上限");
        enqueue(r,latest.getGeneration()+1,key);return view(r);
    }
    public PracticeDtos.AttemptView confirm(String id,Confirm body) {
        key(body.clientRequestId());var base=recording(id);
        if(base.getSessionId().startsWith("interview:")) throw new PracticeConflictException("模拟面试转写请在面试页面核对并提交");
        sessions.lock(base.getSessionId()).orElseThrow();var r=lock(id);requireAudio(r);
        if(r.getAttemptId()!=null) { var a=practice.getAttempt(r.getAttemptId());
            if(!Objects.equals(r.getConfirmationKey(),body.clientRequestId())||!a.answer().equals(body.text())) throw new PracticeConflictException("该录音已确认提交，不能覆盖回答");return a; }
        activeSession(r.getSessionId());
        if(recordings.findBySessionIdAndConfirmationKey(r.getSessionId(),body.clientRequestId()).isPresent()) throw new PracticeConflictException("确认标识已用于另一段录音");
        if(!"SUCCEEDED".equals(latest(id).getStatus())) throw new PracticeConflictException("请等待转写成功再确认");
        var a=practice.submitRecorded(r.getSessionId(),new PracticeDtos.SubmitAnswer(body.text(),r.getParentAttemptId(),"TEXT",body.clientRequestId()),r.getDurationMs());
        r.setAttemptId(a.id());r.setConfirmationKey(body.clientRequestId());return a;
    }
    @Transactional(readOnly=true) public List<RecordingView> list(String sessionId) { practice.get(sessionId);return recordings.findBySessionIdOrderByCreatedAtAsc(sessionId).stream().map(this::view).toList(); }
    @Transactional(readOnly=true) public RecordingView get(String id) { return view(recording(id)); }
    @Transactional(readOnly=true) public byte[] audio(String id) { var r=recording(id);requireAudio(r);return files.read(id); }
    public void delete(String id) { var base=recording(id);lockSession(base.getSessionId());clear(lock(id),"DELETED"); }
    private void clear(SpeechRecording r,String state) {
        files.delete(r.getId());r.setState(state);r.setAttemptId(null);r.setParentAttemptId(null);r.setConfirmationKey(null);
        for(var j:jobs.findByRecording_IdOrderByGeneration(r.getId())) { j.setOriginalText(null);j.setStatus(state);j.setErrorCode(null); }
    }
    public void expire() { for(var r:recordings.findTop50ByStateAndExpiresAtBefore("ACTIVE",Instant.now())) clear(lock(r.getId()),"EXPIRED"); }
    private void requireAudio(SpeechRecording r) { if(!"ACTIVE".equals(r.getState())||!r.getExpiresAt().isAfter(Instant.now())) throw new PracticeConflictException("录音已删除或到期，文字回答仍可回看"); }
    private SpeechRecording recording(String id) { return recordings.findById(id).orElseThrow(()->new NotFoundException("录音不存在")); }
    private SpeechRecording lock(String id) { return recordings.lock(id).orElseThrow(()->new NotFoundException("录音不存在")); }
    private TranscriptionJob latest(String id) { var all=jobs.findByRecording_IdOrderByGeneration(id);return all.get(all.size()-1); }
    private RecordingView view(SpeechRecording r) { boolean expired=!r.getExpiresAt().isAfter(Instant.now());
        return new RecordingView(r.getId(),r.getSessionId(),r.getClientRequestId(),r.getParentAttemptId(),expired?"EXPIRED":r.getState(),r.getDurationMs(),r.getByteSize(),r.getExpiresAt(),r.getAttemptId(),
        jobs.findByRecording_IdOrderByGeneration(r.getId()).stream().map(j->new JobView(j.getId(),j.getGeneration(),expired?"EXPIRED":j.getStatus(),expired?null:j.getOriginalText(),j.getErrorCode(),j.getCreatedAt(),j.getFinishedAt())).toList()); }
}
