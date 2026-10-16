package vn.clarity.controller;
import org.springframework.web.bind.annotation.*;
import vn.clarity.service.*;
import vn.clarity.model.Models.*;
import java.time.LocalDate;
import java.util.*;
@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService products;
    private final RentalInventoryService inventory;
    public ProductController(ProductService products,RentalInventoryService inventory) {this.products=products;this.inventory=inventory;}
    @GetMapping public Map<String,Object> list(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String category,@RequestParam(defaultValue="default") String sort) {return Map.of("items",products.list(q.trim(),category,sort));}
    @GetMapping("/{id}") public ProductView get(@PathVariable long id) {return products.get(id);}
    @GetMapping("/{id}/availability") public Availability availability(@PathVariable long id,@RequestParam long variantId,
        @RequestParam LocalDate startDate,@RequestParam LocalDate endDate,@RequestParam(defaultValue="1") int quantity) {
        if(products.get(id).variants().stream().noneMatch(v->v.id()==variantId)) throw vn.clarity.exception.ApiException.missing();
        return inventory.availability(variantId,startDate,endDate,quantity);
    }
}
