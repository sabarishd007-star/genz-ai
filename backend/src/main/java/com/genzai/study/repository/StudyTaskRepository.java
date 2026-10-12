package com.genzai.study.repository;

import com.genzai.study.model.StudyTask;
import com.genzai.study.model.TaskStatus;
import com.genzai.study.model.TaskType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudyTaskRepository extends JpaRepository<StudyTask, UUID> {

    List<StudyTask> findByPlanIdOrderByTaskDateAsc(UUID planId);

    Optional<StudyTask> findByIdAndUserId(UUID id, UUID userId);

    List<StudyTask> findByPlanIdAndUserIdOrderByTaskDateAsc(UUID planId, UUID userId);

    List<StudyTask> findByPlanIdAndUserIdAndTaskDate(UUID planId, UUID userId, LocalDate taskDate);

    List<StudyTask> findByPlanIdAndUserIdAndTaskDateLessThanAndStatus(
            UUID planId, UUID userId, LocalDate date, TaskStatus status
    );

    @Query("SELECT COUNT(t) > 0 FROM StudyTask t WHERE t.planId = :planId AND t.userId = :userId AND t.topic = :topic AND t.type = :type AND t.taskDate BETWEEN :startDate AND :endDate")
    boolean existsAptitudeRevisionThisWeek(
            @Param("planId") UUID planId,
            @Param("userId") UUID userId,
            @Param("topic") String topic,
            @Param("type") TaskType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
