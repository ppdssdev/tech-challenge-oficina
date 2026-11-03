package br.com.fiap.techchallenge.oficina.adapters.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/** Runs inside Boot's observation filter, so MDC holds the real server span. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RequestCompletionLoggingFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            LoggerFactory.getLogger(RequestCompletionLoggingFilter.class).info(
                "HTTP request completed status={} route={}", response.getStatus(),
                route instanceof String pattern ? PiiMasker.mask(pattern) : "[unmatched]");
        }
    }
}
