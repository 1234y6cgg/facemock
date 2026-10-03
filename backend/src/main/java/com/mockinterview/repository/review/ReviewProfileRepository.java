package com.mockinterview.repository.review;
import com.mockinterview.domain.review.ReviewProfile;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface ReviewProfileRepository extends JpaRepository<ReviewProfile,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from ReviewProfile p where p.id=:id")
    Optional<ReviewProfile> lock(@Param("id") String id);
}
