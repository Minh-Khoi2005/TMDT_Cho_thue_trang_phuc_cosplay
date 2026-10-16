package vn.clarity.controller;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.clarity.security.ClarityUserPrincipal;
import vn.clarity.service.CartService;
import vn.clarity.model.Models.CartView;
import vn.clarity.dto.Requests.*;
@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService carts;
    public CartController(CartService carts) {this.carts=carts;}
    @GetMapping public CartView get(@AuthenticationPrincipal ClarityUserPrincipal p) {return carts.get(p.id());}
    @PostMapping("/items") public CartView add(@AuthenticationPrincipal ClarityUserPrincipal p,@Valid @RequestBody AddCart request) {return carts.add(p.id(),request);}
    @PatchMapping("/items/{id}") public CartView update(@AuthenticationPrincipal ClarityUserPrincipal p,@PathVariable long id,@Valid @RequestBody UpdateCart request) {return carts.update(p.id(),id,request);}
    @DeleteMapping("/items/{id}") public CartView delete(@AuthenticationPrincipal ClarityUserPrincipal p,@PathVariable long id,@RequestParam long version) {return carts.delete(p.id(),id,version);}
}
