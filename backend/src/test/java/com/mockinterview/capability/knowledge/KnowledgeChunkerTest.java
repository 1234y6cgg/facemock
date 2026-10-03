package com.mockinterview.capability.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.config.KnowledgeProperties;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class KnowledgeChunkerTest {
    private final KnowledgeProperties properties = new KnowledgeProperties();
    private final KnowledgeChunker chunker = new KnowledgeChunker(properties, new ObjectMapper());

    @Test
    void normalizedInputHasStableIdsAndSourceChangesCreateNewRevision() {
        var doc = document("库存\r\nRedis Lua", "官方文档");
        var first = chunker.split(doc);
        assertEquals(first, chunker.split(document("库存\nRedis Lua", "官方文档")));
        assertNotEquals(first.get(0).revision(), chunker.split(document(doc.content(), "我的笔记")).get(0).revision());
        assertEquals("note", first.get(0).documentId());
    }

    @Test
    void overlappingChunksPreserveContentAndUnicodeBoundaries() {
        String text = java.util.stream.IntStream.range(0, 150)
                .mapToObj(i -> "记录" + i + "库存🔒订单并发处理。").collect(java.util.stream.Collectors.joining());
        var chunks = chunker.split(document(text, "笔记"));
        assertTrue(chunks.size() > 1);
        int covered = 0;
        for (var chunk : chunks) {
            assertTrue(chunk.content().length() <= properties.getChunkSize());
            assertFalse(Character.isLowSurrogate(chunk.content().charAt(0)));
            assertFalse(Character.isHighSurrogate(chunk.content().charAt(chunk.content().length() - 1)));
            int start = text.indexOf(chunk.content(), Math.max(0, covered - properties.getChunkOverlap() - 2));
            assertTrue(start >= 0 && start <= covered);
            covered = Math.max(covered, start + chunk.content().length());
        }
        assertEquals(text.length(), covered);
        assertTrue(text.endsWith(chunks.get(chunks.size() - 1).content()));
    }

    @Test
    void rejectsBlankContentAndInvalidOverlap() {
        assertThrows(IllegalArgumentException.class, () -> chunker.split(document(" \n ", "笔记")));
        properties.setChunkOverlap(properties.getChunkSize());
        assertThrows(IllegalArgumentException.class, () -> new KnowledgeChunker(properties, new ObjectMapper()));
    }

    private KnowledgeDocument document(String content, String source) {
        return new KnowledgeDocument("note", "库存", content, source, "https://example.com/note", List.of("Redis"));
    }
}
