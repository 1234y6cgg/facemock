package com.mockinterview.capability.evaluation;
import com.mockinterview.domain.question.QuestionSnapshot;
import com.mockinterview.service.practice.PracticeReferences.Context;
import com.mockinterview.service.question.QuestionJson;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class PracticeEvaluationEngine {
    private final PracticeAssessmentModel model;
    private final TrainingEvaluationParser parser;
    private final QuestionJson json;
    public PracticeEvaluationEngine(PracticeAssessmentModel model,TrainingEvaluationParser parser,QuestionJson json) {
        this.model=model; this.parser=parser; this.json=json;
    }
    public TrainingEvaluation evaluate(QuestionSnapshot snapshot,String answer,Context context) {
        if(context.sources().isEmpty()) {
            var q=snapshot.question();
            var uncertain=new TrainingEvaluation(q.id(),q.version(),q.rubricVersion(),QuestionJson.sha256(answer),
                q.criteria().stream().map(c->new TrainingEvaluation.CriterionAssessment(c.id(),CriterionStatus.UNCERTAIN,
                    List.of(),"缺少可核验资料，无法进行知识判定。",List.of())).toList(),List.of());
            return parser.parse(json.write(uncertain),snapshot,answer,context.availableSourceIds());
        }
        return parser.parse(model.assess(snapshot,answer,context),snapshot,answer,context.availableSourceIds());
    }
}
