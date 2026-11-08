package com.sayeed.lender_training_portal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import com.sayeed.lender_training_portal.auth.JwtProperties;
import com.sayeed.lender_training_portal.support.IntegrationTestBase;
import com.sayeed.lender_training_portal.user.AppUser;

/**
 * Real login, real HS256 tokens, real JwtDecoder and validators. The only thing replaced is the Clock.
 */
class AuthIntegrationTest extends IntegrationTestBase {

    @Autowired JwtEncoder jwtEncoder;
    @Autowired JwtProperties jwtProperties;

    @Test
    void login_withCorrectCredentials_returnsBearerToken() throws Exception {
        data.lender("lena@example.com");

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "lena@example.com", "password": "Correct-Horse-1"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void login_upperCaseEmail_isNormalised() throws Exception {
        data.lender("lena@example.com");

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "LENA@Example.com", "password": "Correct-Horse-1"}
                        """))
                .andExpect(status().isOk());
    }

    @Test
    void mixedCaseUser_isStoredLowercase_andCanLogInTypingAnyCase() throws Exception {
        AppUser user = data.lender("Mixed.Case@Example.com");      // goes through the AppUser constructor

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "MIXED.case@example.com", "password": "Correct-Horse-1"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        assertThat(user.getEmail()).isEqualTo("mixed.case@example.com");
    }

    @Test
    void login_withWrongPassword_returns401ProblemDetail() throws Exception {
        data.lender("lena@example.com");

        // Unlike the filter-chain 401s, this one is thrown inside a controller (ResponseStatusException),
        // so ResponseEntityExceptionHandler turns it into a ProblemDetail.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "lena@example.com", "password": "wrong"}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid email address or password"));
    }

    @Test
    void login_withUnknownEmail_returnsTheSame401_noUserEnumeration() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "ghost@example.com", "password": "whatever"}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email address or password"));
    }

    @Test
    void me_returnsIdRoleAndEmailFromRealToken() throws Exception {
        AppUser trainer = data.trainer("tina@example.com");
        String token = login(trainer);

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(trainer.getId()))
                .andExpect(jsonPath("$.role").value("TRAINER"))
                .andExpect(jsonPath("$.email").value("tina@example.com"))
                .andExpect(jsonPath("$.expiresAt").value(clock.instant().plus(Duration.ofHours(1)).toString()));
    }

    @Test
    void mintedValidToken_isAccepted_controlForTheNegativeTests() throws Exception {
        // Proves mint() builds tokens the app accepts, so the 401s below are caused by the one thing we broke.
        AppUser lender = data.lender("lena@example.com");
        Instant now = Instant.now();
        String token = mint(lender, jwtProperties.issuer(), now, now.plusSeconds(300));

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void tamperedToken_returns401() throws Exception {
        AppUser lender = data.lender("lena@example.com");
        String[] parts = login(lender).split("\\.");
        // Privilege escalation attempt: rewrite the payload's role, keep the original signature.
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("\"LENDER\"", "\"ADMIN\"");
        String forged = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(forged)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("invalid_token")))
                .andExpect(content().string(""));
    }

    @Test
    void expiredToken_returns401() throws Exception {
        AppUser lender = data.lender("lena@example.com");
        // The decoder checks exp against the real system clock and allows 60s of clock skew,
        // so "expired" has to be clearly in the past.
        Instant twoHoursAgo = Instant.now().minus(Duration.ofHours(2));
        String token = mint(lender, jwtProperties.issuer(), twoHoursAgo, twoHoursAgo.plus(Duration.ofHours(1)));

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("expired")));
    }

    @Test
    void tokenFromWrongIssuer_returns401() throws Exception {
        AppUser lender = data.lender("lena@example.com");
        Instant now = Instant.now();
        String token = mint(lender, "https://evil.example.com", now, now.plusSeconds(300));

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("invalid_token")));
    }

    /** Signs a token with the application's own key, so only the claims we choose can be wrong. */
    private String mint(AppUser user, String issuer, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getId().toString())
                .claim("roles", List.of(user.getRole().name()))
                .claim("email", user.getEmail())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
