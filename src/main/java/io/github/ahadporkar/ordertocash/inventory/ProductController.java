package io.github.ahadporkar.ordertocash.inventory;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

  private final InventoryService inventory;

  public ProductController(InventoryService inventory) {
    this.inventory = inventory;
  }

  @GetMapping
  public List<Product> catalog() {
    return inventory.catalog();
  }
}
