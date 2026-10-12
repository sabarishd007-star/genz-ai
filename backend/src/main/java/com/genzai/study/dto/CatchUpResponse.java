package com.genzai.study.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatchUpResponse {
    private int redistributedTasksCount;
    private String summary;
    private List<String> warnings;
}
