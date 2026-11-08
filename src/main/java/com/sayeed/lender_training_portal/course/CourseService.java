package com.sayeed.lender_training_portal.course;

import java.time.Clock;
import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sayeed.lender_training_portal.common.NotFoundException;
import com.sayeed.lender_training_portal.user.AppUser;
import com.sayeed.lender_training_portal.user.AppUserRepository;
import com.sayeed.lender_training_portal.user.CurrentUser;


@Service 
@Transactional (readOnly = true)
public class CourseService {
    
    private final CourseRepository courses;
    private final AppUserRepository users;
    private final Clock clock;

    public CourseService(CourseRepository courses, AppUserRepository users, Clock clock){
        this.courses = courses;
        this.users = users;
        this.clock = clock;
    }

    public Page<CourseSummary> listPublished(Pageable pageable) {
        return courses.findSummariesByStatus(CourseStatus.PUBLISHED, pageable);
    }

    public CourseDetail getCourse(Long courseid, CurrentUser viewer){
        Course course = courses.findDetailById(courseid)
                            .filter(c -> c.getStatus() == CourseStatus.PUBLISHED || canManage(c, viewer))
                            .orElseThrow( () -> notFound(courseid));
        
        return CourseDetail.from(course);
    }

    @Transactional 
    public CourseDetail createCourse(CurrentUser trainer, CreateCourseRequest request) {
        AppUser owner = users.getReferenceById(trainer.id());
        Course course = new Course(request.title(), request.description(), owner, Instant.now(clock));

        if (request.modules() != null) {
            request.modules().forEach(m -> course.addModule(m.title(), m.content(),  m.durationMinutes()));
        }

        Course saved = courses.save(course);
        return CourseDetail.from(saved);
    }

    @Transactional
    public CourseDetail publish(Long courseId, CurrentUser user) {
        Course course = courses.findDetailById(courseId).orElseThrow(() -> notFound(courseId));

        if (!canManage(course, user)){
            throw new AccessDeniedException("Only the course's trainer or an admin can publish course");
        }

        course.publish();
        courses.flush();

        return CourseDetail.from(course);
    }



    private boolean canManage(Course course, CurrentUser user) {
        return user.isAdmin() || course.isOwnedBy(user.id());
    }

    private static NotFoundException notFound(Long id) {
        return new NotFoundException("Course " + id + " not found");
    }
}
