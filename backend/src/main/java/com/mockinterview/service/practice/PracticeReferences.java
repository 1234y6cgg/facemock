package com.mockinterview.service.practice;
import com.mockinterview.capability.knowledge.*;
import com.mockinterview.domain.question.QuestionSnapshot;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.*;

@Component
public class PracticeReferences {
    private final TechKnowledgeService knowledge;
    public PracticeReferences(TechKnowledgeService knowledge) { this.knowledge=knowledge; }
    public Context resolve(QuestionSnapshot snapshot,String answer) {
        var sources=snapshot.sources().stream().filter(s->s.verifiedOn()!=null
            && !s.verifiedOn().isAfter(LocalDate.now()) && s.content()!=null && !s.content().isBlank()).toList();
        try {
            // Titles disambiguate prompts such as "参数值和动态结构" across a larger catalog.
            var query=snapshot.question().title()+" "+snapshot.question().prompt()+" "+answer;
            var hits=knowledge.search(query.substring(0,Math.min(500,query.length())),5);
            // Only matching immutable question sources can contribute evaluation evidence.
            var bound=hits.stream().filter(h->sources.stream().anyMatch(s->s.documentId().equals(h.documentId())
                && s.content().contains(h.content()) && s.sourceUrl().equals(h.sourceUrl()))).toList();
            var notice=sources.isEmpty()?"缺少可核验依据，相关要点将标为无法判断。":
                bound.isEmpty()?"未命中本题的相关检索片段，已使用题目核对资料进行有限评估。":"已结合本题核对资料和相关检索片段。";
            return new Context(bound.isEmpty()?"NO_MATCH":"AVAILABLE",
                notice,sources,bound);
        } catch(KnowledgeUnavailableException e) {
            return new Context("UNAVAILABLE",sources.isEmpty()?"知识检索不可用，缺少可核验依据，相关要点将标为无法判断。":
                "知识检索不可用，已使用题目核对资料进行有限评估。",sources,List.of());
        }
    }
    public record Context(String state,String notice,List<QuestionSnapshot.SourceSnapshot> sources,List<KnowledgeHit> hits) {
        public Set<String> availableSourceIds() { return sources.stream().map(QuestionSnapshot.SourceSnapshot::sourceId).collect(java.util.stream.Collectors.toSet()); }
    }
}
