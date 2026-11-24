package kz.edu.biletflow.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kz.edu.biletflow.backend.exception.ForbiddenOperationException;
import kz.edu.biletflow.backend.exception.ErrorCode;
import kz.edu.biletflow.backend.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserRepository userRepository;
    // Errors raised here go through GlobalExceptionHandler, so they have the usual ErrorResponse body.
    private final HandlerExceptionResolver exceptionResolver;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository,
                                   @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.exceptionResolver = exceptionResolver;
    }

    public String extractTokenFromRequest(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        String email;
        String role;
        Long userId;
        try {
            email = jwtService.extractEmail(jwt);
            role = jwtService.extractRole(jwt);
            userId = jwtService.extractUserId(jwt);
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context", ex);
            filterChain.doFilter(request, response);
            return;
        }

        if (email != null && userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            // The token is valid until it expires, so the account's state is checked on every request.
            Optional<Boolean> suspended = userRepository.findSuspendedById(userId);
            if (suspended.isEmpty()) {
                // Deleted account: the token belongs to no one any more, so the request stays anonymous.
                filterChain.doFilter(request, response);
                return;
            }
            if (suspended.get()) {
                exceptionResolver.resolveException(request, response, null,
                        new ForbiddenOperationException(ErrorCode.ACCOUNT_SUSPENDED, "This account has been suspended"));
                return;
            }

            SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);
            UserPrincipal userPrincipal = new UserPrincipal(userId, email, List.of(authority));
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    userPrincipal, null, userPrincipal.getAuthorities());
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }
        filterChain.doFilter(request, response);
    }
}
