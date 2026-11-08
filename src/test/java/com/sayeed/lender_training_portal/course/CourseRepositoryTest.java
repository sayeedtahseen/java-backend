package com.sayeed.lender_training_portal.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.List;

import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import com.sayeed.lender_training_portal.support.TestcontainersConfiguration;
import com.sayeed.lender_training_portal.user.AppUser;
import com.sayeed.lender_training_portal.user.Role;

import jakarta.persistence.EntityManagerFactory;

/**
 * Repository slice: only JPA, Flyway and the DataSource are started. There are no controllers, services or
 * security. Each test runs in a transaction that is rolled back afterwards, which is fine here because we
 * test queries, not transaction boundaries.
 *
 * <p>{@code replace = NONE}: H2 is still on the classpath (the main spring-boot-h2console dependency
 * pulls it in), so this tells Boot explicitly not to swap our Postgres for an embedded database.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class CourseRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-01-01T09:00:00Z");

    @Autowired CourseRepository courses;
    @Autowired TestEntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory emf;

    private Statistics statistics;

    @BeforeEach
    void grabStatistics() {
        statistics = emf.unwrap(SessionFactory.class).getStatistics();
    }

    @AfterEach
    void turnStatisticsOff() {
        // Statistics live on the shared SessionFactory, so switch them off again for the other tests.
        statistics.setStatisticsEnabled(false);
        statistics.clear();
    }

    @Test
    void flywayMigrations_wereAppliedToRealPostgres() {
        String product = jdbc.queryForObject("select version()", String.class);
        List<String> versions = jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);

        assertThat(product).startsWith("PostgreSQL");
        assertThat(versions).containsExactly("1", "2");
    }

    @Test
    void findSummariesByStatus_returnsOnlyPublished_withModuleCounts_andPaging() {
        AppUser trainer = em.persist(user("trainer@example.com", "Tina Trainer", Role.TRAINER));
        em.persist(course(trainer, "Charlie", 2, true));
        em.persist(course(trainer, "Alpha", 3, true));
        em.persist(course(trainer, "Bravo", 1, true));
        em.persist(course(trainer, "Delta draft", 4, false));
        em.flush();
        em.clear();

        Page<CourseSummary> first = courses.findSummariesByStatus(
                CourseStatus.PUBLISHED, PageRequest.of(0, 2, Sort.by("title")));
        Page<CourseSummary> second = courses.findSummariesByStatus(
                CourseStatus.PUBLISHED, PageRequest.of(1, 2, Sort.by("title")));

        assertThat(first.getContent())
                .extracting(CourseSummary::title, CourseSummary::moduleCount, CourseSummary::trainerName)
                .containsExactly(tuple("Alpha", 3L, "Tina Trainer"), tuple("Bravo", 1L, "Tina Trainer"));
        // Guards the constructor-expression argument order: description is the 3rd argument in the JPQL.
        assertThat(first.getContent()).extracting(CourseSummary::description)
                .containsExactly("Alpha description", "Bravo description");
        assertThat(first.getTotalElements()).isEqualTo(3);   // from the custom countQuery; the draft is excluded
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(second.getContent()).extracting(CourseSummary::title, CourseSummary::moduleCount)
                .containsExactly(tuple("Charlie", 2L));
    }

    @Test
    void findDetailById_loadsTrainerAndModulesInOneStatement() {
        AppUser trainer = em.persist(user("trainer@example.com", "Tina Trainer", Role.TRAINER));
        Long id = em.persist(course(trainer, "Alpha", 3, true)).getId();
        em.flush();
        em.clear();                      // empty the 1st-level cache, so everything below must come from SQL
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        Course course = courses.findDetailById(id).orElseThrow();
        String trainerName = course.getTrainer().getFull_name();
        List<String> moduleTitles = course.getModules().stream().map(CourseModule::getTitle).toList();

        assertThat(Hibernate.isInitialized(course.getTrainer())).isTrue();
        assertThat(trainerName).isEqualTo("Tina Trainer");
        assertThat(moduleTitles).containsExactly("Alpha module 1", "Alpha module 2", "Alpha module 3");
        assertThat(statistics.getPrepareStatementCount())
                .as("join fetch should load course + trainer + modules in a single SELECT")
                .isEqualTo(1);
    }

    @Test
    void findAll_thenTouchingLazyAssociations_issuesNPlusOneStatements() {
        AppUser tina = em.persist(user("tina@example.com", "Tina", Role.TRAINER));
        AppUser omar = em.persist(user("omar@example.com", "Omar", Role.TRAINER));
        em.persist(course(tina, "A", 1, true));
        em.persist(course(tina, "B", 2, true));
        em.persist(course(omar, "C", 3, true));
        em.flush();
        em.clear();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        for (Course c : courses.findAll()) {
            c.getTrainer().getFull_name();   // initialises the LAZY trainer proxy
            c.getModules().size();           // initialises the LAZY modules collection
        }

        // 1 SELECT for the courses
        // + 2 for the trainers (Tina's proxy is loaded once and then served from the persistence context)
        // + 3 for the module collections (one per course)
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(6);
    }

    static AppUser user(String email, String name, Role role) {
        return new AppUser(email, "{noop}irrelevant", name, role, T0);
    }

    static Course course(AppUser trainer, String title, int modules, boolean published) {
        Course course = new Course(title, title + " description", trainer, T0);
        for (int i = 1; i <= modules; i++) {
            course.addModule(title + " module " + i, null, 15);
        }
        if (published) {
            course.publish();
        }
        return course;
    }
}
