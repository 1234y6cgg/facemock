package com.mockinterview.service.project;
import com.mockinterview.infrastructure.chroma.ChromaProjectStore;
import com.mockinterview.config.KnowledgeProperties;
import com.mockinterview.service.project.ProjectDtos.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="P4_CHROMA_TEST_URL",matches=".+")
class ProjectChromaIntegrationTest {
    private final ChromaProjectStore store=new ChromaProjectStore(System.getenv("P4_CHROMA_TEST_URL"),new KnowledgeProperties());
    private final List<Float> vector=java.util.stream.IntStream.range(0,512).mapToObj(i->i==0?1f:0f).toList();
    private final List<String> ids=new ArrayList<>();
    private Snapshot snapshot(String id,int revision,String text){return new Snapshot(id,"示例",revision,new Facts("","","","","","","",true),List.of(new Source("material",revision,"说明","本人测试材料",text,false)));}
    private String id(){var id=UUID.randomUUID().toString();ids.add(id);return id;}
    private void write(Snapshot s){var chunks=ProjectIndex.chunks(s);store.replace(s,chunks,chunks.stream().map(c->vector).toList());}
    @AfterEach void clean(){for(var id:ids)store.delete(id);}
    @Test void identicalVectorsCannotBringOtherProjectFactsIntoResults(){var a=snapshot(id(),1,"A：只负责库存校验，无性能数据");var b=snapshot(id(),1,"B：负责物流系统，日单量一百万");write(a);write(b);
        assertEquals("A：只负责库存校验，无性能数据",store.search(a,vector).get(0).content());assertTrue(store.search(a,vector).stream().noneMatch(s->s.content().contains("一百万")));assertEquals(b.sources(),store.search(b,vector));}
    @Test void replacementAndMaterialDeletionPruneOldRevisions(){String id=id();var old=snapshot(id,1,"旧材料：未压测");write(old);var current=snapshot(id,2,"新材料：本机验证");write(current);
        assertTrue(store.search(old,vector).isEmpty());assertEquals(current.sources(),store.search(current,vector));
        var removed=new Snapshot(id,"示例",3,current.facts(),List.of());write(removed);assertTrue(store.search(current,vector).isEmpty());assertTrue(store.search(removed,vector).isEmpty());}
    @Test void factChunksCannotCrowdSupplementaryMaterialsOutOfRetrieval(){String id=id();var sources=new ArrayList<Source>();
        for(int i=0;i<7;i++)sources.add(new Source("fact:"+i,1,"事实","本人核对","事实卡"+i,false));
        var material=new Source("material",1,"验证记录","测试记录","补充材料中的验证口径",false);sources.add(material);
        var s=new Snapshot(id,"示例",1,new Facts("","","","","","","",true),sources);write(s);
        assertEquals(List.of(material),store.search(s,vector));}
}
