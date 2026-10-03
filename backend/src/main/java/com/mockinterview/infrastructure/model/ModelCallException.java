package com.mockinterview.infrastructure.model;
/** Safe messages only: no vendor response bodies or credential-bearing requests. */
public class ModelCallException extends IllegalStateException {
    public ModelCallException(String message){super(message);}
}
