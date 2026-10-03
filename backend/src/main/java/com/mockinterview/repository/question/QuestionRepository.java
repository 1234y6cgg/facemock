package com.mockinterview.repository.question;

import com.mockinterview.domain.question.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<QuestionEntity, String> {
    @EntityGraph(attributePaths = "currentRevision")
    @Query("select q from QuestionEntity q where q.active = true and q.currentRevision is not null "
            + "and (:topic is null or q.topic = :topic) and (:difficulty is null or q.difficulty = :difficulty) "
            + "and (:keyword = '' or locate(:keyword, lower(q.title)) > 0)")
    Page<QuestionEntity> browse(@Param("topic") QuestionTopic topic, @Param("difficulty") Integer difficulty,
                                @Param("keyword") String keyword, Pageable pageable);

    interface TopicCount { QuestionTopic getTopic(); long getCount(); }
    @Query("select q.topic as topic, count(q) as count from QuestionEntity q "
            + "where q.active = true and q.currentRevision is not null group by q.topic")
    java.util.List<TopicCount> topicCounts();
}
