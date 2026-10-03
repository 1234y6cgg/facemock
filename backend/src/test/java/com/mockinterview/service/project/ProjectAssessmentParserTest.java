package com.mockinterview.service.project;
import com.mockinterview.service.question.QuestionJson;
import com.mockinterview.capability.evaluation.InvalidEvaluationException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.mockinterview.service.project.ProjectDtos.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectAssessmentParserTest {
    private final QuestionJson json=new QuestionJson();private final ProjectAssessmentParser parser=new ProjectAssessmentParser(json);
    private final String answer="我负责整个系统，日单量一百万，已经加入补偿。";
    private final Snapshot snapshot=new Snapshot(UUID.randomUUID().toString(),"A",3,new Facts("","","","","","","",true),List.of(new Source("role",1,"职责","简历 A 项目","只负责库存模块",false),new Source("plan",1,"改进","本人事实卡","计划加入补偿，尚未完成",true)));
    private Assessment result(String status,String reason,String quote,List<Citation> refs){return new Assessment(QuestionJson.sha256(answer),List.of(new Criterion("relevance","COVERED","ANSWERED",answer,List.of()),
        new Criterion("contribution","PARTIAL","NEEDS_DETAIL",answer,List.of()),new Criterion("evidence","UNCERTAIN","NO_EVIDENCE",answer,List.of()),new Criterion("consistency",status,reason,quote,refs)),List.of("scale"),List.of("MEASURE"));}
    @Test void acceptsPairedOriginalQuotesForMaterialDisagreement(){var result=result("CONFLICT","CONTRADICTS_MATERIAL","我负责整个系统",List.of(new Citation("role",1,"只负责库存模块")));assertEquals(result,parser.parse(json.write(result),snapshot,answer));}
    @Test void rejectsInventedAnswerQuoteSourceAndOldVersion(){for(var refs:List.of(List.of(new Citation("other-project",1,"一百万")),List.of(new Citation("role",2,"只负责库存模块")),List.of(new Citation("role",1,"我负责整个系统"))))
        assertThrows(InvalidEvaluationException.class,()->parser.parse(json.write(result("CONFLICT","CONTRADICTS_MATERIAL",answer,refs)),snapshot,answer));
        assertThrows(InvalidEvaluationException.class,()->parser.parse(json.write(result("CONFLICT","CONTRADICTS_MATERIAL","我优化了80%",List.of(new Citation("role",1,"只负责库存模块")))),snapshot,answer));}
    @Test void plansCannotProveCompletedWorkOrJustifyConflict(){for(var status:List.of("CONFLICT","COVERED"))assertThrows(InvalidEvaluationException.class,()->parser.parse(json.write(result(status,status.equals("CONFLICT")?"CONTRADICTS_MATERIAL":"ANSWERED",answer,List.of(new Citation("plan",1,"计划加入补偿")))),snapshot,answer));
        assertThrows(InvalidEvaluationException.class,()->parser.parse(json.write(result("CONFLICT","CONTRADICTS_MATERIAL",answer,List.of(new Citation("plan",1,"计划加入补偿"),new Citation("role",1,"只负责库存模块")))),snapshot,answer));}
    @Test void missingMetricsProduceUncertaintyRatherThanInventedRewrite(){var valid=result("UNCERTAIN","UNVERIFIABLE",answer,List.of());assertEquals(List.of("scale"),parser.parse(json.write(valid),snapshot,answer).missingFields());
        assertThrows(InvalidEvaluationException.class,()->parser.parse(json.write(valid).replace("MEASURE","我已提升90%"),snapshot,answer));assertThrows(InvalidEvaluationException.class,()->parser.parse(json.write(valid).replace("\"scale\"","\"日单量一百万\""),snapshot,answer));}
    @Test void rejectsExtraFreeTextRepeatedCriteriaHashMismatchAndEmptyReferences(){var valid=json.write(result("UNCERTAIN","UNVERIFIABLE",answer,List.of()));assertThrows(InvalidEvaluationException.class,()->parser.parse(valid.replaceFirst("\\{","{\"rewrittenAnswer\":\"优化80%\","),snapshot,answer));
        assertThrows(InvalidEvaluationException.class,()->parser.parse(valid.replace("consistency","evidence"),snapshot,answer));assertThrows(InvalidEvaluationException.class,()->parser.parse(valid,snapshot,answer+"改变"));
        assertThrows(InvalidEvaluationException.class,()->parser.parse(json.write(result("CONFLICT","CONTRADICTS_MATERIAL",answer,List.of())),snapshot,answer));}
}
