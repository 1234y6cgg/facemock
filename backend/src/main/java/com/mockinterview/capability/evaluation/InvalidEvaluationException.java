package com.mockinterview.capability.evaluation;

public class InvalidEvaluationException extends RuntimeException {
    public InvalidEvaluationException(String message) { super(message); }
    public InvalidEvaluationException(String message, Throwable cause) { super(message, cause); }
}
