package vn.clarity.exception;
import org.springframework.http.HttpStatus;
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public ApiException(HttpStatus status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public static ApiException bad(String code) { return new ApiException(HttpStatus.BAD_REQUEST, code, code); }
    public static ApiException conflict(String code) { return new ApiException(HttpStatus.CONFLICT, code, code); }
    public static ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found or not accessible."); }
}
