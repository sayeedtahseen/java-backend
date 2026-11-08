package com.sayeed.lender_training_portal.enrollment;

import jakarta.validation.constraints.NotNull;

public record EnrollmentRequest(@NotNull Long courseId) { }