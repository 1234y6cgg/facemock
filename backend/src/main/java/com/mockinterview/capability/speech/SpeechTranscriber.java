package com.mockinterview.capability.speech;
public interface SpeechTranscriber {
    String transcribe(byte[] wav);
    String provider();
}
