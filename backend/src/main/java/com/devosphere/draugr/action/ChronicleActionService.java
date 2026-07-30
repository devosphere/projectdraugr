package com.devosphere.draugr.action;

import com.devosphere.draugr.chronicle.ChroniclePhysiologyService;
import com.devosphere.draugr.narration.NarrationPolicy;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.capability.CapabilityAdaptationService;
import com.devosphere.draugr.construction.ConstructionService;
import com.devosphere.draugr.chronicle.ChronicleDiscoveryService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
import com.devosphere.draugr.construction.FireService;
import com.devosphere.draugr.literature.LiteratureService;
import com.devosphere.draugr.survival.FoodPreservationService;
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
    private static final Pattern DOCUMENT_EDIT = Pattern.compile("(?is)^\\s*(append|replace|write)\\s+(?:to\\s+)?(?:document|journal|map)\\s+([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\s*:\\s*(.+)$");
    private static final Pattern WRITE_CONTENT = Pattern.compile("(?is):\\s*(.+)$");
    private final JdbcTemplate jdbc; private final SimulationTickService ticks; private final ChroniclePhysiologyService physiology; private final NarrationPolicy narration; private final PhysicalItemService items; private final CapabilityAdaptationService capability; private final ConstructionService construction; private final ChronicleDiscoveryService discoveries; private final WildlifeEncounterService wildlife; private final FireService fire; private final LiteratureService literature; private final FoodPreservationService food;
    public ChronicleActionService(JdbcTemplate jdbc, SimulationTickService ticks, ChroniclePhysiologyService physiology, NarrationPolicy narration, PhysicalItemService items, CapabilityAdaptationService capability, ConstructionService construction, ChronicleDiscoveryService discoveries, WildlifeEncounterService wildlife, FireService fire, LiteratureService literature, FoodPreservationService food) { this.jdbc = jdbc; this.ticks = ticks; this.physiology = physiology; this.narration = narration; this.items=items; this.capability=capability; this.construction=construction; this.discoveries=discoveries; this.wildlife=wildlife; this.fire=fire; this.literature=literature; this.food=food; }

    @Transactional
    public ActionResult resolve(String text) { return resolve(text, null); }

    @Transactional
    public ActionResult resolve(String text, UUID idempotencyKey) {
        if (text == null || text.trim().isEmpty() || text.length() > 2500) throw new IllegalArgumentException("An action must contain 1 to 2500 characters.");
        if (idempotencyKey != null) {
            // A duplicate submission returns the original outcome without resolving
            // again, so the world never advances twice for one intended action.
            ActionResult prior = jdbc.query("SELECT id, intent_type, outcome, duration_minutes, resolved_at, narration FROM chronicle_action WHERE idempotency_key = ?", rs -> rs.next() ? new ActionResult(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getTimestamp(5).toInstant(), rs.getString(6), null) : null, idempotencyKey);
            if (prior != null) return new ActionResult(prior.actionId(), prior.intent(), prior.outcome(), prior.durationMinutes(), prior.resolvedAt(), prior.perception(), physiology.activeBody());
        }
        ActiveChronicle chronicle = jdbc.query("SELECT c.id, w.current_location_id FROM chronicle c JOIN world_object w ON w.id=c.id WHERE c.life_state='LIVING' FOR UPDATE", rs -> rs.next() ? new ActiveChronicle(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)) : null);
        if (chronicle == null) throw new IllegalStateException("No living Chronicle exists.");
        Intent intent = classify(text); int minutes = durationFor(text, intent);
        Instant resolvedAt = ticks.advanceBy(Duration.ofMinutes(minutes)).simulatedAt();
        java.sql.Timestamp resolvedTs = java.sql.Timestamp.from(resolvedAt);
        UUID actionId = UUID.randomUUID(); String outcome = "SUCCEEDED"; String perception;
        // chronicle_action_effect has a foreign key to chronicle_action, and the
        // action row is written only after intent resolution. Gather effects are
        // therefore captured here and inserted after the parent row exists.
        String gatherEffectType = null; String gatherPayloadKey = null; int gatherCount = 0;
        if (intent == Intent.OBSERVE) perception = observe(chronicle.location());
        else if (intent == Intent.MOVE) perception = move(chronicle, text, actionId, resolvedAt);
        else if (intent == Intent.URINATE || intent == Intent.DEFECATE) {
            boolean bowel = intent == Intent.DEFECATE;
            physiology.applyRelief(chronicle.id(), bowel);
            UUID waste = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id, object_type, display_name, current_location_id) VALUES (?, 'WASTE', ?, ?)", waste, bowel ? "Human waste" : "Urine-soaked ground", chronicle.location());
            perception = "You take a brief moment away from the immediate ground around you.";
        } else if (intent == Intent.REST) { physiology.rest(chronicle.id(), minutes); perception = "You remain still while the forest continues around you."; }
        else if (intent == Intent.GATHER_FIBER) { int bundles=items.gatherPlantFiber(chronicle.id(),chronicle.location(),resolvedAt); outcome=bundles>0?"SUCCEEDED":"FAILED"; perception=bundles>0?"You patiently separate usable plant fiber from the living growth around you.":"You search through the growth, but leave it as it is."; gatherEffectType="PLANT_FIBER_GATHERED"; gatherPayloadKey="bundles"; gatherCount=bundles; }
        else if (intent == Intent.GATHER_STONE) { int stones=items.gatherFieldStones(chronicle.id(),chronicle.location(),resolvedAt); outcome=stones>0?"SUCCEEDED":"FAILED"; perception=stones>0?"You work loose a few stones from the ground and carry them with you.":"You turn over the ground for a while, then leave it undisturbed."; gatherEffectType="FIELD_STONE_GATHERED"; gatherPayloadKey="stones"; gatherCount=stones; }
        else if (intent == Intent.GATHER_BERRIES) { int berries=items.gatherWildBerries(chronicle.id(),chronicle.location(),resolvedAt); outcome=berries>0?"SUCCEEDED":"FAILED"; perception=berries>0?"You gather a small handful of ripe berries from the living growth.":"You search the low growth carefully, then let it settle back into place."; gatherEffectType="WILD_BERRIES_GATHERED"; gatherPayloadKey="berries"; gatherCount=berries; }
        else if (intent == Intent.GATHER_BRANCHES) { int branches=items.gatherDryBranches(chronicle.id(),chronicle.location(),resolvedAt); outcome=branches>0?"SUCCEEDED":"FAILED"; perception=branches>0?"You gather a few dry branches from beneath the trees.":"You search the leaf litter for dry wood, then leave with empty hands."; gatherEffectType="DRY_BRANCH_GATHERED"; gatherPayloadKey="branches"; gatherCount=branches; }
        else if (intent == Intent.EAT) { FoodPreservationService.Consumption cooked=food.consume(chronicle.id(),"cooked_game_meat",resolvedAt); FoodPreservationService.Consumption raw=cooked.consumed()?new FoodPreservationService.Consumption(false,false):food.consume(chronicle.id(),"raw_game_meat",resolvedAt); if(cooked.consumed()){physiology.eatCookedMeal(chronicle.id()); if(cooked.spoiled())physiology.applyFoodborneIllness(chronicle.id(),actionId,resolvedAt); perception="The cooked meat is warm and dense, and the meal settles heavily but well.";} else if(items.consumeOne(chronicle.id(),"wild_berries",resolvedAt)){physiology.eat(chronicle.id()); perception="The berries break softly between your teeth, leaving a faint sweetness behind.";} else if(raw.consumed()){physiology.eat(chronicle.id()); if(raw.spoiled())physiology.applyFoodborneIllness(chronicle.id(),actionId,resolvedAt); perception="The raw meat is cold and difficult to swallow, but it settles the immediate emptiness.";} else {outcome="FAILED";perception="You search through what you can reach, then lower your hand again.";} }
        else if (intent == Intent.COOK_MEAT) { if(fire.cookGameMeat(chronicle.id(),chronicle.location(),resolvedAt)) perception="You hold the meat over the steady heat until its surface changes and darkens."; else {outcome="FAILED";perception="You prepare the meat for a moment, then set it aside unchanged.";} }
        else if (intent == Intent.DRINK) { String biome=jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?",String.class,chronicle.location()); if("WETLAND".equals(biome)){physiology.drink(chronicle.id());perception="You drink from the moving water and let the cold settle in your throat.";}else{outcome="FAILED";perception="You pause, but find nothing here that you can drink.";} }
        else if (intent == Intent.WASH) { String biome=jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?",String.class,chronicle.location()); if("WETLAND".equals(biome)){physiology.wash(chronicle.id());perception="Cold water runs over your hands and skin, carrying away some of the dirt.";}else{outcome="FAILED";perception="You pause, but find no water here to wash with.";} }
        else if (intent == Intent.TREAT_WOUND) { if (physiology.bindWound(chronicle.id(), items, actionId, resolvedAt)) perception="You press and bind the wounded place until the immediate bleeding eases."; else { outcome="FAILED"; perception="You work at yourself for a moment, then stop without changing the wound."; } }
        else if (intent == Intent.EDIT_DOCUMENT) { try { reviseDocument(chronicle.id(), actionId, resolvedAt, text); perception="Your marks remain on the physical page."; } catch (IllegalArgumentException | IllegalStateException ignored) { outcome="FAILED"; perception="You handle the page for a while, then set it aside unchanged."; } }
        else if (intent == Intent.CONFRONT_WILDLIFE) { WildlifeEncounterService.EncounterResult result=wildlife.confront(chronicle.id(),chronicle.location(),actionId,resolvedAt); outcome=result.outcome(); perception=result.narration(); }
        else if (intent == Intent.HARVEST_CARCASS) { WildlifeEncounterService.HarvestResult result=wildlife.harvest(chronicle.id(),chronicle.location(),actionId,resolvedAt); outcome=result.outcome(); perception=result.narration(); }
        else if (intent == Intent.LIGHT_FIRE) { if(fire.light(chronicle.id(),chronicle.location(),resolvedAt)) perception="A small flame takes among the branches and steadies within the ring of stone."; else {outcome="FAILED"; perception="The attempt leaves the air unchanged.";} }
        else if (intent == Intent.FEED_FIRE) { if(fire.feed(chronicle.id(),chronicle.location(),resolvedAt)) perception="You settle another dry branch into the coals and watch the fire deepen."; else {outcome="FAILED"; perception="You handle the branch near the cold stones, then take it back.";} }
        else if (intent == Intent.START_LEAN_TO) { if (construction.startLeanTo(chronicle.id(), chronicle.location(), actionId, resolvedAt)) perception="You mark out a low shelter frame against the weather."; else { outcome="FAILED"; perception="The ground holds the same unfinished frame you found there."; } }
        else if (intent == Intent.WORK_LEAN_TO) { if (construction.workLeanTo(chronicle.id(), chronicle.location(), resolvedAt)) perception="You bind the shelter frame a little further into place."; else { outcome="FAILED"; perception="The unfinished frame remains as it was."; } }
        else if (intent == Intent.ABANDON_LEAN_TO) { if (construction.abandonLeanTo(chronicle.location(),resolvedAt)) perception="You leave the unfinished frame where it stands."; else { outcome="FAILED"; perception="There is no unfinished frame here to leave behind."; } }
        else if (intent == Intent.RESUME_LEAN_TO) { if (construction.resumeLeanTo(chronicle.location(),resolvedAt)) perception="You return to the old frame and set your hands to it again."; else { outcome="FAILED"; perception="You find no abandoned frame here to take up again."; } }
        else if (intent == Intent.REPAIR_LEAN_TO) { if(construction.repairLeanTo(chronicle.id(),chronicle.location(),resolvedAt)) perception="You tighten the frame and replace the worst of its weathered bindings."; else {outcome="FAILED";perception="You work over the shelter for a while, then leave it unchanged.";} }
        // Material sufficiency is checked before invoking the crafting methods.
        // Those methods are @Transactional and throw when material is missing;
        // letting that throw reach the shared transaction would mark it
        // rollback-only even though we catch it, turning an ordinary failed
        // attempt into a hard rollback. Prechecking keeps failed attempts graceful.
        else if (intent == Intent.CRAFT_BASKET) { if (items.hasAtLeast(chronicle.id(),"plant_fiber",8)) { items.craftBasket(); perception="Plant fiber tightens beneath your hands until a rough basket holds its shape."; } else { outcome="FAILED"; perception="The loose fibers shift and fall away from one another. Nothing holds its shape."; } }
        else if (intent == Intent.CRAFT_SPEAR) { if (items.hasAtLeast(chronicle.id(),"dry_branch",1) && items.hasAtLeast(chronicle.id(),"field_stone",1) && items.hasAtLeast(chronicle.id(),"plant_fiber",1)) { items.craftPrimitiveSpear(resolvedAt); perception="You bind stone to a straight branch until a crude spear rests in your hand."; } else { outcome="FAILED"; perception="The pieces refuse to hold together long enough to become a usable tool."; } }
        else if (intent == Intent.CRAFT_KNIFE) { if (items.hasAtLeast(chronicle.id(),"field_stone",1) && items.hasAtLeast(chronicle.id(),"plant_fiber",1)) { items.craftPrimitiveTool("stone_knife","Stone knife",false,resolvedAt); perception="You work a sharp edge into the stone and bind it into a small hand tool."; } else { outcome="FAILED"; perception="The stone and loose fiber never settle into a usable edge."; } }
        else if (intent == Intent.CRAFT_HAMMER) { if (items.hasAtLeast(chronicle.id(),"field_stone",1) && items.hasAtLeast(chronicle.id(),"plant_fiber",1) && items.hasAtLeast(chronicle.id(),"dry_branch",1)) { items.craftPrimitiveTool("stone_hammer","Stone hammer",true,resolvedAt); perception="You bind the heavy stone to a branch until the head holds firm."; } else { outcome="FAILED"; perception="The head shifts loose before the tool can hold together."; } }
        else if (intent == Intent.CRAFT_PICKAXE) { if (items.hasAtLeast(chronicle.id(),"field_stone",1) && items.hasAtLeast(chronicle.id(),"plant_fiber",1) && items.hasAtLeast(chronicle.id(),"dry_branch",1)) { items.craftPrimitiveTool("primitive_pickaxe","Primitive pickaxe",true,resolvedAt); perception="You lash a shaped stone across the branch and test its weight in your hand."; } else { outcome="FAILED"; perception="The pieces refuse to hold in a form that can work the ground."; } }
        else if (intent == Intent.BUILD_FIRE_PIT) { if (construction.buildFirePit(chronicle.id(), chronicle.location(), actionId, resolvedAt)) perception = "You settle stone into a low, deliberate ring. The fire pit remains where you made it."; else { outcome = "FAILED"; perception = "You set a few stones apart, then leave them where they lie. The ground remains unchanged."; } }
        else if (intent == Intent.STRIP_BARK) { int n = items.stripBark(chronicle.id(), chronicle.location(), resolvedAt); outcome = n > 0 ? "SUCCEEDED" : "FAILED"; perception = n > 0 ? "You work a broad strip of bark free from a tree and keep it." : "You look for a tree with workable bark, but find none within reach."; }
        else if (intent == Intent.MAKE_CHARCOAL) { boolean made = items.makeCharcoal(chronicle.id(), chronicle.location(), resolvedAt); outcome = made ? "SUCCEEDED" : "FAILED"; perception = made ? "You lift a piece of cooled charcoal from the spent fire." : "You search for usable charcoal, but the ground offers none."; }
        else if (intent == Intent.WRITE) { String[] r = writeOrDraw(chronicle, text, actionId, resolvedAt); outcome = r[0]; perception = r[1]; }
        else { outcome = "FAILED"; perception = "The attempt passes without changing the immediate world around you."; }
        jdbc.update("INSERT INTO chronicle_action (id, chronicle_id, resolved_at, action_text, intent_type, outcome, duration_minutes, narration, idempotency_key) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", actionId, chronicle.id(), resolvedTs, text.trim(), intent.name(), outcome, minutes, perception, idempotencyKey);
        jdbc.update("INSERT INTO chronicle_action_effect (action_id, effect_domain, effect_type, payload) VALUES (?, 'TIME', 'TIME_ADVANCED', jsonb_build_object('minutes', ?))", actionId, minutes);
        if (gatherEffectType != null) jdbc.update("INSERT INTO chronicle_action_effect (action_id, effect_domain, effect_type, payload) VALUES (?, 'ITEM', ?, jsonb_build_object(?, ?))", actionId, gatherEffectType, gatherPayloadKey, gatherCount);
        jdbc.update("INSERT INTO chronicle_event (chronicle_id, occurred_at, event_type, payload) VALUES (?, ?, 'CHRONICLE_ACTION_RESOLVED', jsonb_build_object('actionId', ?::text, 'intent', ?, 'outcome', ?))", chronicle.id(), resolvedTs, actionId.toString(), intent.name(), outcome);
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
        java.sql.Timestamp occurredTs = java.sql.Timestamp.from(occurredAt);
        jdbc.update("UPDATE world_object SET current_location_id=?, updated_at=? WHERE id=?", destination, occurredTs, chronicle.id());
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'MOVED',jsonb_build_object('fromLocationId',?::text,'toLocationId',?::text,'direction',?))", chronicle.id(), occurredTs, chronicle.location().toString(), destination.toString(), direction.name());
        jdbc.update("INSERT INTO chronicle_event (chronicle_id,occurred_at,event_type,payload) VALUES (?,?,'CHRONICLE_MOVED',jsonb_build_object('fromLocationId',?::text,'toLocationId',?::text,'direction',?))", chronicle.id(), occurredTs, chronicle.location().toString(), destination.toString(), direction.name());
        return "You travel " + direction.description + ", leaving the last stand of trees behind you.";
    }
    private int durationFor(String action, Intent intent) {
        Matcher match = DURATION.matcher(action);
        if (match.find()) { int amount = Integer.parseInt(match.group(1)); int minutes = match.group(2).toLowerCase(Locale.ROOT).startsWith("h") ? amount * 60 : amount; return Math.max(1, Math.min(minutes, 24 * 60)); }
        return switch (intent) { case OBSERVE -> 10; case REST -> 60; case GATHER_FIBER, GATHER_STONE, GATHER_BERRIES, GATHER_BRANCHES -> 25; case EAT, DRINK, LIGHT_FIRE, FEED_FIRE -> 5; case COOK_MEAT, TREAT_WOUND, CONFRONT_WILDLIFE, HARVEST_CARCASS -> 10; case EDIT_DOCUMENT, WRITE -> 15; case STRIP_BARK -> 15; case MAKE_CHARCOAL -> 10; case CRAFT_BASKET -> 45; case CRAFT_SPEAR -> 35; case BUILD_FIRE_PIT, START_LEAN_TO -> 30; case WORK_LEAN_TO -> 45; case ABANDON_LEAN_TO, RESUME_LEAN_TO -> 5; case MOVE -> 30; default -> 5; };
    }
    private Intent classify(String action) {
        String value=action.toLowerCase(Locale.ROOT);
        if(DOCUMENT_EDIT.matcher(action).matches()) return Intent.EDIT_DOCUMENT;
        if((value.contains("craft")||value.contains("make"))&&value.contains("spear")) return Intent.CRAFT_SPEAR;
        if((value.contains("craft")||value.contains("make"))&&value.contains("knife")) return Intent.CRAFT_KNIFE;
        if((value.contains("craft")||value.contains("make"))&&value.contains("hammer")) return Intent.CRAFT_HAMMER;
        if((value.contains("craft")||value.contains("make"))&&(value.contains("pickaxe")||value.contains("pick axe"))) return Intent.CRAFT_PICKAXE;
        if(value.contains("lean-to") || value.contains("lean to")) return classifyLeanTo(value);
        if(value.contains("wash")||value.contains("bathe")||value.contains("clean myself")) return Intent.WASH;
        if(value.contains("charcoal")&&(value.contains("make")||value.contains("take")||value.contains("gather")||value.contains("get")||value.contains("collect"))) return Intent.MAKE_CHARCOAL;
        if(value.contains("bark")&&(value.contains("strip")||value.contains("peel")||value.contains("gather")||value.contains("cut")||value.contains("collect")||value.contains("pull"))) return Intent.STRIP_BARK;
        if(action.contains(":")&&(value.contains("write")||value.contains("draw")||value.contains("sketch")||value.contains("record")||value.contains("inscribe")||value.contains("mark ")||value.contains("note"))) return Intent.WRITE;
        return classifyLegacy(action);
    }
    private Intent classifyLeanTo(String value) {
        if(value.contains("repair")) return Intent.REPAIR_LEAN_TO;
        if(value.contains("abandon")||value.contains("leave")) return Intent.ABANDON_LEAN_TO;
        if(value.contains("resume")||value.contains("return to")) return Intent.RESUME_LEAN_TO;
        return (value.contains("work") || value.contains("continue") || value.contains("build") || value.contains("weave") || value.contains("bind")) ? Intent.WORK_LEAN_TO : Intent.START_LEAN_TO;
    }
    private Intent classifyLegacy(String action) { String value = action.toLowerCase(Locale.ROOT); if ((value.contains("cook") || value.contains("roast")) && (value.contains("meat") || value.contains("game"))) return Intent.COOK_MEAT; if ((value.contains("harvest") || value.contains("butcher") || value.contains("skin")) && (value.contains("carcass") || value.contains("remains") || value.contains("animal"))) return Intent.HARVEST_CARCASS; if ((value.contains("bind") || value.contains("bandage") || value.contains("dress")) && (value.contains("wound") || value.contains("injury") || value.contains("bleeding"))) return Intent.TREAT_WOUND; if ((value.contains("feed") || value.contains("stoke") || value.contains("add wood")) && value.contains("fire")) return Intent.FEED_FIRE; if ((value.contains("light")||value.contains("ignite")) && value.contains("fire")) return Intent.LIGHT_FIRE; if (value.contains("fire pit") || value.contains("firepit")) return Intent.BUILD_FIRE_PIT; if ((value.contains("fight")||value.contains("attack")||value.contains("strike")) && (value.contains("animal")||value.contains("wildlife")||value.contains("creature"))) return Intent.CONFRONT_WILDLIFE; if ((value.contains("weave") || value.contains("craft") || value.contains("make")) && value.contains("basket")) return Intent.CRAFT_BASKET; if ((value.contains("gather")||value.contains("collect")) && value.contains("fiber")) return Intent.GATHER_FIBER; if ((value.contains("gather")||value.contains("collect")) && (value.contains("branch")||value.contains("stick"))) return Intent.GATHER_BRANCHES; if ((value.contains("gather")||value.contains("collect")) && (value.contains("berry")||value.contains("berries"))) return Intent.GATHER_BERRIES; if ((value.contains("gather")||value.contains("collect")) && (value.contains("stone")||value.contains("rock"))) return Intent.GATHER_STONE; if (value.contains("eat")||value.contains("consume")) return Intent.EAT; if (value.contains("drink")) return Intent.DRINK; if (Direction.from(value) != null && (value.contains("walk") || value.contains("travel") || value.contains("go ") || value.contains("move"))) return Intent.MOVE; if (value.contains("observe") || value.contains("look") || value.contains("inspect")) return Intent.OBSERVE; if (value.contains("rest") || value.contains("wait")) return Intent.REST; if (value.contains("urinate") || value.contains("pee")) return Intent.URINATE; if (value.contains("defecate") || value.contains("poop")) return Intent.DEFECATE; return Intent.UNKNOWN; }
    private String[] writeOrDraw(ActiveChronicle chronicle, String text, UUID actionId, Instant at) {
        Matcher m = WRITE_CONTENT.matcher(text);
        if (!m.find() || m.group(1).trim().isEmpty()) return new String[]{"FAILED", "You hold the charcoal a moment, then set nothing down."};
        String content = m.group(1).trim();
        String value = text.toLowerCase(Locale.ROOT);
        String kind = value.contains("map") ? "MAP" : "LITERATURE";
        if (!items.hasAtLeast(chronicle.id(), "charcoal", 1)) return new String[]{"FAILED", "Without anything that leaves a mark, the surface stays blank."};
        UUID existing = referencesExisting(value) ? literature.reachableDocumentOfKind(chronicle.id(), kind) : null;
        if (existing != null) {
            literature.revise(existing, chronicle.id(), actionId, at, LiteratureService.Edit.APPEND, "\n" + content, null);
            return new String[]{"SUCCEEDED", kind.equals("MAP") ? "You add another mark to the map you carry." : "You add a few more lines to the record you carry."};
        }
        UUID surface = items.findReachable(chronicle.id(), "bark_sheet");
        if (surface == null) surface = items.findReachable(chronicle.id(), "animal_hide");
        if (surface == null) return new String[]{"FAILED", "You have nothing suitable to write on, and the charcoal marks only your fingers."};
        literature.createFromSurface(surface, chronicle.id(), actionId, at, kind, kind.equals("MAP") ? "Hand-drawn map" : "Written record", content);
        return new String[]{"SUCCEEDED", kind.equals("MAP") ? "You scratch a rough map onto the surface, marking what you know of the land." : "You press charcoal to the surface and set down your first written words."};
    }
    private boolean referencesExisting(String value) { return value.matches("(?s).*\\b(my|the)\\b.*\\b(journal|record|map|note|book|writing|log|diary|chronicle)\\b.*"); }
    private void reviseDocument(UUID chronicleId, UUID actionId, Instant resolvedAt, String text) { Matcher match=DOCUMENT_EDIT.matcher(text); if(!match.matches()) throw new IllegalArgumentException("Unrecognized document edit."); UUID documentId=UUID.fromString(match.group(2)); LiteratureService.Edit edit="append".equalsIgnoreCase(match.group(1))?LiteratureService.Edit.APPEND:LiteratureService.Edit.REPLACE; if(!literature.documentReachable(documentId,chronicleId)) throw new IllegalArgumentException("The document is not physically reachable."); literature.revise(documentId,chronicleId,actionId,resolvedAt,edit,match.group(3),null); }
    private record ActiveChronicle(UUID id, UUID location) { } private enum Intent { OBSERVE, MOVE, REST, GATHER_FIBER, GATHER_STONE, GATHER_BERRIES, GATHER_BRANCHES, EAT, DRINK, WASH, TREAT_WOUND, EDIT_DOCUMENT, WRITE, STRIP_BARK, MAKE_CHARCOAL, LIGHT_FIRE, FEED_FIRE, COOK_MEAT, CONFRONT_WILDLIFE, HARVEST_CARCASS, CRAFT_BASKET, CRAFT_SPEAR, CRAFT_KNIFE, CRAFT_HAMMER, CRAFT_PICKAXE, BUILD_FIRE_PIT, START_LEAN_TO, WORK_LEAN_TO, ABANDON_LEAN_TO, RESUME_LEAN_TO, REPAIR_LEAN_TO, URINATE, DEFECATE, UNKNOWN }
    private enum Direction { NORTH(0,-1,"north"), SOUTH(0,1,"south"), EAST(1,0,"east"), WEST(-1,0,"west"); final int dx; final int dy; final String description; Direction(int dx,int dy,String description){this.dx=dx;this.dy=dy;this.description=description;} static Direction from(String action){String value=action.toLowerCase(Locale.ROOT); for(Direction direction:values()) if(value.matches(".*\\b"+direction.description+"\\b.*")) return direction; return null;} }
    public record ActionResult(UUID actionId, String intent, String outcome, int durationMinutes, Instant resolvedAt, String perception, ChroniclePhysiologyService.BodyHudSnapshot body) { }
    public record NarrationEntry(UUID id, Instant occurredAt, String narration) { }
    public record NarrationPage(List<NarrationEntry> entries, boolean hasMore) { }
}
