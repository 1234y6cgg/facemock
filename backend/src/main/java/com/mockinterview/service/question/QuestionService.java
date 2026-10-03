package com.mockinterview.service.question;

import com.mockinterview.controller.NotFoundException;
import com.mockinterview.domain.question.*;
import com.mockinterview.repository.question.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class QuestionService {
    private final QuestionRepository questions;
    private final QuestionRevisionRepository revisions;
    private final QuestionJson json;
    public QuestionService(QuestionRepository questions, QuestionRevisionRepository revisions, QuestionJson json) {
        this.questions = questions; this.revisions = revisions; this.json = json;
    }

    public QuestionPage list(QuestionTopic topic, Integer difficulty, int page, int size) {
        return list(topic, difficulty, page, size, "");
    }

    public QuestionPage list(QuestionTopic topic, Integer difficulty, int page, int size, String keyword) {
        if (difficulty != null && (difficulty < 1 || difficulty > 5)) throw new IllegalArgumentException("难度必须在 1 到 5 之间");
        if (page < 0 || page > 10000 || size < 1 || size > 50) throw new IllegalArgumentException("页码为 0–10000，每页为 1–50");
        String term = keyword == null ? "" : keyword.trim().toLowerCase(java.util.Locale.ROOT);
        if (term.length() > 80) throw new IllegalArgumentException("搜索词最多 80 个字符");
        var result = questions.browse(topic, difficulty, term, PageRequest.of(page, size, Sort.by("id")));
        return new QuestionPage(result.getContent().stream().map(q -> publicView(
                json.read(q.getCurrentRevision().getSnapshotJson(), QuestionSnapshot.class).question())).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    public CatalogSummary summary() {
        var counts = questions.topicCounts().stream()
                .sorted(java.util.Comparator.comparing(c -> c.getTopic().ordinal()))
                .map(c -> new TopicSummary(c.getTopic(), c.getCount())).toList();
        return new CatalogSummary(counts.stream().mapToLong(TopicSummary::count).sum(), counts);
    }

    public PublicQuestion detail(String id, Integer version) {
        var q = questions.findById(id).filter(QuestionEntity::isActive)
                .orElseThrow(() -> new NotFoundException("题目不存在：" + id));
        int requested = version == null ? q.getCurrentRevision().getVersion() : version;
        return publicView(snapshot(id, requested).question());
    }

    /** Internal use for future training; not exposed through the question controller. */
    public QuestionSnapshot snapshot(String id, int version) {
        if (version < 1) throw new IllegalArgumentException("题目版本必须大于 0");
        var revision = revisions.findByQuestion_IdAndVersion(id, version)
                .orElseThrow(() -> new NotFoundException("题目版本不存在：" + id + " v" + version));
        return json.read(revision.getSnapshotJson(), QuestionSnapshot.class);
    }

    private PublicQuestion publicView(QuestionCatalog.Question q) {
        return new PublicQuestion(q.id(), q.version(), q.topic(), q.difficulty(), q.title(), q.prompt(), q.suggestedSeconds());
    }

    public record PublicQuestion(String id, int version, QuestionTopic topic, int difficulty,
                                 String title, String prompt, int suggestedSeconds) {}
    public record QuestionPage(List<PublicQuestion> items, int page, int size, long totalElements, int totalPages) {}
    public record TopicSummary(QuestionTopic topic, long count) {}
    public record CatalogSummary(long totalQuestions, List<TopicSummary> topics) {}
}
