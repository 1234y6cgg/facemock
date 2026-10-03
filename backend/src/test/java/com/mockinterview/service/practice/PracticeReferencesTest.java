package com.mockinterview.service.practice;
import com.mockinterview.capability.evaluation.EvaluationFixtures;
import com.mockinterview.capability.knowledge.*;
import com.mockinterview.domain.question.*;
import com.mockinterview.service.question.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PracticeReferencesTest {
    @Test void onlyImmutableBoundDocumentContentContributesRagEvidence() {
        var json=new QuestionJson();
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var loader=new QuestionCatalogLoader(json,new QuestionCatalogValidator(factory.getValidator()));
            var snapshot=EvaluationFixtures.snapshot(loader.loadBuiltin(),"redis.lua-stock",json);
            var source=snapshot.sources().get(0);
            var knowledge=mock(TechKnowledgeService.class);
            var valid=new KnowledgeHit("h1",source.documentId(),source.title(),source.source(),source.sourceUrl(),source.content(),.8);
            var unbound=new KnowledgeHit("h2","other.doc","其他题","其他","https://example.com","其他答案",.99);
            var overwritten=new KnowledgeHit("h3",source.documentId(),source.title(),source.source(),source.sourceUrl(),"覆盖后的伪造内容",.98);
            when(knowledge.search(anyString(),eq(5))).thenReturn(List.of(valid,unbound,overwritten));
            var result=new PracticeReferences(knowledge).resolve(snapshot,"a".repeat(10000));
            assertEquals("AVAILABLE",result.state()); assertEquals(List.of(valid),result.hits());
            assertEquals(Set.of(source.sourceId()),result.availableSourceIds());
            verify(knowledge).search(argThat(s->s.length()==500 && s.startsWith(snapshot.question().title()+" "+snapshot.question().prompt())),eq(5));
        }
    }
    @Test void emptyMatchesAndUnavailableSearchKeepAuditedFallbackExplicit() {
        var json=new QuestionJson();
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var loader=new QuestionCatalogLoader(json,new QuestionCatalogValidator(factory.getValidator()));
            var snapshot=EvaluationFixtures.snapshot(loader.loadBuiltin(),"redis.lua-stock",json);
            var knowledge=mock(TechKnowledgeService.class);
            when(knowledge.search(anyString(),anyInt())).thenReturn(List.of());
            assertEquals("NO_MATCH",new PracticeReferences(knowledge).resolve(snapshot,"回答").state());
            var noSources=new QuestionSnapshot(snapshot.question(),snapshot.knowledgePoints(),List.of());
            var insufficient=new PracticeReferences(knowledge).resolve(noSources,"回答");
            assertEquals("NO_MATCH",insufficient.state()); assertTrue(insufficient.sources().isEmpty());
            assertTrue(insufficient.notice().contains("缺少可核验依据")); assertFalse(insufficient.notice().contains("已使用"));
            when(knowledge.search(anyString(),anyInt())).thenThrow(new KnowledgeUnavailableException("outage"));
            var result=new PracticeReferences(knowledge).resolve(snapshot,"回答");
            assertEquals("UNAVAILABLE",result.state()); assertFalse(result.sources().isEmpty()); assertTrue(result.notice().contains("有限评估"));
        }
    }
}
