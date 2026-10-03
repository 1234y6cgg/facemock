package com.mockinterview.capability.knowledge;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;

import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeEmbeddingService {
    public static final String MODEL_ID = "bge-small-zh-v1.5-q:512:v1";
    public static final int DIMENSIONS = 512;
    public static final String QUERY_PREFIX = "为这个句子生成表示以用于检索相关文章：";
    private final EmbeddingModel model;

    public KnowledgeEmbeddingService(@Lazy @Qualifier("knowledgeEmbeddingModel") EmbeddingModel model) {
        this.model = model;
    }

    public List<Float> embedQuery(String query) {
        return checked(model.embed(QUERY_PREFIX + query).content().vectorAsList());
    }

    public List<List<Float>> embedChunks(List<KnowledgeChunk> chunks) {
        List<List<Float>> vectors = new ArrayList<>();
        // Keep both native inference and temporary allocations bounded during imports.
        for (int offset = 0; offset < chunks.size(); offset += 16) {
            List<TextSegment> segments = chunks.subList(offset, Math.min(offset + 16, chunks.size()))
                    .stream().map(c -> TextSegment.from(c.embeddingText())).toList();
            var response = model.embedAll(segments).content();
            if (response.size() != segments.size()) throw new IllegalStateException("向量模型返回数量不匹配");
            response.forEach(e -> vectors.add(checked(e.vectorAsList())));
        }
        return vectors;
    }

    private List<Float> checked(List<Float> vector) {
        if (vector.size() != DIMENSIONS || vector.stream().anyMatch(v -> v == null || !Float.isFinite(v))
                || vector.stream().allMatch(v -> v == 0)) {
            throw new IllegalStateException("向量模型返回了无效的向量");
        }
        return vector;
    }
}
