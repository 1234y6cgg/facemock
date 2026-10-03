package com.mockinterview.controller;

import com.mockinterview.capability.knowledge.KnowledgeDocument;
import com.mockinterview.capability.knowledge.KnowledgeHit;
import com.mockinterview.capability.knowledge.TechKnowledgeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {
    private final TechKnowledgeService knowledge;

    public KnowledgeController(TechKnowledgeService knowledge) { this.knowledge = knowledge; }

    @GetMapping("/status")
    public TechKnowledgeService.Status status() { return knowledge.status(); }

    @GetMapping("/search")
    public List<KnowledgeHit> search(@RequestParam String query, @RequestParam(required = false) Integer topK) {
        return knowledge.search(query, topK == null ? knowledge.defaultTopK() : topK);
    }

    @PostMapping("/documents")
    public TechKnowledgeService.ImportResult importDocument(@Valid @RequestBody KnowledgeDocument document) {
        rejectManagedDocument(document.id());
        return knowledge.upsert(document);
    }

    @DeleteMapping("/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(@PathVariable String documentId) {
        rejectManagedDocument(documentId);
        knowledge.deleteDocument(documentId);
    }

    private void rejectManagedDocument(String id) {
        if (id != null && id.startsWith("p1.")) {
            throw new IllegalArgumentException("p1. 为版本化题库知识，请通过题库资源维护，不能覆盖或删除");
        }
    }

    @PostMapping("/seed")
    public List<TechKnowledgeService.ImportResult> seed() { return knowledge.seedBuiltin(); }
}
