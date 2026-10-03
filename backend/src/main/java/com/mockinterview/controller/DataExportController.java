package com.mockinterview.controller;
import com.mockinterview.service.data.DataExportService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
public class DataExportController {
    private final DataExportService data;
    public DataExportController(DataExportService data){this.data=data;}
    @GetMapping("/api/data/export") public ResponseEntity<String> export(){return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=interview-training-export.json")
        .header(HttpHeaders.CACHE_CONTROL,"no-store").body(data.export());}
}
