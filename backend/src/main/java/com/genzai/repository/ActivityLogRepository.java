package com.genzai.repository;

import com.genzai.model.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {

    List<ActivityLog> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT DISTINCT a.createdAt FROM ActivityLog a WHERE a.userId = :userId ORDER BY a.createdAt DESC")
    List<Instant> findActivityTimestampsByUserId(@Param("userId") UUID userId);

    long countByUserId(UUID userId);
}
