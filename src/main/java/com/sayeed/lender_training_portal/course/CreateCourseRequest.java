package com.sayeed.lender_training_portal.course;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCourseRequest(
    @NotBlank @Size(max=200) String title,
    @Size(max = 4000) String description,
    @Size(max=50) List<@Valid ModuleRequest> modules
) {
    
    public record ModuleRequest(
        @NotBlank @Size(max=200) String title,
        @Size(max = 4000) String content,
        @Min(1) @Max(600) int durationMinutes
    ) {}
}
