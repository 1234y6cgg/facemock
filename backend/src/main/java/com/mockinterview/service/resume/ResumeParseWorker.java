package com.mockinterview.service.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.domain.Resume;
import com.mockinterview.domain.ResumeStatus;
import com.mockinterview.domain.ResumeStructured;
import com.mockinterview.repository.ResumeRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ResumeParseWorker {

    private final ResumeRepository repository;
    private final ResumeParser parser;
    private final ObjectMapper objectMapper;

    public ResumeParseWorker(ResumeRepository repository, ResumeParser parser, ObjectMapper objectMapper) {
        this.repository = repository;
        this.parser = parser;
        this.objectMapper = objectMapper;
    }

    @Async
    public void parse(Long resumeId, String rawText) {
        repository.findById(resumeId).ifPresent(resume -> {
            try {
                ResumeStructured structured = parser.parse(rawText);
                resume.setParsedJson(objectMapper.writeValueAsString(structured));
                resume.setStatus(ResumeStatus.PARSED);
            } catch (Exception e) {
                resume.setStatus(ResumeStatus.FAILED);
            }
            repository.save(resume);
        });
    }
}
