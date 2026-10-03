package com.mockinterview.capability.knowledge;

public class KnowledgeUnavailableException extends RuntimeException {
    public KnowledgeUnavailableException(String message) {
        super(message);
    }

    public KnowledgeUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
