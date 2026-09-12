package com.mockinterview.controller;

import com.mockinterview.domain.dto.*;
import com.mockinterview.service.interview.InterviewService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {

    private final InterviewService service;

    public InterviewController(InterviewService service) {
        this.service = service;
    }

    @PostMapping
    public CreateInterviewResponse create(@RequestBody CreateInterviewRequest request) {
        return new CreateInterviewResponse(service.create(request.resumeId()));
    }

    @PostMapping("/{id}/start")
    public SseEmitter start(@PathVariable Long id) {
        SseEmitter emitter = new SseEmitter(5 * 60_000L);
        service.start(id, emitter);
        return emitter;
    }

    @PostMapping("/{id}/answer")
    public SseEmitter answer(@PathVariable Long id, @RequestBody AnswerRequest request) {
        SseEmitter emitter = new SseEmitter(5 * 60_000L);
        service.answer(id, request.content(), emitter);
        return emitter;
    }

    @GetMapping("/{id}/messages")
    public List<MessageDto> messages(@PathVariable Long id) {
        return service.messages(id);
    }

    @GetMapping("/{id}/report")
    public ReportResponse report(@PathVariable Long id) {
        return service.report(id);
    }
}
