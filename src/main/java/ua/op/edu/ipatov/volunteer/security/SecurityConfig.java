package ua.op.edu.ipatov.volunteer.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import java.net.URI;

/**
 * Конфігурація безпеки платформи: хешування паролів Argon2id (OWASP),
 * CSRF-захист HTML-форм та RFC 9457 Problem Details для помилок доступу REST API.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Рекомендовані параметри OWASP для Argon2id: salt=16B, hash=32B, parallelism=1, memory=19456 KiB, iterations=2
        return new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http
                .securityMatcher("/api/**")
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .httpBasic(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/volunteer/**").hasRole("VOLUNTEER")
                        .requestMatchers("/api/coordinator/**").hasRole("COORDINATOR")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, authEx) -> {
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            res.setCharacterEncoding("UTF-8");
                            ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                                    HttpStatus.UNAUTHORIZED,
                                    "Для виконання операції необхідно автентифікуватися");
                            pd.setTitle("Неавтентифікований запит");
                            pd.setType(URI.create("https://volunthub.op.edu.ua/problems/unauthorized"));
                            objectMapper.writeValue(res.getWriter(), pd);
                        })
                        .accessDeniedHandler((req, res, deniedEx) -> {
                            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            res.setCharacterEncoding("UTF-8");
                            ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                                    HttpStatus.FORBIDDEN,
                                    "Недостатньо повноважень для виконання дії над цим ресурсом");
                            pd.setTitle("Доступ заборонено");
                            pd.setType(URI.create("https://volunthub.op.edu.ua/problems/forbidden"));
                            objectMapper.writeValue(res.getWriter(), pd);
                        })
                );
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/tasks", "/tasks/*", "/initiatives", "/initiatives/*",
                                "/login", "/register", "/access-denied",
                                "/css/**", "/js/**", "/favicon.ico",
                                "/actuator/health/**", "/actuator/info", "/actuator/metrics/**",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers("/volunteer/**").hasRole("VOLUNTEER")
                        .requestMatchers("/coordinator/**").hasRole("COORDINATOR")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/dashboard", false)
                        .failureUrl("/login?error=true")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("VOLUNTHUB_SID")
                )
                .exceptionHandling(ex -> ex
                        .accessDeniedPage("/access-denied")
                );
        return http.build();
    }
}
