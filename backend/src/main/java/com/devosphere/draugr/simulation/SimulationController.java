package com.devosphere.draugr.simulation;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/simulation")
@CrossOrigin(origins = {"${draugr.frontend-origin:http://localhost:5173}", "http://127.0.0.1:5173"})
public class SimulationController {
    private final SimulationTickService ticks;
    public SimulationController(SimulationTickService ticks) { this.ticks = ticks; }
    @PostMapping("/ticks") @ResponseStatus(HttpStatus.CREATED)
    public SimulationTickService.SimulationTick advance() { return ticks.advance(); }
    @GetMapping
    public SimulationTickService.SimulationTick current() { return ticks.current(); }
}
