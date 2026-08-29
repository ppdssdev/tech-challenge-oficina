package br.com.fiap.techchallenge.oficina.adapters.out.security;

import br.com.fiap.techchallenge.oficina.application.port.out.CredentialVerifierPort.AuthenticatedUser;
import br.com.fiap.techchallenge.oficina.application.port.out.TokenIssuerPort;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenSecurityAdapter implements TokenIssuerPort {
    private final String secret;
    private final long expirationMinutes;
    private final UserDetailsService userDetailsService;

    public JwtTokenSecurityAdapter(
        @Value("${app.jwt.secret}") String secret,
        @Value("${app.jwt.expiration-minutes}") long expirationMinutes,
        UserDetailsService userDetailsService
    ) {
        this.secret = secret;
        this.expirationMinutes = expirationMinutes;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public String issue(AuthenticatedUser user) {
        return generateToken(userDetailsService.loadUserByUsername(user.username()));
    }

    public String generateToken(UserDetails userDetails) {
        var now = Instant.now();
        return Jwts.builder()
            .subject(userDetails.getUsername())
            .claim("roles", userDetails.getAuthorities().stream().map(Object::toString).toList())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
            .signWith(signingKey())
            .compact();
    }

    public String extractUsername(String token) { return extractAllClaims(token).getSubject(); }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        return extractUsername(token).equals(userDetails.getUsername())
            && !extractAllClaims(token).getExpiration().before(new Date());
    }

    @Override public long expirationMinutes() { return expirationMinutes; }

    private Claims extractAllClaims(String token) {
        return Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token).getPayload();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
