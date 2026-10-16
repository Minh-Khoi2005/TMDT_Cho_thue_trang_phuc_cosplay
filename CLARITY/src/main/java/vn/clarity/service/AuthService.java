package vn.clarity.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.clarity.dto.Requests;
import vn.clarity.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final EmailService mail;

    public AuthService(JdbcTemplate db, PasswordEncoder passwords, EmailService mail) {
        this.db = db;
        this.passwords = passwords;
        this.mail = mail;
    }

    public record RegistrationResult(String email, boolean mailSent) {}
    private record PendingMail(long userId, String email, String name, String token) {}
    private record TokenRow(long id, long userId, LocalDateTime expires, LocalDateTime used) {}

    @Transactional
    public RegistrationResult register(Requests.Register request) {
        PendingMail pending = createPendingAccount(request);
        boolean sent = mail.trySendVerification(pending.email(), pending.name(), pending.token());
        return new RegistrationResult(pending.email(), sent);
    }

    private PendingMail createPendingAccount(Requests.Register request) {
        String name = request.displayName().trim();
        String email = normalizeEmail(request.email());
        String phone = normalizePhone(request.phone());
        String role = request.role().trim().toUpperCase(Locale.ROOT);
        validatePassword(request.password());
        if (!request.acceptTerms()) throw ApiException.bad("TERMS_REQUIRED");
        if (!List.of("CUSTOMER", "OWNER").contains(role)) throw ApiException.bad("INVALID_ROLE");

        Integer emailCount = db.queryForObject("SELECT COUNT(*) FROM app_users WHERE LOWER(email)=?", Integer.class, email);
        if (emailCount != null && emailCount > 0) throw ApiException.conflict("EMAIL_ALREADY_REGISTERED");
        Integer phoneCount = db.queryForObject("SELECT COUNT(*) FROM app_users WHERE phone=?", Integer.class, phone);
        if (phoneCount != null && phoneCount > 0) throw ApiException.conflict("PHONE_ALREADY_REGISTERED");

        db.update("INSERT INTO app_users(email,phone,display_name,password_hash,role,active,created_at) VALUES(?,?,?,?,?,FALSE,UTC_TIMESTAMP(6))",
            email, phone, name, passwords.encode(request.password()), role);
        Long userId = db.queryForObject("SELECT id FROM app_users WHERE email=?", Long.class, email);
        if (userId == null) throw new IllegalStateException("Created account not found");
        String token = issueVerificationToken(userId);
        return new PendingMail(userId, email, name, token);
    }

    @Transactional
    public Map<String, Object> resendVerification(Requests.EmailRequest request) {
        String email = normalizeEmail(request.email());
        var user = db.query("SELECT id,display_name,active FROM app_users WHERE LOWER(email)=? LIMIT 1",
            (rs, n) -> Map.of("id", rs.getLong("id"), "name", rs.getString("display_name"), "active", rs.getBoolean("active")), email);
        if (user.isEmpty() || Boolean.TRUE.equals(user.get(0).get("active"))) return Map.of("accepted", true, "mailConfigured", mail.isConfigured());
        long userId = (Long) user.get(0).get("id");
        if (sentRecently("email_verification_tokens", userId)) throw ApiException.bad("EMAIL_COOLDOWN");
        String token = issueVerificationToken(userId);
        mail.trySendVerification(email, String.valueOf(user.get(0).get("name")), token);
        return Map.of("accepted", true, "mailConfigured", mail.isConfigured());
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        String hash = tokenHash(rawToken);
        var rows = db.query("SELECT id,user_id,expires_at,used_at FROM email_verification_tokens WHERE token_hash=? FOR UPDATE",
            (rs, n) -> new TokenRow(rs.getLong("id"), rs.getLong("user_id"),
                rs.getTimestamp("expires_at").toLocalDateTime(), rs.getTimestamp("used_at") == null ? null : rs.getTimestamp("used_at").toLocalDateTime()), hash);
        if (rows.isEmpty()) throw ApiException.bad("INVALID_OR_EXPIRED_TOKEN");
        TokenRow row = rows.get(0);
        if (row.used() != null || row.expires() == null || !row.expires().isAfter(LocalDateTime.now(ZoneOffset.UTC))) throw ApiException.bad("INVALID_OR_EXPIRED_TOKEN");
        long userId = row.userId();
        db.update("UPDATE app_users SET active=TRUE WHERE id=?", userId);
        db.update("UPDATE email_verification_tokens SET used_at=UTC_TIMESTAMP(6) WHERE user_id=? AND used_at IS NULL", userId);
    }

    @Transactional
    public Map<String, Object> forgotPassword(Requests.EmailRequest request) {
        String email = normalizeEmail(request.email());
        var user = db.query("SELECT id,display_name FROM app_users WHERE LOWER(email)=? AND active=TRUE LIMIT 1",
            (rs, n) -> Map.entry(rs.getLong("id"), rs.getString("display_name")), email);
        if (user.isEmpty()) return Map.of("accepted", true, "mailConfigured", mail.isConfigured());
        long userId = user.get(0).getKey();
        if (sentRecently("password_reset_tokens", userId)) return Map.of("accepted", true, "mailConfigured", mail.isConfigured());
        String token = issueResetToken(userId);
        mail.trySendPasswordReset(email, user.get(0).getValue(), token);
        return Map.of("accepted", true, "mailConfigured", mail.isConfigured());
    }

    @Transactional
    public void resetPassword(Requests.ResetPassword request) {
        validatePassword(request.password());
        String hash = tokenHash(request.token());
        var rows = db.query("SELECT id,user_id,expires_at,used_at FROM password_reset_tokens WHERE token_hash=? FOR UPDATE",
            (rs, n) -> new TokenRow(rs.getLong("id"), rs.getLong("user_id"),
                rs.getTimestamp("expires_at").toLocalDateTime(), rs.getTimestamp("used_at") == null ? null : rs.getTimestamp("used_at").toLocalDateTime()), hash);
        if (rows.isEmpty()) throw ApiException.bad("INVALID_OR_EXPIRED_TOKEN");
        TokenRow row = rows.get(0);
        if (row.used() != null || row.expires() == null || !row.expires().isAfter(LocalDateTime.now(ZoneOffset.UTC))) throw ApiException.bad("INVALID_OR_EXPIRED_TOKEN");
        long userId = row.userId();
        db.update("UPDATE app_users SET password_hash=? WHERE id=?", passwords.encode(request.password()), userId);
        db.update("UPDATE password_reset_tokens SET used_at=UTC_TIMESTAMP(6) WHERE user_id=? AND used_at IS NULL", userId);
    }

    private String issueVerificationToken(long userId) {
        db.update("UPDATE email_verification_tokens SET used_at=UTC_TIMESTAMP(6) WHERE user_id=? AND used_at IS NULL", userId);
        String token = newToken();
        db.update("INSERT INTO email_verification_tokens(user_id,token_hash,expires_at,created_at) VALUES(?,?,DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 24 HOUR),UTC_TIMESTAMP(6))",
            userId, tokenHash(token));
        return token;
    }

    private String issueResetToken(long userId) {
        db.update("UPDATE password_reset_tokens SET used_at=UTC_TIMESTAMP(6) WHERE user_id=? AND used_at IS NULL", userId);
        String token = newToken();
        db.update("INSERT INTO password_reset_tokens(user_id,token_hash,expires_at,created_at) VALUES(?,?,DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 30 MINUTE),UTC_TIMESTAMP(6))",
            userId, tokenHash(token));
        return token;
    }

    private boolean sentRecently(String table, long userId) {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE user_id=? AND created_at>DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 60 SECOND)";
        Integer count = db.queryForObject(sql, Integer.class, userId);
        return count != null && count > 0;
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String tokenHash(String token) {
        if (token == null || !token.matches("^[A-Za-z0-9_-]{40,100}$")) throw ApiException.bad("INVALID_OR_EXPIRED_TOKEN");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizePhone(String phone) {
        if (phone == null) return "";
        String value = phone.trim().replaceAll("[\\s().-]", "");
        if (value.startsWith("+84")) value = "0" + value.substring(3);
        return value;
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72 ||
            password.chars().noneMatch(ch -> !Character.isLetterOrDigit(ch))) {
            throw ApiException.bad("WEAK_PASSWORD");
        }
    }
}
