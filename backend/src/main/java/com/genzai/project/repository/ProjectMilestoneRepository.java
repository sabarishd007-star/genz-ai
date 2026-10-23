package com.genzai.project.repository;

import com.genzai.project.model.ProjectMilestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectMilestoneRepository extends JpaRepository<ProjectMilestone, UUID> {
    List<ProjectMilestone> findByProjectIdAndUserIdOrderByPositionAsc(UUID projectId, UUID userId);
    Optional<ProjectMilestone> findByIdAndUserId(UUID id, UUID userId);
    long countByUserIdAndStatus(UUID userId, String status);
    long countByUserId(UUID userId);
}
