package dev.mathalama.identityservice.infrastructure.security;

import dev.mathalama.identityservice.infrastructure.security.filter.JwtAuthenticationFilter;
import dev.mathalama.identityservice.infrastructure.security.handler.OAuth2FailureHandler;
import dev.mathalama.identityservice.infrastructure.security.handler.OAuth2SuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthenticationFilter;
        private final OAuth2SuccessHandler oauth2SuccessHandler;
        private final OAuth2FailureHandler oauth2FailureHandler;

        public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                        OAuth2SuccessHandler oauth2SuccessHandler,
                        OAuth2FailureHandler oauth2FailureHandler) {
                this.jwtAuthenticationFilter = jwtAuthenticationFilter;
                this.oauth2SuccessHandler = oauth2SuccessHandler;
                this.oauth2FailureHandler = oauth2FailureHandler;
        }

        @Bean
        @Order(1)
        public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
                http
                                .securityMatcher(
                                                "/auth/**",
                                                "/api/**",
                                                "/actuator/**",
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html")
                                .cors(Customizer.withDefaults())
                                .csrf(AbstractHttpConfigurer::disable)
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(authorize -> authorize
                                                .requestMatchers(
                                                                "/auth/register",
                                                                "/auth/authenticate",
                                                                "/auth/refresh",
                                                                "/auth/verify-email",
                                                                "/auth/resend-verification",
                                                                "/auth/forgot-password",
                                                                "/auth/reset-password",
                                                                "/auth/oauth-exchange")
                                                .permitAll()
                                                .requestMatchers(
                                                                "/actuator/health")
                                                .permitAll()
                                                .requestMatchers(
                                                                "/v3/api-docs/**",
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html")
                                                .permitAll()
                                                .requestMatchers(
                                                                "/auth/logout",
                                                                "/auth/sessions",
                                                                "/auth/sessions/**")
                                                .authenticated()

                                                .requestMatchers(
                                                                "/api/**")
                                                .authenticated()
                                                .anyRequest().denyAll())
                                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        @Bean
        @Order(2)
        public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
                http
                                .authorizeHttpRequests(authorize -> authorize
                                                .requestMatchers(
                                                                "/login",
                                                                "/login/oauth2/code/**")
                                                .permitAll()
                                                .requestMatchers(
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html",
                                                                "/v3/api-docs/**")
                                                .permitAll()
                                                .anyRequest().authenticated())
                                .oauth2Login(oauth2 -> oauth2
                                                .successHandler(oauth2SuccessHandler)
                                                .failureHandler(oauth2FailureHandler))
                                .formLogin(Customizer.withDefaults());

                return http.build();
        }
}
