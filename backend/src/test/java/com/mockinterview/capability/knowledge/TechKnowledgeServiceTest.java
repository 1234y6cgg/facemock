package com.mockinterview.capability.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.config.KnowledgeProperties;
import com.mockinterview.infrastructure.chroma.ChromaKnowledgeStore;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TechKnowledgeServiceTest {
    private final KnowledgeProperties properties = new KnowledgeProperties();
    private final KnowledgeEmbeddingService embeddings = mock(KnowledgeEmbeddingService.class);
    private final ChromaKnowledgeStore store = mock(ChromaKnowledgeStore.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final ValidatorFactory validators = Validation.buildDefaultValidatorFactory();
    private final TechKnowledgeService knowledge = new TechKnowledgeService(properties,
            new KnowledgeChunker(properties, mapper), embeddings, store, mapper, validators.getValidator());

    @AfterEach
    void close() { validators.close(); }

    @Test
    void failedUpsertNeverDeletesPreviousRevision() {
        var doc = new KnowledgeDocument("mine", "Redis", "原子扣减库存", "笔记", "", List.of("Redis"));
        when(embeddings.embedChunks(anyList())).thenReturn(List.of(List.of(0.1f)));
        doThrow(new IllegalStateException("connection failed")).when(store).upsert(anyList(), anyList());
        assertThrows(KnowledgeUnavailableException.class, () -> knowledge.upsert(doc));
        verify(store, never()).removeOldRevisions(anyString(), anyString());
    }

    @Test
    void disabledKnowledgeDoesNotCallChromaOrLoadModel() {
        properties.setEnabled(false);
        assertEquals("", knowledge.retrieve("Redis", 3));
        assertFalse(knowledge.status().available());
        assertThrows(KnowledgeUnavailableException.class, () -> knowledge.search("Redis", 3));
        verifyNoInteractions(store, embeddings);
    }

    @Test
    void explicitSearchReportsFailureWhileInterviewDegrades() {
        properties.setSeedOnStartup(false);
        when(embeddings.embedQuery(anyString())).thenThrow(new IllegalStateException("native model unavailable"));
        assertThrows(KnowledgeUnavailableException.class, () -> knowledge.search("Redis", 3));
        assertEquals("", knowledge.retrieve("Redis", 3));
    }

    @Test
    void rejectsInvalidQueriesBeforeInference() {
        assertThrows(IllegalArgumentException.class, () -> knowledge.search(" ", 3));
        assertThrows(IllegalArgumentException.class, () -> knowledge.search("问".repeat(501), 3));
        assertThrows(IllegalArgumentException.class, () -> knowledge.search("Redis", 0));
        assertThrows(IllegalArgumentException.class, () -> knowledge.search("Redis", 11));
        verifyNoInteractions(store, embeddings);
    }

    @Test
    void failedStartupSeedCanBeRetriedOnSearch() {
        doThrow(new IllegalStateException("not ready")).doNothing().when(store).upsert(anyList(), anyList());
        when(embeddings.embedChunks(anyList())).thenReturn(List.of(List.of(0.1f)));
        when(embeddings.embedQuery(anyString())).thenReturn(List.of(0.1f));
        when(store.query(anyList(), anyInt(), anyDouble())).thenReturn(List.of());
        assertThrows(KnowledgeUnavailableException.class, knowledge::seedBuiltin);
        assertTrue(knowledge.search("Redis", 3).isEmpty());
        int callsAfterSeed = mockingDetails(store).getInvocations().size();
        knowledge.search("Redis", 3);
        assertEquals(callsAfterSeed + 1, mockingDetails(store).getInvocations().size(), "成功后只查询，不重复导入");
    }
}
