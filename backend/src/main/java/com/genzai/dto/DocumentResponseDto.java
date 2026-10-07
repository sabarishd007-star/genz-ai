package com.genzai.dto;

import java.time.LocalDateTime;

public record DocumentResponseDto(
    Long id,
    String fileName,
    String subject,
    String status,
    Long fileSize,
    int chunkCount,
    LocalDateTime createdAt
) {}
