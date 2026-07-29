package com.devosphere.draugr.action;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/actions") @CrossOrigin(origins = "${draugr.frontend-origin:http://localhost:5173}")
public class ChronicleActionController {
    private final ChronicleActionService actions; public ChronicleActionController(ChronicleActionService actions) { this.actions = actions; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ChronicleActionService.ActionResult resolve(@RequestBody ActionRequest request) { return actions.resolve(request.text()); }
    public record ActionRequest(String text) { }
}
