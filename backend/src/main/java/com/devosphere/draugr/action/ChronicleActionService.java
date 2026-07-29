package com.devosphere.draugr.action;

import com.devosphere.draugr.chronicle.ChroniclePhysiologyService;
import com.devosphere.draugr.narration.NarrationPolicy;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.capability.CapabilityAdaptationService;
import com.devosphere.draugr.simulation.SimulationTickService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ChronicleActionService {
    private final JdbcTemplate jdbc; private final SimulationTickService ticks; private final ChroniclePhysiologyService physiology; private final NarrationPolicy narration; private final PhysicalItemService items; private final CapabilityAdaptationService capability;
    public ChronicleActionService(JdbcTemplate jdbc, SimulationTickService ticks, ChroniclePhysiologyService physiology, NarrationPolicy narration, PhysicalItemService items, CapabilityAdaptationService capability) { this.jdbc = jdbc; this.ticks = ticks; this.physiology = physiology; this.narration = narration; this.items=items; this.capability=capability; }

    @Transactional
    public ActionResult resolve(String text) {
        if (text == null || text.trim().isEmpty() || text.length() > 2500) throw new IllegalArgumentException("An action must contain 1 to 2500 characters.");
        ActiveChronicle chronicle = jdbc.query("SELECT c.id, w.current_location_id FROM chronicle c JOIN world_object w ON w.id=c.id WHERE c.life_state='LIVING' FOR UPDATE", rs -> rs.next() ? new ActiveChronicle(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)) : null);
        if (chronicle == null) throw new IllegalStateException("No living Chronicle exists.");
        Intent intent = classify(text); int minutes = intent == Intent.OBSERVE ? 10 : intent == Intent.REST ? 60 : intent == Intent.GATHER_FIBER ? 25 : 5;
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
        else if (intent == Intent.GATHER_FIBER) { int bundles=items.gatherPlantFiber(chronicle.id(),chronicle.location()); perception="You patiently separate usable plant fiber from the living growth around you."; jdbc.update("INSERT INTO chronicle_action_effect (action_id, effect_domain, effect_type, payload) VALUES (?, 'ITEM', 'PLANT_FIBER_GATHERED', jsonb_build_object('bundles', ?))", actionId, bundles); }
        else if (intent == Intent.BUILD_FIRE_PIT) { outcome = "FAILED"; perception = "You set a few stones apart, then leave them where they lie. The ground remains unchanged."; }
        else { outcome = "FAILED"; perception = "The attempt passes without changing the immediate world around you."; }
        jdbc.update("INSERT INTO chronicle_action (id, chronicle_id, resolved_at, action_text, intent_type, outcome, duration_minutes, narration) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", actionId, chronicle.id(), resolvedAt, text.trim(), intent.name(), outcome, minutes, perception);
        jdbc.update("INSERT INTO chronicle_action_effect (action_id, effect_domain, effect_type, payload) VALUES (?, 'TIME', 'TIME_ADVANCED', jsonb_build_object('minutes', ?))", actionId, minutes);
        jdbc.update("INSERT INTO chronicle_event (chronicle_id, occurred_at, event_type, payload) VALUES (?, ?, 'CHRONICLE_ACTION_RESOLVED', jsonb_build_object('actionId', ?::text, 'intent', ?, 'outcome', ?))", chronicle.id(), resolvedAt, actionId.toString(), intent.name(), outcome);
        if ("SUCCEEDED".equals(outcome)) capability.record(chronicle.id(), actionId, intent==Intent.GATHER_FIBER?"LOAD":intent==Intent.OBSERVE?"ATTENTION":intent==Intent.REST?"RECOVERY":"FINE_MOTOR", minutes, intent==Intent.GATHER_FIBER?.18:.05, intent==Intent.REST?.75:.45, resolvedAt);
        narration.validate(perception);
        return new ActionResult(actionId, intent.name(), outcome, minutes, resolvedAt, perception, physiology.activeBody());
    }
    @Transactional(readOnly = true)
    public NarrationPage narrationHistory(Instant before, UUID beforeId, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 50));
        UUID chronicle = jdbc.query("SELECT id FROM chronicle WHERE life_state='LIVING'", rs -> rs.next() ? rs.getObject(1, UUID.class) : null);
        if (chronicle == null) return new NarrationPage(List.of(), false);
        if ((before == null) != (beforeId == null)) throw new IllegalArgumentException("Narration cursor requires both time and action identity.");
        List<NarrationEntry> entries = before == null
                ? jdbc.query("SELECT id, resolved_at, narration FROM chronicle_action WHERE chronicle_id = ? AND narration IS NOT NULL ORDER BY resolved_at DESC, id DESC LIMIT ?", (rs, row) -> new NarrationEntry(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant(), rs.getString(3)), chronicle, limit + 1)
                : jdbc.query("SELECT id, resolved_at, narration FROM chronicle_action WHERE chronicle_id = ? AND narration IS NOT NULL AND (resolved_at, id) < (?, ?) ORDER BY resolved_at DESC, id DESC LIMIT ?", (rs, row) -> new NarrationEntry(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant(), rs.getString(3)), chronicle, java.sql.Timestamp.from(before), beforeId, limit + 1);
        boolean hasMore = entries.size() > limit;
        if (hasMore) entries = entries.subList(0, limit);
        return new NarrationPage(List.copyOf(entries), hasMore);
    }
    private String observe(UUID location) { Integer sites = jdbc.queryForObject("SELECT COUNT(*) FROM ecology_site WHERE chunk_id = ?", Integer.class, location); return sites != null && sites > 0 ? "You notice signs that this place has been shaped by more than rain and roots alone." : "Rain-darkened ground, roots, and wet leaves hold the nearest details of the forest."; }
    private Intent classify(String action) { String value = action.toLowerCase(Locale.ROOT); if (value.contains("fire pit") || value.contains("firepit")) return Intent.BUILD_FIRE_PIT; if ((value.contains("gather")||value.contains("collect")) && value.contains("fiber")) return Intent.GATHER_FIBER; if (value.contains("observe") || value.contains("look") || value.contains("inspect")) return Intent.OBSERVE; if (value.contains("rest") || value.contains("wait")) return Intent.REST; if (value.contains("urinate") || value.contains("pee")) return Intent.URINATE; if (value.contains("defecate") || value.contains("poop")) return Intent.DEFECATE; return Intent.UNKNOWN; }
    private record ActiveChronicle(UUID id, UUID location) { } private enum Intent { OBSERVE, REST, GATHER_FIBER, BUILD_FIRE_PIT, URINATE, DEFECATE, UNKNOWN }
    public record ActionResult(UUID actionId, String intent, String outcome, int durationMinutes, Instant resolvedAt, String perception, ChroniclePhysiologyService.BodyHudSnapshot body) { }
    public record NarrationEntry(UUID id, Instant occurredAt, String narration) { }
    public record NarrationPage(List<NarrationEntry> entries, boolean hasMore) { }
}
