package vn.clarity.model;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class Models {
    private Models() {}
    public record UserView(long id,String name,String email,String phone,String role) {}
    public record ImageView(String url,String alt) {}
    public record VariantView(long id,String sku,String label,int stockQuantity) {}
    public record ProductView(long id,String name,String series,String category,String categoryName,
                              String description,long shopId,String shopName,String city,
                              BigDecimal dailyPrice,BigDecimal depositPerUnit,String imageUrl,
                              List<VariantView> variants,List<ImageView> images) {}
    public record VariantData(long id,long productId,long shopId,String productName,String label,
                              String shopName,String city,String imageUrl,int stockQuantity,
                              BigDecimal dailyPrice,BigDecimal depositPerUnit,boolean active) {}
    public record CartData(long id,long variantId,int quantity,LocalDate startDate,LocalDate endDate) {}
    public record CartLine(long id,long variantId,long productId,long shopId,String productName,
                           String variantLabel,String shopName,String city,String imageUrl,
                           int quantity,LocalDate startDate,LocalDate endDate,int rentalDays,
                           BigDecimal dailyPrice,BigDecimal depositPerUnit,
                           BigDecimal rentalTotal,BigDecimal depositTotal,boolean active) {}
    public record CartView(long version,List<CartLine> items,BigDecimal rentalTotal,
                           BigDecimal depositTotal,BigDecimal total,int units,boolean canCheckout,List<String> problems) {}
    public record Availability(long variantId,LocalDate startDate,LocalDate endDate,int days,
                               int availableQuantity,int cleaningDays,BigDecimal rentalTotal,
                               BigDecimal depositTotal,BigDecimal total,boolean available) {}
    public record OrderItem(long id,long productId,long variantId,long shopId,String productName,
                            String variantLabel,String shopName,String imageUrl,int quantity,
                            LocalDate startDate,LocalDate endDate,int rentalDays,
                            BigDecimal dailyPrice,BigDecimal depositPerUnit,BigDecimal rentalTotal,BigDecimal depositTotal) {}
    public record OrderEvent(String type,String description,Instant at) {}
    public record OrderView(long id,String code,String status,String paymentStatus,String paymentMethod,
                            String recipientName,String recipientPhone,String note,
                            BigDecimal rentalTotal,BigDecimal depositTotal,BigDecimal totalDue,BigDecimal paidTotal,
                            Instant createdAt,Instant holdExpiresAt,List<OrderItem> items,List<OrderEvent> events) {}
    public record OrderPage(List<OrderView> items,int page,int size,long total,int totalPages) {}
}
