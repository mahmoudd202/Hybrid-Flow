package com.example.hybridflow.service;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

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
 * CHANGED from original:
 *   - createOAuthUser() previously created any Google/GitHub user as GUEST
 *     with no company, no team, and no authorization check. Anyone with a
 *     Google account could get in.
 *   - Now new OAuth users are only accepted if they were authorized via CSV
 *     or via invitation. Unauthorized users are redirected to an error page.
 */
@Component
public class OAuthSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final InvitationRepository invitationRepository;
    private final JwtService jwtService;

    public OAuthSuccessHandler(
            UserRepository userRepository,
            UserProfileRepository profileRepository,
            InvitationRepository invitationRepository,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.invitationRepository = invitationRepository;
        this.jwtService = jwtService;
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
        AuthProvider provider = AuthProvider.valueOf(registrationId.toUpperCase());

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

            // GitHub accounts with no public email: use login@github.local as a fallback.
            // This fallback email will almost certainly not match any CSV record or invitation,
            // so the authorization check below will reject the user, which is the correct behavior.
            if (email == null) {
                Object loginAttr = oAuth2User.getAttribute("login");
                email = (loginAttr != null ? loginAttr.toString() : "unknown") + "@github.local";
            }

        } else {
            // Any provider we don't support yet → redirect to error page.
            response.sendRedirect("http://localhost:8080/login.html?error=unsupported_provider");
            return;
        }

        if (providerId == null || email == null) {
            response.sendRedirect("http://localhost:8080/login.html?error=missing_oauth_data");
            return;
        }

        final String finalEmail = email;
        final String finalProviderId = providerId;

        // ── CASE 1: Returning user ────────────────────────────────────────────
        // An existing account was created via OAuth and is linked by provider+providerId.
        // This is the normal returning-user path. No changes needed.
        Optional<User> existingByProvider =
                userRepository.findByProviderAndProviderId(provider, providerId);

        if (existingByProvider.isPresent()) {
            String jwt = jwtService.generateToken(existingByProvider.get());
            response.sendRedirect("http://localhost:8080/login.html?token=" + jwt);
            return;
        }

        // ── CASE 2: New OAuth user — authorization check required ─────────────
        // This person has never logged in via OAuth before.
        // Check if they were pre-authorized by HR via CSV or invitation.

        // Check A: Was this email pre-loaded by HR via CSV?
        // A CSV-planted user has: password=null, enabled=false, company and team assigned.
        Optional<User> csvUser = userRepository.findByEmail(finalEmail);

        if (csvUser.isPresent() && csvUser.get().getPassword() == null
                && csvUser.get().getCompany() != null
                && csvUser.get().getTeam() != null) {

            // This is a CSV-planted user authenticating via OAuth for the first time.
            // Link the OAuth identity to their existing record and activate them.
            User user = csvUser.get();
            user.setProvider(provider);
            user.setProviderId(finalProviderId);
            // OAuth login counts as identity verification — enable the account.
            user.setEnabled(true);
            userRepository.save(user);

            // Create a profile if one doesn't exist yet.
            // (CSV upload does not create a profile — the user was supposed to set
            // username etc. via /auth/activate. Since they chose OAuth instead, we
            // generate a default username from their provider identity.)
            if (profileRepository.findByUserId(user.getId()).isEmpty()) {
                UserProfile profile = new UserProfile();
                profile.setUser(user);
                // Default username: provider name + underscore + providerId.
                // The user can update this later in their profile settings.
                profile.setUsername(provider.name().toLowerCase() + "_" + finalProviderId);
                profileRepository.save(profile);
            }

            String jwt = jwtService.generateToken(user);
            response.sendRedirect("http://localhost:8080/login.html?token=" + jwt);
            return;
        }

        // Check B: Does this email have an active, unexpired invitation from HR?
        // Uses the new InvitationRepository method: findFirstByEmailAndUsedFalseAndExpiryDateAfter
        Optional<Invitation> activeInvitation =
                invitationRepository.findFirstByEmailAndUsedFalseAndExpiryDateAfter(
                        finalEmail, Instant.now());

        if (activeInvitation.isPresent()) {
            Invitation inv = activeInvitation.get();

            // Create the user using the role, team, and company from the invitation.
            // Do NOT default to GUEST — use the actual role HR intended.
            User user = new User();
            user.setEmail(finalEmail);
            user.setProvider(provider);
            user.setProviderId(finalProviderId);
            user.setRole(inv.getRole());
            user.setTeam(inv.getTeam());
            user.setCompany(inv.getCompany());
            user.setEnabled(true);
            userRepository.save(user);

            // Create a default profile. The user can update their username later.
            UserProfile profile = new UserProfile();
            profile.setUser(user);
            profile.setUsername(provider.name().toLowerCase() + "_" + finalProviderId);
            profileRepository.save(profile);

            // Mark invitation as used so it cannot be reused.
            inv.setUsed(true);
            invitationRepository.save(inv);

            String jwt = jwtService.generateToken(user);
            response.sendRedirect("http://localhost:8080/login.html?token=" + jwt);
            return;
        }

        // ── CASE 3: Not authorized ────────────────────────────────────────────
        // The email is not in the DB as a CSV-planted user and has no active invitation.
        response.sendRedirect(
                "http://localhost:8080/login.html?error=not_authorized&message=" +
                "Your+email+has+not+been+authorized+by+your+organization."
        );
    }
}