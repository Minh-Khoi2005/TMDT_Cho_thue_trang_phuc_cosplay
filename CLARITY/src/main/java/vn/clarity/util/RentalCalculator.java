package vn.clarity.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Pure domain logic, independent of Spring/SQL. End dates are inclusive. */
public final class RentalCalculator {
    private RentalCalculator() {}
    public record Allocation(LocalDate start, LocalDate blockedUntil, int quantity) {
        public Allocation {
            Objects.requireNonNull(start); Objects.requireNonNull(blockedUntil);
            if (blockedUntil.isBefore(start) || quantity < 1) throw new IllegalArgumentException("INVALID_ALLOCATION");
        }
    }
    public record Price(int days, BigDecimal rental, BigDecimal deposit, BigDecimal total) {}
    public static int days(LocalDate start, LocalDate end) {
        Objects.requireNonNull(start); Objects.requireNonNull(end);
        long d = ChronoUnit.DAYS.between(start,end) + 1;
        if (d < 1 || d > Integer.MAX_VALUE) throw new IllegalArgumentException("INVALID_DATES");
        return (int)d;
    }
    public static Price price(LocalDate start, LocalDate end, int quantity, BigDecimal daily, BigDecimal deposit) {
        if (quantity < 1 || daily.signum() <= 0 || deposit.signum() < 0) throw new IllegalArgumentException("INVALID_PRICE");
        int days = days(start,end);
        BigDecimal rent = daily.multiply(BigDecimal.valueOf((long)days * quantity));
        BigDecimal pledge = deposit.multiply(BigDecimal.valueOf(quantity));
        return new Price(days,rent,pledge,rent.add(pledge));
    }
    public static int peak(List<Allocation> items, LocalDate from, LocalDate until) {
        TreeMap<LocalDate,Integer> events = new TreeMap<>();
        for (Allocation a: items) {
            LocalDate s = a.start().isBefore(from) ? from : a.start();
            LocalDate e = a.blockedUntil().isAfter(until) ? until : a.blockedUntil();
            if (s.isAfter(e)) continue;
            events.merge(s,a.quantity(),Integer::sum);
            events.merge(e.plusDays(1),-a.quantity(),Integer::sum);
        }
        int current=0, peak=0;
        for (int delta: events.values()) { current+=delta; peak=Math.max(peak,current); }
        return peak;
    }
    /** Validate simultaneous demand, NOT the sum of all intersecting reservations. */
    public static boolean fits(int stock, List<Allocation> existing, List<Allocation> requested) {
        TreeMap<LocalDate,int[]> events = new TreeMap<>();
        addEvents(events,existing,0); addEvents(events,requested,1);
        int reserved=0, wanted=0;
        for (int[] delta: events.values()) {
            reserved += delta[0]; wanted += delta[1];
            if (wanted > 0 && reserved + wanted > stock) return false;
        }
        return true;
    }
    private static void addEvents(TreeMap<LocalDate,int[]> events, List<Allocation> list, int index) {
        for (Allocation a:list) {
            events.computeIfAbsent(a.start(), k->new int[2])[index] += a.quantity();
            events.computeIfAbsent(a.blockedUntil().plusDays(1), k->new int[2])[index] -= a.quantity();
        }
    }
}
