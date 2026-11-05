package com.sayeed.lender_training_portal.auth;

import org.springframework.web.bind.annotation.RestController;

import com.sayeed.lender_training_portal.user.CurrentUser;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.Instant;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;



@RestController 
class AuthController {
    
    private final AuthService authService;

    AuthController(AuthService authService){
        this.authService = authService;
    }

    @PostMapping("/api/auth/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/api/me")
    MeResponse  me(@AuthenticationPrincipal Jwt jwt) {
        CurrentUser user = AuthenticatedUser.from(jwt);
        return new MeResponse(
            user.id(),
            user.role().name(),
            jwt.getClaimAsStringList("email").getFirst(),
            jwt.getExpiresAt());
    }
    
    record MeResponse(Long id, String role, String email, Instant expiresAt) { }
    
}
