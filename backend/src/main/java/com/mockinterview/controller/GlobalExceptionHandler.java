package com.mockinterview.controller;

import com.mockinterview.domain.dto.ApiError;
import com.mockinterview.service.question.CatalogConflictException;
import com.mockinterview.service.practice.PracticeConflictException;
import com.mockinterview.capability.knowledge.KnowledgeUnavailableException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> uploadTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new ApiError(413,"录音或上传文件超过大小上限"));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(404, e.getMessage()));
    }

    @ExceptionHandler(KnowledgeUnavailableException.class)
    public ResponseEntity<ApiError> knowledgeUnavailable(KnowledgeUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError(503, e.getMessage()));
    }

    @ExceptionHandler({CatalogConflictException.class, PracticeConflictException.class})
    public ResponseEntity<ApiError> catalogConflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(409, e.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class,
            MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> invalidRequest(Exception e) {
        String message = e instanceof IllegalArgumentException ? e.getMessage() : "请求参数格式或长度不正确";
        return ResponseEntity.badRequest().body(new ApiError(400, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> generic(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(500, e.getMessage()));
    }
}
