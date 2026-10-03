package com.mockinterview.service.question;

import com.mockinterview.domain.question.*;
import com.mockinterview.repository.question.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class QuestionImportService {
    private final QuestionRepository questions;
    private final QuestionRevisionRepository revisions;
    private final KnowledgePointRepository points;
    private final RubricCriterionRepository criteria;
    private final QuestionSourceRevisionRepository sources;
    private final QuestionCatalogValidator validator;
    private final QuestionJson json;

    public QuestionImportService(QuestionRepository questions, QuestionRevisionRepository revisions,
            KnowledgePointRepository points, RubricCriterionRepository criteria,
            QuestionSourceRevisionRepository sources, QuestionCatalogValidator validator, QuestionJson json) {
        this.questions = questions;
        this.revisions = revisions;
        this.points = points;
        this.criteria = criteria;
        this.sources = sources;
        this.validator = validator;
        this.json = json;
    }

    @Transactional
    public synchronized ImportResult importCatalog(QuestionCatalog catalog) {
        validator.validate(catalog);
        var sourceSnapshots = new LinkedHashMap<String, QuestionSnapshot.SourceSnapshot>();
        int newSources = 0, newPoints = 0, newQuestions = 0, newRevisions = 0, unchanged = 0;
        for (var source : catalog.sources()) {
            String hash = QuestionJson.sha256(json.write(source));
            var existing = sources.findBySourceIdAndVersion(source.id(), source.version());
            if (existing.isPresent() && !existing.get().getContentHash().equals(hash)) {
                throw new CatalogConflictException("来源同版本内容发生变化，请升级版本：" + source.id());
            }
            var doc = source.document();
            var snapshot = new QuestionSnapshot.SourceSnapshot(source.id(), source.version(), doc.id(), hash,
                    doc.title(), doc.source(), doc.sourceUrl(), doc.content(), source.verifiedOn());
            if (existing.isEmpty()) {
                if (sources.findByDocumentId(doc.id()).isPresent()) {
                    throw new CatalogConflictException("新来源版本必须使用新的知识文档 ID：" + doc.id());
                }
                var entity = new QuestionSourceRevisionEntity();
                entity.setSourceId(source.id()); entity.setVersion(source.version()); entity.setDocumentId(doc.id());
                entity.setContentHash(hash); entity.setSnapshotJson(json.write(snapshot));
                sources.save(entity);
                newSources++;
            }
            sourceSnapshots.put(source.id(), snapshot);
        }
        var pointEntities = new LinkedHashMap<String, KnowledgePointEntity>();
        var pointDefinitions = catalog.knowledgePoints().stream()
                .collect(Collectors.toMap(QuestionCatalog.KnowledgePoint::id, p -> p));
        for (var point : catalog.knowledgePoints()) {
            var existing = points.findById(point.id());
            if (existing.isEmpty()) newPoints++;
            var entity = existing.orElseGet(KnowledgePointEntity::new);
            if (existing.isPresent() && entity.getTopic() != point.topic()) {
                throw new CatalogConflictException("知识点不能跨主题重新定义：" + point.id());
            }
            entity.setId(point.id()); entity.setTitle(point.title()); entity.setTopic(point.topic());
            entity.setDescription(point.description());
            pointEntities.put(point.id(), points.save(entity));
        }
        for (var definition : catalog.questions()) {
            var boundSources = definition.criteria().stream().flatMap(c -> c.sourceIds().stream()).distinct()
                    .map(sourceSnapshots::get).toList();
            var boundPoints = definition.criteria().stream().map(QuestionCatalog.Criterion::knowledgePointId)
                    .distinct().map(pointDefinitions::get).toList();
            var snapshot = new QuestionSnapshot(definition, boundPoints, boundSources);
            String payload = json.write(snapshot), hash = QuestionJson.sha256(payload);
            String rubricHash = QuestionJson.sha256(json.write(List.of(definition.criteria(), boundPoints, boundSources)));
            var sameRubric = revisions.findFirstByQuestion_IdAndRubricVersion(definition.id(), definition.rubricVersion());
            if (sameRubric.isPresent() && !sameRubric.get().getRubricHash().equals(rubricHash)) {
                throw new CatalogConflictException("评分资料发生变化，请同时升级 rubricVersion：" + definition.id());
            }
            var existing = revisions.findByQuestion_IdAndVersion(definition.id(), definition.version());
            if (existing.isPresent()) {
                if (!hash.equals(existing.get().getContentHash())) {
                    throw new CatalogConflictException("题目同版本内容发生变化，请升级版本：" + definition.id());
                }
                unchanged++;
                continue;
            }
            var q = questions.findById(definition.id()).orElse(null);
            if (q == null) {
                q = new QuestionEntity();
                q.setId(definition.id()); q.setTopic(definition.topic()); q.setDifficulty(definition.difficulty());
                q.setTitle(definition.title()); q.setActive(definition.active());
                q = questions.save(q);
                newQuestions++;
            }
            var revision = new QuestionRevisionEntity();
            revision.setQuestion(q); revision.setVersion(definition.version()); revision.setRubricVersion(definition.rubricVersion());
            revision.setContentHash(hash); revision.setSnapshotJson(payload); revision.setCreatedAt(Instant.now());
            revision.setRubricHash(rubricHash);
            revision = revisions.save(revision);
            for (var criterion : definition.criteria()) {
                var row = new RubricCriterionEntity();
                row.setRevision(revision); row.setCriterionId(criterion.id());
                row.setKnowledgePoint(pointEntities.get(criterion.knowledgePointId()));
                row.setDefinitionJson(json.write(criterion));
                criteria.save(row);
            }
            if (q.getCurrentRevision() == null || definition.version() > q.getCurrentRevision().getVersion()) {
                q.setCurrentRevision(revision); q.setTopic(definition.topic()); q.setDifficulty(definition.difficulty());
                q.setTitle(definition.title()); q.setActive(definition.active());
            }
            newRevisions++;
        }
        return new ImportResult(catalog.questions().size(), newQuestions, newRevisions, unchanged, newSources, newPoints);
    }

    public record ImportResult(int questionCount, int newQuestions, int newRevisions,
                               int unchangedRevisions, int newSources, int newKnowledgePoints) {}
}
