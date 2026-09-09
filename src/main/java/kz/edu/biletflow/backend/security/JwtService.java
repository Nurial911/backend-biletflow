package kz.edu.biletflow.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import kz.edu.biletflow.backend.configs.JwtConfig;
import kz.edu.biletflow.backend.entities.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class JwtService {
    private final JwtConfig jwtConfig;

    public String generateToken(User user) {
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("role", user.getRole().name())
                .claim("id", user.getId())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtConfig.getTokenExpiration()))
                .signWith(jwtConfig.getSecretKey(), Jwts.SIG.HS512)
                .compact();
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public boolean isTokenValid(String token, UserPrincipal principal) {
        String email = extractEmail(token);
        return email.equals(principal.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }
    private Claims extractAllClaims(String token) {
        // Throws JwtException (expired/malformed/bad signature) for invalid tokens -
        // callers (JwtAuthenticationFilter) are expected to catch that and treat the
        // request as unauthenticated rather than letting it propagate as a 500.
        return Jwts.parser()
                .verifyWith(jwtConfig.getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }


    public Long extractUserId(String jwt) {
        return extractClaim(jwt, claims -> claims.get("id", Long.class));
    }
}
