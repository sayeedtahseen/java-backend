package com.sayeed.lender_training_portal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import com.sayeed.lender_training_portal.course.Course;
import com.sayeed.lender_training_portal.support.IntegrationTestBase;

/**
 * Two requests try to enroll the same lender in the same course at the same moment.
 *
 * <p>Which code path rejects the loser is NOT deterministic:
 * <ul>
 *   <li>the loser's existsByUserIdAndCourseId() runs after the winner committed, giving BusinessRuleException (409), or</li>
 *   <li>both pass the check, and Postgres blocks the loser's INSERT on the unique index until the winner commits,
 *       then rejects it, giving DataIntegrityViolationException (409).</li>
 * </ul>
 * So we only assert what IS deterministic: one 201, one 409, one row. Repeated to shake out flakiness.
 */
class EnrollmentConcurrencyIntegrationTest extends IntegrationTestBase {

    @Autowired JdbcTemplate jdbc;

    @RepeatedTest(10)
    void simultaneousDuplicateEnrollments_exactlyOneSucceeds() throws Exception {
        Course course = data.publishedCourse(data.trainer("tina@example.com"), "Fair lending", 1);
        String token = login(data.lender("lena@example.com"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Callable<Integer> attempt = () -> {
            ready.countDown();
            go.await();                               // both threads wait here until released together
            return mvc.perform(post("/api/enrollments")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"courseId": %d}
                                    """.formatted(course.getId())))
                    .andReturn().getResponse().getStatus();
        };

        List<Integer> statuses;
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = pool.submit(attempt);
            Future<Integer> second = pool.submit(attempt);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            statuses = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        }

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("select count(*) from enrollment", Integer.class)).isEqualTo(1);
    }
}
