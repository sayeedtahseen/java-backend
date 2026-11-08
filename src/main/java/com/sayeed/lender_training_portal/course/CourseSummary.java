package com.sayeed.lender_training_portal.course;

public record CourseSummary(
    Long id,
    String title,
    String description,
    CourseStatus status,
    String trainerName,
    Long moduleCount
) {}
