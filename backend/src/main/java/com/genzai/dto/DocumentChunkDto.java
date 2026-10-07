package com.genzai.dto;

public record DocumentChunkDto(
    Long id,
    int chunkIndex,
    String content,
    int tokenCount
) {}
