package com.sayeed.lender_training_portal.enrollment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    boolean existsByUserIdAndCourseId(Long userId, Long courseId);

    // Ownership is part of the query: another lender's enrollment simply isn't found.
    Optional<Enrollment> findByIdAndUserId(Long id, Long userId);

    @Query("""
            select new com.sayeed.lender_training_portal.enrollment.EnrollmentProgress(
                e.id, c.id, c.title, e.status,
                (select count(mc) from ModuleCompletion mc where mc.enrollment = e),
                (select count(m) from CourseModule m where m.course = c),
                e.enrolledAt, e.completedAt)
            from Enrollment e
            join e.course c
            where e.user.id = :userId
            order by e.enrolledAt desc
            """)
    List<EnrollmentProgress> findProgressByUserId(@Param("userId") Long userId);
}
