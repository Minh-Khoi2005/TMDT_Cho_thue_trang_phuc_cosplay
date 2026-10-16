package vn.clarity.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.clarity.exception.ApiException;
import vn.clarity.util.RentalCalculator;
import java.time.*;
import java.util.Map;

@Component
public class RentalPolicy {
    private final ZoneId zone;
    private final int maxDays, advanceDays, cleaningDays, holdHours;
    public RentalPolicy(@Value("${app.rental.zone:Asia/Ho_Chi_Minh}") String zone,
                       @Value("${app.rental.max-days:30}") int maxDays,
                       @Value("${app.rental.advance-days:365}") int advanceDays,
                       @Value("${app.rental.cleaning-days:1}") int cleaningDays,
                       @Value("${app.rental.hold-hours:24}") int holdHours) {
        this.zone=ZoneId.of(zone); this.maxDays=maxDays; this.advanceDays=advanceDays;
        this.cleaningDays=cleaningDays; this.holdHours=holdHours;
        if(maxDays<1 || maxDays>90 || advanceDays<maxDays || advanceDays>730 || cleaningDays<0 || cleaningDays>7 || holdHours<1 || holdHours>72)
            throw new IllegalArgumentException("Invalid rental configuration");
    }
    public LocalDate today() { return LocalDate.now(zone); }
    public LocalDateTime utcNow() { return LocalDateTime.now(ZoneOffset.UTC); }
    public LocalDate blockedUntil(LocalDate end) { return end.plusDays(cleaningDays); }
    public int holdHours() { return holdHours; }
    public void validate(LocalDate start, LocalDate end, int quantity) {
        if (start==null || end==null || end.isBefore(start)) throw ApiException.bad("INVALID_DATES");
        if (start.isBefore(today())) throw ApiException.bad("PAST_DATES");
        if (end.isAfter(today().plusDays(advanceDays))) throw ApiException.bad("TOO_FAR_AHEAD");
        if (RentalCalculator.days(start,end)>maxDays) throw ApiException.bad("RENTAL_TOO_LONG");
        if(quantity<1 || quantity>10) throw ApiException.bad("INVALID_QUANTITY");
    }
    public Map<String,Object> publicConfig() {
        return Map.of("today",today(),"maxRentalDays",maxDays,"advanceDays",advanceDays,
                "cleaningDays",cleaningDays,"holdHours",holdHours,"zone",zone.toString(),
                "serverTime",Instant.now(),"paymentMode","PAY_AT_SHOP");
    }
}
