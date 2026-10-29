package kz.edu.biletflow.backend.configs;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * TEMPORARY - debugging the frontend login. Delete this file once the login works.
 * Logs method, path, Origin and the final status of every request (watch with `docker logs -f`).
 * Runs before Spring Security, so CORS-rejected preflights (403) show up too.
 * Never logs bodies, so passwords and tokens are not written to the log.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TempRequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            log.info("TEMP-DEBUG {} {} origin={} preflightMethod={} preflightHeaders={} contentType={} -> {}",
                    request.getMethod(), request.getRequestURI(),
                    request.getHeader("Origin"),
                    request.getHeader("Access-Control-Request-Method"),
                    request.getHeader("Access-Control-Request-Headers"),
                    request.getContentType(),
                    response.getStatus());
        }
    }
}
