package com.genzai.rag.dto;

public record DocumentChunkResponse(
        int chunkIndex,
        String content,
        int tokenCount
) {}
