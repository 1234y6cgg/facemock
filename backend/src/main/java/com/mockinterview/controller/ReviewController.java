package com.mockinterview.controller;
import com.mockinterview.service.review.*;
import com.mockinterview.service.practice.PracticeDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController
public class ReviewController {
    private final ReviewService review;
    public ReviewController(ReviewService review){this.review=review;}
    @GetMapping("/api/review/today") public ReviewDtos.Today today(){return review.today();}
    @GetMapping("/api/progress/knowledge-points") public ReviewDtos.Overview progress(){return review.overview();}
    @PutMapping("/api/review/preferences") public ReviewDtos.Preferences preferences(@Valid @RequestBody ReviewDtos.Configure body){return review.configure(body);}
    @PostMapping("/api/review/skip") public ReviewDtos.Today skip(@Valid @RequestBody ReviewDtos.Skip body){return review.skip(body.questionId());}
    @PostMapping("/api/review/sessions") public PracticeDtos.SessionView start(@Valid @RequestBody ReviewDtos.Start body){return review.start(body);}
}
