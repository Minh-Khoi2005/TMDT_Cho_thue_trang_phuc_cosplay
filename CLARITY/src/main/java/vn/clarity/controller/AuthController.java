package vn.clarity.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.clarity.security.ClarityUserPrincipal;
import vn.clarity.config.RentalPolicy;
import vn.clarity.dto.Requests;
import vn.clarity.service.AuthService;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final RentalPolicy rules;
    private final JdbcTemplate db;
    private final AuthService accounts;

    public AuthController(RentalPolicy rules, JdbcTemplate db, AuthService accounts) {
        this.rules = rules;
        this.db = db;
        this.accounts = accounts;
    }

    @GetMapping("/auth/csrf")
    public Map<String,String> csrf(CsrfToken token) {
        return Map.of("headerName",token.getHeaderName(),"token",token.getToken());
    }

    @GetMapping("/auth/me")
    public Map<String,Object> me(@AuthenticationPrincipal ClarityUserPrincipal p) {
        if (p == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return Map.of("user",p.view());
    }

    @PostMapping("/auth/register")
    public Map<String,Object> register(@Valid @RequestBody Requests.Register request) {
        var result = accounts.register(request);
        return Map.of("created",true,"email",result.email(),"mailSent",result.mailSent());
    }

    @PostMapping("/auth/resend-verification")
    public Map<String,Object> resend(@Valid @RequestBody Requests.EmailRequest request) {
        return accounts.resendVerification(request);
    }

    @PostMapping("/auth/verify-email")
    public Map<String,Object> verify(@Valid @RequestBody Requests.VerifyEmail request) {
        accounts.verifyEmail(request.token());
        return Map.of("verified",true);
    }

    @PostMapping("/auth/forgot-password")
    public Map<String,Object> forgot(@Valid @RequestBody Requests.EmailRequest request) {
        return accounts.forgotPassword(request);
    }

    @PostMapping("/auth/reset-password")
    public Map<String,Object> reset(@Valid @RequestBody Requests.ResetPassword request) {
        accounts.resetPassword(request);
        return Map.of("reset",true);
    }

    @GetMapping("/config") public Map<String,Object> config() { return rules.publicConfig(); }
    @GetMapping("/health") public Map<String,Object> health() {
        db.queryForObject("SELECT COUNT(*) FROM app_users",Long.class);
        return Map.of("status","UP","database","CONNECTED","version","0.3.0");
    }
}
