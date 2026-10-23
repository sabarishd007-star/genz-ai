package com.genzai.project.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "viva_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VivaQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "blueprint_id", nullable = false)
    private UUID blueprintId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(name = "model_answer", nullable = false, columnDefinition = "TEXT")
    private String modelAnswer;

    @Column(length = 100)
    private String topic;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
