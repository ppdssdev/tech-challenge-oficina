package br.com.fiap.techchallenge.oficina.adapters.out.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Base64;
import java.util.List;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtTokenSecurityAdapter jwt;
    private final UserDetailsService userDetailsService;
    private final CustomerJwtVerifier customers;
    private final ObjectMapper headers = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    public JwtAuthenticationFilter(JwtTokenSecurityAdapter jwt, UserDetailsService userDetailsService, CustomerJwtVerifier customers) {
        this.customers = customers;
        this.jwt = jwt;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        try {
            String token = header.substring(7);
            if (token.length() > 8192) throw new JwtException("JWT too large.");
            String[] segments = token.split("\\.", -1);
            if (segments.length != 3) throw new JwtException("Invalid JWT format.");
            // Unverified alg selects a fixed validator only; it never grants authorities.
            var untrustedHeader = headers.readTree(Base64.getUrlDecoder().decode(segments[0]));
            String algorithm = untrustedHeader.path("alg").asText();
            UsernamePasswordAuthenticationToken authentication;
            if ("RS256".equals(algorithm)) {
                String customerId = customers.verify(token);
                authentication = new UsernamePasswordAuthenticationToken(customerId, null,
                    List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
            } else if (List.of("HS256", "HS384", "HS512").contains(algorithm)) {
                String username = jwt.extractUsername(token);
                var user = userDetailsService.loadUserByUsername(username);
                if (!jwt.isTokenValid(token, user) || user.getAuthorities().size() != 1
                    || user.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
                    throw new JwtException("Not an administrative identity.");
                }
                authentication = new UsernamePasswordAuthenticationToken(user, null,
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            } else {
                throw new JwtException("Unsupported JWT algorithm.");
            }
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (RuntimeException | IOException exception) {
            SecurityContextHolder.clearContext();
            log.warn("Falha ao autenticar token JWT. cause={}", exception.getClass().getSimpleName());
            response.sendError(401, "Token JWT ausente ou inválido.");
            return;
        }
        chain.doFilter(request, response);
    }
}
