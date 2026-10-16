package vn.clarity.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.clarity.config.RentalPolicy;
import vn.clarity.exception.ApiException;
import vn.clarity.model.Models.*;
import vn.clarity.util.RentalCalculator.Allocation;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import vn.clarity.util.RentalCalculator;
@Service
public class RentalInventoryService {
    private final JdbcTemplate db;
    private final ProductService products;
    private final RentalPolicy rules;
    public RentalInventoryService(JdbcTemplate db,ProductService products,RentalPolicy rules) { this.db=db;this.products=products;this.rules=rules; }
    private List<Allocation> reserved(long variantId,LocalDate through) {
        // Overdue items still physically out on rent remain blocked, including future dates.
        return db.query("""
            SELECT i.start_date,i.blocked_until,i.end_date,i.quantity,o.status
            FROM rental_order_items i JOIN rental_orders o ON o.id=i.order_id
            WHERE i.variant_id=? AND i.start_date<=? AND
             (o.status IN ('CONFIRMED','RENTING') OR (o.status='PENDING_CONFIRMATION' AND o.hold_expires_at>UTC_TIMESTAMP(6)))
            """,(rs,n)-> {
                LocalDate until=rs.getDate("blocked_until").toLocalDate();
                if("RENTING".equals(rs.getString("status")) && rs.getDate("end_date").toLocalDate().isBefore(rules.today()) && until.isBefore(through)) until=through;
                return new Allocation(rs.getDate("start_date").toLocalDate(),until,rs.getInt("quantity"));
            },variantId,through);
    }
    public Availability availability(long variantId,LocalDate start,LocalDate end,int quantity) {
        rules.validate(start,end,quantity);
        VariantData v=products.variant(variantId,false);
        if(!v.active()) throw ApiException.missing();
        LocalDate until=rules.blockedUntil(end);
        int free=Math.max(0,v.stockQuantity()-RentalCalculator.peak(reserved(variantId,until),start,until));
        var price=RentalCalculator.price(start,end,quantity,v.dailyPrice(),v.depositPerUnit());
        int cleaning=(int)java.time.temporal.ChronoUnit.DAYS.between(end,until);
        return new Availability(variantId,start,end,price.days(),free,cleaning,price.rental(),price.deposit(),price.total(),quantity<=free);
    }
    /** Caller must be in a READ_COMMITTED transaction when lock=true. */
    public Map<Long,VariantData> validateCart(List<CartData> items,boolean lock) {
        if(items.isEmpty()) throw ApiException.conflict("EMPTY_CART");
        if(items.size()>20 || items.stream().mapToInt(CartData::quantity).sum()>30) throw ApiException.bad("CART_LIMIT");
        Map<Long,List<CartData>> grouped=items.stream().collect(Collectors.groupingBy(CartData::variantId,TreeMap::new,Collectors.toList()));
        Map<Long,VariantData> variants=new LinkedHashMap<>();
        // Deterministic row locking serializes competing checkouts for the same physical stock.
        for(long id:grouped.keySet()) variants.put(id,products.variant(id,lock));
        for(var group:grouped.entrySet()) {
            VariantData variant=variants.get(group.getKey());
            if(!variant.active()) throw ApiException.conflict("PRODUCT_UNAVAILABLE");
            List<Allocation> requested=new ArrayList<>();
            LocalDate through=rules.today();
            for(CartData item:group.getValue()) {
                rules.validate(item.startDate(),item.endDate(),item.quantity());
                LocalDate until=rules.blockedUntil(item.endDate());
                requested.add(new Allocation(item.startDate(),until,item.quantity()));
                if(until.isAfter(through)) through=until;
            }
            if(!RentalCalculator.fits(variant.stockQuantity(),reserved(group.getKey(),through),requested)) throw ApiException.conflict("STOCK_CONFLICT");
        }
        return variants;
    }
}
