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

    /**
     * Which backdrop this place calls for (#225/#234).
     *
     * <p>The resolver was written as a pure function and then called by nothing but its own test, so no place in
     * the world actually got a backdrop out of it. This is where the decision is made for real: the same context
     * the payload above reports, run through the same precedence, every time.
     *
     * <p>The <b>fingerprint</b> is carried through deliberately. It is the answer to "is this still the same
     * place, unchanged" — so a caller can hold an image while it matches and only fetch or decode again when it
     * does not. Two different places can call for the same backdrop key; only the fingerprint says whether
     * anything about the place has moved. That is the difference between a cache that is correct and one that is
     * merely lucky.
     *
     * @param version the contract version, matching the payload's
     * @param key     the backdrop key, always present — {@link BackdropResolver#FALLBACK_KEY} when a place has
     *                nothing remarkable on it, which is an answer and not a failure
     * @param reason  why that key, in terms of what is underfoot; safe to show
     * @param fingerprint the visual context's fingerprint, for caching
     */
    public record Backdrop(int version, String key, String reason, String fingerprint) { }

    @GetMapping("/v1/backdrop")
    public Backdrop backdrop() {
        VisualContextService.VisualContext here = context.active(ticks.current().simulatedAt());
        BackdropResolver.Choice choice = BackdropResolver.resolve(here);
        return new Backdrop(VisualContextService.VERSION, choice.key(), choice.reason(),
            here == null ? null : here.fingerprint());
    }
}
