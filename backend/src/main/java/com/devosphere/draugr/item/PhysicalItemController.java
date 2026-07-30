package com.devosphere.draugr.item;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
@RestController @RequestMapping("/api/items") @CrossOrigin(origins={"${draugr.frontend-origin:http://localhost:5173}", "http://127.0.0.1:5173"})
public class PhysicalItemController {
 private final PhysicalItemService items; public PhysicalItemController(PhysicalItemService items){this.items=items;}
 @GetMapping("/carried") public List<PhysicalItemService.ItemView> carried(){return items.carried();}
 @GetMapping("/state") public PhysicalItemService.ItemState state(){return items.state();}
 @PostMapping("/{id}/equip") public Map<String,Object> equip(@PathVariable UUID id, @RequestBody Map<String,String> body){ items.equip(id, body.get("position"), body.get("layer")); return Map.of("ok",true); }
 @PostMapping("/{id}/unequip") public Map<String,Object> unequip(@PathVariable UUID id){ boolean done=items.unequip(id,Instant.now()); return Map.of("ok",done); }
 @PostMapping("/{id}/drop") public Map<String,Object> drop(@PathVariable UUID id, @RequestBody Map<String,String> body){ UUID location=UUID.fromString(body.get("locationId")); items.drop(id,location,Instant.now()); return Map.of("ok",true); }
}
