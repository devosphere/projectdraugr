package com.devosphere.draugr.action;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController @RequestMapping("/api/actions") @CrossOrigin(origins = "${draugr.frontend-origin:http://localhost:5173}")
public class ChronicleActionController {
    private final ChronicleActionService actions; public ChronicleActionController(ChronicleActionService actions) { this.actions = actions; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ChronicleActionService.ActionResult resolve(@RequestBody ActionRequest request) { return actions.resolve(request.text()); }
    @GetMapping("/history") public ChronicleActionService.NarrationPage history(@RequestParam(required = false) Instant before, @RequestParam(required = false) UUID beforeId, @RequestParam(defaultValue = "20") int limit) { return actions.narrationHistory(before, beforeId, limit); }
    public record ActionRequest(String text) { }
}
