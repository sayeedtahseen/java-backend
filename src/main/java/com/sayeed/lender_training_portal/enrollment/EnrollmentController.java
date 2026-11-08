package com.sayeed.lender_training_portal.enrollment;

import com.sayeed.lender_training_portal.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/enrollments")
class EnrollmentController {

    private final EnrollmentService enrollmentService;

    EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @GetMapping("/me")
    List<EnrollmentProgress> mine(@AuthenticationPrincipal Jwt jwt) {
        return enrollmentService.myEnrollments(AuthenticatedUser.from(jwt).id());
    }

    @PostMapping
    ResponseEntity<EnrollmentProgress> enroll(@Valid @RequestBody EnrollmentRequest request,
                                              @AuthenticationPrincipal Jwt jwt) {
        EnrollmentProgress progress = enrollmentService.enroll(AuthenticatedUser.from(jwt).id(), request.courseId());
        return ResponseEntity.status(HttpStatus.CREATED).body(progress);
    }

    @PutMapping("/{enrollmentId}/completions/{moduleId}")
    EnrollmentProgress complete(@PathVariable Long enrollmentId, @PathVariable Long moduleId,
                                @AuthenticationPrincipal Jwt jwt) {
        return enrollmentService.completeModule(AuthenticatedUser.from(jwt).id(), enrollmentId, moduleId);
    }
}