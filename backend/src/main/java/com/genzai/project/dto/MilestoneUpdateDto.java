package com.genzai.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneUpdateDto {

    @NotBlank(message = "Status cannot be blank")
    @Pattern(regexp = "TODO|DOING|DONE", message = "Status must be TODO, DOING, or DONE")
    private String status;
}
