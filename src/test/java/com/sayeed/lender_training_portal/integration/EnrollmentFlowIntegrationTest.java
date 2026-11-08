package com.sayeed.lender_training_portal.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.sayeed.lender_training_portal.course.Course;
import com.sayeed.lender_training_portal.course.CourseModule;
import com.sayeed.lender_training_portal.support.IntegrationTestBase;
import com.sayeed.lender_training_portal.user.AppUser;

class EnrollmentFlowIntegrationTest extends IntegrationTestBase {

    @Test
    void enroll_201_thenEnrollAgain_409() throws Exception {
        Course course = data.publishedCourse(data.trainer("tina@example.com"), "Fair lending", 2);
        String token = login(data.lender("lena@example.com"));

        enroll(token, course.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.completedModules").value(0))
                .andExpect(jsonPath("$.totalModules").value(2))
                .andExpect(jsonPath("$.enrolledAt").value(clock.instant().toString()));

        enroll(token, course.getId())
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Business rule violated"));
    }

    @Test
    void completingAllModules_marksEnrollmentCompleted_andRepeatPutIsIdempotent() throws Exception {
        Course course = data.publishedCourse(data.trainer("tina@example.com"), "Fair lending", 3);
        String token = login(data.lender("lena@example.com"));
        long enrollmentId = idOf(enroll(token, course.getId()).andExpect(status().isCreated()));
        String now = clock.instant().toString();

        for (int i = 0; i < 3; i++) {
            CourseModule module = course.getModules().get(i);
            complete(token, enrollmentId, module.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.completedModules").value(i + 1));
        }

        CourseModule last = course.getModules().get(2);
        complete(token, enrollmentId, last.getId())          // same PUT again
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedModules").value(3))
                .andExpect(jsonPath("$.completedAt").value(now));

        // Read back from the database through a different query (the progress projection).
        mvc.perform(get("/api/enrollments/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].completedModules").value(3))
                .andExpect(jsonPath("$[0].totalModules").value(3))
                .andExpect(jsonPath("$[0].completedAt").value(now));
    }

    @Test
    void anotherLendersEnrollmentId_returns404() throws Exception {
        Course course = data.publishedCourse(data.trainer("tina@example.com"), "Fair lending", 1);
        long lenasEnrollment = idOf(enroll(login(data.lender("lena@example.com")), course.getId()));
        String intruderToken = login(data.lender("ivan@example.com"));

        complete(intruderToken, lenasEnrollment, course.getModules().get(0).getId())
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void moduleFromAnotherCourse_returns404() throws Exception {
        AppUser tina = data.trainer("tina@example.com");
        Course mine = data.publishedCourse(tina, "Mine", 1);
        Course other = data.publishedCourse(tina, "Other", 1);
        String token = login(data.lender("lena@example.com"));
        long enrollmentId = idOf(enroll(token, mine.getId()));

        complete(token, enrollmentId, other.getModules().get(0).getId())
                .andExpect(status().isNotFound());
    }

    @Test
    void enrollInDraftCourse_returns404() throws Exception {
        Course draft = data.draftCourse(data.trainer("tina@example.com"), "Draft", 1);

        enroll(login(data.lender("lena@example.com")), draft.getId())
                .andExpect(status().isNotFound());
    }

    @Test
    void enrollInMissingCourse_returns404() throws Exception {
        enroll(login(data.lender("lena@example.com")), 987_654L)
                .andExpect(status().isNotFound());
    }

    @Test
    void trainerCannotEnroll_403FromFilter() throws Exception {
        Course course = data.publishedCourse(data.trainer("tina@example.com"), "Fair lending", 1);

        enroll(login(data.trainer("omar@example.com")), course.getId())
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));
    }

    private ResultActions enroll(String token, long courseId) throws Exception {
        return mvc.perform(post("/api/enrollments").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"courseId": %d}
                        """.formatted(courseId)));
    }

    private ResultActions complete(String token, long enrollmentId, long moduleId) throws Exception {
        return mvc.perform(put("/api/enrollments/{e}/completions/{m}", enrollmentId, moduleId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private static long idOf(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.enrollmentId")).longValue();
    }
}
