package com.sayeed.lender_training_portal.course;

public record CourseSummary(
    Long id,
    String title,
    String Description,
    CourseStatus status,
    String trainerName,
    Long moduleCount
) {}
