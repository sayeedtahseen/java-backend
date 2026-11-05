package com.sayeed.lender_training_portal.auth;

import org.springframework.security.oauth2.jwt.Jwt;

import com.sayeed.lender_training_portal.user.CurrentUser;
import com.sayeed.lender_training_portal.user.Role;

public final class AuthenticatedUser {
    
    private AuthenticatedUser () {}

    public static CurrentUser from(Jwt jwt) {
        Long id = Long.valueOf(jwt.getSubject());
        Role role = Role.valueOf(jwt.getClaimAsStringList("roles").getFirst());
        return new CurrentUser(id, role);
    }
}
