package vn.clarity.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.clarity.exception.ApiException;
import vn.clarity.model.Models.*;
import vn.clarity.util.AssetPaths;
import java.util.*;

@Service
public class ProductService {
    private final JdbcTemplate db;
    public ProductService(JdbcTemplate db) { this.db=db; }
    private static final String BASE="""
        SELECT p.id,p.name,p.series,p.description,p.shop_id,p.daily_price,p.deposit_per_unit,
          c.code AS category,c.name AS category_name,s.name AS shop_name,s.city,
          COALESCE((SELECT i.url FROM product_images i WHERE i.product_id=p.id ORDER BY i.sort_order,i.id LIMIT 1),'/assets/img/favicon.svg') AS image_url
        FROM products p JOIN shops s ON s.id=p.shop_id JOIN categories c ON c.id=p.category_id
        WHERE p.active=1 AND s.active=1
        """;
    public ProductView get(long id) {
        var list=db.query(BASE+" AND p.id=?",(rs,n)->new ProductView(rs.getLong("id"),rs.getString("name"),rs.getString("series"),
            rs.getString("category"),rs.getString("category_name"),rs.getString("description"),rs.getLong("shop_id"),
            rs.getString("shop_name"),rs.getString("city"),rs.getBigDecimal("daily_price"),rs.getBigDecimal("deposit_per_unit"),
            AssetPaths.normalize(rs.getString("image_url")),List.of(),List.of()),id);
        if(list.isEmpty()) throw ApiException.missing();
        ProductView p=list.get(0);
        var variants=db.query("SELECT id,sku,label,stock_quantity FROM product_variants WHERE product_id=? AND active=1 ORDER BY id",
            (rs,n)->new VariantView(rs.getLong("id"),rs.getString("sku"),rs.getString("label"),rs.getInt("stock_quantity")),id);
        var images=db.query("SELECT url,alt_text FROM product_images WHERE product_id=? ORDER BY sort_order,id",
            (rs,n)->new ImageView(AssetPaths.normalize(rs.getString("url")),rs.getString("alt_text")),id);
        return new ProductView(p.id(),p.name(),p.series(),p.category(),p.categoryName(),p.description(),p.shopId(),p.shopName(),p.city(),
            p.dailyPrice(),p.depositPerUnit(),p.imageUrl(),variants,images);
    }
    public List<ProductView> list(String q,String category,String sort) {
        if(q.length()>120 || category.length()>30) throw ApiException.bad("INVALID_INPUT");
        String order=switch(sort) { case "asc"->"p.daily_price ASC,p.id";case "desc"->"p.daily_price DESC,p.id";default->"p.id";};
        // Only order-by identifiers are interpolated and they come from the closed switch above.
        String sql="SELECT p.id FROM products p JOIN shops s ON s.id=p.shop_id JOIN categories c ON c.id=p.category_id "
            +"WHERE p.active=1 AND s.active=1 AND (?='' OR LOCATE(?,CONCAT(p.name,' ',p.series,' ',s.name))>0) "
            +"AND (?='' OR c.code=?) ORDER BY "+order+" LIMIT 200";
        var ids=db.queryForList(sql,Long.class,q,q,category,category);
        return ids.stream().map(this::get).toList();
    }
    public VariantData variant(long id,boolean lock) {
        if(lock) {
            var locked=db.queryForList("SELECT id FROM product_variants WHERE id=? FOR UPDATE",Long.class,id);
            if(locked.isEmpty()) throw ApiException.missing();
        }
        var rows=db.query("""
            SELECT v.id,v.product_id,v.label,v.stock_quantity,p.shop_id,p.name AS product_name,
                p.daily_price,p.deposit_per_unit,s.name AS shop_name,s.city,
                (v.active AND p.active AND s.active) AS active,
                COALESCE((SELECT i.url FROM product_images i WHERE i.product_id=p.id ORDER BY i.sort_order,i.id LIMIT 1),'/assets/img/favicon.svg') AS image_url
            FROM product_variants v JOIN products p ON p.id=v.product_id JOIN shops s ON s.id=p.shop_id WHERE v.id=?
            """,(rs,n)->new VariantData(rs.getLong("id"),rs.getLong("product_id"),rs.getLong("shop_id"),rs.getString("product_name"),
                rs.getString("label"),rs.getString("shop_name"),rs.getString("city"),AssetPaths.normalize(rs.getString("image_url")),rs.getInt("stock_quantity"),
                rs.getBigDecimal("daily_price"),rs.getBigDecimal("deposit_per_unit"),rs.getBoolean("active")),id);
        if(rows.isEmpty()) throw ApiException.missing();
        return rows.get(0);
    }
}
