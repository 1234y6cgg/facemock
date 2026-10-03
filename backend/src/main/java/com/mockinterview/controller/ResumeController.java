package com.mockinterview.controller;

import com.mockinterview.domain.Resume;
import com.mockinterview.domain.dto.ResumeStatusResponse;
import com.mockinterview.domain.dto.ResumeUploadResponse;
import com.mockinterview.service.resume.ResumeService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeService service;

    public ResumeController(ResumeService service) {
        this.service = service;
    }

    @PostMapping
    public ResumeUploadResponse upload(@RequestParam("file") MultipartFile file) {
        return new ResumeUploadResponse(service.upload(file));
    }

    @GetMapping("/{id}")
    public ResumeStatusResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable Long id) {
        Resume resume = service.file(id);
        if (resume.getFileData() == null) {
            return ResponseEntity.notFound().build();
        }
        String filename = URLEncoder.encode(
                resume.getFilename() == null ? "resume" : resume.getFilename(),
                StandardCharsets.UTF_8).replace("+", "%20");
        MediaType type = resume.getContentType() != null
                ? MediaType.parseMediaType(resume.getContentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + filename)
                .body(resume.getFileData());
    }
}
