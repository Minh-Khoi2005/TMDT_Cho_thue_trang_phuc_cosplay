package vn.clarity.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.clarity.security.ClarityUserPrincipal;
import vn.clarity.service.GoogleAuthService;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

@Controller
@RequestMapping("/api/auth/google")
public class GoogleAuthController {
    private static final String STATE_KEY = "CLARITY_GOOGLE_OAUTH_STATE";
    private static final String NEXT_KEY = "CLARITY_GOOGLE_OAUTH_NEXT";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final GoogleAuthService google;

    public GoogleAuthController(GoogleAuthService google) {
        this.google = google;
    }

    @GetMapping("/start")
    public void start(@RequestParam(required = false) String next,
                      HttpServletRequest request,
                      HttpServletResponse response) throws IOException {
        if (!google.isConfigured()) {
            System.err.println("GOOGLE OAUTH: NOT_CONFIGURED");
            response.sendRedirect("/pages/login.html?google=not-configured");
            return;
        }

        String state = randomState();
        HttpSession session = request.getSession(true);
        session.setAttribute(STATE_KEY, state);
        session.setAttribute(NEXT_KEY, safeNext(next));

        System.out.println("GOOGLE OAUTH START: session=" + session.getId());
        response.sendRedirect(google.authorizationUri(state).toString());
    }

    @GetMapping("/callback")
    public void callback(@RequestParam(required = false) String code,
                         @RequestParam(required = false) String state,
                         @RequestParam(required = false) String error,
                         @RequestParam(name = "error_description", required = false) String errorDescription,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        String expected = session == null ? null : (String) session.getAttribute(STATE_KEY);
        String next = session == null ? null : (String) session.getAttribute(NEXT_KEY);

        System.out.println("GOOGLE OAUTH CALLBACK: session=" + (session == null ? "null" : session.getId()));

        if (session != null) {
            session.removeAttribute(STATE_KEY);
            session.removeAttribute(NEXT_KEY);
        }

        if (error != null) {
            System.err.println("GOOGLE PROVIDER ERROR: " + error +
                    (errorDescription == null ? "" : " | " + errorDescription));
            response.sendRedirect("/pages/login.html?google=provider-error");
            return;
        }

        if (!GoogleAuthService.sameState(expected, state)) {
            System.err.println("GOOGLE STATE MISMATCH");
            System.err.println("expected=" + expected);
            System.err.println("actual=" + state);
            response.sendRedirect("/pages/login.html?google=state-mismatch");
            return;
        }

        if (code == null || code.isBlank()) {
            System.err.println("GOOGLE CODE MISSING");
            response.sendRedirect("/pages/login.html?google=code-missing");
            return;
        }

        try {
            ClarityUserPrincipal principal = google.loginOrCreate(google.exchange(code));

            var authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    principal.getAuthorities()
            );

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            HttpSession authenticatedSession = request.getSession(true);
            authenticatedSession.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    context
            );

            System.out.println("GOOGLE LOGIN SUCCESS: role=" + principal.role());

            response.sendRedirect(safeNextForRole(next, principal.role()));
        } catch (RuntimeException ex) {
            System.err.println("========== GOOGLE LOGIN FAILED ==========");
            System.err.println(ex.getClass().getName() + ": " + ex.getMessage());
            ex.printStackTrace();
            System.err.println("=========================================");
            response.sendRedirect("/pages/login.html?google=failed");
        }
    }

    private static String randomState() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String safeNext(String value) {
        if (value == null || value.isBlank() || !value.startsWith("/") || value.startsWith("//") || value.contains("://")) {
            return "/";
        }
        return value;
    }

    private static String safeNextForRole(String next, String role) {
        if (next != null && !next.equals("/") && next.startsWith("/") && !next.startsWith("//") && !next.contains("://")) {
            return next;
        }

        return switch (role) {
            case "ADMIN" -> "/pages/admin-dashboard.html";
            case "STAFF" -> "/pages/staff-rentals.html";
            case "OWNER" -> "/pages/owner-dashboard.html";
            default -> "/";
        };
    }
}
