package com.genzai.project.repository;

import com.genzai.project.model.ProjectBlueprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectBlueprintRepository extends JpaRepository<ProjectBlueprint, UUID> {
    Optional<ProjectBlueprint> findTopByProjectIdAndUserIdOrderByVersionDesc(UUID projectId, UUID userId);
    Optional<ProjectBlueprint> findByProjectIdAndVersionAndUserId(UUID projectId, Integer version, UUID userId);
    List<ProjectBlueprint> findByProjectIdAndUserIdOrderByVersionDesc(UUID projectId, UUID userId);
}
