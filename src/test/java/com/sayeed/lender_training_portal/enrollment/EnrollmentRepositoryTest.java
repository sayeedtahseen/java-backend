package com.sayeed.lender_training_portal.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import com.sayeed.lender_training_portal.course.Course;
import com.sayeed.lender_training_portal.course.CourseModule;
import com.sayeed.lender_training_portal.support.TestcontainersConfiguration;
import com.sayeed.lender_training_portal.user.AppUser;
import com.sayeed.lender_training_portal.user.Role;

/** Same slice configuration as CourseRepositoryTest, so both share one cached context and one container. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class EnrollmentRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-01-01T09:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-02T09:00:00Z");

    @Autowired EnrollmentRepository enrollments;
    @Autowired TestEntityManager em;

    @Test
    void findProgressByUserId_countsCompletedAndTotalModules_newestFirst() {
        AppUser trainer = em.persist(new AppUser("t@example.com", "x", "Trainer", Role.TRAINER, T0));
        AppUser lender = em.persist(new AppUser("l@example.com", "x", "Lender", Role.LENDER, T0));
        AppUser otherLender = em.persist(new AppUser("o@example.com", "x", "Other", Role.LENDER, T0));
        Course threeModules = em.persist(publishedCourse(trainer, "Three", 3));
        Course oneModule = em.persist(publishedCourse(trainer, "One", 1));

        Enrollment older = em.persist(new Enrollment(lender, threeModules, T0));
        em.persist(new Enrollment(lender, oneModule, T1));
        em.persist(new Enrollment(otherLender, threeModules, T1));
        List<CourseModule> modules = threeModules.getModules();
        em.persist(new ModuleCompletion(older, modules.get(0), T1));
        em.persist(new ModuleCompletion(older, modules.get(1), T1));
        em.flush();
        em.clear();

        List<EnrollmentProgress> progress = enrollments.findProgressByUserId(lender.getId());

        assertThat(progress)
                .extracting(EnrollmentProgress::courseTitle, EnrollmentProgress::completedModules,
                        EnrollmentProgress::totalModules, EnrollmentProgress::status)
                .containsExactly(
                        tuple("One", 0L, 1L, EnrollmentStatus.ACTIVE),     // enrolled at T1, so it comes first
                        tuple("Three", 2L, 3L, EnrollmentStatus.ACTIVE));
    }

    @Test
    void findByIdAndUserId_onlyFindsTheOwnersEnrollment() {
        AppUser trainer = em.persist(new AppUser("t@example.com", "x", "Trainer", Role.TRAINER, T0));
        AppUser lender = em.persist(new AppUser("l@example.com", "x", "Lender", Role.LENDER, T0));
        AppUser intruder = em.persist(new AppUser("i@example.com", "x", "Intruder", Role.LENDER, T0));
        Course course = em.persist(publishedCourse(trainer, "Course", 1));
        Long id = em.persistAndFlush(new Enrollment(lender, course, T0)).getId();

        assertThat(enrollments.findByIdAndUserId(id, lender.getId())).isPresent();
        assertThat(enrollments.findByIdAndUserId(id, intruder.getId())).isEmpty();
        assertThat(enrollments.existsByUserIdAndCourseId(lender.getId(), course.getId())).isTrue();
        assertThat(enrollments.existsByUserIdAndCourseId(intruder.getId(), course.getId())).isFalse();
    }

    @Test
    void duplicateUserAndCourse_violatesUniqueConstraint() {
        AppUser trainer = em.persist(new AppUser("t@example.com", "x", "Trainer", Role.TRAINER, T0));
        AppUser lender = em.persist(new AppUser("l@example.com", "x", "Lender", Role.LENDER, T0));
        Course course = em.persist(publishedCourse(trainer, "Course", 1));
        em.persistAndFlush(new Enrollment(lender, course, T0));

        // The database's uq_enrollment_user_course constraint is the real guard. With IDENTITY ids, Hibernate
        // sends the INSERT during save() itself (it needs the generated id), and saveAndFlush makes that explicit.
        // The repository proxy turns the SQL error into Spring's DataIntegrityViolationException.
        assertThatThrownBy(() -> enrollments.saveAndFlush(new Enrollment(lender, course, T1)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_enrollment_user_course");
    }

    private static Course publishedCourse(AppUser trainer, String title, int modules) {
        Course course = new Course(title, null, trainer, T0);
        for (int i = 1; i <= modules; i++) {
            course.addModule(title + " m" + i, null, 10);
        }
        course.publish();
        return course;
    }
}
