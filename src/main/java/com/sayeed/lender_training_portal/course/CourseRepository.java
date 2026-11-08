package com.sayeed.lender_training_portal.course;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

public interface CourseRepository extends JpaRepository<Course, Long>{
    
    @EntityGraph(attributePaths = "trainer")
    List<Course> findByStatus(CourseStatus status);

    @Query(value = """
        select new com.sayeed.lender_training_portal.course.CourseSummary(
            c.id, c.title, c.description, c.status, t.fullName, count(m))
        from Course c
        join c.trainer t
        left join c.modules m
        where c.status = :status
        group by c.id, c.title, c.description, c.status, t.fullName
        """,
        countQuery = "select count(c) from Course c where c.status = :status")
        Page<CourseSummary> findSummariesByStatus(@Param("status") CourseStatus status, Pageable pageable);

    @Query(value = """
            select c from Course c
            join fetch c.trainer
            left join fetch c.modules
            where c.id = :id
            """)
    Optional<Course> findDetailById(@Param("id") Long id);
}
