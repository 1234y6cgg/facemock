package com.mockinterview.repository.speech;
import com.mockinterview.domain.speech.SpeechRecording;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
public interface SpeechRecordingRepository extends JpaRepository<SpeechRecording,String> {
    Optional<SpeechRecording> findBySessionIdAndClientRequestId(String sessionId,String requestId);
    Optional<SpeechRecording> findBySessionIdAndConfirmationKey(String sessionId,String key);
    List<SpeechRecording> findBySessionIdOrderByCreatedAtAsc(String sessionId);
    List<SpeechRecording> findTop50ByStateAndExpiresAtBefore(String state,Instant time);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from SpeechRecording r where r.id=:id") Optional<SpeechRecording> lock(@Param("id") String id);
}
