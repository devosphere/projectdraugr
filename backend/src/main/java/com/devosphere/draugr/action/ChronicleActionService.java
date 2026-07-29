package com.devosphere.draugr.action;

import com.devosphere.draugr.chronicle.ChroniclePhysiologyService;
import com.devosphere.draugr.narration.NarrationPolicy;
import com.devosphere.draugr.simulation.SimulationTickService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class ChronicleActionService {
    private final JdbcTemplate jdbc; private final SimulationTickService ticks; private final ChroniclePhysiologyService physiology; private final NarrationPolicy narration;
    public ChronicleActionService(JdbcTemplate jdbc, SimulationTickService ticks, ChroniclePhysiologyService physiology, NarrationPolicy narration) { this.jdbc = jdbc; this.ticks = ticks; this.physiology = physiology; this.narration = narration; }

    @Transactional
    public ActionResult resolve(String text) {
        if (text == null || text.trim().isEmpty() || text.length() > 2500) throw new IllegalArgumentException("An action must contain 1 to 2500 characters.");
        ActiveChronicle chronicle = jdbc.query("SELECT c.id, w.current_location_id FROM chronicle c JOIN world_object w ON w.id=c.id WHERE c.life_state='LIVING' FOR UPDATE", rs -> rs.next() ? new ActiveChronicle(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)) : null);
        if (chronicle == null) throw new IllegalStateException("No living Chronicle exists.");
        Intent intent = classify(text); int minutes = intent == Intent.OBSERVE ? 10 : intent == Intent.REST ? 60 : 5;
        Instant resolvedAt = ticks.advanceBy(Duration.ofMinutes(minutes)).simulatedAt();
        UUID actionId = UUID.randomUUID(); String outcome = "SUCCEEDED"; String perception;
        if (intent == Intent.OBSERVE) perception = observe(chronicle.location());
        else if (intent == Intent.URINATE || intent == Intent.DEFECATE) {
            boolean bowel = intent == Intent.DEFECATE;
            physiology.applyRelief(chronicle.id(), bowel);
            UUID waste = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id, object_type, display_name, current_location_id) VALUES (?, 'WASTE', ?, ?)", waste, bowel ? "Human waste" : "Urine-soaked ground", chronicle.location());
            perception = "You take a brief moment away from the immediate ground around you.";
        } else if (intent == Intent.REST) perception = "You remain still while the forest continues around you.";
        else { outcome = "FAILED"; perception = "The intention is not yet understood by the current deterministic resolver."; }
        jdbc.update("INSERT INTO chronicle_action (id, chronicle_id, resolved_at, action_text, intent_type, outcome, duration_minutes) VALUES (?, ?, ?, ?, ?, ?, ?)", actionId, chronicle.id(), resolvedAt, text.trim(), intent.name(), outcome, minutes);
        jdbc.update("INSERT INTO chronicle_action_effect (action_id, effect_domain, effect_type, payload) VALUES (?, 'TIME', 'TIME_ADVANCED', jsonb_build_object('minutes', ?))", actionId, minutes);
        jdbc.update("INSERT INTO chronicle_event (chronicle_id, occurred_at, event_type, payload) VALUES (?, ?, 'CHRONICLE_ACTION_RESOLVED', jsonb_build_object('actionId', ?::text, 'intent', ?, 'outcome', ?))", chronicle.id(), resolvedAt, actionId.toString(), intent.name(), outcome);
        narration.validate(perception);
        return new ActionResult(actionId, intent.name(), outcome, minutes, perception, physiology.activeBody());
    }
    private String observe(UUID location) { Integer sites = jdbc.queryForObject("SELECT COUNT(*) FROM ecology_site WHERE chunk_id = ?", Integer.class, location); return sites != null && sites > 0 ? "You notice signs that this place has been shaped by more than rain and roots alone." : "Rain-darkened ground, roots, and wet leaves hold the nearest details of the forest."; }
    private Intent classify(String action) { String value = action.toLowerCase(Locale.ROOT); if (value.contains("observe") || value.contains("look") || value.contains("inspect")) return Intent.OBSERVE; if (value.contains("rest") || value.contains("wait")) return Intent.REST; if (value.contains("urinate") || value.contains("pee")) return Intent.URINATE; if (value.contains("defecate") || value.contains("poop")) return Intent.DEFECATE; return Intent.UNKNOWN; }
    private record ActiveChronicle(UUID id, UUID location) { } private enum Intent { OBSERVE, REST, URINATE, DEFECATE, UNKNOWN }
    public record ActionResult(UUID actionId, String intent, String outcome, int durationMinutes, String perception, ChroniclePhysiologyService.BodyHudSnapshot body) { }
}
