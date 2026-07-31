package com.devosphere.draugr.domain;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/domains")
@CrossOrigin(origins = {"${draugr.frontend-origin:http://localhost:5173}", "http://127.0.0.1:5173"})
public class DomainRegistryController {
    private final DomainRegistryService domains; private final ArchitectRouter architect;
    public DomainRegistryController(DomainRegistryService domains, ArchitectRouter architect) { this.domains = domains; this.architect = architect; }
    @GetMapping public List<DomainRegistryService.Domain> list() { return domains.list(); }
    /** How much of the world is answerable without spending an Architect call. */
    @GetMapping("/coverage") public ArchitectRouter.Coverage coverage() { return architect.coverage(); }
    /** What the Architect would be asked to do for a given action, and whether it need be asked at all. */
    @GetMapping("/assess") public ArchitectRouter.Assessment assess(@org.springframework.web.bind.annotation.RequestParam String action) { return architect.assess(action); }
}
