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
    private final ResumeTextExtractor extractor;
    private final ObjectMapper objectMapper;

    public ResumeParseWorker(ResumeRepository repository, ResumeParser parser, ObjectMapper objectMapper, ResumeTextExtractor extractor) {
        this.repository = repository;
        this.parser = parser;
        this.extractor = extractor;
        this.objectMapper = objectMapper;
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void recoverInterrupted() {
        for (var resume : repository.findByStatus(ResumeStatus.PARSING)) {
            resume.setStatus(ResumeStatus.FAILED);
            resume.setErrorMessage("服务重启中断了简历解析，原文件已保留，请重新上传。");
            repository.save(resume);
        }
    }

    @Async("resumeExecutor")
    public void parse(Long resumeId) {
        repository.findById(resumeId).ifPresent(resume -> {
            try {
                var extraction = extractor.extract(resume.getFileData(), resume.getFilename());
                resume.setRawText(extraction.text());
                resume.setExtractionMethod(extraction.method());
                repository.save(resume);
                ResumeStructured structured = parser.parse(extraction.text());
                resume.setParsedJson(objectMapper.writeValueAsString(structured));
                resume.setStatus(ResumeStatus.PARSED);
                resume.setErrorMessage(null);
            } catch (ResumeExtractionException e) {
                resume.setErrorMessage(e.getMessage());
                resume.setStatus(ResumeStatus.FAILED);
            } catch (Exception e) {
                resume.setErrorMessage("简历结构化解析失败，请检查模型配置后重新上传。");
                resume.setStatus(ResumeStatus.FAILED);
            }
            repository.save(resume);
        });
    }
}
