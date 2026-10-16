package vn.clarity.controller;
import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.clarity.exception.ApiException;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log=LoggerFactory.getLogger(ApiExceptionHandler.class);
    private ResponseEntity<Map<String,Object>> out(HttpStatus status,String code) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("code",code,"message",code));
    }
    @ExceptionHandler(ApiException.class) ResponseEntity<?> domain(ApiException e) { return out(e.status(),e.code()); }
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,jakarta.validation.ConstraintViolationException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class,
        org.springframework.web.bind.MissingRequestHeaderException.class})
    ResponseEntity<?> invalid(Exception e) { return out(HttpStatus.BAD_REQUEST,"INVALID_INPUT"); }
    @ExceptionHandler({DuplicateKeyException.class,org.springframework.dao.ConcurrencyFailureException.class})
    ResponseEntity<?> conflict(Exception e) { return out(HttpStatus.CONFLICT,"CONCURRENT_CHANGE"); }
    @ExceptionHandler(org.springframework.jdbc.CannotGetJdbcConnectionException.class)
    ResponseEntity<?> disconnected(Exception e) { log.warn("Database unavailable: {}",e.getClass().getSimpleName());return out(HttpStatus.SERVICE_UNAVAILABLE,"DATABASE_UNAVAILABLE"); }
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    ResponseEntity<?> absent(Exception e) { return out(HttpStatus.NOT_FOUND,"NOT_FOUND"); }
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ResponseEntity<?> method(Exception e) { return out(HttpStatus.METHOD_NOT_ALLOWED,"INVALID_INPUT"); }
    @ExceptionHandler(Exception.class) ResponseEntity<?> unexpected(Exception e) {
        log.error("Unexpected API error",e);return out(HttpStatus.INTERNAL_SERVER_ERROR,"SERVER_ERROR");
    }
}
