package com.sayeed.lender_training_portal.course;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.sayeed.lender_training_portal.auth.AuthenticatedUser;
import com.sayeed.lender_training_portal.common.PageResponse;

import jakarta.validation.Valid;

import java.net.URI;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;



@RestController 
@RequestMapping ("/api/courses")
class CourseController {
    
    private final CourseService courseService;

    CourseController(CourseService courseService){
        this.courseService = courseService;
    }

    @GetMapping
    PageResponse <CourseSummary> list(@RequestParam (defaultValue = "0") int page, 
                                      @RequestParam (defaultValue = "20") int size) {
                                        
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("title"));
        return PageResponse.from(courseService.listPublished(pageable));
    }

    @GetMapping("/{id}")
    CourseDetail get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {

        return courseService.getCourse(id, AuthenticatedUser.from(jwt));
    }

    @PostMapping
    ResponseEntity<CourseDetail> create(@Valid @RequestBody  CreateCourseRequest request, @AuthenticationPrincipal Jwt jwt) {
        
        CourseDetail created = courseService.createCourse(AuthenticatedUser.from(jwt), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();

        return ResponseEntity.created(location).body(created);
    }

    @PostMapping("/{id}/publish")
    CourseDetail publish(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return courseService.publish(id, AuthenticatedUser.from(jwt));
    }
}
