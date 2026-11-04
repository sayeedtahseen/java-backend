package com.sayeed.lender_training_portal.enrollment;

import java.time.Instant;

import com.sayeed.lender_training_portal.course.Course;
import com.sayeed.lender_training_portal.user.AppUser;

import jakarta.persistence.*;

@Entity 
@Table(name="enrollment")
public class Enrollment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnrollmentStatus status;

    @Column(nullable = false)
    private Instant enrolledAt;

    private Instant completedAt;

    protected Enrollment() { }

    public Enrollment(AppUser user, Course course, Instant enrolledAt) {
        this.user = user;
        this.course = course;
        this.enrolledAt = enrolledAt;
        this.status = EnrollmentStatus.ACTIVE;
    }

    public void markCompleted(Instant at) {
        if (status == EnrollmentStatus.COMPLETED) {
            return; // idempotent
        }
        status = EnrollmentStatus.COMPLETED;
        completedAt = at;
    }

    public boolean belongsTo(Long userId) {
        return user.getId().equals(userId);
    }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public Course getCourse() { return course; }
    public EnrollmentStatus getStatus() { return status; }
    public Instant getEnrolledAt() { return enrolledAt; }
    public Instant getCompletedAt() { return completedAt; }
    
}
