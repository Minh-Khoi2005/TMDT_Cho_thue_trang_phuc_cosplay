package vn.clarity.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import vn.clarity.config.AppConstants;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender sender;
    private final String username;
    private final String from;
    private final String baseUrl;

    public EmailService(JavaMailSender sender) {
        this.sender = sender;
        this.username = AppConstants.MAIL_USERNAME.trim();
        this.from = AppConstants.MAIL_USERNAME.trim();
        this.baseUrl = AppConstants.PUBLIC_BASE_URL.replaceAll("/+$", "");
    }

    public boolean isConfigured() {
        return !username.isBlank();
    }

    public boolean trySendVerification(String email, String name, String token) {
        String link = baseUrl + "/pages/verify-email.html?token=" + encode(token);
        String html = template(
            "Xác nhận tài khoản CLARITY",
            name,
            "Bạn vừa đăng ký tài khoản CLARITY. Hãy xác nhận email để kích hoạt tài khoản.",
            "XÁC NHẬN EMAIL",
            link,
            "Liên kết có hiệu lực trong 24 giờ. Nếu bạn không thực hiện đăng ký này, hãy bỏ qua email."
        );
        return sendHtml(email, "Xác nhận tài khoản CLARITY", html);
    }

    public boolean trySendPasswordReset(String email, String name, String token) {
        String link = baseUrl + "/pages/reset-password.html?token=" + encode(token);
        String html = template(
            "Đặt lại mật khẩu CLARITY",
            name,
            "CLARITY nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn.",
            "ĐẶT LẠI MẬT KHẨU",
            link,
            "Liên kết có hiệu lực trong 30 phút và chỉ dùng được một lần. Nếu bạn không yêu cầu đổi mật khẩu, hãy bỏ qua email."
        );
        return sendHtml(email, "Đặt lại mật khẩu CLARITY", html);
    }

    private boolean sendHtml(String to, String subject, String html) {
        if (username.isBlank()) {
            log.warn("Account email was not sent because SMTP is not configured.");
            return false;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from.isBlank() ? username : from, "CLARITY Cosplay Rental");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(message);
            return true;
        } catch (Exception ex) {
            log.warn("Could not send account email to {}: {}", to, ex.getClass().getSimpleName());
            return false;
        }
    }

    private static String template(String title, String name, String intro, String button, String link, String note) {
        return """
            <!doctype html>
            <html lang="vi"><body style="margin:0;background:#f6f2ec;font-family:Arial,sans-serif;color:#2e2924">
              <div style="max-width:600px;margin:24px auto;background:#fff;border:1px solid #e5ddd2;border-radius:14px;padding:32px">
                <div style="font-size:22px;font-weight:700;letter-spacing:.08em;margin-bottom:24px">CLARITY</div>
                <h2 style="margin:0 0 16px">%s</h2>
                <p>Xin chào <strong>%s</strong>,</p>
                <p style="line-height:1.7">%s</p>
                <p style="margin:28px 0">
                  <a href="%s" style="display:inline-block;background:#2e2924;color:#fff;text-decoration:none;padding:13px 22px;border-radius:9px;font-weight:700">%s</a>
                </p>
                <p style="font-size:13px;line-height:1.6;color:#746b62">%s</p>
                <hr style="border:0;border-top:1px solid #eee5db;margin:24px 0">
                <p style="font-size:12px;color:#8b8177">Đây là email tự động từ CLARITY Cosplay Rental.</p>
              </div>
            </body></html>
            """.formatted(escape(title), escape(name), escape(intro), escapeAttribute(link), escape(button), escape(note));
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String escapeAttribute(String value) {
        return escape(value);
    }

    private static String encode(String token) {
        return URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}
