package com.sayeed.lender_training_portal.auth;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sayeed.lender_training_portal.user.AppUser;
import com.sayeed.lender_training_portal.user.AppUserRepository;


@Service 
public class AuthService {
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final String dummyHash;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");;
    }

    @Transactional( readOnly = true) 
    public LoginResponse login(LoginRequest request) {
        AppUser user = users.findByEmail(request.email().toLowerCase(Locale.ROOT))
                .orElse(null);

        String hash = (user != null) ? user.getPassword_hash() : dummyHash;
        boolean matches = passwordEncoder.matches(request.password(), hash);

        if (user == null || !matches ) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email address or password");
        }

        return tokenService.issue(user);

    }
    
}
