package com.genzai.aptitude.repository;

import com.genzai.aptitude.entity.AptitudeQuestion;
import com.genzai.aptitude.entity.AptitudeTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AptitudeQuestionRepository extends JpaRepository<AptitudeQuestion, UUID> {

    boolean existsByContentHash(String contentHash);

    long countByTrack(AptitudeTrack track);

    @Query("SELECT DISTINCT q.topic FROM AptitudeQuestion q WHERE q.track = :track ORDER BY q.topic ASC")
    List<String> findDistinctTopicsByTrack(@Param("track") AptitudeTrack track);

    @Query(value = "SELECT * FROM aptitude_questions WHERE track = :track ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<AptitudeQuestion> findRandomByTrack(@Param("track") String track, @Param("limit") int limit);

    @Query(value = "SELECT * FROM aptitude_questions WHERE track = :track AND topic = :topic ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<AptitudeQuestion> findRandomByTrackAndTopic(@Param("track") String track, @Param("topic") String topic, @Param("limit") int limit);
}
