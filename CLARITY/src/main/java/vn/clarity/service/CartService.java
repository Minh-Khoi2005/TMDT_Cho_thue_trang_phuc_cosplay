package vn.clarity.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.clarity.dto.Requests.*;
import vn.clarity.exception.ApiException;
import vn.clarity.util.RentalCalculator;
import vn.clarity.model.Models.*;
import java.math.BigDecimal;
import java.util.*;

@Service
public class CartService {
    private final JdbcTemplate db;
    private final ProductService products;
    private final RentalInventoryService inventory;
    public CartService(JdbcTemplate db,ProductService products,RentalInventoryService inventory) { this.db=db;this.products=products;this.inventory=inventory; }
    public long lock(long userId) {
        db.update("INSERT INTO carts(user_id,version,updated_at) VALUES(?,0,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE user_id=user_id",userId);
        return Objects.requireNonNull(db.queryForObject("SELECT version FROM carts WHERE user_id=? FOR UPDATE",Long.class,userId));
    }
    public List<CartData> data(long userId) {
        return db.query("SELECT id,variant_id,quantity,start_date,end_date FROM cart_items WHERE user_id=? ORDER BY id",
            (rs,n)->new CartData(rs.getLong("id"),rs.getLong("variant_id"),rs.getInt("quantity"),rs.getDate("start_date").toLocalDate(),rs.getDate("end_date").toLocalDate()),userId);
    }
    private void changed(long userId) { db.update("UPDATE carts SET version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE user_id=?",userId); }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public CartView get(long userId) { long version=lock(userId);return snapshot(userId,version); }
    public CartView snapshot(long userId,long version) {
        List<CartData> rows=data(userId);
        List<CartLine> lines=new ArrayList<>();
        BigDecimal rental=BigDecimal.ZERO,deposit=BigDecimal.ZERO;
        for(CartData c:rows) {
            VariantData v=products.variant(c.variantId(),false);
            var price=RentalCalculator.price(c.startDate(),c.endDate(),c.quantity(),v.dailyPrice(),v.depositPerUnit());
            lines.add(new CartLine(c.id(),c.variantId(),v.productId(),v.shopId(),v.productName(),v.label(),v.shopName(),v.city(),v.imageUrl(),
                c.quantity(),c.startDate(),c.endDate(),price.days(),v.dailyPrice(),v.depositPerUnit(),price.rental(),price.deposit(),v.active()));
            rental=rental.add(price.rental());deposit=deposit.add(price.deposit());
        }
        List<String> problems=new ArrayList<>();
        try { inventory.validateCart(rows,false); } catch(ApiException e) { problems.add(e.code()); }
        return new CartView(version,lines,rental,deposit,rental.add(deposit),rows.stream().mapToInt(CartData::quantity).sum(),problems.isEmpty(),problems);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public CartView add(long userId,AddCart request) {
        long version=lock(userId);
        List<CartData> rows=new ArrayList<>(data(userId));
        CartData same=rows.stream().filter(c->c.variantId()==request.variantId() && c.startDate().equals(request.startDate()) && c.endDate().equals(request.endDate())).findFirst().orElse(null);
        int quantity=request.quantity()+(same==null?0:same.quantity());
        if(same!=null) rows.remove(same);
        rows.add(new CartData(same==null?0:same.id(),request.variantId(),quantity,request.startDate(),request.endDate()));
        inventory.validateCart(rows,false);
        if(same==null) db.update("INSERT INTO cart_items(user_id,variant_id,quantity,start_date,end_date) VALUES(?,?,?,?,?)",userId,request.variantId(),quantity,request.startDate(),request.endDate());
        else db.update("UPDATE cart_items SET quantity=? WHERE id=? AND user_id=?",quantity,same.id(),userId);
        changed(userId);return snapshot(userId,version+1);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public CartView update(long userId,long id,UpdateCart request) {
        long version=lock(userId);
        if(version!=request.cartVersion()) throw ApiException.conflict("CART_CHANGED");
        List<CartData> rows=new ArrayList<>(data(userId));
        CartData previous=rows.stream().filter(c->c.id()==id).findFirst().orElseThrow(ApiException::missing);
        if(rows.stream().anyMatch(c->c.id()!=id && c.variantId()==previous.variantId() && c.startDate().equals(request.startDate()) && c.endDate().equals(request.endDate()))) throw ApiException.conflict("DUPLICATE_CART_LINE");
        rows.remove(previous);
        rows.add(new CartData(id,previous.variantId(),request.quantity(),request.startDate(),request.endDate()));
        inventory.validateCart(rows,false);
        db.update("UPDATE cart_items SET quantity=?,start_date=?,end_date=? WHERE id=? AND user_id=?",request.quantity(),request.startDate(),request.endDate(),id,userId);
        changed(userId);return snapshot(userId,version+1);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public CartView delete(long userId,long id,long expectedVersion) {
        long version=lock(userId);
        if(version!=expectedVersion) throw ApiException.conflict("CART_CHANGED");
        if(db.update("DELETE FROM cart_items WHERE id=? AND user_id=?",id,userId)!=1) throw ApiException.missing();
        changed(userId);return snapshot(userId,version+1);
    }
    public void clearAfterCheckout(long userId) {
        db.update("DELETE FROM cart_items WHERE user_id=?",userId); changed(userId);
    }
}
