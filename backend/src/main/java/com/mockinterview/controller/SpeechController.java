package com.mockinterview.controller;
import com.mockinterview.service.speech.*;
import com.mockinterview.service.practice.PracticeDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import java.io.IOException;
import java.util.List;
import static com.mockinterview.service.speech.SpeechDtos.*;

@RestController
public class SpeechController {
    private final SpeechStore speech;private final PracticeDeletion deletion;
    public SpeechController(SpeechStore speech,PracticeDeletion deletion){this.speech=speech;this.deletion=deletion;}
    @GetMapping("/api/speech/status") public Status status(){return speech.status();}
    @PostMapping(value="/api/interviews/{id}/speech/recordings",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public RecordingView uploadInterview(@PathVariable Long id,@RequestParam int turn,@RequestParam String clientRequestId,
        @RequestPart MultipartFile file) throws IOException {
        if(file.getSize()>5760044L||file.isEmpty())throw new IllegalArgumentException("录音为空或超过大小上限");
        return speech.uploadInterview(id,turn,clientRequestId,file.getBytes());
    }
    @GetMapping("/api/interviews/{id}/speech/recordings") public List<RecordingView> listInterview(@PathVariable Long id){return speech.listInterview(id);}
    @PostMapping(value="/api/speech/recordings",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public RecordingView upload(@RequestParam String sessionId,@RequestParam(required=false) String parentAttemptId,
        @RequestParam String clientRequestId,@RequestPart MultipartFile file) throws IOException {
        if(file.getSize()>5760044L||file.isEmpty())throw new IllegalArgumentException("录音为空或超过大小上限");
        return speech.upload(sessionId,parentAttemptId,clientRequestId,file.getBytes());
    }
    @GetMapping("/api/speech/recordings") public List<RecordingView> list(@RequestParam String sessionId){return speech.list(sessionId);}
    @GetMapping("/api/speech/recordings/{id}") public RecordingView get(@PathVariable String id){return speech.get(id);}
    @GetMapping("/api/speech/recordings/{id}/audio") public ResponseEntity<byte[]> audio(@PathVariable String id){return ResponseEntity.ok()
        .header("Cache-Control","no-store").header("Content-Disposition","inline; filename=recording.wav").contentType(MediaType.parseMediaType("audio/wav")).body(speech.audio(id));}
    @PostMapping("/api/speech/recordings/{id}/retry") public RecordingView retry(@PathVariable String id,@Valid @RequestBody PracticeDtos.RequestKey body){return speech.retry(id,body.clientRequestId());}
    @PostMapping("/api/speech/recordings/{id}/confirm") public PracticeDtos.AttemptView confirm(@PathVariable String id,@Valid @RequestBody Confirm body){return speech.confirm(id,body);}
    @DeleteMapping("/api/speech/recordings/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable String id){speech.delete(id);}
    @DeleteMapping("/api/practice/sessions/{id}") public PracticeDeletion.Deleted deletePractice(@PathVariable String id){return deletion.delete(id);}
}
