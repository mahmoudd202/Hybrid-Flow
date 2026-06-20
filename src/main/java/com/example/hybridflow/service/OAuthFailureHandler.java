package com.example.hybridflow.service;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class OAuthFailureHandler implements AuthenticationFailureHandler {

    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {

        String message = exception != null && exception.getMessage() != null
                ? exception.getMessage()
                : "OAuth authentication failed.";

        response.sendRedirect(frontendBaseUrl + "/oauth/callback?error=oauth_failed&message="
                + URLEncoder.encode(message, StandardCharsets.UTF_8));
    }
}
