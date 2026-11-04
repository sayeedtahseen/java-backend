package com.sayeed.lender_training_portal.enrollment;

import java.time.Instant;

import com.sayeed.lender_training_portal.course.CourseModule;

import jakarta.persistence.*;

@Entity 
@Table (name="module_completion")
public class ModuleCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id")
    private Enrollment enrollment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_id")
    private CourseModule module;

    @Column(nullable = false)
    private Instant completedAt;

    protected ModuleCompletion() { }

    public ModuleCompletion(Enrollment enrollment, CourseModule module, Instant completedAt) {
        this.enrollment = enrollment;
        this.module = module;
        this.completedAt = completedAt;
    }

    public Long getId() { return id; }
    public Enrollment getEnrollment() { return enrollment; }
    public CourseModule getModule() { return module; }
    public Instant getCompletedAt() { return completedAt; }
}
