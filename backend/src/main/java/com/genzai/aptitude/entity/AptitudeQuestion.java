package com.genzai.aptitude.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "aptitude_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AptitudeQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "content_hash", nullable = false, unique = true, length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AptitudeTrack track;

    @Column(nullable = false, length = 64)
    private String topic;

    @Column(nullable = false, length = 16)
    private String difficulty;

    @Column(name = "difficulty_approximate", nullable = false)
    @Builder.Default
    private Boolean difficultyApproximate = false;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(name = "options_json", nullable = false, columnDefinition = "TEXT")
    private String optionsJson;

    @Column(name = "correct_answer", nullable = false, length = 1)
    private String correctAnswer;

    @Column(columnDefinition = "TEXT")
    private String rationale;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "flagged_for_review", nullable = false)
    @Builder.Default
    private Boolean flaggedForReview = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
