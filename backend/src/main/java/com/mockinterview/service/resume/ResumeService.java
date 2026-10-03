package com.mockinterview.service.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.controller.NotFoundException;
import com.mockinterview.domain.Resume;
import com.mockinterview.domain.ResumeStatus;
import com.mockinterview.domain.ResumeStructured;
import com.mockinterview.domain.dto.ResumeStatusResponse;
import com.mockinterview.repository.ResumeRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;

@Service
public class ResumeService {

    private final ResumeRepository repository;
    private final ResumeTextExtractor extractor;
    private final ResumeParseWorker worker;
    private final ObjectMapper objectMapper;

    public ResumeService(ResumeRepository repository, ResumeTextExtractor extractor,
                         ResumeParseWorker worker, ObjectMapper objectMapper) {
        this.repository = repository;
        this.extractor = extractor;
        this.worker = worker;
        this.objectMapper = objectMapper;
    }

    public Long upload(MultipartFile file) {
        byte[] bytes;
        try { bytes = file.getBytes(); }
        catch (IOException e) { throw new IllegalArgumentException("简历文件无法读取，请重新上传。"); }
        String contentType = extractor.validate(bytes, file.getOriginalFilename());
        Resume resume = Resume.builder()
                .filename(file.getOriginalFilename())
                .fileData(bytes)
                .contentType(contentType)
                .status(ResumeStatus.PARSING)
                .createdAt(LocalDateTime.now())
                .build();
        repository.save(resume);
        try { worker.parse(resume.getId()); }
        catch (org.springframework.core.task.TaskRejectedException e) {
            resume.setStatus(ResumeStatus.FAILED);
            resume.setErrorMessage("同时解析的简历较多，请稍后重新上传。");
            repository.save(resume);
        }
        return resume.getId();
    }

    public ResumeStatusResponse get(Long id) {
        Resume resume = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("简历不存在: " + id));
        ResumeStructured parsed = null;
        if (resume.getStatus() == ResumeStatus.PARSED && resume.getParsedJson() != null) {
            try {
                parsed = objectMapper.readValue(resume.getParsedJson(), ResumeStructured.class);
            } catch (Exception ignored) {
                // 解析失败时返回 null，前端按 PARSING 处理
            }
        }
        return new ResumeStatusResponse(resume.getId(), resume.getStatus(), parsed, resume.getErrorMessage(), resume.getExtractionMethod());
    }

    public com.mockinterview.infrastructure.ocr.PaddleOcrClient.Status ocrStatus() {
        return extractor.ocrStatus();
    }

    public Resume file(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("简历不存在: " + id));
    }
}
