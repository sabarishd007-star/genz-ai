package com.genzai.study.repository;

import com.genzai.study.model.PomodoroSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PomodoroSessionRepository extends JpaRepository<PomodoroSession, UUID> {

    Optional<PomodoroSession> findByIdAndUserId(UUID id, UUID userId);
}
