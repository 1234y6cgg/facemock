package com.mockinterview.service.project;
import com.mockinterview.config.DeepSeekProperties;
import com.mockinterview.infrastructure.deepseek.DeepSeekProjectAssessmentModel;
import com.mockinterview.service.question.QuestionJson;
import com.mockinterview.service.project.ProjectDtos.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic project only; no user's resume, credentials or private material is written into reports. */
@EnabledIfEnvironmentVariable(named="P4_MODEL_TEST",matches="true")
class ProjectModelIntegrationTest {
    record Case(String id,String question,String answer,String expectation,Predicate<Assessment> check) {}
    private String status(Assessment a,String id){return a.criteria().stream().filter(c->c.id().equals(id)).findFirst().orElseThrow().status();}
    @Test void checksSyntheticFactBoundariesWithActualModel() throws Exception {
        var json=new QuestionJson();var parser=new ProjectAssessmentParser(json);var properties=new DeepSeekProperties();properties.setApiKey(System.getenv("P4_MODEL_KEY"));
        properties.setModelName(System.getenv().getOrDefault("P4_MODEL_NAME","deepseek-flash"));properties.setBaseUrl(System.getenv().getOrDefault("P4_MODEL_BASE_URL","https://api.deepseek.com"));
        assertTrue(properties.getApiKey()!=null&&!properties.getApiKey().isBlank(),"Set the test model key locally");var model=new DeepSeekProjectAssessmentModel(properties,json);
        var snapshot=new Snapshot(UUID.randomUUID().toString(),"订单课程项目 A",2,new Facts("课程订单项目，用于学习库存扣减","我只负责库存模块，订单和支付由其他同事负责","Redis Lua 检查库存并扣减","","只做单机功能验证，未压测","","计划引入 Kafka，尚未实施",true),
            List.of(new Source("fact:background",2,"背景","本人事实卡","课程订单项目，用于学习库存扣减",false),
            new Source("fact:contribution",2,"个人职责","本人事实卡","我只负责库存模块，订单和支付由其他同事负责",false),
            new Source("fact:approach",2,"方案","本人事实卡","Redis Lua 检查库存并扣减",false),
            new Source("fact:result",2,"结果","本人事实卡","只做单机功能验证，未压测",false),
            new Source("fact:improvements",2,"未完成改进","本人事实卡","计划引入 Kafka，尚未实施",true),
            new Source("test-material",1,"验证记录","虚构测试记录","旧材料：只在本机功能验证，无压测数据。",false)));
        var cases=List.of(
            new Case("clear-ownership","请说明个人职责","我只负责库存模块，订单和支付由其他同事负责。我使用 Redis Lua 检查库存并扣减。","个人贡献清楚，材料一致性不能判为冲突",a->status(a,"contribution").equals("COVERED")&&!status(a,"consistency").equals("CONFLICT")),
            new Case("contradictory-ownership","请说明个人职责","整个系统都是我独立完成的，订单和支付模块也全部由我个人实现。","必须展示个人职责矛盾的双方原文",a->status(a,"consistency").equals("CONFLICT")),
            new Case("unsupported-metrics","请说明效果证明与证据","项目日单量一百万，已经达到 QPS 十万，延迟降低百分之八十。","没有材料支持的规模和指标不能认可为已证实",a->!status(a,"evidence").equals("COVERED")),
            new Case("plan-as-completed","请说明已完成的技术方案","我已经引入 Kafka 异步处理库存扣减，已经完成上线。","未完成改进不能证明已完成的 Kafka 方案，也不能拿计划认定事实矛盾",a->!status(a,"evidence").equals("COVERED")&&!status(a,"consistency").equals("CONFLICT")),
            new Case("bounded-improvement","请区分已完成方案和未完成改进","当前已完成的是 Redis Lua 库存校验。以后可以考虑 Kafka，我还没有实施，需要验证边界和收益。","明确区分方案与计划，不能判为材料冲突",a->!status(a,"consistency").equals("CONFLICT")),
            new Case("missing-measurement","请说明效果证明与测量依据","我优化了性能。","应请求补测量或结果证据，不能补数字",a->!status(a,"evidence").equals("COVERED")&&!a.missingFields().isEmpty()),
            new Case("complete-introduction","请用两分钟介绍项目背景、你个人的工作、关键方案和有依据的结果。","这是课程订单项目，用于学习库存扣减。我只负责库存模块，订单和支付由其他同事负责。我使用 Redis Lua 检查库存并扣减。当前只做单机功能验证，未压测，没有可靠的吞吐量和性能提升数据。计划引入 Kafka，但尚未实施。","完整介绍保留事实边界，引用不得超过四条且须对应各材料版本",a->status(a,"contribution").equals("COVERED")&&!status(a,"consistency").equals("CONFLICT")));
        var rows=new ArrayList<Map<String,Object>>();int passed=0;
        for(var test:cases){long start=System.nanoTime();String raw=null;var row=new LinkedHashMap<String,Object>();row.put("id",test.id());row.put("question",test.question());row.put("answer",test.answer());row.put("expectation",test.expectation());
            try{raw=model.assess(snapshot,test.question(),test.answer());var result=parser.parse(raw,snapshot,test.answer());boolean matches=test.check().test(result);row.put("validOutput",true);row.put("result",result);row.put("matchesExpectedBoundary",matches);if(matches)passed++;}
            catch(RuntimeException e){row.put("validOutput",false);row.put("error",e.getClass().getSimpleName());row.put("errorMessage",e.getMessage());row.put("matchesExpectedBoundary",false);if(raw!=null)row.put("rejectedSyntheticOutput",raw);}
            row.put("elapsedMs",(System.nanoTime()-start)/1_000_000);rows.add(row);}
        var report=new LinkedHashMap<String,Object>();report.put("verifiedAt",Instant.now().toString());report.put("syntheticProject",true);report.put("actualModel",true);report.put("model",model.modelName());report.put("promptVersion",ProjectAssessmentParser.VERSION);
        report.put("requestSettings",Map.of("thinking","enabled","reasoningEffort","low","maxTokens",16384,"responseFormat","json_object"));
        report.put("scope","Seven fixed synthetic fact-boundary cases; not general accuracy or independent project verification");report.put("snapshot",snapshot);report.put("cases",rows);report.put("passed",passed);report.put("total",cases.size());
        Files.writeString(Path.of("target/p4-model-evaluation.json"),json.write(report));assertEquals(cases.size(),passed,"See local synthetic-case report; no credentials are included");
    }
}
