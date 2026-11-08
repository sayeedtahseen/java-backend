package com.sayeed.lender_training_portal.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;
import com.sayeed.lender_training_portal.course.Course;
import com.sayeed.lender_training_portal.support.IntegrationTestBase;
import com.sayeed.lender_training_portal.user.AppUser;

class CourseFlowIntegrationTest extends IntegrationTestBase {

    @Test
    void trainerCreatesCourse_201_publishes_200_publishesAgain_409() throws Exception {
        String token = login(data.trainer("tina@example.com"));

        String created = mvc.perform(post("/api/courses").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Fair lending basics",
                                  "description": "ECOA and Reg B",
                                  "modules": [
                                    {"title": "Intro", "content": "Why it matters", "durationMinutes": 15},
                                    {"title": "Reg B", "content": "Adverse action", "durationMinutes": 30}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.trainerName").value("tina"))
                .andExpect(jsonPath("$.modules.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        long courseId = ((Number) JsonPath.read(created, "$.id")).longValue();

        mvc.perform(post("/api/courses/{id}/publish", courseId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.version").value(1));      // @Version bumped by the flush in publish()

        mvc.perform(post("/api/courses/{id}/publish", courseId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Course is already published"));
    }

    @Test
    void createCourse_locationHeaderPointsAtReadableResource() throws Exception {
        String token = login(data.trainer("tina@example.com"));

        String location = mvc.perform(post("/api/courses").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Draft course"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        mvc.perform(get(location).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Draft course"));
    }

    @Test
    void publishCourseWithoutModules_returns409() throws Exception {
        AppUser tina = data.trainer("tina@example.com");
        Course empty = data.draftCourse(tina, "Empty", 0);

        mvc.perform(post("/api/courses/{id}/publish", empty.getId()).header(HttpHeaders.AUTHORIZATION, bearer(login(tina))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A course needs at least one module before it can be published"));
    }

    @Test
    void otherTrainerPublishing_returns403ProblemDetail() throws Exception {
        AppUser owner = data.trainer("tina@example.com");
        AppUser other = data.trainer("omar@example.com");
        Course draft = data.draftCourse(owner, "Tina's course", 2);

        mvc.perform(post("/api/courses/{id}/publish", draft.getId()).header(HttpHeaders.AUTHORIZATION, bearer(login(other))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void adminCanPublishAnyTrainersCourse() throws Exception {
        Course draft = data.draftCourse(data.trainer("tina@example.com"), "Tina's course", 1);
        String adminToken = login(data.admin("ada@example.com"));

        mvc.perform(post("/api/courses/{id}/publish", draft.getId()).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void draftCourse_isHiddenFromLender_404_butVisibleToOwner_200() throws Exception {
        AppUser owner = data.trainer("tina@example.com");
        Course draft = data.draftCourse(owner, "Secret draft", 2);
        String lenderToken = login(data.lender("lena@example.com"));

        mvc.perform(get("/api/courses/{id}", draft.getId()).header(HttpHeaders.AUTHORIZATION, bearer(lenderToken)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        // Owner sees it. The response also needs the LAZY trainer and modules, which proves the DTO is
        // built inside the service transaction (open-in-view is off and the test has no transaction).
        mvc.perform(get("/api/courses/{id}", draft.getId()).header(HttpHeaders.AUTHORIZATION, bearer(login(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.trainerName").value("tina"))
                .andExpect(jsonPath("$.modules.length()").value(2));
    }

    @Test
    void catalog_listsOnlyPublishedCourses_sortedByTitle_withModuleCounts() throws Exception {
        AppUser tina = data.trainer("tina@example.com");
        data.publishedCourse(tina, "Bravo", 1);
        data.publishedCourse(tina, "Alpha", 3);
        data.draftCourse(tina, "Charlie draft", 2);

        mvc.perform(get("/api/courses").param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login(data.lender("lena@example.com")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Alpha"))
                .andExpect(jsonPath("$.content[0].moduleCount").value(3))
                .andExpect(jsonPath("$.content[0].description").value("Alpha description"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void anyAuthenticatedEndpoint_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/courses"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }
}
