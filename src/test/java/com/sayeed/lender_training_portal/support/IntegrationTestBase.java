package com.sayeed.lender_training_portal.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.sayeed.lender_training_portal.user.AppUser;

/**
 * Shared setup for every full integration test.
 *
 * <p>All subclasses have the same annotations, so Spring's test-context cache builds the application
 * context (and the PostgreSQL container) once and reuses it.
 *
 * <p>Deliberately NOT {@code @Transactional}: a test-managed transaction would keep one persistence
 * context open around each whole request, so lazy loading "just works" in the test even where it would throw
 * LazyInitializationException in production (open-in-view is off). It would also hide commit-time
 * failures and make the concurrency test impossible. Each test instead starts from empty tables.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class, TestData.class})
public abstract class IntegrationTestBase {

    @Autowired protected MockMvc mvc;
    @Autowired protected TestData data;
    @Autowired protected Clock clock;

    @BeforeEach
    void cleanDatabase() {
        data.reset();
    }

    /** Logs in through the real endpoint and returns the raw access token. */
    protected String login(AppUser user) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(user.getEmail(), TestData.PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
