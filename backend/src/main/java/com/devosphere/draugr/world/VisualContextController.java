package com.devosphere.draugr.world;

import com.devosphere.draugr.simulation.SimulationTickService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the living Chronicle can see where they stand (#224/#232).
 *
 * <p>Read-only and versioned in the path, so a caller written against v1 keeps working when the shape grows.
 * The clock comes from the simulation, never the wall — the world's own time is what decides whether it is
 * night here.
 */
@RestController
@RequestMapping("/api/visual-context")
@CrossOrigin(origins = {"${draugr.frontend-origin:http://localhost:5173}", "http://127.0.0.1:5173"})
public class VisualContextController {

    private final VisualContextService context;
    private final SimulationTickService ticks;

    public VisualContextController(VisualContextService context, SimulationTickService ticks) {
        this.context = context;
        this.ticks = ticks;
    }

    @GetMapping("/v1")
    public VisualContextService.VisualContext current() {
        return context.active(ticks.current().simulatedAt());
    }
}
