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
    private final DomainRegistryService domains;
    public DomainRegistryController(DomainRegistryService domains) { this.domains = domains; }
    @GetMapping public List<DomainRegistryService.Domain> list() { return domains.list(); }
}
