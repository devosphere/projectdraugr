package com.devosphere.draugr.ai;

import com.devosphere.draugr.ai.PersistentStateArchitect.ArchitectProposal;
import com.devosphere.draugr.ai.PersistentStateArchitect.BacklogEntry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Operator/Overseer surface for the Persistent State Architect (authoring-time). Lets an operator
 * see the routing-miss backlog and ask the model to <b>draft</b> a migration proposal for a gap.
 *
 * <p>This surface only ever returns proposals — text for human review. It never applies a proposal;
 * a reviewed migration is added as a real {@code V*.sql} by a person, through the V53 gate. It is an
 * operator tool, never on the player action path.
 */
@RestController
@RequestMapping("/api/architect")
@CrossOrigin(origins = {"${draugr.frontend-origin:http://localhost:5173}", "http://127.0.0.1:5173"})
public class ArchitectController {

    private final PersistentStateArchitect architect;
    public ArchitectController(PersistentStateArchitect architect) { this.architect = architect; }

    /** The frequency-ranked unresolved gaps, worst first. */
    @GetMapping("/backlog")
    public List<BacklogEntry> backlog(@RequestParam(defaultValue = "20") int limit) {
        return architect.backlog(Math.max(1, Math.min(limit, 100)));
    }

    /**
     * Draft a proposal for the single worst gap in the backlog. 200 with the proposal when the AI
     * layer is on and the model responds; 204 when it is off, the backlog is empty, or the model
     * declines — nothing to review, no error.
     */
    @PostMapping("/propose-top")
    public ResponseEntity<ArchitectProposal> proposeTop() {
        List<BacklogEntry> top = architect.backlog(1);
        if (top.isEmpty()) return ResponseEntity.noContent().build();
        return architect.propose(top.get(0))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
