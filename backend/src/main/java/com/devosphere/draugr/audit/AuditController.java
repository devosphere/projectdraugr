package com.devosphere.draugr.audit;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@CrossOrigin(origins = "${draugr.frontend-origin:http://localhost:5173}")
public class AuditController {
    private final PersistentStateAuditor auditor;
    public AuditController(PersistentStateAuditor auditor) { this.auditor = auditor; }
    @GetMapping public PersistentStateAuditor.AuditReport inspect() { return auditor.inspect(); }
}
