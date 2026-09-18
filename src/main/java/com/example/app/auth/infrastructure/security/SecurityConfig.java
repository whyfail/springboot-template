package com.example.app.auth.infrastructure.security;

import com.example.app.shared.config.SecurityProperties;
import com.example.app.shared.error.ErrorCode;
import com.example.app.shared.error.ProblemDetailFactory;
import com.example.app.shared.security.SessionStore;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stateless security wiring for the Bearer-token API: CSRF is disabled (no cookie auth on the API),
 * sessions are never created, CORS uses an explicit allowlist, and Problem Details bodies are
 * rendered for 401/403. Swagger exposure is profile-controlled.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder(SecurityProperties properties) {
        // Storage format carries the algorithm id ({bcrypt}...); strength is configuration-driven.
        return new DelegatingPasswordEncoder("bcrypt", java.util.Map.of("bcrypt", new BCryptPasswordEncoder(properties.bcryptStrength())));
    }

    @Bean
    public JsonMapper problemJsonMapper() {
        return JsonMapper.builder().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build();
    }

    @Bean
    public BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter(
            SessionStore sessionStore, ProblemDetailFactory problemFactory, JsonMapper problemJsonMapper) {
        return new BearerTokenAuthenticationFilter(sessionStore, problemFactory, problemJsonMapper);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of(HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(), HttpMethod.PATCH.name(), HttpMethod.DELETE.name(), HttpMethod.OPTIONS.name()));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-ID"));
        configuration.setMaxAge(3600L);
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityProperties properties,
            BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter,
            CorsConfigurationSource corsConfigurationSource,
            ProblemDetailFactory problemFactory,
            JsonMapper problemJsonMapper)
            throws Exception {
        AuthenticationEntryPoint authenticationEntryPoint = (request, response, exception) ->
                writeProblem(problemFactory, problemJsonMapper, response, ErrorCode.AUTH_REQUIRED);
        AccessDeniedHandler accessDeniedHandler = (request, response, exception) ->
                writeProblem(problemFactory, problemJsonMapper, response, ErrorCode.AUTH_FORBIDDEN);

        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
                .authorizeHttpRequests(authorize -> {
                    authorize
                            .requestMatchers(HttpMethod.POST, "/api/v1/login")
                            .permitAll()
                            .requestMatchers(HttpMethod.POST, "/api/v1/logout")
                            .permitAll()
                            .requestMatchers("/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness")
                            .permitAll();
                    if (properties.swaggerUiEnabled()) {
                        authorize
                                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                                .permitAll();
                    }
                    authorize.anyRequest().authenticated();
                })
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(bearerTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void writeProblem(
            ProblemDetailFactory problemFactory, JsonMapper problemMapper, HttpServletResponse response, ErrorCode errorCode)
            throws java.io.IOException {
        ProblemDetail problem = problemFactory.create(errorCode);
        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        problemMapper.writeValue(response.getOutputStream(), problem);
    }
}
