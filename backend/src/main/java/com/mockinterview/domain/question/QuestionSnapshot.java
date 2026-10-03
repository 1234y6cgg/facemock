package com.mockinterview.domain.question;

import java.time.LocalDate;
import java.util.List;

/** Stored as an immutable revision, never returned by the public question endpoints. */
public record QuestionSnapshot(
        QuestionCatalog.Question question,
        List<QuestionCatalog.KnowledgePoint> knowledgePoints,
        List<SourceSnapshot> sources) {

    public record SourceSnapshot(
            String sourceId, int sourceVersion, String documentId, String documentRevision,
            String title, String source, String sourceUrl, String content, LocalDate verifiedOn) {}
}
