package com.mockinterview.service.project;
import com.mockinterview.service.question.QuestionJson;
import com.mockinterview.capability.evaluation.InvalidEvaluationException;
import org.springframework.stereotype.Component;
import java.util.*;
import static com.mockinterview.service.project.ProjectDtos.*;

@Component
public class ProjectAssessmentParser {
    public static final String VERSION="project-facts-v5";
    public static final Set<String> CRITERIA=Set.of("relevance","contribution","evidence","consistency");
    public static final Set<String> STATUSES=Set.of("COVERED","PARTIAL","MISSING","CONFLICT","UNCERTAIN");
    public static final Set<String> REASONS=Set.of("ANSWERED","NEEDS_DETAIL","NO_EVIDENCE","CONTRADICTS_MATERIAL","NOT_MENTIONED","UNVERIFIABLE","PLAN_ONLY");
    public static final Set<String> FIELDS=Set.of("background","contribution","approach","challenge","result","evidence","improvements","measurement","scale");
    public static final Set<String> ACTIONS=Set.of("MEASURE","CLARIFY_OWNERSHIP","COMPARE_ALTERNATIVES","TRACE_FAILURE","VERIFY_BOUNDARIES");
    private final QuestionJson json;
    public ProjectAssessmentParser(QuestionJson json){this.json=json;}
    public Assessment parse(String raw,Snapshot snapshot,String answer){try{
        if(raw==null||raw.length()>60000)throw new IllegalArgumentException();var result=json.read(raw,Assessment.class);
        if(result==null||!QuestionJson.sha256(answer).equals(result.answerHash())||result.criteria()==null||result.criteria().size()!=4
            ||result.missingFields()==null||result.improvementActions()==null||result.missingFields().size()>9||result.improvementActions().size()>5
            ||!FIELDS.containsAll(result.missingFields())||!ACTIONS.containsAll(result.improvementActions())
            ||new HashSet<>(result.missingFields()).size()!=result.missingFields().size()||new HashSet<>(result.improvementActions()).size()!=result.improvementActions().size())throw new IllegalArgumentException();
        var seen=new HashSet<String>();for(var c:result.criteria()){
            if(c==null||!CRITERIA.contains(c.id())||!seen.add(c.id())||!STATUSES.contains(c.status())||!REASONS.contains(c.reasonCode())
                ||c.candidateQuote()==null||c.candidateQuote().length()>2000||!answer.contains(c.candidateQuote())||c.references()==null||c.references().size()>4)throw new IllegalArgumentException();
            if(Set.of("COVERED","PARTIAL","CONFLICT").contains(c.status())&&c.candidateQuote().isBlank())throw new IllegalArgumentException();
            if("MISSING".equals(c.status())&&!c.candidateQuote().isEmpty())throw new IllegalArgumentException();
            if("CONFLICT".equals(c.status())&&(!"CONTRADICTS_MATERIAL".equals(c.reasonCode())||c.references().isEmpty()))throw new IllegalArgumentException();
            if("CONTRADICTS_MATERIAL".equals(c.reasonCode())&&!"CONFLICT".equals(c.status()))throw new IllegalArgumentException();
            if(Set.of("evidence","consistency").contains(c.id())&&"COVERED".equals(c.status())&&c.references().isEmpty())throw new IllegalArgumentException();
            for(var ref:c.references()){
                if(ref==null||ref.quote()==null||ref.quote().isBlank()||ref.quote().length()>2000||snapshot.sources().stream().noneMatch(s->s.id().equals(ref.sourceId())&&s.version()==ref.version()&&s.content().contains(ref.quote())))throw new IllegalArgumentException();
                if("CONFLICT".equals(c.status())&&snapshot.sources().stream().anyMatch(s->s.id().equals(ref.sourceId())&&s.version()==ref.version()&&s.planned()))throw new IllegalArgumentException();
            }
            if(("CONFLICT".equals(c.status())||Set.of("evidence","consistency").contains(c.id())&&"COVERED".equals(c.status()))
                &&c.references().stream().allMatch(ref->snapshot.sources().stream().filter(s->s.id().equals(ref.sourceId())&&s.version()==ref.version()).allMatch(Source::planned)))throw new IllegalArgumentException();
        }
        return result;
    }catch(RuntimeException e){throw new InvalidEvaluationException("项目反馈的结构、原话或材料引用未通过校验");}}
}
