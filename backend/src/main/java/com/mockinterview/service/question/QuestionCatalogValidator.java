package com.mockinterview.service.question;

import com.mockinterview.domain.question.*;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class QuestionCatalogValidator {
    private final Validator validator;
    public QuestionCatalogValidator(Validator validator) { this.validator = validator; }

    public void validate(QuestionCatalog catalog) {
        if (catalog == null) throw new IllegalArgumentException("题库不能为空");
        var violations = validator.validate(catalog);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        var points = unique(catalog.knowledgePoints(), QuestionCatalog.KnowledgePoint::id);
        var sources = unique(catalog.sources(), QuestionCatalog.Source::id);
        unique(catalog.questions(), QuestionCatalog.Question::id);
        unique(catalog.sources(), s -> s.document().id());
        for (var source : sources.values()) {
            if (!source.document().id().startsWith("p1.")) {
                throw new IllegalArgumentException("题库知识文档 ID 必须使用保留的 p1. 前缀");
            }
            URI uri;
            try { uri = URI.create(source.document().sourceUrl()); }
            catch (Exception e) { throw new IllegalArgumentException("来源必须提供有效 HTTPS 链接", e); }
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("来源必须提供有效 HTTPS 链接");
            }
        }
        for (var question : catalog.questions()) {
            unique(question.criteria(), QuestionCatalog.Criterion::id);
            for (var criterion : question.criteria()) {
                var point = points.get(criterion.knowledgePointId());
                if (point == null || point.topic() != question.topic()) {
                    throw new IllegalArgumentException("评分要点必须绑定同主题的有效知识点：" + criterion.id());
                }
                if (new HashSet<>(criterion.sourceIds()).size() != criterion.sourceIds().size()
                        || !sources.keySet().containsAll(criterion.sourceIds())) {
                    throw new IllegalArgumentException("评分要点引用了不存在或重复的来源：" + criterion.id());
                }
            }
        }
    }

    private <T> Map<String, T> unique(List<T> values, Function<T, String> id) {
        try { return values.stream().collect(Collectors.toMap(id, Function.identity(), (a, b) -> {
            throw new IllegalArgumentException("题库存在重复 ID：" + id.apply(a));
        }, LinkedHashMap::new)); }
        catch (NullPointerException e) { throw new IllegalArgumentException("题库存在空项", e); }
    }
}
