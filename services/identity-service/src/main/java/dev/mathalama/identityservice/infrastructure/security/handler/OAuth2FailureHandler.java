package dev.mathalama.identityservice.infrastructure.security.handler;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2FailureHandler.class);

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        logger.error("=== OAuth2 Authentication Failure ===");
        logger.error("Exception class: {}", exception.getClass().getName());
        logger.error("Exception message: {}", exception.getMessage());
        logger.error("Exception cause: {}", exception.getCause() != null ? exception.getCause().getMessage() : "null");
        
        if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
            OAuth2Error error = oauth2Exception.getError();
            logger.error("OAuth2 Error code: {}", error.getErrorCode());
            logger.error("OAuth2 Error description: {}", error.getDescription());
            logger.error("OAuth2 Error URI: {}", error.getUri());
        }
        
        logger.error("Request method: {}", request.getMethod());
        logger.error("Request URI: {}", request.getRequestURI());
        logger.error("Request URL: {}", request.getRequestURL());
        logger.error("Request query string: {}", request.getQueryString());
        logger.error("Request parameters: {}", request.getParameterMap());

        logger.error("Full exception stack trace:", exception);
        
        if (exception.getCause() != null) {
            logger.error("Cause stack trace:", exception.getCause());
        }
        
        response.sendRedirect("/login?error=" + exception.getMessage());
    }
}
