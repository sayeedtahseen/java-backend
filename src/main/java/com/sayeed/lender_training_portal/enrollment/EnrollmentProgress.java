package com.sayeed.lender_training_portal.enrollment;

import java.time.Instant;

public record EnrollmentProgress(
        Long enrollmentId,
        Long courseId,
        String courseTitle,
        EnrollmentStatus status,
        Long completedModules,
        Long totalModules,
        Instant enrolledAt,
        Instant completedAt) {

}
