package dev.mathalama.identityservice.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "frontend")
@Component
@Getter
@Setter
public class FrontendProperties {
    private String url;
}
