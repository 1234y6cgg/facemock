package com.mockinterview.controller;
import com.mockinterview.service.practice.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import static com.mockinterview.service.practice.PracticeDtos.*;

@RestController
@RequestMapping("/api/practice")
public class PracticeController {
    private final PracticeStore store;
    public PracticeController(PracticeStore store) { this.store=store; }
    @PostMapping("/sessions") public SessionView create(@Valid @RequestBody CreateSession body) { return store.create(body); }
    @GetMapping("/sessions") public HistoryPage history(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="12") int size) { return store.history(page,size); }
    @GetMapping("/sessions/{id}") public SessionView get(@PathVariable String id) { return store.get(id); }
    @PostMapping("/sessions/{id}/attempts") public AttemptView submit(@PathVariable String id,@Valid @RequestBody SubmitAnswer body) { return store.submit(id,body); }
    @PostMapping("/sessions/{id}/hints") public HintView hint(@PathVariable String id) { return store.hint(id); }
    @GetMapping("/sessions/{id}/reference") public ReferenceView reference(@PathVariable String id,@RequestParam(defaultValue="false") boolean assisted) { return store.reference(id,assisted); }
    @PostMapping("/sessions/{id}/complete") public SessionView complete(@PathVariable String id) { return store.complete(id); }
    @GetMapping("/attempts/{id}") public AttemptView attempt(@PathVariable String id) { return store.getAttempt(id); }
    @GetMapping("/attempts/{id}/reference") public ReferenceView reference(@PathVariable String id) { return store.reference(store.getAttempt(id).sessionId(),false); }
    @PostMapping("/attempts/{id}/retry-evaluation") public AttemptView retry(@PathVariable String id,@Valid @RequestBody RequestKey body) { return store.retry(id,body); }
    @PostMapping("/attempts/{id}/followups") public SessionView followup(@PathVariable String id,@Valid @RequestBody RequestKey body) { return store.followup(id,body); }
}
