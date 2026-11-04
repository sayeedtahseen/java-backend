package com.sayeed.lender_training_portal.course;

import java.util.List;

public record CourseDetail(
    Long id,
    String title,
    String description,
    CourseStatus status,
    Long trainerId,
    String trainerName,
    long version,
    List<ModuleView> modules 
) {
    public record ModuleView(Long id, int sortOrder, String tile, String content, int durationMinutes) {}

    public static CourseDetail from (Course c) {
        return new CourseDetail(
            c.getId(), c.getTitle(), c.getDescription(), c.getStatus(), c.getTrainer().getId(), c.getTrainer().getFull_name(), c.getVersion(),
            c.getModules().stream()
            .map(m -> new ModuleView(
                m.getId(),
                m.getSortOrder(),
                m.getTitle(),
                m.getContent(),
                m.getDurationMinutes())
            ).toList()
        );
    }
    
}
