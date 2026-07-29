package com.devosphere.draugr.item;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/items") @CrossOrigin(origins="${draugr.frontend-origin:http://localhost:5173}")
public class PhysicalItemController {
 private final PhysicalItemService items; public PhysicalItemController(PhysicalItemService items){this.items=items;}
 @GetMapping("/carried") public List<PhysicalItemService.ItemView> carried(){return items.carried();}
 @GetMapping("/state") public PhysicalItemService.ItemState state(){return items.state();}
 @PostMapping("/craft/woven-basket") public PhysicalItemService.ItemView basket(){return items.craftBasket();}
 @PostMapping("/{itemId}/container/{containerId}") public void place(@PathVariable UUID itemId,@PathVariable UUID containerId){items.placeInContainer(itemId,containerId);}
 @PostMapping("/{itemId}/equip") public void equip(@PathVariable UUID itemId,@RequestBody EquipRequest request){items.equip(itemId,request.bodyPosition(),request.layer());}
 public record EquipRequest(String bodyPosition,String layer){}
}
