package com.sayeed.lender_training_portal.dev;

import com.sayeed.lender_training_portal.course.Course;
import com.sayeed.lender_training_portal.course.CourseRepository;
import com.sayeed.lender_training_portal.course.CourseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Profile("n1-demo")
class NPlusOneDemo implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(NPlusOneDemo.class);

    private final CourseRepository courses;
    private final TransactionTemplate tx;

    NPlusOneDemo(CourseRepository courses, TransactionTemplate tx) {
        this.courses = courses;
        this.tx = tx;
    }

    @Override
    public void run(String... args) {
        // Each demo gets its own transaction, so one demo's cached entities can't hide another's queries.

        log.info("===== DEMO 1: findAll, then touch trainer and modules (N+1) =====");
        tx.executeWithoutResult(status -> {
            for (Course c : courses.findAll()) {
                log.info("{} | by {} | {} modules",
                        c.getTitle(), c.getTrainer().getFull_name(), c.getModules().size());
            }
        });

        log.info("===== DEMO 2: @EntityGraph, trainer loaded in the same query =====");
        tx.executeWithoutResult(status -> {
            for (Course c : courses.findByStatus(CourseStatus.PUBLISHED)) {
                log.info("{} | by {}", c.getTitle(), c.getTrainer().getFull_name());
            }
        });

        log.info("===== DEMO 3: DTO projection, page 1 of published courses =====");
        tx.executeWithoutResult(status ->
                courses.findSummariesByStatus(CourseStatus.PUBLISHED, PageRequest.of(0, 5, Sort.by("title")))
                       .forEach(s -> log.info("{}", s)));

        log.info("===== DEMO 4: join fetch, one course with trainer and modules =====");
        tx.executeWithoutResult(status ->
                courses.findDetailById(1L).ifPresent(c -> log.info("{} | by {} | modules: {}",
                        c.getTitle(), c.getTrainer().getFull_name(),
                        c.getModules().stream().map(m -> m.getTitle()).toList())));
    }
}