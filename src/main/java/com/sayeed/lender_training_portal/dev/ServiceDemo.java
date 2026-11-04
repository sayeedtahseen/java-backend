package com.sayeed.lender_training_portal.dev;

import com.sayeed.lender_training_portal.course.CourseDetail;
import com.sayeed.lender_training_portal.course.CourseRepository;
import com.sayeed.lender_training_portal.course.CourseService;
import com.sayeed.lender_training_portal.enrollment.EnrollmentProgress;
import com.sayeed.lender_training_portal.enrollment.EnrollmentService;
import com.sayeed.lender_training_portal.user.CurrentUser;
import com.sayeed.lender_training_portal.user.Role;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
@Profile("service-demo")
class ServiceDemo implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ServiceDemo.class);

    private final CourseService courseService;
    private final EnrollmentService enrollmentService;
    private final CourseRepository courseRepository;

    ServiceDemo(CourseService courseService, EnrollmentService enrollmentService,
                CourseRepository courseRepository) {
        this.courseService = courseService;
        this.enrollmentService = enrollmentService;
        this.courseRepository = courseRepository;
    }

    @Override
    public void run(String... args) {
        CurrentUser lender = new CurrentUser(7L, Role.LENDER);
        CurrentUser aisha = new CurrentUser(2L, Role.TRAINER);   // owns course 11
        CurrentUser brian = new CurrentUser(3L, Role.TRAINER);   // owns course 12

        step("1. Published catalog, page 1 of 3 per page", () ->
                courseService.listPublished(PageRequest.of(0, 3, Sort.by("title")))
                        .forEach(s -> log.info("   {}", s)));

        Set<Long> already = enrollmentService.myEnrollments(lender.id()).stream()
                .map(EnrollmentProgress::courseId).collect(Collectors.toSet());
        long newCourseId = LongStream.rangeClosed(1, 10)
                .filter(id -> !already.contains(id)).findFirst().orElseThrow();

        step("2. Lender enrolls in course " + newCourseId, () ->
                log.info("   {}", enrollmentService.enroll(lender.id(), newCourseId)));

        step("3. Same enrollment again (expect BusinessRuleException)", () ->
                enrollmentService.enroll(lender.id(), newCourseId));

        step("4. Complete every module", () -> {
            Long enrollmentId = enrollmentService.myEnrollments(lender.id()).stream()
                    .filter(p -> p.courseId().equals(newCourseId)).findFirst().orElseThrow().enrollmentId();
            CourseDetail course = courseService.getCourse(newCourseId, lender);
            for (CourseDetail.ModuleView m : course.modules()) {
                log.info("   {}", enrollmentService.completeModule(lender.id(), enrollmentId, m.id()));
            }
        });

        step("5. Owner publishes course 12, which has no modules (expect BusinessRuleException)", () ->
                courseService.publish(12L, brian));

        step("6. Wrong trainer publishes course 11 (expect AccessDeniedException)", () ->
                courseService.publish(11L, brian));

        step("7. Owner publishes course 11", () ->
                log.info("   {}", courseService.publish(11L, aisha)));

        step("8. Touch a lazy field OUTSIDE a transaction (expect LazyInitializationException)", () ->
                courseRepository.findById(1L).orElseThrow().getTrainer().getFull_name());
    }

    private void step(String name, Runnable action) {
        log.info("===== {} =====", name);
        try {
            action.run();
        } catch (RuntimeException e) {
            log.info("   -> threw {}: {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }
}