package dev.mathalama.identityservice.infrastructure.security.handler;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

import dev.mathalama.identityservice.domain.port.in.OAuthExchangeUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import dev.mathalama.identityservice.domain.port.out.EventPublisher;
import dev.mathalama.identityservice.domain.port.out.TokenStore;
import dev.mathalama.identityservice.domain.port.in.OAuthProviderUseCase;
import dev.mathalama.identityservice.domain.model.User;
import dev.mathalama.identityservice.domain.enums.AccountState;
import dev.mathalama.identityservice.domain.port.out.UserRepository;
import dev.mathalama.identityservice.infrastructure.config.FrontendProperties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2SuccessHandler.class);
    private final UserRepository userRepository;
    private final TokenStore tokenStore;
    private final EventPublisher eventPublisher;
    private final OAuthProviderUseCase oauthProviderUseCase;
    private final FrontendProperties frontendProperties;
    private final OAuthExchangeUseCase oAuthExchangeUseCase;

    public OAuth2SuccessHandler(UserRepository userRepository,
                                TokenStore tokenStore,
                                EventPublisher eventPublisher,
                                OAuthProviderUseCase oauthProviderUseCase,
                                FrontendProperties frontendProperties, OAuthExchangeUseCase oAuthExchangeUseCase) {
        this.userRepository = userRepository;
        this.tokenStore = tokenStore;
        this.eventPublisher = eventPublisher;
        this.oauthProviderUseCase = oauthProviderUseCase;
        this.frontendProperties = frontendProperties;
        this.oAuthExchangeUseCase = oAuthExchangeUseCase;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        logger.info("=== OAuth2 Authentication Success ===");
        
        if (authentication.getPrincipal() instanceof OAuth2User oauth2User) {
            Map<String, Object> attributes = oauth2User.getAttributes();
            String email = (String) attributes.get("email");
            String name = (String) attributes.get("name");
            String providerId = extractUniqueIdentifier(attributes);
            String providerName = getAuthProviderName(request);
            
            User user = null;
            boolean isNewUser = false;
            
            var existingUser = oauthProviderUseCase.findUserByOAuthProvider(providerName, providerId);
            
            if (existingUser.isPresent()) {
                user = existingUser.get();
                logger.info("User found by {} provider ID", providerName);
            } else {
                var userByEmail = userRepository.findByEmail(email);
                
                if (userByEmail.isPresent()) {
                    Boolean emailVerified = (Boolean) attributes.get("email_verified");
                    if (emailVerified != null && emailVerified) {
                        user = userByEmail.get();
                        logger.info("User found by email and email is verified. Linking {} provider", providerName);
                        oauthProviderUseCase.linkOAuthProvider(user, providerName, providerId, email);
                    } else {
                        logger.warn("Email not verified by OAuth provider. Cannot safely link account.");
                        response.sendRedirect(frontendProperties.getUrl() + "/login?error=email_not_verified");
                        return;
                    }
                } else {
                    user = createNewUser(email, name, providerId);
                    isNewUser = true;
                    logger.info("New user created. Linking {} provider", providerName);
                    oauthProviderUseCase.linkOAuthProvider(user, providerName, providerId, email);
                }
            }
            
            oauthProviderUseCase.recordLogin(user, providerName);
            
            if (isNewUser) {
                var event = dev.mathalama.identityservice.application.dto.event.UserRegisteredEvent.create(
                        user.getId().toString(),
                        user.getUsername(),
                        user.getEmail(),
                        providerName
                );
                eventPublisher.publishUserRegistered(event);
            }
            
            String tempCode = oAuthExchangeUseCase.createExchangeCode(user.getId().toString());

            String redirectUrl = String.format("%s/auth/callback?code=%s",
                    frontendProperties.getUrl(), tempCode);
            response.sendRedirect(redirectUrl);
        } else {
            logger.error("Invalid OAuth2User type");
            response.sendRedirect(frontendProperties.getUrl() + "/login?error=invalid_user");
        }
    }

    private User createNewUser(String email, String name, String uniqueId) {
        User newUser = new User();
        newUser.setEmail(email);
        String uniqueIdSuffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        newUser.setUsername(email.split("@")[0] + "_" + uniqueIdSuffix);
        newUser.setPassword("OAUTH2_NO_PASSWORD");
        newUser.setEmailVerified(true);
        newUser.setVerifiedAt(new Date());
        newUser.setAccountState(AccountState.ACTIVE);
        newUser.setCreatedAt(new Date());
        
        userRepository.save(newUser);
        return newUser;
    }
    
    private String extractUniqueIdentifier(Map<String, Object> attributes) {
        if (attributes.containsKey("sub")) return String.valueOf(attributes.get("sub"));
        if (attributes.containsKey("id")) return String.valueOf(attributes.get("id"));
        if (attributes.containsKey("login")) return String.valueOf(attributes.get("login"));
        if (attributes.containsKey("email")) return String.valueOf(attributes.get("email")).split("@")[0];
        return String.valueOf(System.currentTimeMillis());
    }
    
    private String getAuthProviderName(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        if (requestUri.contains("google")) return "GOOGLE";

        if (requestUri.contains("microsoft")) return "MICROSOFT";
        return "UNKNOWN";
    }
}
