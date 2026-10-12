package com.genzai.aptitude.repository;

import com.genzai.aptitude.entity.AptitudeAttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AptitudeAttemptAnswerRepository extends JpaRepository<AptitudeAttemptAnswer, UUID> {

    List<AptitudeAttemptAnswer> findByAttemptId(UUID attemptId);

    Optional<AptitudeAttemptAnswer> findByAttemptIdAndQuestionId(UUID attemptId, UUID questionId);

    long countByUserId(UUID userId);

    @Query("SELECT q.topic, q.track, COUNT(ans), SUM(CASE WHEN ans.isCorrect = true THEN 1L ELSE 0L END) " +
           "FROM AptitudeAttemptAnswer ans, AptitudeQuestion q " +
           "WHERE ans.questionId = q.id AND ans.userId = :userId " +
           "GROUP BY q.topic, q.track")
    List<Object[]> getTopicPerformanceByUser(@Param("userId") UUID userId);
}
