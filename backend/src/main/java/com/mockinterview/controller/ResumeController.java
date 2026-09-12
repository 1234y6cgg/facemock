package com.mockinterview.controller;

import com.mockinterview.domain.dto.ResumeStatusResponse;
import com.mockinterview.domain.dto.ResumeUploadResponse;
import com.mockinterview.service.resume.ResumeService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
}
