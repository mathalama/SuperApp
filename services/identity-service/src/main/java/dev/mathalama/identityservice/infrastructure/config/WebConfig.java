package dev.mathalama.identityservice.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvcConfigurer for identity-service.
 * CORS is now handled centrally by the API Gateway.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
}
