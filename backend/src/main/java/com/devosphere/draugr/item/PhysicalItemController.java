package com.devosphere.draugr.item;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
@RestController @RequestMapping("/api/items") @CrossOrigin(origins={"${draugr.frontend-origin:http://localhost:5173}", "http://127.0.0.1:5173"})
public class PhysicalItemController {
 private final PhysicalItemService items; public PhysicalItemController(PhysicalItemService items){this.items=items;}
 @GetMapping("/carried") public List<PhysicalItemService.ItemView> carried(){return items.carried();}
 @GetMapping("/state") public PhysicalItemService.ItemState state(){return items.state();}
}
