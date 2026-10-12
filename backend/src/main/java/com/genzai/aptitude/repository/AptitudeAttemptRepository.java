package com.genzai.aptitude.repository;

import com.genzai.aptitude.entity.AptitudeAttempt;
import com.genzai.aptitude.entity.AttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AptitudeAttemptRepository extends JpaRepository<AptitudeAttempt, UUID> {

    List<AptitudeAttempt> findByUserIdOrderByStartedAtDesc(UUID userId);

    Optional<AptitudeAttempt> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndStatus(UUID userId, AttemptStatus status);

    @Query("SELECT a.track, COUNT(a), AVG(a.scorePct) FROM AptitudeAttempt a WHERE a.userId = :userId AND a.status = :status GROUP BY a.track")
    List<Object[]> getStatsByTrack(@Param("userId") UUID userId, @Param("status") AttemptStatus status);
}
