package com.devosphere.draugr.action;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController @RequestMapping("/api/actions") @CrossOrigin(origins = {"${draugr.frontend-origin:http://localhost:5173}", "http://127.0.0.1:5173"})
public class ChronicleActionController {
    private final ChronicleActionService actions; public ChronicleActionController(ChronicleActionService actions) { this.actions = actions; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ChronicleActionService.ActionResult resolve(@RequestBody ActionRequest request) {
        // Left on the request so a hard fault can be filed against the action that caused it (#83). The ledger row
        // for this action rolls back with the fault; these attributes are what survives into system_error_log.
        org.springframework.web.context.request.RequestAttributes attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            attributes.setAttribute(com.devosphere.draugr.web.SystemErrorRecorder.ACTION_TEXT_ATTRIBUTE, request.text(), org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
            if (request.idempotencyKey() != null)
                attributes.setAttribute(com.devosphere.draugr.web.SystemErrorRecorder.ACTION_KEY_ATTRIBUTE, request.idempotencyKey(), org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
        }
        return actions.resolvePlan(request.text(), request.idempotencyKey());
    }
    @GetMapping("/history") public ChronicleActionService.NarrationPage history(@RequestParam(required = false) Instant before, @RequestParam(required = false) UUID beforeId, @RequestParam(defaultValue = "20") int limit) { return actions.narrationHistory(before, beforeId, limit); }
    public record ActionRequest(String text, UUID idempotencyKey) { }
}
