package com.sayeed.lender_training_portal.support;

import java.time.Clock;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.sayeed.lender_training_portal.course.Course;
import com.sayeed.lender_training_portal.course.CourseRepository;
import com.sayeed.lender_training_portal.user.AppUser;
import com.sayeed.lender_training_portal.user.AppUserRepository;
import com.sayeed.lender_training_portal.user.Role;

/**
 * Builds fixtures for the full integration tests through the real repositories (and the real
 * PasswordEncoder, so the users can log in through POST /api/auth/login). Tests never rely on the
 * seed/ CSVs: each test creates exactly the rows it needs.
 *
 * <p>Registered as a bean with {@code @Import(TestData.class)}.
 */
public class TestData {

    public static final String PASSWORD = "Correct-Horse-1";

    private final AppUserRepository users;
    private final CourseRepository courses;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public TestData(AppUserRepository users, CourseRepository courses, PasswordEncoder passwordEncoder,
                    JdbcTemplate jdbc, Clock clock) {
        this.users = users;
        this.courses = courses;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Empties every table and resets identity sequences. Called before each integration test. */
    public void reset() {
        jdbc.execute("""
                TRUNCATE module_completion, enrollment, course_module, course, app_user
                RESTART IDENTITY CASCADE
                """);
    }

    public AppUser user(String email, Role role) {
        String name = email.substring(0, email.indexOf('@'));
        return users.save(new AppUser(email, passwordEncoder.encode(PASSWORD), name, role, clock.instant()));
    }

    public AppUser lender(String email)  { return user(email, Role.LENDER); }
    public AppUser trainer(String email) { return user(email, Role.TRAINER); }
    public AppUser admin(String email)   { return user(email, Role.ADMIN); }

    public Course draftCourse(AppUser trainer, String title, int moduleCount) {
        return courses.save(newCourse(trainer, title, moduleCount));
    }

    public Course publishedCourse(AppUser trainer, String title, int moduleCount) {
        Course course = newCourse(trainer, title, moduleCount);
        course.publish();
        return courses.save(course);
    }

    private Course newCourse(AppUser trainer, String title, int moduleCount) {
        Course course = new Course(title, title + " description", trainer, clock.instant());
        for (int i = 1; i <= moduleCount; i++) {
            course.addModule(title + " module " + i, "content " + i, 10 * i);
        }
        return course;
    }
}
