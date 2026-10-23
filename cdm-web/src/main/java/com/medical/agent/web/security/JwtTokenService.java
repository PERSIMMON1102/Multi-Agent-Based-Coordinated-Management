package com.medical.agent.web.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JwtTokenService {

    @Value("${auth.jwt.secret}")
    private String secret;

    @Value("${auth.jwt.expiration-seconds:43200}")
    private long expirationSeconds;

    private SecretKey signingKey;

    @PostConstruct
    void init() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (IllegalArgumentException ex) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("auth.jwt.secret must be at least 32 bytes long");
        }
        signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(AuthenticatedUser user, Map<String, Object> claims) {
        Instant now = Instant.now();
        Map<String, Object> payload = new LinkedHashMap<>();
        if (claims != null) {
            payload.putAll(claims);
        }
        payload.put("role", user.role());
        payload.put("uid", user.id());
        return Jwts.builder()
                .claims(payload)
                .subject(user.username())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(signingKey)
                .compact();
    }

    public AuthenticatedUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long userId = claims.get("uid", Number.class).longValue();
        String role = claims.get("role", String.class);
        String username = claims.getSubject();
        return new AuthenticatedUser(userId, username, role);
    }
}
