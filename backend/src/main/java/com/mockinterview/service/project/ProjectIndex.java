package com.mockinterview.service.project;
import com.mockinterview.capability.knowledge.*;
import com.mockinterview.infrastructure.chroma.ChromaProjectStore;
import com.mockinterview.service.question.QuestionJson;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.mockinterview.service.project.ProjectDtos.*;

@Service
public class ProjectIndex {
    private final ProjectStore store;private final ChromaProjectStore chroma;private final KnowledgeEmbeddingService embeddings;
    public ProjectIndex(ProjectStore store,ChromaProjectStore chroma,KnowledgeEmbeddingService embeddings){this.store=store;this.chroma=chroma;this.embeddings=embeddings;}
    public void sync(String id){if(store.archived(id)){int revision=storeRevision(id);try{chroma.delete(id);store.indexFinished(id,revision,true);}catch(RuntimeException e){store.indexFinished(id,revision,false);}return;}
        var snapshot=store.snapshot(id);try{var chunks=chunks(snapshot);chroma.replace(snapshot,chunks,embeddings.embedChunks(chunks));store.indexFinished(id,snapshot.revision(),true);}
        catch(RuntimeException e){store.indexFinished(id,snapshot.revision(),false);}}
    private int storeRevision(String id){return store.archivedRevision(id);}
    public static List<KnowledgeChunk> chunks(Snapshot snapshot){var result=new ArrayList<KnowledgeChunk>();for(var source:snapshot.sources()){
        int offset=0;while(offset<source.content().length()){int end=Math.min(offset+480,source.content().length());if(end<source.content().length()&&Character.isHighSurrogate(source.content().charAt(end-1)))end--;
            var part=source.content().substring(offset,end);var chunkId=snapshot.projectId()+":"+snapshot.revision()+":"+QuestionJson.sha256(source.id()).substring(0,12)+":"+offset;
            result.add(new KnowledgeChunk(chunkId,source.id(),String.valueOf(source.version()),source.title(),source.origin(),"","",part));if(end==source.content().length())break;offset=end-40;}
        }return List.copyOf(result);}
    public Snapshot context(String id,int expectedRevision,String query){var current=store.get(id);if(current.revision()!=expectedRevision)throw new com.mockinterview.service.practice.PracticeConflictException("材料已更新，请刷新后开始练习");
        if(!current.facts().confirmed())throw new com.mockinterview.service.practice.PracticeConflictException("请先核对并确认项目事实卡");
        if(!"READY".equals(current.indexStatus())||current.indexedRevision()!=current.revision())throw new KnowledgeUnavailableException("项目材料尚未完成索引，请等待或重试索引");
        var snapshot=store.snapshot(id);var hits=chroma.search(snapshot,embeddings.embedQuery(query));var sources=new ArrayList<Source>();
        snapshot.sources().stream().filter(s->s.id().startsWith("fact:")).forEach(sources::add);
        hits.stream().filter(s->!s.id().startsWith("fact:")).forEach(sources::add);
        if(sources.isEmpty())throw new KnowledgeUnavailableException("项目没有可供核对的材料，请先补充事实卡");
        return new Snapshot(snapshot.projectId(),snapshot.projectName(),snapshot.revision(),snapshot.facts(),List.copyOf(sources));}
}
