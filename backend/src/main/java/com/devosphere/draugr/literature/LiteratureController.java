package com.devosphere.draugr.literature;

import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/literature") @CrossOrigin(origins="${draugr.frontend-origin:http://localhost:5173}")
public class LiteratureController {
    private final LiteratureService literature; private final JdbcTemplate jdbc;
    public LiteratureController(LiteratureService literature,JdbcTemplate jdbc){this.literature=literature;this.jdbc=jdbc;}
    @GetMapping public List<LiteratureService.DocumentView> reachable(){return literature.reachable(active());}
    @GetMapping("/{id}") public LiteratureService.RevisionView current(@PathVariable UUID id){return literature.current(id,active());}
    private UUID active(){UUID id=jdbc.query("SELECT id FROM chronicle WHERE life_state='LIVING'",rs->rs.next()?rs.getObject(1,UUID.class):null);if(id==null)throw new IllegalStateException("No living Chronicle exists.");return id;}
}
