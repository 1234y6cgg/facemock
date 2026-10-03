package com.mockinterview.service.project;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class ProjectDtos {
    private ProjectDtos() {}
    public record Facts(@Size(max=3000) String background,@Size(max=3000) String contribution,@Size(max=3000) String approach,
        @Size(max=3000) String challenge,@Size(max=3000) String result,@Size(max=3000) String evidence,@Size(max=3000) String improvements,boolean confirmed) {
        public Map<String,String> fields(){var m=new LinkedHashMap<String,String>();m.put("background",clean(background));m.put("contribution",clean(contribution));
            m.put("approach",clean(approach));m.put("challenge",clean(challenge));m.put("result",clean(result));m.put("evidence",clean(evidence));m.put("improvements",clean(improvements));return m;}
    }
    private static String clean(String value){return value==null?"":value.strip();}
    public record ImportResume(@NotNull Long resumeId,@Min(0) int projectIndex) {}
    public record ResumeChoice(Long id,String filename,List<String> projectNames) {}
    public record SaveFacts(@NotBlank @Size(max=120) String name,@Min(1) int expectedRevision,@NotNull @Valid Facts facts) {}
    public record SaveMaterial(@NotBlank @Size(max=120) String title,@NotBlank @Size(max=500) String origin,@NotBlank @Size(max=10000) String content,@Min(1) int expectedRevision) {}
    public record Start(@NotBlank String template,@NotBlank @Pattern(regexp="[a-zA-Z0-9_-]{8,64}") String clientRequestId,@Min(1) int expectedRevision) {}
    public record Answer(@NotBlank @Size(max=10000) String answer,String parentAttemptId,@NotBlank @Pattern(regexp="[a-zA-Z0-9_-]{8,64}") String clientRequestId) {}
    public record MaterialView(String id,int version,String title,String origin,String content,boolean deleted,Instant createdAt) {}
    public record ProjectView(String id,Long resumeId,Integer resumeProjectIndex,String name,int revision,String indexStatus,int indexedRevision,Facts facts,String extraction,List<MaterialView> materials,List<Template> templates) {}
    public record Template(String id,String title,String field,String question,int seconds) {}
    public record Source(String id,int version,String title,String origin,String content,boolean planned) {}
    public record Snapshot(String projectId,String projectName,int revision,Facts facts,List<Source> sources) {}
    public record Citation(String sourceId,int version,String quote) {}
    public record Criterion(String id,String status,String reasonCode,String candidateQuote,List<Citation> references) {}
    public record Assessment(String answerHash,List<Criterion> criteria,List<String> missingFields,List<String> improvementActions) {}
    public record EvaluationView(String id,int generation,String status,String errorCode,String modelName,String promptVersion,Long durationMs,Assessment result) {}
    public record Change(String criterionId,String before,String after,String change) {}
    public record Comparison(boolean comparable,String notice,List<Change> criteria) {}
    public record AttemptView(String id,String clientRequestId,int number,String answer,String parentAttemptId,Instant createdAt,EvaluationView evaluation,List<EvaluationView> evaluations,Comparison comparison) {}
    public record SessionView(String id,String projectId,String template,String prompt,String status,String originAttemptId,Snapshot snapshot,Instant createdAt,List<AttemptView> attempts) {}
    public static final List<Template> TEMPLATES=List.of(
        new Template("INTRO","两分钟介绍","background","请用两分钟介绍项目背景、你个人的工作、关键方案和有依据的结果。",120),
        new Template("OWNERSHIP","个人职责","contribution","请区分团队工作和你个人实现、决策及协作的部分。",90),
        new Template("CHOICE","技术选型","approach","请选择材料中一个技术方案，说明选择依据、备选方案和适用边界。",120),
        new Template("DEBUGGING","困难定位","challenge","请围绕一项实际难点说明现象、定位过程、你的处理和验证。",120),
        new Template("IMPACT","效果证明","result","请说明项目取得的结果、测量口径及证据；没有数据时明确哪些还不能证明。",90),
        new Template("REFLECTION","复盘改进","improvements","请分别说明已完成的方案和仍未完成的改进，解释可以如何验证改进效果。",90));
}
