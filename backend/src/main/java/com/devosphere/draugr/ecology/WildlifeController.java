package com.devosphere.draugr.ecology;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/ecology/wildlife")
@CrossOrigin(origins = {"${draugr.frontend-origin:http://localhost:5173}", "http://127.0.0.1:5173"})
public class WildlifeController {
    private final WildlifeSimulationService wildlife;
    public WildlifeController(WildlifeSimulationService wildlife) { this.wildlife = wildlife; }
    @GetMapping public List<WildlifeSimulationService.PopulationView> populations() { return wildlife.populations(); }
}
