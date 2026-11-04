package com.sayeed.lender_training_portal.enrollment;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sayeed.lender_training_portal.common.BusinessRuleException;
import com.sayeed.lender_training_portal.common.NotFoundException;
import com.sayeed.lender_training_portal.course.Course;
import com.sayeed.lender_training_portal.course.CourseModule;
import com.sayeed.lender_training_portal.course.CourseModuleRepository;
import com.sayeed.lender_training_portal.course.CourseRepository;
import com.sayeed.lender_training_portal.course.CourseStatus;
import com.sayeed.lender_training_portal.user.AppUserRepository;

@Service 
@Transactional (readOnly = true)
public class EnrollmentService {

    private final EnrollmentRepository enrollments;
    private final ModuleCompletionRepository completions;
    private final CourseRepository courses;
    private final CourseModuleRepository modules;
    private final AppUserRepository users;
    private final Clock clock;

    public EnrollmentService(EnrollmentRepository enrollments, ModuleCompletionRepository completions,
            CourseRepository courses, CourseModuleRepository modules, AppUserRepository users, Clock clock) {
        this.enrollments = enrollments;
        this.completions = completions;
        this.courses = courses;
        this.modules = modules;
        this.users = users;
        this.clock = clock;
    }

    public List<EnrollmentProgress> myEnrollments(Long userId) {
        return enrollments.findProgressByUserId(userId);
    }

    @Transactional 
    public EnrollmentProgress enroll(Long userId, Long courseId){
        Course course = courses.findById(courseId)
                    .filter(c -> c.getStatus() == CourseStatus.PUBLISHED)
                    .orElseThrow(() -> new NotFoundException("Course " + courseId + " not found"));

        if (enrollments.existsByUserIdAndCourseId(userId, courseId)){
            throw new BusinessRuleException("You are already enrolled in course " + courseId);
        }

        Enrollment enrollment = enrollments.save(
            new Enrollment(users.getReferenceById(userId), course, Instant.now(clock))
        );

        return toProgress(enrollment,
            completions.countByEnrollmentId(enrollment.getId()),
            modules.countByCourseId(courseId));
    }

    @Transactional 
    public EnrollmentProgress completeModule(Long userId, Long enrollmentId, Long moduleId) {
        Enrollment enrollment = enrollments.findByIdAndUserId(enrollmentId, userId)
                                .orElseThrow(()-> new NotFoundException("Enrollment " + enrollmentId + " not found for user " + users.getReferenceById(userId).getFull_name()));
        
        Long courseId = enrollment.getCourse().getId();

        CourseModule module = modules.findByIdAndCourseId(moduleId, courseId)
                                .orElseThrow(() -> new NotFoundException("Module " + moduleId + " not part of course " + enrollment.getCourse().getTitle()));

        if (!completions.existsByEnrollmentIdAndModuleId(enrollmentId, moduleId)){
            completions.save(new ModuleCompletion(enrollment, module, Instant.now(clock)));
        }

        long done = completions.countByEnrollmentId(enrollmentId);
        long total = modules.countByCourseId(courseId);

        if (total == done){
            enrollment.markCompleted(Instant.now(clock));
        }

        return toProgress(enrollment, done, total);
    }
    
    private EnrollmentProgress toProgress(Enrollment e, long done, long total){
        Course c = e.getCourse();
        return new EnrollmentProgress(
            e.getId(),
            c.getId(),
            c.getTitle(),
            e.getStatus(),
            done,
            total,
            e.getEnrolledAt(),
            e.getCompletedAt()
        );
    }
}
