package com.sayeed.lender_training_portal.enrollment;

import static com.sayeed.lender_training_portal.support.TestJwts.as;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.sayeed.lender_training_portal.common.BusinessRuleException;
import com.sayeed.lender_training_portal.common.NotFoundException;
import com.sayeed.lender_training_portal.config.SecurityConfig;
import com.sayeed.lender_training_portal.user.Role;

@WebMvcTest(EnrollmentController.class)
@Import(SecurityConfig.class)
class EnrollmentControllerWebTest {

    private static final long LENDER_ID = 7L;
    private static final Instant ENROLLED_AT = Instant.parse("2026-03-15T12:00:00Z");

    @Autowired MockMvc mvc;
    @MockitoBean EnrollmentService enrollmentService;

    @Test
    void lenderListsOwnEnrollments_returns200() throws Exception {
        given(enrollmentService.myEnrollments(LENDER_ID)).willReturn(List.of(progress(EnrollmentStatus.ACTIVE, 1L)));

        mvc.perform(get("/api/enrollments/me").with(as(LENDER_ID, Role.LENDER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].enrollmentId").value(100))
                .andExpect(jsonPath("$[0].completedModules").value(1))
                .andExpect(jsonPath("$[0].enrolledAt").value("2026-03-15T12:00:00Z"));
    }

    @Test
    void trainerOnEnrollmentEndpoints_returns403FromFilter() throws Exception {
        mvc.perform(get("/api/enrollments/me").with(as(42L, Role.TRAINER)))
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));

        then(enrollmentService).shouldHaveNoInteractions();
    }

    @Test
    void enroll_returns201_withProgressBody() throws Exception {
        given(enrollmentService.enroll(LENDER_ID, 10L)).willReturn(progress(EnrollmentStatus.ACTIVE, 0L));

        mvc.perform(post("/api/enrollments").with(as(LENDER_ID, Role.LENDER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId": 10}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courseId").value(10))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void enroll_missingCourseId_returns400_withFieldError() throws Exception {
        mvc.perform(post("/api/enrollments").with(as(LENDER_ID, Role.LENDER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("courseId"));

        then(enrollmentService).shouldHaveNoInteractions();
    }

    @Test
    void enroll_alreadyEnrolled_returns409ProblemDetail() throws Exception {
        given(enrollmentService.enroll(LENDER_ID, 10L))
                .willThrow(new BusinessRuleException("You are already enrolled in course 10"));

        mvc.perform(post("/api/enrollments").with(as(LENDER_ID, Role.LENDER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId": 10}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Business rule violated"))
                .andExpect(jsonPath("$.detail").value("You are already enrolled in course 10"));
    }

    @Test
    void completeModule_passesPathVariablesAndCallerId() throws Exception {
        given(enrollmentService.completeModule(LENDER_ID, 100L, 3L)).willReturn(progress(EnrollmentStatus.COMPLETED, 2L));

        mvc.perform(put("/api/enrollments/100/completions/3").with(as(LENDER_ID, Role.LENDER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        then(enrollmentService).should().completeModule(LENDER_ID, 100L, 3L);
    }

    @Test
    void completeModule_unknownEnrollment_returns404ProblemDetail() throws Exception {
        given(enrollmentService.completeModule(LENDER_ID, 555L, 3L))
                .willThrow(new NotFoundException("Enrollment 555 not found"));

        mvc.perform(put("/api/enrollments/555/completions/3").with(as(LENDER_ID, Role.LENDER)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    private static EnrollmentProgress progress(EnrollmentStatus status, long done) {
        return new EnrollmentProgress(100L, 10L, "Fair lending", status, done, 2L, ENROLLED_AT,
                status == EnrollmentStatus.COMPLETED ? ENROLLED_AT.plusSeconds(60) : null);
    }
}
