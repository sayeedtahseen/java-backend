package com.sayeed.lender_training_portal.course;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseModuleRepository extends JpaRepository<CourseModule, Long>{
    
    Optional<CourseModule> findByIdAndCourseId(Long id, Long courseId);

    long countByCourseId(Long courseId);
}
