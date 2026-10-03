package com.mockinterview.service.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.controller.NotFoundException;
import com.mockinterview.domain.Resume;
import com.mockinterview.domain.ResumeStatus;
import com.mockinterview.domain.ResumeStructured;
import com.mockinterview.domain.dto.ResumeStatusResponse;
import com.mockinterview.infrastructure.tika.TikaTextExtractor;
import com.mockinterview.repository.ResumeRepository;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;

@Service
public class ResumeService {

    private final ResumeRepository repository;
    private final TikaTextExtractor tika;
    private final ResumeParseWorker worker;
    private final ObjectMapper objectMapper;

    public ResumeService(ResumeRepository repository, TikaTextExtractor tika,
                         ResumeParseWorker worker, ObjectMapper objectMapper) {
        this.repository = repository;
        this.tika = tika;
        this.worker = worker;
        this.objectMapper = objectMapper;
    }

    public Long upload(MultipartFile file) {
        String rawText;
        byte[] bytes;
        try {
            rawText = tika.extract(file.getInputStream());
            bytes = file.getBytes();
        } catch (IOException | TikaException e) {
            throw new IllegalStateException("简历文本抽取失败", e);
        }
        String trimmed = rawText == null ? "" : rawText.trim();
        if (trimmed.length() < 30) {
            throw new IllegalStateException("未能从简历中提取有效文字，请上传文字版 PDF / Word；扫描件或图片型 PDF 暂不支持");
        }
        Resume resume = Resume.builder()
                .filename(file.getOriginalFilename())
                .rawText(rawText)
                .fileData(bytes)
                .contentType(file.getContentType())
                .status(ResumeStatus.PARSING)
                .createdAt(LocalDateTime.now())
                .build();
        repository.save(resume);
        worker.parse(resume.getId(), rawText);
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
        return new ResumeStatusResponse(resume.getId(), resume.getStatus(), parsed);
    }

    public Resume file(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("简历不存在: " + id));
    }
}
