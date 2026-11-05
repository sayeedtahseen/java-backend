package com.sayeed.lender_training_portal.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.sayeed.lender_training_portal.user.AppUser;

@Service 
public class TokenService {
    private final JwtEncoder encoder;
    private final JwtProperties props;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, JwtProperties props, Clock clock) {
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
    }

    public LoginResponse issue(AppUser user) {
        Instant now = Instant.now(clock);
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(props.issuer())
            .issuedAt(now)
            .expiresAt(now.plus(props.ttl()))
            .subject(user.getId().toString())
            .claim("email", user.getEmail())
            .claim("roles", List.of(user.getRole().name()))
            .build();
        
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return new LoginResponse(token, "Bearer", props.ttl().toSeconds());
    }
}
