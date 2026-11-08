package com.sayeed.lender_training_portal.course;

import static com.sayeed.lender_training_portal.support.TestJwts.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.sayeed.lender_training_portal.common.BusinessRuleException;
import com.sayeed.lender_training_portal.common.NotFoundException;
import com.sayeed.lender_training_portal.config.SecurityConfig;
import com.sayeed.lender_training_portal.user.CurrentUser;
import com.sayeed.lender_training_portal.user.Role;

/**
 * Web slice: Spring MVC + our controller + ApiExceptionHandler (picked up automatically as a
 * {@code @RestControllerAdvice}) + the real SecurityConfig filter chain. There are no JPA, services or
 * database: CourseService is a Mockito mock registered as a bean with {@code @MockitoBean}.
 *
 * <p>This tests the HTTP contract: URL rules, status codes, JSON shape, validation and error mapping.
 */
@WebMvcTest(CourseController.class)
@Import(SecurityConfig.class)
class CourseControllerWebTest {

    private static final long TRAINER_ID = 42L;

    @Autowired MockMvc mvc;
    @MockitoBean CourseService courseService;

    // ---------- security filter chain (no ProblemDetail: these never reach Spring MVC) ----------

    @Test
    void noToken_returns401_withEmptyBodyAndWwwAuthenticateHeader() throws Exception {
        mvc.perform(get("/api/courses"))
                .andExpect(status().isUnauthorized())
                // Spring Security 7 appends an RFC 9728 hint, e.g.
                // Bearer resource_metadata="http://localhost/.well-known/oauth-protected-resource"
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(content().string(""));

        then(courseService).shouldHaveNoInteractions();
    }

    @Test
    void lenderCreatingCourse_returns403FromFilter_notProblemDetail() throws Exception {
        mvc.perform(post("/api/courses").with(as(7L, Role.LENDER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Sneaky course", "modules": []}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer error=\"insufficient_scope\"")))
                .andExpect(content().string(""));

        then(courseService).shouldHaveNoInteractions();
    }

    // ---------- happy paths ----------

    @Test
    void createCourse_returns201WithLocationHeader_andPassesCallerFromToken() throws Exception {
        given(courseService.createCourse(eq(new CurrentUser(TRAINER_ID, Role.TRAINER)), any()))
                .willReturn(detail(99L, CourseStatus.DRAFT));

        mvc.perform(post("/api/courses").with(as(TRAINER_ID, Role.TRAINER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Fair lending basics",
                                  "description": "ECOA and Reg B",
                                  "modules": [{"title": "Intro", "content": "...", "durationMinutes": 15}]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/courses/99"))
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.trainerId").value(TRAINER_ID))
                // Contract: the Angular front end reads modules[].title.
                .andExpect(jsonPath("$.modules[0].title").value("Intro"));

        ArgumentCaptor<CreateCourseRequest> request = ArgumentCaptor.forClass(CreateCourseRequest.class);
        then(courseService).should().createCourse(any(), request.capture());
        assertThat(request.getValue().modules()).singleElement()
                .satisfies(m -> assertThat(m.durationMinutes()).isEqualTo(15));
    }

    @Test
    void listCourses_clampsHugePageSizeTo100_andFixesSortToTitle() throws Exception {
        given(courseService.listPublished(any())).willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        mvc.perform(get("/api/courses").param("size", "5000").param("page", "-3").with(as(7L, Role.LENDER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.totalElements").value(0));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        then(courseService).should().listPublished(pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by("title"));
    }

    @Test
    void listCourses_sizeZero_isClampedUpTo1() throws Exception {
        given(courseService.listPublished(any())).willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 0));

        mvc.perform(get("/api/courses").param("size", "0").with(as(7L, Role.LENDER)))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        then(courseService).should().listPublished(pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(1);
    }

    // ---------- validation ----------

    @Test
    void createCourse_invalidBody_returns400_withNestedFieldPaths() throws Exception {
        mvc.perform(post("/api/courses").with(as(TRAINER_ID, Role.TRAINER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "  ",
                                  "modules": [{"title": "Intro", "durationMinutes": 0}]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[*].field")
                        .value(containsInAnyOrder("title", "modules[0].durationMinutes")))
                .andExpect(jsonPath("$.errors[?(@.field == 'modules[0].durationMinutes')].message")
                        .value(hasItem("must be greater than or equal to 1")));

        then(courseService).shouldHaveNoInteractions();
    }

    @Test
    void createCourse_malformedJson_returns400_notSwallowedByCatchAll() throws Exception {
        mvc.perform(post("/api/courses").with(as(TRAINER_ID, Role.TRAINER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    // ---------- exception -> ProblemDetail mapping (ApiExceptionHandler) ----------

    @Test
    void getCourse_notFound_returns404ProblemDetail() throws Exception {
        given(courseService.getCourse(eq(5L), any())).willThrow(new NotFoundException("Course 5 not found"));

        mvc.perform(get("/api/courses/5").with(as(7L, Role.LENDER)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Course 5 not found"))
                .andExpect(jsonPath("$.instance").value("/api/courses/5"));
    }

    @Test
    void publish_businessRuleViolation_returns409ProblemDetail() throws Exception {
        given(courseService.publish(eq(5L), any())).willThrow(new BusinessRuleException("Course is already published"));

        mvc.perform(post("/api/courses/5/publish").with(as(TRAINER_ID, Role.TRAINER)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Business rule violated"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Course is already published"));
    }

    @Test
    void publish_optimisticLockFailure_returns409ProblemDetail() throws Exception {
        given(courseService.publish(eq(5L), any())).willThrow(new OptimisticLockingFailureException("stale"));

        mvc.perform(post("/api/courses/5/publish").with(as(TRAINER_ID, Role.TRAINER)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Concurrent update"))
                .andExpect(jsonPath("$.detail").value("This record was changed by someone else. Reload it and try again."));
    }

    @Test
    void createCourse_dataIntegrityViolation_returns409_withoutLeakingSqlDetails() throws Exception {
        given(courseService.createCourse(any(), any()))
                .willThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint \"secret_internal_name\""));

        mvc.perform(post("/api/courses").with(as(TRAINER_ID, Role.TRAINER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Anything"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("The request conflicts with existing data"));
    }

    @Test
    void publish_accessDeniedFromService_returns403ProblemDetail_notCatchAll500() throws Exception {
        // The URL rule lets any TRAINER through; ownership is checked in the service, which throws.
        given(courseService.publish(eq(5L), any()))
                .willThrow(new AccessDeniedException("Only the course's trainer or an admin can publish course"));

        mvc.perform(post("/api/courses/5/publish").with(as(TRAINER_ID, Role.TRAINER)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("Only the course's trainer or an admin can publish course"));
    }

    @Test
    void getCourse_unexpectedException_returns500_withGenericMessage() throws Exception {
        given(courseService.getCourse(eq(5L), any())).willThrow(new IllegalStateException("db password is hunter2"));

        mvc.perform(get("/api/courses/5").with(as(7L, Role.LENDER)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Internal error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    private static CourseDetail detail(Long id, CourseStatus status) {
        return new CourseDetail(id, "Fair lending basics", "ECOA and Reg B", status, TRAINER_ID, "Tina Trainer", 0L,
                List.of(new CourseDetail.ModuleView(1L, 1, "Intro", "...", 15)));
    }
}
