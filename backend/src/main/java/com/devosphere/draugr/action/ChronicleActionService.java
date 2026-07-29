package com.devosphere.draugr.action;

import com.devosphere.draugr.chronicle.ChroniclePhysiologyService;
import com.devosphere.draugr.narration.NarrationPolicy;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.capability.CapabilityAdaptationService;
import com.devosphere.draugr.construction.ConstructionService;
import com.devosphere.draugr.chronicle.ChronicleDiscoveryService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
import com.devosphere.draugr.construction.FireService;
import com.devosphere.draugr.simulation.SimulationTickService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChronicleActionService {
    private static final Pattern DURATION = Pattern.compile("\\b(?:for\\s+)?(\\d{1,3})\\s*(minute|min|minutes|mins|hour|hours|hr|hrs)\\b", Pattern.CASE_INSENSITIVE);
    private final JdbcTemplate jdbc; private final SimulationTickService ticks; private final ChroniclePhysiologyService physiology; private final NarrationPolicy narration; private final PhysicalItemService items; private final CapabilityAdaptationService capability; private final ConstructionService construction; private final ChronicleDiscoveryService discoveries; private final WildlifeEncounterService wildlife; private final FireService fire;
    public ChronicleActionService(JdbcTemplate jdbc, SimulationTickService ticks, ChroniclePhysiologyService physiology, NarrationPolicy narration, PhysicalItemService items, CapabilityAdaptationService capability, ConstructionService construction, ChronicleDiscoveryService discoveries, WildlifeEncounterService wildlife, FireService fire) { this.jdbc = jdbc; this.ticks = ticks; this.physiology = physiology; this.narration = narration; this.items=items; this.capability=capability; this.construction=construction; this.discoveries=discoveries; this.wildlife=wildlife; this.fire=fire; }

    @Transactional
    public ActionResult resolve(String text) {
        if (text == null || text.trim().isEmpty() || text.length() > 2500) throw new IllegalArgumentException("An action must contain 1 to 2500 characters.");
        ActiveChronicle chronicle = jdbc.query("SELECT c.id, w.current_location_id FROM chronicle c JOIN world_object w ON w.id=c.id WHERE c.life_state='LIVING' FOR UPDATE", rs -> rs.next() ? new ActiveChronicle(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)) : null);
        if (chronicle == null) throw new IllegalStateException("No living Chronicle exists.");
        Intent intent = classify(text); int minutes = durationFor(text, intent);
        Instant resolvedAt = ticks.advanceBy(Duration.ofMinutes(minutes)).simulatedAt();
        UUID actionId = UUID.randomUUID(); String outcome = "SUCCEEDED"; String perception;
        if (intent == Intent.OBSERVE) perception = observe(chronicle.location());
        else if (intent == Intent.MOVE) perception = move(chronicle, text, actionId, resolvedAt);
        else if (intent == Intent.URINATE || intent == Intent.DEFECATE) {
            boolean bowel = intent == Intent.DEFECATE;
            physiology.applyRelief(chronicle.id(), bowel);
            UUID waste = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id, object_type, display_name, current_location_id) VALUES (?, 'WASTE', ?, ?)", waste, bowel ? "Human waste" : "Urine-soaked ground", chronicle.location());
            perception = "You take a brief moment away from the immediate ground around you.";
        } else if (intent == Intent.REST) { physiology.rest(chronicle.id(), minutes); perception = "You remain still while the forest continues around you."; }
        else if (intent == Intent.GATHER_FIBER) { int bundles=items.gatherPlantFiber(chronicle.id(),chronicle.location()); perception="You patiently separate usable plant fiber from the living growth around you."; jdbc.update("INSERT INTO chronicle_action_effect (action_id, effect_domain, effect_type, payload) VALUES (?, 'ITEM', 'PLANT_FIBER_GATHERED', jsonb_build_object('bundles', ?))", actionId, bundles); }
        else if (intent == Intent.GATHER_STONE) { int stones=items.gatherFieldStones(chronicle.id(),chronicle.location()); perception="You work loose a few stones from the ground and carry them with you."; jdbc.update("INSERT INTO chronicle_action_effect (action_id, effect_domain, effect_type, payload) VALUES (?, 'ITEM', 'FIELD_STONE_GATHERED', jsonb_build_object('stones', ?))", actionId, stones); }
        else if (intent == Intent.GATHER_BERRIES) { int berries=items.gatherWildBerries(chronicle.id(),chronicle.location()); perception="You gather a small handful of ripe berries from the living growth."; jdbc.update("INSERT INTO chronicle_action_effect (action_id,effect_domain,effect_type,payload) VALUES (?,'ITEM','WILD_BERRIES_GATHERED',jsonb_build_object('berries',?))",actionId,berries); }
        else if (intent == Intent.GATHER_BRANCHES) { int branches=items.gatherDryBranches(chronicle.id(),chronicle.location()); perception="You gather a few dry branches from beneath the trees."; jdbc.update("INSERT INTO chronicle_action_effect (action_id,effect_domain,effect_type,payload) VALUES (?,'ITEM','DRY_BRANCH_GATHERED',jsonb_build_object('branches',?))",actionId,branches); }
        else if (intent == Intent.EAT) { if(items.consumeOne(chronicle.id(),"wild_berries",resolvedAt)){physiology.eat(chronicle.id()); perception="The berries break softly between your teeth, leaving a faint sweetness behind.";}else{outcome="FAILED";perception="You search through what you can reach, then lower your hand again.";} }
        else if (intent == Intent.DRINK) { String biome=jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?",String.class,chronicle.location()); if("WETLAND".equals(biome)){physiology.drink(chronicle.id());perception="You drink from the moving water and let the cold settle in your throat.";}else{outcome="FAILED";perception="You pause, but find nothing here that you can drink.";} }
        else if (intent == Intent.WASH) { String biome=jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?",String.class,chronicle.location()); if("WETLAND".equals(biome)){physiology.wash(chronicle.id());perception="Cold water runs over your hands and skin, carrying away some of the dirt.";}else{outcome="FAILED";perception="You pause, but find no water here to wash with.";} }
        else if (intent == Intent.CONFRONT_WILDLIFE) { WildlifeEncounterService.EncounterResult result=wildlife.confront(chronicle.id(),chronicle.location(),actionId,resolvedAt); outcome=result.encountered()?"PARTIAL":"FAILED"; perception=result.narration(); }
        else if (intent == Intent.LIGHT_FIRE) { if(fire.light(chronicle.id(),chronicle.location(),resolvedAt)) perception="A small flame takes among the branches and steadies within the ring of stone."; else {outcome="FAILED"; perception="The attempt leaves the air unchanged.";} }
        else if (intent == Intent.START_LEAN_TO) { if (construction.startLeanTo(chronicle.id(), chronicle.location(), actionId, resolvedAt)) perception="You mark out a low shelter frame against the weather."; else { outcome="FAILED"; perception="The ground holds the same unfinished frame you found there."; } }
        else if (intent == Intent.WORK_LEAN_TO) { if (construction.workLeanTo(chronicle.id(), chronicle.location(), resolvedAt)) perception="You bind the shelter frame a little further into place."; else { outcome="FAILED"; perception="The unfinished frame remains as it was."; } }
        else if (intent == Intent.CRAFT_BASKET) { try { items.craftBasket(); perception="Plant fiber tightens beneath your hands until a rough basket holds its shape."; } catch (IllegalStateException ignored) { outcome="FAILED"; perception="The loose fibers shift and fall away from one another. Nothing holds its shape."; } }
        else if (intent == Intent.BUILD_FIRE_PIT) { if (construction.buildFirePit(chronicle.id(), chronicle.location(), actionId, resolvedAt)) perception = "You settle stone into a low, deliberate ring. The fire pit remains where you made it."; else { outcome = "FAILED"; perception = "You set a few stones apart, then leave them where they lie. The ground remains unchanged."; } }
        else { outcome = "FAILED"; perception = "The attempt passes without changing the immediate world around you."; }
        jdbc.update("INSERT INTO chronicle_action (id, chronicle_id, resolved_at, action_text, intent_type, outcome, duration_minutes, narration) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", actionId, chronicle.id(), resolvedAt, text.trim(), intent.name(), outcome, minutes, perception);
        jdbc.update("INSERT INTO chronicle_action_effect (action_id, effect_domain, effect_type, payload) VALUES (?, 'TIME', 'TIME_ADVANCED', jsonb_build_object('minutes', ?))", actionId, minutes);
        jdbc.update("INSERT INTO chronicle_event (chronicle_id, occurred_at, event_type, payload) VALUES (?, ?, 'CHRONICLE_ACTION_RESOLVED', jsonb_build_object('actionId', ?::text, 'intent', ?, 'outcome', ?))", chronicle.id(), resolvedAt, actionId.toString(), intent.name(), outcome);
        if ("SUCCEEDED".equals(outcome) && intent == Intent.CRAFT_BASKET) discoveries.record(chronicle.id(), "WOVEN_BASKET", actionId, resolvedAt);
        if ("SUCCEEDED".equals(outcome) && intent == Intent.BUILD_FIRE_PIT) discoveries.record(chronicle.id(), "STONE_FIRE_PIT", actionId, resolvedAt);
        if ("SUCCEEDED".equals(outcome)) capability.record(chronicle.id(), actionId, (intent==Intent.GATHER_FIBER||intent==Intent.GATHER_STONE)?"LOAD":intent==Intent.OBSERVE?"ATTENTION":intent==Intent.REST?"RECOVERY":"FINE_MOTOR", minutes, (intent==Intent.GATHER_FIBER||intent==Intent.GATHER_STONE)?.18:.05, intent==Intent.REST?.75:.45, resolvedAt);
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
    private String move(ActiveChronicle chronicle, String action, UUID actionId, Instant occurredAt) {
        Direction direction = Direction.from(action);
        if (direction == null) return "You shift through the wet ground, but do not commit to a direction.";
        UUID destination = jdbc.query("SELECT next.id FROM world_chunk current JOIN world_chunk next ON next.world_id=current.world_id AND next.grid_x=current.grid_x+? AND next.grid_y=current.grid_y+? WHERE current.id=?", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, direction.dx, direction.dy, chronicle.location());
        if (destination == null) return "The ground gives way toward the edge of what you can cross. You turn back before leaving the land behind.";
        jdbc.update("UPDATE world_object SET current_location_id=?, updated_at=? WHERE id=?", destination, occurredAt, chronicle.id());
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'MOVED',jsonb_build_object('fromLocationId',?::text,'toLocationId',?::text,'direction',?))", chronicle.id(), occurredAt, chronicle.location().toString(), destination.toString(), direction.name());
        jdbc.update("INSERT INTO chronicle_event (chronicle_id,occurred_at,event_type,payload) VALUES (?,?,'CHRONICLE_MOVED',jsonb_build_object('fromLocationId',?::text,'toLocationId',?::text,'direction',?))", chronicle.id(), occurredAt, chronicle.location().toString(), destination.toString(), direction.name());
        return "You travel " + direction.description + ", leaving the last stand of trees behind you.";
    }
    private int durationFor(String action, Intent intent) {
        Matcher match = DURATION.matcher(action);
        if (match.find()) { int amount = Integer.parseInt(match.group(1)); int minutes = match.group(2).toLowerCase(Locale.ROOT).startsWith("h") ? amount * 60 : amount; return Math.max(1, Math.min(minutes, 24 * 60)); }
        return switch (intent) { case OBSERVE -> 10; case REST -> 60; case GATHER_FIBER, GATHER_STONE, GATHER_BERRIES, GATHER_BRANCHES -> 25; case EAT, DRINK, LIGHT_FIRE -> 5; case CONFRONT_WILDLIFE -> 10; case CRAFT_BASKET -> 45; case BUILD_FIRE_PIT, START_LEAN_TO -> 30; case WORK_LEAN_TO -> 45; case MOVE -> 30; default -> 5; };
    }
    private Intent classify(String action) { String value=action.toLowerCase(Locale.ROOT); if(value.contains("lean-to") || value.contains("lean to")) return (value.contains("work") || value.contains("continue") || value.contains("build") || value.contains("weave") || value.contains("bind")) ? Intent.WORK_LEAN_TO : Intent.START_LEAN_TO; if(value.contains("wash")||value.contains("bathe")||value.contains("clean myself"))return Intent.WASH; return classifyLegacy(action); }
    private Intent classifyLegacy(String action) { String value = action.toLowerCase(Locale.ROOT); if ((value.contains("light")||value.contains("ignite")) && value.contains("fire")) return Intent.LIGHT_FIRE; if (value.contains("fire pit") || value.contains("firepit")) return Intent.BUILD_FIRE_PIT; if ((value.contains("fight")||value.contains("attack")||value.contains("strike")) && (value.contains("animal")||value.contains("wildlife")||value.contains("creature"))) return Intent.CONFRONT_WILDLIFE; if ((value.contains("weave") || value.contains("craft") || value.contains("make")) && value.contains("basket")) return Intent.CRAFT_BASKET; if ((value.contains("gather")||value.contains("collect")) && value.contains("fiber")) return Intent.GATHER_FIBER; if ((value.contains("gather")||value.contains("collect")) && (value.contains("branch")||value.contains("stick"))) return Intent.GATHER_BRANCHES; if ((value.contains("gather")||value.contains("collect")) && (value.contains("berry")||value.contains("berries"))) return Intent.GATHER_BERRIES; if ((value.contains("gather")||value.contains("collect")) && (value.contains("stone")||value.contains("rock"))) return Intent.GATHER_STONE; if (value.contains("eat")||value.contains("consume")) return Intent.EAT; if (value.contains("drink")) return Intent.DRINK; if (Direction.from(value) != null && (value.contains("walk") || value.contains("travel") || value.contains("go ") || value.contains("move"))) return Intent.MOVE; if (value.contains("observe") || value.contains("look") || value.contains("inspect")) return Intent.OBSERVE; if (value.contains("rest") || value.contains("wait")) return Intent.REST; if (value.contains("urinate") || value.contains("pee")) return Intent.URINATE; if (value.contains("defecate") || value.contains("poop")) return Intent.DEFECATE; return Intent.UNKNOWN; }
    private record ActiveChronicle(UUID id, UUID location) { } private enum Intent { OBSERVE, MOVE, REST, GATHER_FIBER, GATHER_STONE, GATHER_BERRIES, GATHER_BRANCHES, EAT, DRINK, WASH, LIGHT_FIRE, CONFRONT_WILDLIFE, CRAFT_BASKET, BUILD_FIRE_PIT, START_LEAN_TO, WORK_LEAN_TO, URINATE, DEFECATE, UNKNOWN }
    private enum Direction { NORTH(0,-1,"north"), SOUTH(0,1,"south"), EAST(1,0,"east"), WEST(-1,0,"west"); final int dx; final int dy; final String description; Direction(int dx,int dy,String description){this.dx=dx;this.dy=dy;this.description=description;} static Direction from(String action){String value=action.toLowerCase(Locale.ROOT); for(Direction direction:values()) if(value.matches(".*\\b"+direction.description+"\\b.*")) return direction; return null;} }
    public record ActionResult(UUID actionId, String intent, String outcome, int durationMinutes, Instant resolvedAt, String perception, ChroniclePhysiologyService.BodyHudSnapshot body) { }
    public record NarrationEntry(UUID id, Instant occurredAt, String narration) { }
    public record NarrationPage(List<NarrationEntry> entries, boolean hasMore) { }
}
