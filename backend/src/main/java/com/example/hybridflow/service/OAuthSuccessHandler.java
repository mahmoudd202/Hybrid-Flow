package com.example.hybridflow.service;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.core.ParameterizedTypeReference;

import com.example.hybridflow.entity.AuthProvider;
import com.example.hybridflow.entity.Invitation;
import com.example.hybridflow.entity.User;
import com.example.hybridflow.entity.UserProfile;
import com.example.hybridflow.repository.InvitationRepository;
import com.example.hybridflow.repository.UserProfileRepository;
import com.example.hybridflow.repository.UserRepository;
import com.example.hybridflow.security.JwtService;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handles the redirect after a successful OAuth2 login (Google or GitHub).
 *
 * Flow:
 *
 * 1. If the user already has an account (matched by provider + providerId):
 *    → Issue JWT and redirect to login.html. (No changes needed.)
 *
 * 2. If the user is NEW (no existing account for this provider+id):
 *    → Check if the email was pre-loaded via CSV upload (has a DB row,
 *      password=null, enabled=false, company and team assigned).
 *    → OR check if the email has a valid active invitation from HR.
 *    → If NEITHER condition is true → reject with an error redirect.
 *      Nobody can join without HR authorization.
 *
 * GitHub email handling:
 *   GitHub users with a private email return null from the standard profile
 *   endpoint. We fetch the real primary verified email from /user/emails
 *   instead of falling back to a fake @github.local address.
 *
 * Name handling:
 *   Google provides "given_name" / "family_name" directly.
 *   GitHub provides a full "name" field (split on first space) or falls back
 *   to the "login" handle. Results are stored as firstName / lastName on
 *   UserProfile instead of the removed username field.
 */
@Component
public class OAuthSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final InvitationRepository invitationRepository;
    private final JwtService jwtService;
    private final OAuth2AuthorizedClientService authorizedClientService;

    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    public OAuthSuccessHandler(
            UserRepository userRepository,
            UserProfileRepository profileRepository,
            InvitationRepository invitationRepository,
            JwtService jwtService,
            OAuth2AuthorizedClientService authorizedClientService) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.invitationRepository = invitationRepository;
        this.jwtService = jwtService;
        this.authorizedClientService = authorizedClientService;
    }

    @Override
    @Transactional
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        OAuth2AuthenticationToken authToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = authToken.getPrincipal();

        String registrationId = authToken.getAuthorizedClientRegistrationId();
        AuthProvider provider;
        try {
            provider = AuthProvider.valueOf(registrationId.toUpperCase());
        } catch (IllegalArgumentException ex) {
            response.sendRedirect(frontendBaseUrl + "/oauth/callback?error=unsupported_provider");
            return;
        }

        String providerId;
        String email;

        if (provider == AuthProvider.GOOGLE) {
            providerId = oAuth2User.getAttribute("sub");
            email = oAuth2User.getAttribute("email");

        } else if (provider == AuthProvider.GITHUB) {
            Object idAttr = oAuth2User.getAttribute("id");
            providerId = idAttr != null ? idAttr.toString() : null;

            Object emailAttr = oAuth2User.getAttribute("email");
            email = emailAttr != null ? emailAttr.toString() : null;

            // GitHub users with a private email return null from the standard profile
            // endpoint. Fetch the real primary verified email from /user/emails instead.
            if (email == null || email.isBlank()) {
                email = fetchPrimaryVerifiedGithubEmail(authToken);
            }

            if (email == null || email.isBlank()) {
                response.sendRedirect(frontendBaseUrl + "/oauth/callback?error=github_email_not_available");
                return;
            }

        } else {
            response.sendRedirect(frontendBaseUrl + "/oauth/callback?error=unsupported_provider");
            return;
        }

        if (providerId == null || providerId.isBlank() || email == null || email.isBlank()) {
            response.sendRedirect(frontendBaseUrl + "/oauth/callback?error=missing_oauth_data");
            return;
        }

        final String finalEmail = email;
        final String finalProviderId = providerId;

        // ── CASE 1: Returning user ────────────────────────────────────────────
        Optional<User> existingByProvider =
                userRepository.findByProviderAndProviderId(provider, providerId);

        if (existingByProvider.isPresent()) {
            String jwt = jwtService.generateToken(existingByProvider.get());
            response.sendRedirect(frontendBaseUrl + "/oauth/callback?token=" + jwt);
            return;
        }

        // ── CASE 2: New OAuth user — authorization check required ─────────────

        // Check A: Was this email pre-loaded by HR via CSV?
        Optional<User> csvUser = userRepository.findByEmail(finalEmail);

        if (csvUser.isPresent() && csvUser.get().getPassword() == null
                && csvUser.get().getCompany() != null
                && csvUser.get().getTeam() != null) {

            User user = csvUser.get();
            user.setProvider(provider);
            user.setProviderId(finalProviderId);
            user.setEnabled(true);
            userRepository.save(user);

            if (profileRepository.findByUserId(user.getId()).isEmpty()) {
                UserProfile profile = createProfile(user, provider, oAuth2User);
                profileRepository.save(profile);
            }

            String jwt = jwtService.generateToken(user);
            response.sendRedirect(frontendBaseUrl + "/oauth/callback?token=" + jwt);
            return;
        }

        // Check B: Does this email have an active, unexpired invitation from HR?
        Optional<Invitation> activeInvitation =
                invitationRepository.findFirstByEmailAndUsedFalseAndExpiryDateAfter(
                        finalEmail, Instant.now());

        if (activeInvitation.isPresent()) {
            Invitation inv = activeInvitation.get();

            User user = new User();
            user.setEmail(finalEmail);
            user.setProvider(provider);
            user.setProviderId(finalProviderId);
            user.setRole(inv.getRole());
            user.setTeam(inv.getTeam());
            user.setCompany(inv.getCompany());
            user.setEnabled(true);
            userRepository.save(user);

            UserProfile profile = createProfile(user, provider, oAuth2User);
            profileRepository.save(profile);

            inv.setUsed(true);
            invitationRepository.save(inv);

            String jwt = jwtService.generateToken(user);
            response.sendRedirect(frontendBaseUrl + "/oauth/callback?token=" + jwt);
            return;
        }

        // ── CASE 3: Not authorized ────────────────────────────────────────────
        response.sendRedirect(
                frontendBaseUrl + "/oauth/callback?error=not_authorized&message=" +
                "Your+email+has+not+been+authorized+by+your+organization."
        );
    }

    // ── GitHub email fetching ─────────────────────────────────────────────────

    private String fetchPrimaryVerifiedGithubEmail(OAuth2AuthenticationToken authToken) {
    OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
            authToken.getAuthorizedClientRegistrationId(),
            authToken.getName()
    );

    if (client == null || client.getAccessToken() == null) {
        return null;
    }

    String accessToken = client.getAccessToken().getTokenValue();

    List<Map<String, Object>> emails;

    try {
        emails = WebClient.create()
                .get()
                .uri("https://api.github.com/user/emails")
                .headers(headers -> {
                    headers.setBearerAuth(accessToken);
                    headers.set("Accept", "application/vnd.github+json");
                    headers.set("X-GitHub-Api-Version", "2022-11-28");
                })
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .block();

    } catch (Exception ex) {
        return null;
    }

    if (emails == null || emails.isEmpty()) {
        return null;
    }

    return emails.stream()
            .filter(e -> Boolean.TRUE.equals(e.get("primary")))
            .filter(e -> Boolean.TRUE.equals(e.get("verified")))
            .map(e -> e.get("email"))
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .findFirst()
            .orElse(null);
}
    // ── Profile creation with firstName / lastName ────────────────────────────

    private UserProfile createProfile(
            User user,
            AuthProvider provider,
            OAuth2User oAuth2User) {
        UserProfile profile = new UserProfile();
        profile.setUser(user);

        NameParts nameParts = extractName(provider, oAuth2User);
        profile.setFirstName(nameParts.firstName());
        profile.setLastName(nameParts.lastName());

        return profile;
    }

    private NameParts extractName(AuthProvider provider, OAuth2User oAuth2User) {
        if (provider == AuthProvider.GOOGLE) {
            String firstName = oAuth2User.getAttribute("given_name");
            String lastName  = oAuth2User.getAttribute("family_name");
            return sanitizeName(firstName, lastName, "Google", "User");
        }

        if (provider == AuthProvider.GITHUB) {
            String fullName = oAuth2User.getAttribute("name");

            if (fullName != null && !fullName.isBlank()) {
                return splitFullName(fullName);
            }

            String login = oAuth2User.getAttribute("login");
            if (login != null && !login.isBlank()) {
                return sanitizeName(login, "GitHub", "GitHub", "User");
            }

            return new NameParts("GitHub", "User");
        }

        return new NameParts("OAuth", "User");
    }

    private NameParts splitFullName(String fullName) {
        String cleaned = fullName.trim();

        if (cleaned.isBlank()) {
            return new NameParts("OAuth", "User");
        }

        String[] parts = cleaned.split("\\s+", 2);
        String firstName = parts[0];
        String lastName  = parts.length > 1 ? parts[1] : "User";

        return sanitizeName(firstName, lastName, "OAuth", "User");
    }

    private NameParts sanitizeName(
            String firstName,
            String lastName,
            String fallbackFirstName,
            String fallbackLastName) {
        if (firstName == null || firstName.isBlank()) firstName = fallbackFirstName;
        if (lastName  == null || lastName.isBlank())  lastName  = fallbackLastName;
        return new NameParts(firstName.trim(), lastName.trim());
    }

    private record NameParts(String firstName, String lastName) {}
}