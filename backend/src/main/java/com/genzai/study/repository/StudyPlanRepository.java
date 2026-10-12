package com.genzai.study.repository;

import com.genzai.study.model.PlanStatus;
import com.genzai.study.model.StudyPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudyPlanRepository extends JpaRepository<StudyPlan, UUID> {

    List<StudyPlan> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<StudyPlan> findByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT COUNT(p) FROM StudyPlan p WHERE p.userId = :userId AND p.status = :status AND p.examDate >= :currentDate")
    long countActivePlansForDashboard(
            @Param("userId") UUID userId,
            @Param("status") PlanStatus status,
            @Param("currentDate") LocalDate currentDate
    );
}
