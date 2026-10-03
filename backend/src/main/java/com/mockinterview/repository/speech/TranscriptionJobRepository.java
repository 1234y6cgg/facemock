package com.mockinterview.repository.speech;
import com.mockinterview.domain.speech.TranscriptionJob;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface TranscriptionJobRepository extends JpaRepository<TranscriptionJob,String> {
    List<TranscriptionJob> findByRecording_IdOrderByGeneration(String recordingId);
    List<TranscriptionJob> findTop3ByStatusOrderByCreatedAtAsc(String status);
    List<TranscriptionJob> findByStatus(String status);
    Optional<TranscriptionJob> findByRecording_IdAndRequestKey(String recordingId,String key);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select j from TranscriptionJob j where j.id=:id") Optional<TranscriptionJob> lock(@Param("id") String id);
}
