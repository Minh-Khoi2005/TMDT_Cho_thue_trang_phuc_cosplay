package vn.clarity.service;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import vn.clarity.security.ClarityUserPrincipal;
import vn.clarity.config.AppConstants;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class GoogleAuthService {
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final RestClient http;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public GoogleAuthService(JdbcTemplate db, PasswordEncoder passwords) {
        this.db = db;
        this.passwords = passwords;
        this.http = RestClient.create();
        this.clientId = AppConstants.GOOGLE_CLIENT_ID.trim();
        this.clientSecret = AppConstants.GOOGLE_CLIENT_SECRET.trim();
        this.redirectUri = AppConstants.GOOGLE_REDIRECT_URI.trim();
    }

    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank() && !redirectUri.isBlank();
    }

    public URI authorizationUri(String state) {
        if (!isConfigured()) throw new IllegalStateException("GOOGLE_OAUTH_NOT_CONFIGURED");
        return UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .queryParam("access_type", "online")
                .queryParam("prompt", "select_account")
                .build()
                .encode()
                .toUri();
    }

    public GoogleProfile exchange(String code) {
        if (!isConfigured()) throw new IllegalStateException("GOOGLE_OAUTH_NOT_CONFIGURED");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("GOOGLE_CODE_MISSING");

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        form.add("grant_type", "authorization_code");

        @SuppressWarnings("unchecked")
        Map<String, Object> token = http.post()
                .uri("https://oauth2.googleapis.com/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);

        String accessToken = token == null ? null : Objects.toString(token.get("access_token"), null);
        if (accessToken == null || accessToken.isBlank()) throw new IllegalStateException("GOOGLE_TOKEN_MISSING");

        @SuppressWarnings("unchecked")
        Map<String, Object> profile = http.get()
                .uri("https://www.googleapis.com/oauth2/v3/userinfo")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(Map.class);

        if (profile == null) throw new IllegalStateException("GOOGLE_PROFILE_MISSING");
        String sub = Objects.toString(profile.get("sub"), "").trim();
        String email = Objects.toString(profile.get("email"), "").trim().toLowerCase(Locale.ROOT);
        String name = Objects.toString(profile.get("name"), "").trim();
        boolean verified = Boolean.TRUE.equals(profile.get("email_verified")) || "true".equalsIgnoreCase(Objects.toString(profile.get("email_verified"), ""));

        if (sub.isBlank() || email.isBlank() || !verified) throw new IllegalStateException("GOOGLE_EMAIL_NOT_VERIFIED");
        if (name.isBlank()) name = email.substring(0, Math.max(1, email.indexOf('@')));
        if (name.length() > 100) name = name.substring(0, 100);
        return new GoogleProfile(sub, email, name);
    }

    @Transactional
    public ClarityUserPrincipal loginOrCreate(GoogleProfile profile) {
        var linked = db.query("""
                SELECT u.id,u.email,u.phone,u.display_name,u.password_hash,u.role,u.active
                FROM oauth_accounts o JOIN app_users u ON u.id=o.user_id
                WHERE o.provider='GOOGLE' AND o.provider_user_id=? LIMIT 1
                """,
                (rs, n) -> principal(rs.getLong("id"), rs.getString("email"), rs.getString("phone"), rs.getString("display_name"), rs.getString("password_hash"), rs.getString("role"), rs.getBoolean("active")),
                profile.subject());
        if (!linked.isEmpty()) return linked.get(0);

        var byEmail = db.query("SELECT id,email,phone,display_name,password_hash,role,active FROM app_users WHERE LOWER(email)=? LIMIT 1",
                (rs, n) -> principal(rs.getLong("id"), rs.getString("email"), rs.getString("phone"), rs.getString("display_name"), rs.getString("password_hash"), rs.getString("role"), rs.getBoolean("active")),
                profile.email());

        ClarityUserPrincipal user;
        if (!byEmail.isEmpty()) {
            user = byEmail.get(0);
            if (!user.isEnabled()) {
                db.update("UPDATE app_users SET active=TRUE WHERE id=?", user.id());
                db.update("UPDATE email_verification_tokens SET used_at=UTC_TIMESTAMP(6) WHERE user_id=? AND used_at IS NULL", user.id());
                user = load(user.id());
            }
        } else {
            String unusablePassword = passwords.encode(UUID.randomUUID().toString());
            db.update("INSERT INTO app_users(email,phone,display_name,password_hash,role,active,created_at) VALUES(?,NULL,?,?, 'CUSTOMER',TRUE,UTC_TIMESTAMP(6))",
                    profile.email(), profile.name(), unusablePassword);
            Long id = db.queryForObject("SELECT id FROM app_users WHERE LOWER(email)=?", Long.class, profile.email());
            if (id == null) throw new IllegalStateException("GOOGLE_ACCOUNT_CREATE_FAILED");
            user = load(id);
        }

        db.update("INSERT INTO oauth_accounts(user_id,provider,provider_user_id,email_at_link,created_at) VALUES(?, 'GOOGLE', ?, ?, UTC_TIMESTAMP(6))",
                user.id(), profile.subject(), profile.email());
        return user;
    }

    private ClarityUserPrincipal load(long id) {
        return db.queryForObject("SELECT id,email,phone,display_name,password_hash,role,active FROM app_users WHERE id=?",
                (rs, n) -> principal(rs.getLong("id"), rs.getString("email"), rs.getString("phone"), rs.getString("display_name"), rs.getString("password_hash"), rs.getString("role"), rs.getBoolean("active")), id);
    }

    private static ClarityUserPrincipal principal(long id, String email, String phone, String name, String password, String role, boolean active) {
        return new ClarityUserPrincipal(id, email, phone, name, password, role, active);
    }

    public static boolean sameState(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    public record GoogleProfile(String subject, String email, String name) {}
}
