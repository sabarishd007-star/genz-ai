package com.genzai.rag.dto;

import java.util.List;

public record ChatRequest(String query, List<String> documentIds) {}
