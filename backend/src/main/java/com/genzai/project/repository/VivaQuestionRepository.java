package com.genzai.project.repository;

import com.genzai.project.model.VivaQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VivaQuestionRepository extends JpaRepository<VivaQuestion, UUID> {
    List<VivaQuestion> findByBlueprintIdAndUserId(UUID blueprintId, UUID userId);
    Optional<VivaQuestion> findByIdAndUserId(UUID id, UUID userId);
}
