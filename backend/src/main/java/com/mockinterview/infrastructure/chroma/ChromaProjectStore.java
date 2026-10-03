package com.mockinterview.infrastructure.chroma;
import com.fasterxml.jackson.databind.JsonNode;
import com.mockinterview.capability.knowledge.*;
import com.mockinterview.config.KnowledgeProperties;
import com.mockinterview.service.project.ProjectDtos.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Separate private collection per project; server-side filters also bind project and immutable revision. */
@Component
public class ChromaProjectStore {
    private final RestClient http;private final KnowledgeProperties properties;
    private final Map<String,String> collections=new ConcurrentHashMap<>();
    public ChromaProjectStore(@Value("${chroma.base-url:http://localhost:8000}") String baseUrl,KnowledgeProperties properties){this.properties=properties;
        var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(properties.getConnectTimeoutMs());factory.setReadTimeout(properties.getReadTimeoutMs());
        http=RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();}
    private String base(){return "/api/v2/tenants/"+properties.getTenant()+"/databases/"+properties.getDatabase()+"/collections";}
    public static String name(String project){if(project==null||!project.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("项目标识格式不正确");return "private_project_"+project.replace('-','_');}
    private synchronized String collection(String project){return collections.computeIfAbsent(project,key->{var node=http.post().uri(base()).body(Map.of("name",name(key),"get_or_create",true,
        "configuration",Map.of("hnsw",Map.of("space","cosine")),"metadata",Map.of("project_id",key,"embedding_model",KnowledgeEmbeddingService.MODEL_ID))).retrieve().body(JsonNode.class);
        if(node==null||!key.equals(node.path("metadata").path("project_id").asText())||!KnowledgeEmbeddingService.MODEL_ID.equals(node.path("metadata").path("embedding_model").asText())
            ||node.path("id").asText().isBlank()||node.path("dimension").isNumber()&&node.path("dimension").asInt()!=512)throw new IllegalStateException("项目集合配置不匹配");return node.path("id").asText();});}
    private JsonNode post(String project,String action,Object body){try{var result=http.post().uri(base()+"/"+collection(project)+"/"+action).body(body).retrieve().body(JsonNode.class);
        return result==null?com.fasterxml.jackson.databind.node.MissingNode.getInstance():result;
        }catch(RuntimeException e){collections.remove(project);throw new KnowledgeUnavailableException("项目私有检索不可用",e);}}
    public void replace(Snapshot snapshot,List<KnowledgeChunk> chunks,List<List<Float>> vectors){if(chunks.size()!=vectors.size())throw new IllegalArgumentException("项目向量数量不一致");
        if(!chunks.isEmpty())post(snapshot.projectId(),"upsert",Map.of("ids",chunks.stream().map(KnowledgeChunk::id).toList(),"documents",chunks.stream().map(KnowledgeChunk::content).toList(),"embeddings",vectors,
            "metadatas",chunks.stream().map(c->Map.of("project_id",snapshot.projectId(),"project_revision",snapshot.revision(),"source_id",c.documentId(),"source_version",Integer.parseInt(c.revision()),"source_kind",c.documentId().startsWith("fact:")?"fact":"material")).toList()));
        post(snapshot.projectId(),"delete",Map.of("where",Map.of("project_revision",Map.of("$lt",snapshot.revision()))));}
    public List<Source> search(Snapshot snapshot,List<Float> vector){var response=post(snapshot.projectId(),"query",Map.of("query_embeddings",List.of(vector),"n_results",6,
        "where",Map.of("$and",List.of(Map.of("project_id",Map.of("$eq",snapshot.projectId())),Map.of("project_revision",Map.of("$eq",snapshot.revision())),Map.of("source_kind",Map.of("$eq","material")))),"include",List.of("documents","metadatas")));
        var docs=response.path("documents").path(0);var meta=response.path("metadatas").path(0);if(!docs.isArray()||!meta.isArray()||docs.size()!=meta.size())throw new KnowledgeUnavailableException("项目检索响应不完整");
        var result=new ArrayList<Source>();for(int i=0;i<docs.size();i++){var m=meta.get(i);String content=docs.get(i).asText();
            if(!snapshot.projectId().equals(m.path("project_id").asText())||snapshot.revision()!=m.path("project_revision").asInt(-1))throw new KnowledgeUnavailableException("项目检索范围校验失败");
            var original=snapshot.sources().stream().filter(s->s.id().equals(m.path("source_id").asText())&&s.version()==m.path("source_version").asInt(-1)&&!content.isBlank()&&s.content().contains(content)).findFirst()
                .orElseThrow(()->new KnowledgeUnavailableException("项目检索来源校验失败"));
            result.add(new Source(original.id(),original.version(),original.title(),original.origin(),content,original.planned()));}
        return List.copyOf(result);}
    public void delete(String project){http.delete().uri(base()+"/"+name(project)).retrieve().toBodilessEntity();collections.remove(project);}
}
