package vn.clarity.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.clarity.dto.Requests.Checkout;
import vn.clarity.config.RentalPolicy;
import vn.clarity.exception.ApiException;
import vn.clarity.util.AssetPaths;
import vn.clarity.util.RentalCalculator;
import vn.clarity.model.Models.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class RentalOrderService {
    private final JdbcTemplate db;
    private final CartService carts;
    private final RentalInventoryService inventory;
    private final RentalPolicy rules;
    public RentalOrderService(JdbcTemplate db,CartService carts,RentalInventoryService inventory,RentalPolicy rules) {this.db=db;this.carts=carts;this.inventory=inventory;this.rules=rules;}
    private static final Set<String> STATUSES=Set.of("PENDING_CONFIRMATION","CONFIRMED","RENTING","COMPLETED","CANCELLED","EXPIRED");
    private static final String EFFECTIVE="CASE WHEN status='PENDING_CONFIRMATION' AND hold_expires_at<=UTC_TIMESTAMP(6) THEN 'EXPIRED' ELSE status END";
    private String fingerprint(Checkout r) {
        String[] fields={Long.toString(r.cartVersion()),r.quotedTotal().stripTrailingZeros().toPlainString(),
            r.recipientName().trim(),r.recipientPhone(),Objects.toString(r.note(),"").trim(),Boolean.toString(r.acceptTerms())};
        StringBuilder payload=new StringBuilder();
        for(String field:fields) payload.append(field.length()).append(':').append(field);
        String text=payload.toString();
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public OrderView create(long userId,Checkout request,String key) {
        if(key==null || !key.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) throw ApiException.bad("INVALID_IDEMPOTENCY_KEY");
        key=key.toLowerCase(Locale.ROOT);
        long version=carts.lock(userId);
        String fingerprint=fingerprint(request);
        var repeated=db.query("SELECT id,request_fingerprint FROM rental_orders WHERE user_id=? AND idempotency_key=?",
            (rs,n)->Map.entry(rs.getLong("id"),rs.getString("request_fingerprint")),userId,key);
        if(!repeated.isEmpty()) {
            if(!fingerprint.equals(repeated.get(0).getValue())) throw ApiException.conflict("IDEMPOTENCY_CONFLICT");
            return detail(userId,repeated.get(0).getKey());
        }
        if(version!=request.cartVersion()) throw ApiException.conflict("CART_CHANGED");
        var rows=carts.data(userId);
        // All variant locks stay held until the order + lines + event + cart deletion commit together.
        var variants=inventory.validateCart(rows,true);
        BigDecimal rent=BigDecimal.ZERO,deposit=BigDecimal.ZERO;
        for(CartData c:rows) {
            VariantData v=variants.get(c.variantId());
            var quote=RentalCalculator.price(c.startDate(),c.endDate(),c.quantity(),v.dailyPrice(),v.depositPerUnit());
            rent=rent.add(quote.rental());deposit=deposit.add(quote.deposit());
        }
        final BigDecimal rentalTotal=rent,depositTotal=deposit,total=rent.add(deposit);
        if(total.compareTo(request.quotedTotal())!=0) throw ApiException.conflict("PRICE_CHANGED");
        String code="CLR-"+rules.today().format(DateTimeFormatter.ofPattern("yyMMdd"))+"-"+UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT);
        LocalDateTime now=rules.utcNow(),expires=now.plusHours(rules.holdHours());
        final String idempotencyKey=key;
        KeyHolder holder=new GeneratedKeyHolder();
        db.update(connection->{
            PreparedStatement ps=connection.prepareStatement("""
                INSERT INTO rental_orders(code,user_id,status,payment_status,payment_method,recipient_name,recipient_phone,note,
                    rental_total,deposit_total,total_due,paid_total,hold_expires_at,idempotency_key,request_fingerprint,created_at)
                VALUES(?,?,'PENDING_CONFIRMATION','UNPAID','PAY_AT_SHOP',?,?,?,?,?,?,0,?,?,?,?)
                """,Statement.RETURN_GENERATED_KEYS);
            int i=1;ps.setString(i++,code);ps.setLong(i++,userId);ps.setString(i++,request.recipientName().trim());
            ps.setString(i++,request.recipientPhone());ps.setString(i++,Objects.toString(request.note(),"").trim());
            ps.setBigDecimal(i++,rentalTotal);ps.setBigDecimal(i++,depositTotal);ps.setBigDecimal(i++,total);
            ps.setTimestamp(i++,java.sql.Timestamp.valueOf(expires));ps.setString(i++,idempotencyKey);ps.setString(i++,fingerprint);ps.setTimestamp(i,java.sql.Timestamp.valueOf(now));
            return ps;
        },holder);
        long id=Objects.requireNonNull(holder.getKey()).longValue();
        for(CartData c:rows) {
            VariantData v=variants.get(c.variantId());
            var price=RentalCalculator.price(c.startDate(),c.endDate(),c.quantity(),v.dailyPrice(),v.depositPerUnit());
            db.update("""
                INSERT INTO rental_order_items(order_id,product_id,variant_id,shop_id,product_name,variant_label,shop_name,image_url,
                  quantity,start_date,end_date,blocked_until,rental_days,daily_price,deposit_per_unit,rental_total,deposit_total)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,id,v.productId(),v.id(),v.shopId(),v.productName(),v.label(),v.shopName(),v.imageUrl(),c.quantity(),c.startDate(),c.endDate(),
                rules.blockedUntil(c.endDate()),price.days(),v.dailyPrice(),v.depositPerUnit(),price.rental(),price.deposit());
        }
        db.update("INSERT INTO order_events(order_id,event_type,description,created_at) VALUES(?,?,?,?)",id,"ORDER_CREATED","Order saved; payment has not been collected.",now);
        carts.clearAfterCheckout(userId);
        return detail(userId,id);
    }
    private static Instant instant(ResultSet rs,String column) throws SQLException {
        java.sql.Timestamp t=rs.getTimestamp(column);return t==null?null:t.toLocalDateTime().toInstant(ZoneOffset.UTC);
    }
    private List<OrderItem> items(long id) {
        return db.query("SELECT * FROM rental_order_items WHERE order_id=? ORDER BY id",(rs,n)->new OrderItem(rs.getLong("id"),rs.getLong("product_id"),rs.getLong("variant_id"),rs.getLong("shop_id"),
            rs.getString("product_name"),rs.getString("variant_label"),rs.getString("shop_name"),AssetPaths.normalize(rs.getString("image_url")),rs.getInt("quantity"),
            rs.getDate("start_date").toLocalDate(),rs.getDate("end_date").toLocalDate(),rs.getInt("rental_days"),rs.getBigDecimal("daily_price"),rs.getBigDecimal("deposit_per_unit"),rs.getBigDecimal("rental_total"),rs.getBigDecimal("deposit_total")),id);
    }
    public OrderView detail(long userId,long id) {
        var found=db.query("SELECT *,"+EFFECTIVE+" AS effective_status FROM rental_orders WHERE id=? AND user_id=?",(rs,n)->new OrderView(rs.getLong("id"),rs.getString("code"),rs.getString("effective_status"),
            rs.getString("payment_status"),rs.getString("payment_method"),rs.getString("recipient_name"),rs.getString("recipient_phone"),rs.getString("note"),
            rs.getBigDecimal("rental_total"),rs.getBigDecimal("deposit_total"),rs.getBigDecimal("total_due"),rs.getBigDecimal("paid_total"),
            instant(rs,"created_at"),instant(rs,"hold_expires_at"),List.of(),List.of()),id,userId);
        if(found.isEmpty()) throw ApiException.missing();
        OrderView o=found.get(0);
        var events=db.query("SELECT event_type,description,created_at FROM order_events WHERE order_id=? ORDER BY created_at,id",(rs,n)->new OrderEvent(rs.getString("event_type"),rs.getString("description"),instant(rs,"created_at")),id);
        return new OrderView(o.id(),o.code(),o.status(),o.paymentStatus(),o.paymentMethod(),o.recipientName(),o.recipientPhone(),o.note(),o.rentalTotal(),o.depositTotal(),o.totalDue(),o.paidTotal(),o.createdAt(),o.holdExpiresAt(),items(id),events);
    }
    public OrderPage list(long userId,int page,int size,String status) {
        if(page<1 || size<1 || size>50 || page>100000 || (!status.isEmpty() && !STATUSES.contains(status))) throw ApiException.bad("INVALID_INPUT");
        String filter=" WHERE user_id=? AND (?='' OR "+EFFECTIVE+"=?)";
        Long count=db.queryForObject("SELECT COUNT(*) FROM rental_orders"+filter,Long.class,userId,status,status);
        long total=count==null?0:count;
        var ids=db.queryForList("SELECT id FROM rental_orders"+filter+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",Long.class,userId,status,status,size,(page-1)*size);
        return new OrderPage(ids.stream().map(id->detail(userId,id)).toList(),page,size,total,(int)Math.ceil(total/(double)size));
    }
    @Scheduled(fixedDelay=60000,initialDelay=60000)
    public void expireUnconfirmedOrders() {
        // Availability ignores expired holds immediately, even before this periodic normalization.
        try {db.update("UPDATE rental_orders SET status='EXPIRED' WHERE status='PENDING_CONFIRMATION' AND hold_expires_at<=UTC_TIMESTAMP(6)");}
        catch(org.springframework.dao.DataAccessException e) {org.slf4j.LoggerFactory.getLogger(RentalOrderService.class).warn("Expiry sweep skipped: database unavailable or schema not imported.");}
    }
}
