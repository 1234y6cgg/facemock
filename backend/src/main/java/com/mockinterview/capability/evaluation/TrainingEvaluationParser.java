package com.mockinterview.capability.evaluation;

import com.mockinterview.domain.question.QuestionSnapshot;
import com.mockinterview.service.question.QuestionJson;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class TrainingEvaluationParser {
    private final QuestionJson json;
    private final Validator validator;
    public TrainingEvaluationParser(QuestionJson json, Validator validator) {
        this.json = json; this.validator = validator;
    }

    /**
     * availableSourceIds must be built by the server from supplied reference snapshots,
     * never copied from model output. Citation checks establish provenance, not semantic correctness.
     */
    public TrainingEvaluation parse(String raw, QuestionSnapshot snapshot, String originalAnswer,
                                    Set<String> availableSourceIds) {
        if (raw == null || raw.length() > 100000 || originalAnswer == null || originalAnswer.isBlank()
                || originalAnswer.length() > 10000 || snapshot == null || availableSourceIds == null) {
            throw new InvalidEvaluationException("评估输入不完整或超过长度限制");
        }
        TrainingEvaluation evaluation;
        try { evaluation = json.read(raw, TrainingEvaluation.class); }
        catch (IllegalArgumentException e) { throw new InvalidEvaluationException("评估必须是符合规定的单个 JSON 对象", e); }
        if (evaluation == null || !validator.validate(evaluation).isEmpty()) {
            throw new InvalidEvaluationException("评估字段、枚举或长度不符合规定");
        }
        var question = snapshot.question();
        if (!question.id().equals(evaluation.questionId()) || question.version() != evaluation.questionVersion()
                || question.rubricVersion() != evaluation.rubricVersion()
                || !QuestionJson.sha256(originalAnswer).equals(evaluation.answerHash())) {
            throw new InvalidEvaluationException("评估引用的题目、评分版本或回答不匹配");
        }
        var expected = question.criteria().stream().collect(Collectors.toMap(c -> c.id(), c -> c));
        var sources = snapshot.sources().stream().collect(Collectors.toMap(s -> s.sourceId(), s -> s));
        if (!sources.keySet().containsAll(availableSourceIds)) {
            throw new InvalidEvaluationException("评估上下文包含未绑定到题目的来源");
        }
        Set<String> assessed = new HashSet<>();
        for (var result : evaluation.criteria()) {
            var criterion = expected.get(result.criterionId());
            if (criterion == null || !assessed.add(result.criterionId())) {
                throw new InvalidEvaluationException("评估存在未知或重复的要点");
            }
            for (String quote : result.candidateQuotes()) {
                if (!originalAnswer.contains(quote)) throw new InvalidEvaluationException("候选人原话不在提交的回答中");
            }
            if (result.status() == CriterionStatus.MISSING && !result.candidateQuotes().isEmpty()) {
                throw new InvalidEvaluationException("缺失要点不能附带已覆盖原话");
            }
            if ((result.status() == CriterionStatus.COVERED || result.status() == CriterionStatus.PARTIAL
                    || result.status() == CriterionStatus.INCORRECT) && result.candidateQuotes().isEmpty()) {
                throw new InvalidEvaluationException("已作出知识判定但缺少候选人原话");
            }
            if (result.status() != CriterionStatus.UNCERTAIN && result.references().isEmpty()) {
                throw new InvalidEvaluationException("已作出知识判定但缺少可用依据");
            }
            Set<String> seenReferences = new HashSet<>();
            for (var citation : result.references()) {
                var source = sources.get(citation.sourceId());
                if (source == null || !availableSourceIds.contains(citation.sourceId())
                        || !criterion.sourceIds().contains(citation.sourceId())
                        || !source.documentRevision().equals(citation.documentRevision())
                        || !source.content().contains(citation.quote())) {
                    throw new InvalidEvaluationException("引用了虚构、过期、不可用或与要点不匹配的来源");
                }
                if (!seenReferences.add(json.write(citation))) throw new InvalidEvaluationException("来源引用重复");
            }
        }
        if (!assessed.equals(expected.keySet())) throw new InvalidEvaluationException("评估没有覆盖全部评分要点");
        for (var feedback : evaluation.expressionFeedback()) {
            if (!originalAnswer.contains(feedback.quote())) throw new InvalidEvaluationException("表达反馈原话不在回答中");
        }
        return evaluation;
    }
}
