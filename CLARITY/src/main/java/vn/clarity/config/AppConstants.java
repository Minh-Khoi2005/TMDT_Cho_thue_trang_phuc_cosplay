package vn.clarity.config;

/**
 * Cấu hình chung của đồ án CLARITY.
 * Điền tài khoản Gmail App Password và Google OAuth tại đây một lần cho project nhóm.
 */
public final class AppConstants {
    private AppConstants() {
    }

    public static final String APP_NAME = "CLARITY Cosplay Rental";
    public static final String PUBLIC_BASE_URL = "http://localhost:8080";

    public static final String MAIL_USERNAME = "minhkhoi2005zzz@gmail.com";
    public static final String MAIL_APP_PASSWORD = "jpbfppklhtelugyx";

    public static final String GOOGLE_CLIENT_ID = System.getenv("GOOGLE_CLIENT_ID");
    public static final String GOOGLE_CLIENT_SECRET = System.getenv("GOOGLE_CLIENT_SECRET");
    public static final String GOOGLE_REDIRECT_URI = PUBLIC_BASE_URL + "/api/auth/google/callback";
}
