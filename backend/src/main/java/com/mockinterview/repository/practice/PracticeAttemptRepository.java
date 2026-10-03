package com.mockinterview.repository.practice;
import com.mockinterview.domain.practice.PracticeAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface PracticeAttemptRepository extends JpaRepository<PracticeAttempt,String> {
    List<PracticeAttempt> findBySession_IdOrderByAttemptNumber(String id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PracticeAttempt a where a.session.id=:sessionId order by a.attemptNumber")
    List<PracticeAttempt> lockBySession(@Param("sessionId") String sessionId);
    Optional<PracticeAttempt> findBySession_IdAndClientRequestId(String id,String requestId);
}
