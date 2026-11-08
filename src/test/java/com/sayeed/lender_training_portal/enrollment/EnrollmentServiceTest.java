package com.sayeed.lender_training_portal.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.sayeed.lender_training_portal.common.NotFoundException;
import com.sayeed.lender_training_portal.course.Course;
import com.sayeed.lender_training_portal.course.CourseModule;
import com.sayeed.lender_training_portal.course.CourseModuleRepository;
import com.sayeed.lender_training_portal.course.CourseRepository;
import com.sayeed.lender_training_portal.user.AppUser;
import com.sayeed.lender_training_portal.user.AppUserRepository;
import com.sayeed.lender_training_portal.user.Role;

/**
 * Unit test with Mockito: no Spring context and no database. We construct EnrollmentService ourselves with
 * mocked repositories and a fixed Clock, so only its decision logic runs.
 *
 * <p>What this can NOT tell us: whether the repository queries are right, whether @Transactional is applied,
 * whether the unique constraints work. The integration tests cover those. That is the trade-off.
 */
@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    private static final Long LENDER_ID = 7L;
    private static final Long ENROLLMENT_ID = 100L;
    private static final Long COURSE_ID = 10L;
    private static final Instant NOW = Instant.parse("2026-03-15T12:00:00Z");

    @Mock EnrollmentRepository enrollments;
    @Mock ModuleCompletionRepository completions;
    @Mock CourseRepository courses;
    @Mock CourseModuleRepository modules;
    @Mock AppUserRepository users;

    private EnrollmentService service;
    private Enrollment enrollment;
    private CourseModule lastModule;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new EnrollmentService(enrollments, completions, courses, modules, users, fixed);

        AppUser trainer = new AppUser("t@example.com", "h", "Trainer", Role.TRAINER, NOW);
        AppUser lender = new AppUser("l@example.com", "h", "Lender", Role.LENDER, NOW);
        Course course = new Course("Fair lending", null, trainer, NOW);
        course.addModule("Intro", null, 10);
        course.addModule("Wrap-up", null, 10);
        ReflectionTestUtils.setField(course, "id", COURSE_ID);
        lastModule = course.getModules().get(1);
        ReflectionTestUtils.setField(lastModule, "id", 2L);

        enrollment = new Enrollment(lender, course, NOW.minusSeconds(3600));
        ReflectionTestUtils.setField(enrollment, "id", ENROLLMENT_ID);
    }

    @Test
    void completeModule_lastModule_marksEnrollmentCompleted() {
        given(enrollments.findByIdAndUserId(ENROLLMENT_ID, LENDER_ID)).willReturn(Optional.of(enrollment));
        given(modules.findByIdAndCourseId(2L, COURSE_ID)).willReturn(Optional.of(lastModule));
        given(completions.existsByEnrollmentIdAndModuleId(ENROLLMENT_ID, 2L)).willReturn(false);
        given(completions.countByEnrollmentId(ENROLLMENT_ID)).willReturn(2L);
        given(modules.countByCourseId(COURSE_ID)).willReturn(2L);

        EnrollmentProgress progress = service.completeModule(LENDER_ID, ENROLLMENT_ID, 2L);

        ArgumentCaptor<ModuleCompletion> saved = ArgumentCaptor.forClass(ModuleCompletion.class);
        then(completions).should().save(saved.capture());
        assertThat(saved.getValue().getModule()).isSameAs(lastModule);
        assertThat(saved.getValue().getCompletedAt()).isEqualTo(NOW);

        assertThat(progress.status()).isEqualTo(EnrollmentStatus.COMPLETED);
        assertThat(progress.completedAt()).isEqualTo(NOW);
        assertThat(progress.completedModules()).isEqualTo(2L);
        assertThat(progress.totalModules()).isEqualTo(2L);
    }

    @Test
    void completeModule_alreadyCompletedModule_doesNotSaveAgain_andKeepsCompletedAt() {
        Instant firstCompletion = NOW.minusSeconds(600);
        enrollment.markCompleted(firstCompletion);
        given(enrollments.findByIdAndUserId(ENROLLMENT_ID, LENDER_ID)).willReturn(Optional.of(enrollment));
        given(modules.findByIdAndCourseId(2L, COURSE_ID)).willReturn(Optional.of(lastModule));
        given(completions.existsByEnrollmentIdAndModuleId(ENROLLMENT_ID, 2L)).willReturn(true);
        given(completions.countByEnrollmentId(ENROLLMENT_ID)).willReturn(2L);
        given(modules.countByCourseId(COURSE_ID)).willReturn(2L);

        EnrollmentProgress progress = service.completeModule(LENDER_ID, ENROLLMENT_ID, 2L);

        then(completions).should(never()).save(any());
        assertThat(progress.status()).isEqualTo(EnrollmentStatus.COMPLETED);
        assertThat(progress.completedAt()).isEqualTo(firstCompletion);
    }

    @Test
    void completeModule_notLastModule_staysActive() {
        given(enrollments.findByIdAndUserId(ENROLLMENT_ID, LENDER_ID)).willReturn(Optional.of(enrollment));
        given(modules.findByIdAndCourseId(2L, COURSE_ID)).willReturn(Optional.of(lastModule));
        given(completions.existsByEnrollmentIdAndModuleId(ENROLLMENT_ID, 2L)).willReturn(false);
        given(completions.countByEnrollmentId(ENROLLMENT_ID)).willReturn(1L);
        given(modules.countByCourseId(COURSE_ID)).willReturn(2L);

        EnrollmentProgress progress = service.completeModule(LENDER_ID, ENROLLMENT_ID, 2L);

        assertThat(progress.status()).isEqualTo(EnrollmentStatus.ACTIVE);
        assertThat(progress.completedAt()).isNull();
    }

    @Test
    void completeModule_enrollmentNotFoundForUser_throwsNotFound_withoutTouchingOtherRepositories() {
        // Covers both "no such enrollment" and "it belongs to another lender": ownership is part of the query.
        given(enrollments.findByIdAndUserId(ENROLLMENT_ID, LENDER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeModule(LENDER_ID, ENROLLMENT_ID, 2L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Enrollment 100 not found");

        // Exactly one repository call happened; nothing else was looked up just to build the error message.
        then(enrollments).should().findByIdAndUserId(ENROLLMENT_ID, LENDER_ID);
        verifyNoMoreInteractions(enrollments);
        verifyNoInteractions(completions, courses, modules, users);
    }

    @Test
    void completeModule_moduleFromAnotherCourse_throwsNotFound_andSavesNothing() {
        given(enrollments.findByIdAndUserId(ENROLLMENT_ID, LENDER_ID)).willReturn(Optional.of(enrollment));
        // The module exists, but not in this enrollment's course, so the course-scoped lookup finds nothing.
        given(modules.findByIdAndCourseId(999L, COURSE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeModule(LENDER_ID, ENROLLMENT_ID, 999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Module 999");

        then(completions).shouldHaveNoInteractions();
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
    }
}
