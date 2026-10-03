package com.mockinterview.repository.practice;
import com.mockinterview.domain.practice.PracticeSession;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.util.Collection;
public interface PracticeSessionRepository extends JpaRepository<PracticeSession,String> {
    Optional<PracticeSession> findByClientRequestId(String requestId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from PracticeSession s where s.id=:id")
    Optional<PracticeSession> lock(@Param("id") String id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PracticeSession s where s.originAttemptId in :attemptIds order by s.id")
    List<PracticeSession> lockChildren(@Param("attemptIds") Collection<String> attemptIds);
    Page<PracticeSession> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
