package vn.clarity.controller;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.clarity.security.ClarityUserPrincipal;
import vn.clarity.service.RentalOrderService;
import vn.clarity.model.Models.*;
import vn.clarity.dto.Requests.Checkout;
@RestController
@RequestMapping("/api/orders")
public class RentalOrderController {
    private final RentalOrderService orders;
    public RentalOrderController(RentalOrderService orders) {this.orders=orders;}
    @PostMapping public OrderView create(@AuthenticationPrincipal ClarityUserPrincipal p,@Valid @RequestBody Checkout r,
        @RequestHeader("Idempotency-Key") String key) {return orders.create(p.id(),r,key);}
    @GetMapping public OrderPage list(@AuthenticationPrincipal ClarityUserPrincipal p,@RequestParam(defaultValue="1") int page,
        @RequestParam(defaultValue="10") int size,@RequestParam(defaultValue="") String status) {return orders.list(p.id(),page,size,status);}
    @GetMapping("/{id}") public OrderView get(@AuthenticationPrincipal ClarityUserPrincipal p,@PathVariable long id) {return orders.detail(p.id(),id);}
}
