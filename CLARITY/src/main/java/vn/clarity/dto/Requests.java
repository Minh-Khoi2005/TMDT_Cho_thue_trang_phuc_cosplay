package vn.clarity.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class Requests {
    private Requests() {}

    public record AddCart(@Positive long variantId,@Min(1) @Max(10) int quantity,
                          @NotNull LocalDate startDate,@NotNull LocalDate endDate) {}
    public record UpdateCart(@Min(0) long cartVersion,@Min(1) @Max(10) int quantity,
                             @NotNull LocalDate startDate,@NotNull LocalDate endDate) {}
    public record Checkout(@Min(0) long cartVersion,@NotNull @DecimalMin("0") BigDecimal quotedTotal,
                           @NotBlank @Size(max=100) String recipientName,
                           @NotBlank @Pattern(regexp="^(0[35789][0-9]{8}|\\+84[35789][0-9]{8})$") String recipientPhone,
                           @Size(max=500) String note,@AssertTrue boolean acceptTerms) {}

    public record Register(
        @NotBlank @Size(min=2,max=100) String displayName,
        @NotBlank @Email @Size(max=120) String email,
        @NotBlank @Pattern(regexp="^(0[35789][0-9]{8}|\\+84[35789][0-9]{8})$") String phone,
        @NotBlank @Size(min=8,max=128) String password,
        @NotBlank @Size(max=20) String role,
        boolean acceptTerms) {}

    public record EmailRequest(@NotBlank @Email @Size(max=120) String email) {}
    public record VerifyEmail(@NotBlank @Size(min=40,max=100) String token) {}
    public record ResetPassword(@NotBlank @Size(min=40,max=100) String token,
                                @NotBlank @Size(min=8,max=128) String password) {}
}
