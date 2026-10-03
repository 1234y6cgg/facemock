package com.mockinterview.service.question;

import com.mockinterview.domain.question.QuestionCatalog;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;

@Component
public class QuestionCatalogLoader {
    private final QuestionJson json;
    private final QuestionCatalogValidator validator;
    public QuestionCatalogLoader(QuestionJson json, QuestionCatalogValidator validator) {
        this.json = json;
        this.validator = validator;
    }
    public QuestionCatalog loadBuiltin() {
        var base = load("questions/catalog-v1.json");
        var expansion = load("questions/catalog-v2.json");
        var points = new java.util.ArrayList<>(base.knowledgePoints()); points.addAll(expansion.knowledgePoints());
        var sources = new java.util.ArrayList<>(base.sources()); sources.addAll(expansion.sources());
        var questions = new java.util.ArrayList<>(base.questions()); questions.addAll(expansion.questions());
        var combined = new QuestionCatalog(expansion.catalogVersion(), points, sources, questions);
        validator.validate(combined);
        return combined;
    }

    private QuestionCatalog load(String path) {
        try (var input = new ClassPathResource(path).getInputStream()) {
            var catalog = json.read(new String(input.readAllBytes(), StandardCharsets.UTF_8), QuestionCatalog.class);
            validator.validate(catalog);
            return catalog;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("无法读取内置题库", e);
        }
    }
}
