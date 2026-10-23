package com.genzai.project.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "viva_answers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VivaAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "answer_text", nullable = false, columnDefinition = "TEXT")
    private String answerText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> scores; // { "correctness": 8, "completeness": 8, "clarity": 7, "overall": 7.7 }

    @Column(nullable = false, columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "judged_by", nullable = false, length = 50)
    private String judgedBy; // "LLM" or "HEURISTIC"

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
