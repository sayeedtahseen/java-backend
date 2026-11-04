package com.sayeed.lender_training_portal.enrollment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ModuleCompletionRepository extends JpaRepository<ModuleCompletion, Long>{
    boolean existsByEnrollmentIdAndModuleId(Long enrollmentId, Long moduleId);

    long countByEnrollmentId(Long enrollmentId);
}
