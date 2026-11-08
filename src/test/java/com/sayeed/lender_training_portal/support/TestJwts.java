package com.sayeed.lender_training_portal.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;

import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

import com.sayeed.lender_training_portal.user.Role;

/**
 * Builds authenticated requests for {@code @WebMvcTest} slices with spring-security-test's {@code jwt()}.
 *
 * <p>{@code jwt()} skips the JwtDecoder and puts a JwtAuthenticationToken straight into the security
 * context. By default it grants {@code SCOPE_read}, which would not satisfy {@code hasRole(...)}. So we
 * pass the same claim-to-authority mapping that SecurityConfig uses ("roles" claim, "ROLE_" prefix).
 *
 * <p>This mapping is a copy, because SecurityConfig's converter is a private method. The full integration
 * tests use real tokens, so they would catch it if the two drifted apart.
 */
public final class TestJwts {

    private static final JwtGrantedAuthoritiesConverter ROLES_CONVERTER = new JwtGrantedAuthoritiesConverter();
    static {
        ROLES_CONVERTER.setAuthoritiesClaimName("roles");
        ROLES_CONVERTER.setAuthorityPrefix("ROLE_");
    }

    private TestJwts() { }

    public static JwtRequestPostProcessor as(long userId, Role role) {
        return jwt()
                .jwt(token -> token
                        .subject(String.valueOf(userId))
                        .claim("roles", List.of(role.name()))
                        .claim("email", "user" + userId + "@example.com"))
                .authorities(ROLES_CONVERTER);
    }
}
