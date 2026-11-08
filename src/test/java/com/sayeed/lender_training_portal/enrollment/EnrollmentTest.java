package com.sayeed.lender_training_portal.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.sayeed.lender_training_portal.course.Course;
import com.sayeed.lender_training_portal.user.AppUser;
import com.sayeed.lender_training_portal.user.Role;

class EnrollmentTest {

    private static final Instant ENROLLED = Instant.parse("2026-01-01T09:00:00Z");
    private static final Instant FIRST_COMPLETION = Instant.parse("2026-01-02T10:00:00Z");
    private static final Instant SECOND_COMPLETION = Instant.parse("2026-01-03T11:00:00Z");

    @Test
    void newEnrollment_isActiveWithoutCompletedAt() {
        Enrollment enrollment = newEnrollment();

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        assertThat(enrollment.getEnrolledAt()).isEqualTo(ENROLLED);
        assertThat(enrollment.getCompletedAt()).isNull();
    }

    @Test
    void markCompleted_setsStatusAndTimestamp() {
        Enrollment enrollment = newEnrollment();

        enrollment.markCompleted(FIRST_COMPLETION);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
        assertThat(enrollment.getCompletedAt()).isEqualTo(FIRST_COMPLETION);
    }

    @Test
    void markCompleted_twice_keepsFirstCompletedAt() {
        Enrollment enrollment = newEnrollment();
        enrollment.markCompleted(FIRST_COMPLETION);

        enrollment.markCompleted(SECOND_COMPLETION);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
        assertThat(enrollment.getCompletedAt()).isEqualTo(FIRST_COMPLETION);
    }

    private static Enrollment newEnrollment() {
        AppUser lender = new AppUser("l@example.com", "hash", "Lender", Role.LENDER, ENROLLED);
        AppUser trainer = new AppUser("t@example.com", "hash", "Trainer", Role.TRAINER, ENROLLED);
        return new Enrollment(lender, new Course("Course", null, trainer, ENROLLED), ENROLLED);
    }
}
