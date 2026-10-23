package com.genzai.project.repository;

import com.genzai.project.model.VivaAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VivaAnswerRepository extends JpaRepository<VivaAnswer, UUID> {
    List<VivaAnswer> findByQuestionIdAndUserId(UUID questionId, UUID userId);
    List<VivaAnswer> findByUserId(UUID userId);
    Optional<VivaAnswer> findByIdAndUserId(UUID id, UUID userId);
}
