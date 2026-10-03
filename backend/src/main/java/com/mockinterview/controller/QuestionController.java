package com.mockinterview.controller;

import com.mockinterview.domain.question.QuestionTopic;
import com.mockinterview.service.question.QuestionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {
    private final QuestionService questions;
    public QuestionController(QuestionService questions) { this.questions = questions; }

    @GetMapping
    public QuestionService.QuestionPage list(@RequestParam(required = false) QuestionTopic topic,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "") String q) {
        return questions.list(topic, difficulty, page, size, q);
    }

    @GetMapping("/topics")
    public QuestionService.CatalogSummary topics() { return questions.summary(); }

    @GetMapping("/{id}")
    public QuestionService.PublicQuestion detail(@PathVariable String id, @RequestParam(required = false) Integer version) {
        return questions.detail(id, version);
    }
}
