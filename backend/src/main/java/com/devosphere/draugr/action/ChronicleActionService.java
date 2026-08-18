package com.devosphere.draugr.action;

import com.devosphere.draugr.assembly.AssemblyService;
import com.devosphere.draugr.chronicle.ChroniclePhysiologyService;
import com.devosphere.draugr.narration.NarrationPolicy;
import com.devosphere.draugr.narration.NarrationRouter;
import com.devosphere.draugr.narration.ActionInputClassifier;
import com.devosphere.draugr.ai.SimulationNarrator;
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
    // Captures the name a chronicle gives a place: "...as the Sleeping Area",
    // "name this Wolf Kingdom", "call this place the Drinking Area".
    private static final Pattern DESIGNATE_NAME = Pattern.compile("(?is)\\b(?:as|called|named|be)\\s+(?:the\\s+|a\\s+|my\\s+)?(.+)$");
    // Alternation is ordered, so the multi-word demonstratives must precede bare "this": otherwise
    // "name this place Camp Site" matches only "this" and captures "place Camp Site" as the name.
    private static final Pattern DESIGNATE_FALLBACK = Pattern.compile("(?is)\\b(?:designate|name|call|establish|found|christen|mark)\\s+(?:this\\s+place|this\\s+area|this\\s+spot|this|here|it)?\\s*(?:the\\s+|a\\s+|my\\s+)?(.+)$");
    private static final Pattern WRITE_CONTENT = Pattern.compile("(?is):\\s*(.+)$");
    // Signal groups for the fire-lighting specificity score. Each group is a set
    // of synonyms; naming the tool, the tinder, the ember, and the motion reads
    // as a competent, deliberate attempt. A bare "light the fire" matches none.
    private static final List<String[]> FIRE_SIGNALS = List.of(
        new String[]{"spindle","drill","bow","hearth","board","friction","plough","plow"},
        new String[]{"tinder","nest","kindling","dry grass","fibre","fiber","bark shavings"},
        new String[]{"ember","coal","spark","smoke","glow"},
        new String[]{"downward","steady","press","spin","rotate","back and forth","until","then","slowly","patient"});
    // Signal groups for a hunting attempt: naming a weapon or strike, a target on
    // the animal, an approach, and a follow-through reads as a deliberate kill.
    // "kill the boar" matches none and rests on raw body state and what is in hand.
    private static final List<String[]> HUNT_SIGNALS = List.of(
        new String[]{"spear","thrust","stab","throw","strike","blow","jab","drive the","swing"},
        new String[]{"throat","neck","heart","eye","head","skull","chest","flank","side","leg","hamstring","belly"},
        new String[]{"ambush","flank","circle","corner","distract","downwind","sneak","creep","approach slowly","wait for","from behind"},
        new String[]{"pin","finish","again","repeated","hold it down","press the attack","keep"});
    // Signal groups for effort put into a gather (#68): care, a thorough working of the ground, and a will to
    // take as much as it holds. Their presence — plus the LOAD mastery — wins a little more where the source has it.
    private static final List<String[]> GATHER_SIGNALS = List.of(
        new String[]{"careful","carefully","patient","patiently","methodical","methodically","thorough","thoroughly","diligent"},
        new String[]{"comb","scour","search","pick through","work loose","dig deep","turn over","strip the","go over"},
        new String[]{"all","every","as much","fill","load up","armful","as many","the lot","clear the"});
    // Cues that the action text is spending effort on perceiving the surroundings,
    // rather than on a single heads-down task. Their presence lifts the frame's
    // ATTENTION, so the world it reveals matches what the chronicle actually looked at.
    private static final List<String> ATTENTION_CUES = List.of(
        "look around","look about","glance around","take in","survey","scan","scout","observe","examine",
        "inspect","study the","study my","search the","search for","scour","peer","gaze","watch the",
        "keep watch","keep an eye","listen","carefully","cautiously","warily","alert","note the","eye the");
    /**
     * Witness-stance prose for an action the world could not resolve at all — a real procedure it has
     * no mechanic for, or something that does not connect to the physical world here. Varied by the
     * action text so distinct attempts do not read identically (#1: failed attempts felt ambiguous and
     * repetitive). Names no reason and gives no hint — the world simply does not answer to it; the
     * player must bring the knowledge of what would.
     */
    private static final String[] UNRESOLVED_ATTEMPT = {
        "You work at it for a while, but nothing here answers to the attempt, and the moment passes into the rest.",
        "Whatever you meant by that, your hands find no purchase on it. The world around you goes on unchanged.",
        "You try, and the effort goes into the air. The ground and everything on it is exactly as it was."};
    // A recognised piece of material/world work the world cannot yet resolve (#68): a grounded "no way comes to
    // you" that names the material effort, rather than the flat gibberish line above. The routing miss is still
    // recorded (inside runProcess) so the gap is on the backlog for review.
    private static final String[] MATERIAL_UNRESOLVED = {
        "You work the material over, turning it for a way in, but no method for what you meant comes to your hands here.",
        "You set to it in earnest, but the working of it into that is beyond what your hands and knowledge can find on this ground.",
        "You handle and test it, feeling for the trick of it, but the way to make what you intend does not come to you yet."};
    private final JdbcTemplate jdbc; private final SimulationTickService ticks; private final ChroniclePhysiologyService physiology; private final NarrationPolicy narration; private final PhysicalItemService items; private final CapabilityAdaptationService capability; private final ConstructionService construction; private final ChronicleDiscoveryService discoveries; private final WildlifeEncounterService wildlife; private final FireService fire; private final LiteratureService literature; private final FoodPreservationService food; private final ActionInputClassifier inputClassifier; private final AssemblyService assembly; private final NarrationRouter narrationRouter; private final SimulationNarrator simulationNarrator; private final com.devosphere.draugr.narration.NarrationEngine narrationEngine; private final com.devosphere.draugr.ai.RuntimeAuthoringService authoring; private final ExaminationService examination;
    public ChronicleActionService(JdbcTemplate jdbc, SimulationTickService ticks, ChroniclePhysiologyService physiology, NarrationPolicy narration, PhysicalItemService items, CapabilityAdaptationService capability, ConstructionService construction, ChronicleDiscoveryService discoveries, WildlifeEncounterService wildlife, FireService fire, LiteratureService literature, FoodPreservationService food, ActionInputClassifier inputClassifier, AssemblyService assembly, NarrationRouter narrationRouter, SimulationNarrator simulationNarrator, com.devosphere.draugr.narration.NarrationEngine narrationEngine, com.devosphere.draugr.ai.RuntimeAuthoringService authoring, ExaminationService examination) { this.jdbc = jdbc; this.ticks = ticks; this.physiology = physiology; this.narration = narration; this.items=items; this.capability=capability; this.construction=construction; this.discoveries=discoveries; this.wildlife=wildlife; this.fire=fire; this.literature=literature; this.food=food; this.inputClassifier=inputClassifier; this.assembly=assembly; this.narrationRouter=narrationRouter; this.simulationNarrator=simulationNarrator; this.narrationEngine=narrationEngine; this.authoring=authoring; this.examination=examination; }

    @Transactional
    public ActionResult resolve(String text) { return resolve(text, null); }

    @Transactional
    public ActionResult resolve(String text, UUID idempotencyKey) {
        if (text == null || text.trim().isEmpty() || text.length() > 2500) throw new IllegalArgumentException("An action must contain 1 to 2500 characters.");
        if (idempotencyKey != null) {
            // A duplicate submission returns the original outcome without resolving
            // again, so the world never advances twice for one intended action.
            ActionResult prior = jdbc.query("SELECT ca.id, ca.intent_type, ca.outcome, ca.duration_minutes, ca.resolved_at, COALESCE(can.narration, ca.narration) FROM chronicle_action ca LEFT JOIN chronicle_action_narration can ON can.action_id = ca.id WHERE ca.idempotency_key = ?", rs -> rs.next() ? new ActionResult(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getTimestamp(5).toInstant(), rs.getString(6), null, null, false) : null, idempotencyKey);
            // A replayed action does not advance the world, so no fresh frame is built;
            // the durable perception prose and current body state are returned as-is.
            if (prior != null) return new ActionResult(prior.actionId(), prior.intent(), prior.outcome(), prior.durationMinutes(), prior.resolvedAt(), prior.perception(), physiology.activeBody(), null, physiology.activeBody() == null);
        }
        ActiveChronicle chronicle = jdbc.query("SELECT c.id, w.current_location_id FROM chronicle c JOIN world_object w ON w.id=c.id WHERE c.life_state='LIVING' FOR UPDATE", rs -> rs.next() ? new ActiveChronicle(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)) : null);
        if (chronicle == null) throw new IllegalStateException("No living Chronicle exists.");
        // The pre-pass filter runs before intent classification (DR-0019). Gibberish
        // and the physically impossible are intercepted here: no tick, no
        // chronicle_action row, no AI call — zero cost. Personal physical acts and
        // aggression are mapped to intents that DO advance the world, so they pass
        // through the normal path and carry real physiological or ecological
        // consequence. The world applies physics, not judgment.
        ActionInputClassifier.InputClass inputClass = inputClassifier.classify(text);
        if (inputClass == ActionInputClassifier.InputClass.NONSENSICAL || inputClass == ActionInputClassifier.InputClass.PHYSICALLY_IMPOSSIBLE) {
            Instant nowSim = ticks.current().simulatedAt();
            return new ActionResult(UUID.randomUUID(), inputClass.name(), "NO_EFFECT", 0, nowSim, inputClassifier.narrate(inputClass, text), physiology.activeBody(), null, false);
        }
        Intent intent = switch (inputClass) {
            case PERSONAL_PHYSICAL_ACT -> Intent.PERSONAL_ACT;
            case AGGRESSION_TOWARD_WILDLIFE -> Intent.AGGRESSION_WILDLIFE;
            case AGGRESSION_TOWARD_INANIMATE -> Intent.AGGRESSION_INANIMATE;
            default -> classify(text);
        };
        // Travel time is resolved before the tick advances, because it scales with
        // the distance to a place the chronicle can actually find its way to.
        TravelPlan travel = intent == Intent.TRAVEL ? planTravel(chronicle, text) : null;
        // "go to the Tool Shed" names a zone in the CURRENT settlement — a short walk within the chunk, not an
        // inter-chunk journey (V70/F8). Reachability is unaffected (it's chunk-wide either way).
        String localZone = intent == Intent.TRAVEL ? matchLocalZone(chronicle, text) : null;
        int minutes = localZone != null ? 5 : (intent == Intent.TRAVEL ? (travel == null ? 20 : Math.max(15, travel.distance() * 18)) : durationFor(text, intent));
        // F2 — capture the body and the sky as they stood before the tick runs, so
        // the frame can report what the passage of time changed, not just the state
        // it left behind. A chronicle who lies down hungry and wakes starving must
        // have that passage surfaced rather than silently swallowed.
        ChroniclePhysiologyService.BodyHudSnapshot beforeBody = physiology.activeBody();
        String beforeWeather = jdbc.query("SELECT ww.weather_kind FROM world_weather ww JOIN world_chunk c ON c.world_id=ww.world_id WHERE c.id=?", rs -> rs.next() ? rs.getString(1) : null, chronicle.location());
        Instant resolvedAt = ticks.advanceBy(Duration.ofMinutes(minutes)).simulatedAt();
        java.sql.Timestamp resolvedTs = java.sql.Timestamp.from(resolvedAt);
        UUID actionId = UUID.randomUUID(); String outcome = "SUCCEEDED"; String perception;
        // chronicle_action_effect has a foreign key to chronicle_action, and the
        // action row is written only after intent resolution. Gather effects are
        // therefore captured here and inserted after the parent row exists.
        String gatherEffectType = null; String gatherPayloadKey = null; int gatherCount = 0;
        // Light (#75): fine sight-work — reading, writing, sketching, close examination, measuring — cannot be
        // done in the dark by feel alone. A fire in reach lights it for free; otherwise a portable light (a
        // rushlight, a tallow candle, or an oil lamp burning fish oil) is lit and spent to see the work.
        if (isSightWork(intent) && isDark(resolvedAt) && !fireInReach(chronicle.location()) && !items.consumePortableLight(chronicle.id(), resolvedAt)) {
            outcome = "FAILED";
            perception = "It is too dark to see the fine of it. With no fire and no light to work by, this is not something your hands can do by feel alone.";
        }
        else if (intent == Intent.OBSERVE) perception = survey(chronicle, resolvedAt);
        else if (intent == Intent.MOVE) perception = move(chronicle, text, actionId, resolvedAt);
        else if (intent == Intent.TRAVEL) {
            if (localZone != null) { jdbc.update("UPDATE chronicle SET current_zone=? WHERE id=?", localZone, chronicle.id()); perception = "You cross the settlement to " + localZone + ", a short walk over ground you know by heart."; }
            else { String[] r = travelTo(chronicle, travel, resolvedAt); outcome = r[0]; perception = r[1]; }
        }
        else if (intent == Intent.MARK) { String[] r = markLandmark(chronicle, text, actionId, resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.URINATE || intent == Intent.DEFECATE) {
            boolean bowel = intent == Intent.DEFECATE;
            physiology.applyRelief(chronicle.id(), bowel);
            UUID waste = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id, object_type, display_name, current_location_id) VALUES (?, 'WASTE', ?, ?)", waste, bowel ? "Human waste" : "Urine-soaked ground", chronicle.location());
            perception = "You take a brief moment away from the immediate ground around you.";
        } else if (intent == Intent.REST) { physiology.rest(chronicle.id(), minutes); perception = "You remain still while the forest continues around you."; }
        else if (intent == Intent.SLEEP) { boolean safe = physiology.sleep(chronicle.id(), minutes); perception = safe ? "You lie down under cover and let sleep take you. You wake to a changed sky, the deep tiredness lifted from your limbs." : "You settle onto the bare ground and drift into a broken, shallow sleep, waking stiff and only half-rested as the light shifts."; }
        else if (intent == Intent.GATHER_FIBER) { int bundles=items.gatherPlantFiber(chronicle.id(),chronicle.location(),resolvedAt,gatherBonus(text,chronicle.id())); outcome=bundles>0?"SUCCEEDED":"FAILED"; perception=bundles>0?"You patiently separate usable plant fiber from the living growth around you.":"You search through the growth, but leave it as it is."; gatherEffectType="PLANT_FIBER_GATHERED"; gatherPayloadKey="bundles"; gatherCount=bundles; }
        else if (intent == Intent.GATHER_STONE) { int stones=items.gatherFieldStones(chronicle.id(),chronicle.location(),resolvedAt,gatherBonus(text,chronicle.id())); outcome=stones>0?"SUCCEEDED":"FAILED"; perception=stones>0?"You work loose a few stones from the ground and carry them with you.":"You turn over the ground for a while, then leave it undisturbed."; gatherEffectType="FIELD_STONE_GATHERED"; gatherPayloadKey="stones"; gatherCount=stones; }
        else if (intent == Intent.GATHER_BERRIES) { int berries=items.gatherWildBerries(chronicle.id(),chronicle.location(),resolvedAt,gatherBonus(text,chronicle.id())); outcome=berries>0?"SUCCEEDED":"FAILED"; perception=berries>0?"You gather a small handful of ripe berries from the living growth.":"You search the low growth carefully, then let it settle back into place."; gatherEffectType="WILD_BERRIES_GATHERED"; gatherPayloadKey="berries"; gatherCount=berries; }
        else if (intent == Intent.GATHER_BRANCHES) { int branches=items.gatherDryBranches(chronicle.id(),chronicle.location(),resolvedAt,gatherBonus(text,chronicle.id())); outcome=branches>0?"SUCCEEDED":"FAILED"; perception=branches>0?"You gather a few dry branches from beneath the trees.":"You search the leaf litter for dry wood, then leave with empty hands."; gatherEffectType="DRY_BRANCH_GATHERED"; gatherPayloadKey="branches"; gatherCount=branches; }
        else if (intent == Intent.GATHER_CLAY) { boolean shovel=items.hasAtLeast(chronicle.id(),"wooden_shovel",1); boolean stick=!shovel&&items.hasAtLeast(chronicle.id(),"digging_stick",1); int dig=shovel?2:(stick?1:0); int lumps=items.gatherClay(chronicle.id(),chronicle.location(),resolvedAt,gatherBonus(text,chronicle.id())+dig); outcome=lumps>0?"SUCCEEDED":"FAILED"; perception=lumps>0?(shovel?"You bite the shovel deep into the bank and turn out heavy lumps of wet clay by the load.":stick?"You lever the earth open with the digging stick and prise free dense lumps of wet clay.":"You work the earth with your hands and pull free a dense lump of wet clay."):"You search the ground for workable clay, but the earth here holds nothing useful."; gatherEffectType="CLAY_GATHERED"; gatherPayloadKey="lumps"; gatherCount=lumps; }
        else if (intent == Intent.GATHER_STONE_SLAB) { int slabs=items.gatherStoneSlab(chronicle.id(),chronicle.location(),resolvedAt); outcome=slabs>0?"SUCCEEDED":"FAILED"; perception=slabs>0?"You work a broad, flat slab of stone free from the rock and take up its considerable weight.":"You search the rock for a slab flat enough to work, but nothing here breaks away clean."; gatherEffectType="STONE_SLAB_GATHERED"; gatherPayloadKey="slabs"; gatherCount=slabs; }
        else if (intent == Intent.GATHER_PLANT) { String[] r=items.gatherPlant(chronicle.id(),chronicle.location(),text,resolvedAt); outcome=r[0]; perception=r[1]; }
        else if (intent == Intent.FORAGE_GROUND) { String[] r=items.forageGround(chronicle.id(),chronicle.location(),text,resolvedAt); outcome=r[0]; perception=r[1]; }
        else if (intent == Intent.FELL_TREE) { String[] r=items.fellTree(chronicle.id(),chronicle.location(),resolvedAt); outcome=r[0]; perception=r[1]; }
        else if (intent == Intent.PROCESS_MATERIAL) { String[] r=items.runProcess(chronicle.id(),chronicle.location(),text,resolvedAt); outcome=r[0]; perception=r[1]; }
        else if (intent == Intent.CRAFT_FIRE_TOOL) { String[] r=items.craftFireTool(chronicle.id(),text,resolvedAt); outcome=r[0]; perception=r[1]; }
        else if (intent == Intent.GATHER_MINERAL) { String[] r=items.gatherMineral(chronicle.id(),chronicle.location(),text,resolvedAt); outcome=r[0]; perception=r[1]; }
        else if (intent == Intent.CRAFT_GARMENT) { String[] r=items.craftGarment(chronicle.id(),text,resolvedAt); outcome=r[0]; perception=r[1]; }
        else if (intent == Intent.LURE) { WildlifeEncounterService.EncounterResult r=wildlife.lure(chronicle.id(),chronicle.location(),resolvedAt,text); outcome=r.outcome(); perception=r.narration(); }
        else if (intent == Intent.SET_TRAP) { WildlifeEncounterService.EncounterResult r=wildlife.setTrap(chronicle.id(),chronicle.location(),resolvedAt,text); outcome=r.outcome(); perception=r.narration(); }
        else if (intent == Intent.CHECK_TRAP) { WildlifeEncounterService.EncounterResult r=wildlife.checkTrap(chronicle.id(),chronicle.location(),actionId,resolvedAt); outcome=r.outcome(); perception=r.narration(); }
        else if (intent == Intent.TAME) { WildlifeEncounterService.EncounterResult r=wildlife.tame(chronicle.id(),chronicle.location(),actionId,resolvedAt,text); outcome=r.outcome(); perception=r.narration(); }
        else if (intent == Intent.TRACK) { WildlifeEncounterService.EncounterResult r=wildlife.track(chronicle.id(),chronicle.location(),actionId,resolvedAt,attentionLevel(text,intent),capability.familiarity(chronicle.id(),"ATTENTION")); outcome=r.outcome(); perception=r.narration(); }
        else if (intent == Intent.SCOUT) { WildlifeEncounterService.EncounterResult r=wildlife.scoutBoundary(chronicle.id(),chronicle.location(),attentionLevel(text,intent),capability.familiarity(chronicle.id(),"ATTENTION")); outcome=r.outcome(); perception=r.narration(); }
        else if (intent == Intent.BUILD_FENCE) { String[] r=construction.buildFence(chronicle.id(),chronicle.location(),text,resolvedAt); outcome=r[0]; perception=r[1]; }
        else if (intent == Intent.BUILD_LOOKOUT) { String[] r=construction.buildLookout(chronicle.id(),chronicle.location(),resolvedAt); outcome=r[0]; perception=r[1]; }
        else if (intent == Intent.BUILD_FUEL_RACK) { String[] r=construction.buildFuelRack(chronicle.id(),chronicle.location(),resolvedAt); outcome=r[0]; perception=r[1]; }
        else if (intent == Intent.FISH) { WildlifeEncounterService.EncounterResult r=wildlife.fish(chronicle.id(),chronicle.location(),actionId,resolvedAt,text); outcome=r.outcome(); perception=r.narration(); }
        else if (intent == Intent.SNARE) { WildlifeEncounterService.EncounterResult r=wildlife.snare(chronicle.id(),chronicle.location(),actionId,resolvedAt); outcome=r.outcome(); perception=r.narration(); }
        else if (intent == Intent.RAID_HIVE) { PhysicalItemService.InsectHarvest r=items.raidHive(chronicle.id(),chronicle.location(),text,resolvedAt); outcome=r.outcome(); perception=r.narration(); applyInsectHazard(chronicle.id(),r,actionId,resolvedAt); }
        else if (intent == Intent.COLLECT_INSECTS) { PhysicalItemService.InsectHarvest r=items.collectInsects(chronicle.id(),chronicle.location(),text,resolvedAt); outcome=r.outcome(); perception=r.narration(); applyInsectHazard(chronicle.id(),r,actionId,resolvedAt); }
        else if (intent == Intent.EAT) { String[] r = eat(chronicle.id(), text, actionId, resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.COOK_MEAT) { int cooked=fire.cookGameMeat(chronicle.id(),chronicle.location(),resolvedAt); if(cooked>1) perception="You lay several pieces out over the steady heat and turn them until each darkens through — a batch done at once."; else if(cooked==1) perception="You hold the meat over the steady heat until its surface changes and darkens."; else {outcome="FAILED";perception="You prepare the meat for a moment, then set it aside unchanged.";} }
        else if (intent == Intent.DRINK) {
            // The safest water you carry first (boiled > filtered > raw), else raw from a source in reach. Raw and
            // standing water carry a gut-illness risk that accumulates; boiled water and a clean moving source do not (#71).
            String carried = items.bestWaterCarried(chronicle.id());
            if (carried != null) {
                items.consumeOne(chronicle.id(), carried, resolvedAt); physiology.drink(chronicle.id());
                if ("clean_water".equals(carried)) perception = "You drink your fill of the boiled water — clean, flat, and safe.";
                else if ("filtered_water".equals(carried)) { physiology.applyWaterborneRisk(chronicle.id(), 2); perception = "You drink the filtered water; it runs clearer than it was, though not beyond all doubt."; }
                else { physiology.applyWaterborneRisk(chronicle.id(), 5); perception = "You drink the raw water you carry. It eases the dryness, but sits uneasy in the gut."; }
            } else if (waterInReach(chronicle.location())) {
                physiology.drink(chronicle.id());
                if (safeWaterSource(chronicle.location())) perception = "You drink from the clean, moving water and let the cold settle in your throat.";
                else { physiology.applyWaterborneRisk(chronicle.id(), 6); perception = "You drink from the standing water here. It eases the dryness, but it is not clean, and the gut will know it."; }
            } else { outcome = "FAILED"; perception = "You look about, but there is no water here fit to drink — no stream, no spring, only dry ground that gives nothing back."; }
        }
        else if (intent == Intent.COLLECT_WATER) {
            if (!waterInReach(chronicle.location())) { outcome = "FAILED"; perception = "There is no water here to fill from — no stream, spring, or standing water within reach."; }
            else if (!items.hasWaterVessel(chronicle.id())) { outcome = "FAILED"; perception = "You have nothing that will carry water — a waterskin, bucket, or pot must come first."; }
            else { int n = items.makeWater(chronicle.id(), "raw_water", "Raw water", 3, resolvedAt); outcome = n > 0 ? "SUCCEEDED" : "FAILED"; perception = n > 0 ? "You fill your vessel with water from the source here — raw yet, and better boiled before you trust it." : "Your vessels are already brimful; there is no room for more water."; }
        }
        else if (intent == Intent.BOIL_WATER) {
            boolean fireproof = items.hasFireproofVessel(chronicle.id());
            boolean stoneBoil = items.hasAtLeast(chronicle.id(), "boiling_stone_set", 1) && items.hasWaterVessel(chronicle.id());
            if (!fireInReach(chronicle.location())) { outcome = "FAILED"; perception = "There is no fire burning here to boil water over."; }
            else if (!fireproof && !stoneBoil) { outcome = "FAILED"; perception = "You have nothing that can take a boil — a fireproof clay or soapstone vessel to set on the flame, or a set of hot stones to drop into a vessel of water."; }
            else {
                int n = items.convertWater(chronicle.id(), "raw_water", "clean_water", "Boiled water", 3, resolvedAt);
                if (n == 0 && waterInReach(chronicle.location()) && items.hasWaterVessel(chronicle.id())) n = items.makeWater(chronicle.id(), "clean_water", "Boiled water", 3, resolvedAt);
                outcome = n > 0 ? "SUCCEEDED" : "FAILED";
                perception = n == 0 ? "You have no water to boil and no source and vessel to draw and boil it from."
                    : fireproof ? "You bring the water to a hard, rolling boil over the fire until it is clean and safe to drink."
                    : "You heat the stones in the fire and drop them hissing into the vessel, one after another, until the water rolls to a clean boil.";
            }
        }
        else if (intent == Intent.FILTER_WATER) {
            // A fired clay filter clears best; a bare-hand bark-and-charcoal cone (#141) is the first-hours
            // stand-in — either will do to pour raw water through and clarify it.
            boolean clay = items.hasAtLeast(chronicle.id(), "clay_water_filter", 1);
            boolean bark = !clay && items.hasAtLeast(chronicle.id(), "bark_water_filter", 1);
            if (!clay && !bark) { outcome = "FAILED"; perception = "You have no water filter to pour through — a bark-and-charcoal filter, or a fired clay one, must be made first."; }
            else { int n = items.convertWater(chronicle.id(), "raw_water", "filtered_water", "Filtered water", 3, resolvedAt); outcome = n > 0 ? "SUCCEEDED" : "FAILED"; perception = n > 0 ? (clay ? "You pour the raw water slowly through the clay filter; it comes out the other side notably clearer." : "You pour the raw water slowly through the bark-and-charcoal cone; it drips out the bottom clearer and less foul than it went in.") : "You have no raw water to pour through the filter."; }
        }
        else if (intent == Intent.WASH) { if(waterInReach(chronicle.location())){boolean soap=items.hasAtLeast(chronicle.id(),"soap",1)&&items.consumeOne(chronicle.id(),"soap",resolvedAt);physiology.wash(chronicle.id(),soap);perception=soap?"You work the soap into a lather and scrub down; the water carries off far more than it would alone, and you come up clean.":"Cold water runs over your hands and skin, carrying away some of the dirt.";}else{outcome="FAILED";perception="You look for water to wash in, but there is none here — the ground is dry, and nothing runs or stands within reach.";} }
        else if (intent == Intent.WARM_BODY) { if(fireInReach(chronicle.location())){physiology.warmByFire(chronicle.id());perception="You crouch close to the fire and hold out your hands, letting its heat soak in until the chill loosens its grip.";}else{outcome="FAILED";perception="You cast about for warmth, but there is no fire burning within reach — only the cold air and colder ground.";} }
        else if (intent == Intent.DRY_BODY) { if(fireInReach(chronicle.location())||shelterInReach(chronicle.location())){physiology.dryOff(chronicle.id());perception="Out of the wet, you work the damp from your skin and clothes until you are drier than you were.";}else{outcome="FAILED";perception="You try to dry off, but with no fire and no cover here the damp only clings the harder.";} }
        else if (intent == Intent.COOL_BODY) { if(shelterInReach(chronicle.location())||waterInReach(chronicle.location())){physiology.coolOff(chronicle.id());perception="You get out of the sun and let the heat bleed off you until your head clears a little.";}else{outcome="FAILED";perception="You look for shade or water to cool in, but there is none here — the heat has nowhere to go.";} }
        else if (intent == Intent.SHELTER_BODY) { if(shelterInReach(chronicle.location())){physiology.shelterFromWeather(chronicle.id());perception="You duck under the shelter and out of the weather, and the worst of it stops reaching you.";}else{outcome="FAILED";perception="You look for cover, but there is none built here — nothing stands between you and the weather.";} }
        else if (intent == Intent.STRETCH) { physiology.stretch(chronicle.id()); perception="You stretch and work the stiffness out of your limbs, and stand a little easier for it."; }
        else if (intent == Intent.MAKE_BED) { String[] r = construction.makeBed(chronicle.id(), chronicle.location(), resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.MAINTAIN_CAMP) { String[] r = construction.maintainCamp(chronicle.id(), chronicle.location(), resolvedAt); outcome = r[0]; perception = r[1]; if ("SUCCEEDED".equals(outcome)) physiology.settleCamp(chronicle.id()); }
        else if (intent == Intent.PLACE_WINDBREAK) { String[] r = construction.placeWindbreak(chronicle.id(), chronicle.location(), resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.PLACE_COVER) { String[] r = construction.placeCover(chronicle.id(), chronicle.location(), resolvedAt, coverKindOf(text.toLowerCase(Locale.ROOT))); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.TREAT_WOUND) { if (physiology.bindWound(chronicle.id(), items, actionId, resolvedAt)) perception="You press and bind the wounded place until the immediate bleeding eases."; else { outcome="FAILED"; perception="You work at yourself for a moment, then stop without changing the wound."; } }
        else if (intent == Intent.EDIT_DOCUMENT) { try { reviseDocument(chronicle.id(), actionId, resolvedAt, text); perception="Your marks remain on the physical page."; } catch (IllegalArgumentException | IllegalStateException ignored) { outcome="FAILED"; perception="You handle the page for a while, then set it aside unchanged."; } }
        else if (intent == Intent.CONFRONT_WILDLIFE) { double spec=SuccessModel.specificity(text,HUNT_SIGNALS); double fam=capability.familiarity(chronicle.id(),"AIM"); int tactic=(int)Math.round(spec*30 + Math.min(0.20,fam*3.0)*100); WildlifeEncounterService.EncounterResult result=wildlife.confront(chronicle.id(),chronicle.location(),actionId,resolvedAt,tactic); outcome=result.outcome(); perception=result.narration(); }
        else if (intent == Intent.HARVEST_CARCASS) { WildlifeEncounterService.HarvestResult result=wildlife.harvest(chronicle.id(),chronicle.location(),actionId,resolvedAt); outcome=result.outcome(); perception=result.narration(); }
        else if (intent == Intent.DISENGAGE) { physiology.applyMinorExertion(chronicle.id(), 3); String v=text.toLowerCase(Locale.ROOT); perception = (v.contains("hide")||v.contains("conceal")||v.contains("go to ground")) ? "You drop low and still, putting cover between yourself and whatever you fear, and wait there unseen." : (v.contains("flee")||v.contains("run")||v.contains("escape")) ? "You break away and put ground between yourself and the danger, heart hammering, until it falls behind you." : "You give way and withdraw step by careful step, keeping your face to the danger until distance makes it safe."; }
        else if (intent == Intent.LIGHT_FIRE) {
            // Layer 2 (how well the attempt was described) and Layer 3 (how practiced
            // the chronicle is) modulate whether the ember catches. Friction fire is
            // hard: a bare command from an unpracticed hand often fails and burns the tinder.
            double spec = SuccessModel.specificity(text, FIRE_SIGNALS);
            double fam = capability.familiarity(chronicle.id(), "FINE_MOTOR");
            // Which technique was reached for decides the baseline (V49): a hand drill
            // is punishing, a bow drill workable, flint on pyrite kinder still, and a
            // carried ember easiest of all. Description and practice then move it from
            // there, so naming a method well is rewarded rather than assumed.
            String method = fire.detectMethod(chronicle.id(), text);
            FireService.MethodProfile mp = fire.profile(chronicle.id(), method);
            double base = Math.max(0.05, 1.0 - mp.difficulty() / 100.0);
            double prob = base + 0.35 * spec + Math.min(0.30, fam * 3.0);
            // Daylight methods are worthless in the dark, and most ignition fails in rain.
            int hourOfDay = resolvedAt.atZone(java.time.ZoneOffset.UTC).getHour();
            if (mp.requiresDaylight() && (hourOfDay < 7 || hourOfDay > 18)) prob = 0;
            prob *= wetFireOdds(chronicle.location(), mp.requiresDry(), beforeWeather);
            boolean caught = SuccessModel.roll(Math.min(0.95, prob), actionId);
            FireService.LightResult r = fire.light(chronicle.id(), chronicle.location(), resolvedAt, caught, method);
            if(r==FireService.LightResult.LIT){perception="An ember catches in the tinder. You feed it into the fuel and a flame steadies within the ring of stone.";} else {outcome="FAILED"; perception=switch(r){ case NO_PIT -> "You crouch over the bare ground, but there is no ring of stone here to hold a fire."; case NO_KIT -> "You press your palms together and search for something to work, but your hands raise no heat from bare wood alone."; case NO_TINDER -> "You spin the spindle until a wisp of smoke curls up, but the ember finds nothing fine enough to catch, and dies against the board."; case NO_FUEL -> "A small ember glows in the tinder and then fades, with nothing dry set ready to build it into a fire."; case NO_CATCH -> "You work the spindle and coax a thread of smoke, but the ember never quite takes, and the tinder blackens to nothing in your hands."; default -> "The attempt leaves the air unchanged."; };} }
        else if (intent == Intent.FEED_FIRE) { if(fire.feed(chronicle.id(),chronicle.location(),resolvedAt)) perception="You settle another dry branch into the coals and watch the fire deepen."; else {outcome="FAILED"; perception="You handle the branch near the cold stones, then take it back.";} }
        else if (intent == Intent.EXTINGUISH_FIRE) { if(fire.extinguish(chronicle.location(),resolvedAt)) perception="You smother the fire with earth and beat down the last of it until only cooling, dead ash remains."; else {outcome="FAILED"; perception="You move to put out a fire, but none is burning here.";} }
        else if (intent == Intent.BANK_FIRE) { if(fire.bank(chronicle.location(),resolvedAt)) perception="You rake the coals into a tight heap and cover them with ash, so the embers will hold low and long and can be woken again later."; else {outcome="FAILED"; perception="There is no fire here to bank down.";} }
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
        else if (intent == Intent.CRAFT_BASKET) { if (items.basketWeaveUnitsInReach(chronicle.id()) >= 8) { items.craftBasket(); perception="Flexible lengths — fibre, vine, or cordage — tighten row on row beneath your hands until a rough basket holds its shape."; } else { outcome="FAILED"; perception="You have too little flexible stock within reach to weave a basket — it wants eight lengths of plant fibre, vine, or cordage, and the loose few you hold fall away from one another."; } }
        else if (intent == Intent.CRAFT_SPEAR) { if (items.hasAtLeast(chronicle.id(),"dry_branch",1) && items.hasAtLeast(chronicle.id(),"field_stone",1) && items.hasAtLeast(chronicle.id(),"plant_fiber",1)) { items.craftPrimitiveSpear(resolvedAt); perception="You lash the shaped stone hard against the straight branch, working the binding tight until it will not shift. A crude spear rests in your hand — rough in the balance, but real in the point."; } else { outcome="FAILED"; perception="The pieces refuse to hold together long enough to become a usable tool. Stone slides against wood, the binding slips, and what you meant to make comes apart in your hands."; } }
        else if (intent == Intent.CRAFT_KNIFE) { if (items.hasAtLeast(chronicle.id(),"field_stone",1) && items.hasAtLeast(chronicle.id(),"plant_fiber",1)) { items.craftPrimitiveTool("stone_knife","Stone knife",false,resolvedAt); perception="You strike a working edge into the stone, patient blow after blow, then bind it firm into a haft that fills the palm. It is small and plain, but it will cut."; } else { outcome="FAILED"; perception="The stone and loose fiber never settle into a usable edge. Each strike takes off the wrong flake, and what is left is a lump, not a blade."; } }
        else if (intent == Intent.CRAFT_HAMMER) { if (items.hasAtLeast(chronicle.id(),"field_stone",1) && items.hasAtLeast(chronicle.id(),"plant_fiber",1) && items.hasAtLeast(chronicle.id(),"dry_branch",1)) { items.craftPrimitiveTool("stone_hammer","Stone hammer",true,resolvedAt); perception="You seat the heavy stone into the split of a branch and bind it down, turn on turn, until the head holds without any play. It sits in the hand with a solid, purposeful weight."; } else { outcome="FAILED"; perception="The head shifts loose before the tool can hold together. Every test-swing works the binding a little looser, until you are left with a stone and a stick again."; } }
        else if (intent == Intent.CRAFT_PICKAXE) { if (items.hasAtLeast(chronicle.id(),"field_stone",1) && items.hasAtLeast(chronicle.id(),"plant_fiber",1) && items.hasAtLeast(chronicle.id(),"dry_branch",1)) { items.craftPrimitiveTool("primitive_pickaxe","Primitive pickaxe",true,resolvedAt); perception="You lash a shaped stone crosswise to the branch and swing it through the air to test it. The weight pulls true at the end of the arc — a tool made for breaking hard ground."; } else { outcome="FAILED"; perception="The pieces refuse to hold in a form that can work the ground. The head lolls sideways on every swing, and no amount of binding sets it straight."; } }
        else if (intent == Intent.CRAFT_HATCHET) { if (items.hasAtLeast(chronicle.id(),"field_stone",1) && items.hasAtLeast(chronicle.id(),"plant_fiber",1) && items.hasAtLeast(chronicle.id(),"dry_branch",1)) { items.craftPrimitiveTool("stone_hatchet","Stone hatchet",true,resolvedAt); perception="You knap a stone down to a broad, biting edge and lash it into the split of a branch, wedging and binding until it is one thing. It is heavy in the head and rough in the haft, but the edge bites — a hatchet, enough to bring a tree down."; } else { outcome="FAILED"; perception="Without a stone edge, a branch, and cordage to bind them, the head never seats. It rocks in the split and tears free the moment you put any force behind it."; } }
        else if (intent == Intent.CRAFT_FIRE_KIT) { if (items.craftFireKit(resolvedAt)) perception="You carve a flat notched board and a straight spindle from dry wood — the makings of a friction fire."; else { outcome="FAILED"; perception="Without a blade and sound dry wood, your hands shape nothing that would raise an ember."; } }
        else if (intent == Intent.CRAFT_TINDER) { if (items.craftTinder(resolvedAt)) perception="You tease plant fiber apart into a loose, dry nest, fine enough to hold a spark."; else { outcome="FAILED"; perception="You pull at what you have, but nothing here is dry and fine enough to catch an ember."; } }
        else if (intent == Intent.CRAFT_DESK) { if (items.craftFurniture(chronicle.id(),chronicle.location(),"wooden_desk","Wooden desk",5,0,false,0,0,resolvedAt)) perception="You lash cut branches into a broad, steady work surface and set it in place. The desk stands where you built it."; else { outcome="FAILED"; perception="Without a blade, sound branches, and fiber to bind them, no surface holds together."; } }
        else if (intent == Intent.CRAFT_CHAIR) { if (items.craftFurniture(chronicle.id(),chronicle.location(),"wooden_chair","Wooden chair",3,0,false,0,0,resolvedAt)) perception="You bind branches into a low seat and set it down. The chair holds your weight."; else { outcome="FAILED"; perception="The pieces will not hold as a seat without a blade, branches, and binding fiber."; } }
        else if (intent == Intent.CRAFT_SHELF) { if (items.craftFurniture(chronicle.id(),chronicle.location(),"stone_shelf","Stone slab shelf",0,3,true,30000,40000,resolvedAt)) perception="You stack and level flat slabs into a standing shelf of stone — a place to keep what you make and what you know."; else { outcome="FAILED"; perception="Without enough flat stone slabs, nothing here will stand as a shelf."; } }
        // A workstation (V69): a steady surface or a loom that eases and speeds the crafts it serves. It never
        // gates a craft and never decides its grade — the hands do that — so it is built for efficiency.
        else if (intent == Intent.CRAFT_WORKSTATION) {
            String v = text.toLowerCase(Locale.ROOT); boolean ok; String made;
            if (v.contains("loom")) { ok = items.craftFurniture(chronicle.id(), chronicle.location(), "loom", "Upright loom", 4, 0, false, 0, 0, resolvedAt); made = "loom"; }
            else if (v.contains("stone")) { ok = items.craftFurniture(chronicle.id(), chronicle.location(), "stoneworking_bench", "Stoneworking bench", 3, 2, false, 0, 0, resolvedAt); made = "stoneworking bench"; }
            else { ok = items.craftFurniture(chronicle.id(), chronicle.location(), "woodworking_bench", "Woodworking bench", 5, 0, false, 0, 0, resolvedAt); made = "woodworking bench"; }
            outcome = ok ? "SUCCEEDED" : "FAILED";
            perception = ok ? "You frame and lash together a sturdy " + made + " and set it in place — a steady, waist-high surface to hold the work while your hands are busy." : "Without a blade, sound branches, and fiber to bind them, no workstation holds together.";
        }
        else if (intent == Intent.CRAFT_NET) {
            // Making a net is not fishing (#36): a mesh knotted from processed fibre cordage. A landing net
            // adds a bent-branch hoop. Reachable cordage + a blade; the object persists and can later be used.
            boolean landing = text.toLowerCase(Locale.ROOT).contains("landing") || text.toLowerCase(Locale.ROOT).contains("hoop") || text.toLowerCase(Locale.ROOT).contains("dip net");
            int need = landing ? 3 : 6;
            if (!items.hasCuttingTool(chronicle.id())) { outcome = "FAILED"; perception = "You gather the cordage to knot a net, but with no blade to cut and start it, the mesh will not begin."; }
            else if (!items.hasAtLeast(chronicle.id(), "fiber_cordage", need)) { outcome = "FAILED"; perception = "A net wants far more cordage than you have twisted — the mesh needs " + need + " lengths of processed fibre cordage, knotted row on row. Twist more first."; }
            else if (landing && !items.hasAtLeast(chronicle.id(), "dry_branch", 2)) { outcome = "FAILED"; perception = "You have the cordage for the mesh, but nothing to bend into a hoop for a landing net — you need a couple of green branches for the frame."; }
            else { items.craftFishingNet(landing, resolvedAt); perception = landing ? "You bend a branch into a hoop and knot the cordage across it, row by row, until a landing net hangs ready in your hand." : "You knot the cordage row on row, working the mesh even and true, until a fishing net lies finished across your knees."; }
        }
        else if (intent == Intent.CRAFT_BELT) {
            // The primitive utility belt (#35): a fibre strap with tool loops. Cordage for the strap, plant
            // fibre for the loops, a blade to cut and fit them; it equips to the waist once made.
            if (!items.hasCuttingTool(chronicle.id())) { outcome = "FAILED"; perception = "You lay out the fibre to make a belt, but with no blade to cut and fit the strap, it will not come together."; }
            else if (!items.hasAtLeast(chronicle.id(), "fiber_cordage", 2) || !items.hasAtLeast(chronicle.id(), "plant_fiber", 2)) { outcome = "FAILED"; perception = "A belt wants a length of twisted cordage for the strap and plant fibre for the loops, and you have not both to hand. Twist cordage and gather fibre first."; }
            else { items.craftUtilityBelt(resolvedAt); perception = "You cut and fit a length of cordage into a waist strap and work plant fibre into a row of loops along it. A rough but real utility belt, ready to carry what your hands use most."; }
        }
        else if (intent == Intent.BUILD_FIRE_PIT) { if (construction.buildFirePit(chronicle.id(), chronicle.location(), actionId, resolvedAt)) perception = "You settle stone into a low, deliberate ring. The fire pit remains where you made it."; else { outcome = "FAILED"; perception = "You set a few stones apart, then leave them where they lie. The ground remains unchanged."; } }
        else if (intent == Intent.BUILD_ALARM) { String[] r = construction.buildCampAlarm(chronicle.id(), chronicle.location(), resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.STRIP_BARK) { int n = items.stripBark(chronicle.id(), chronicle.location(), resolvedAt); outcome = n > 0 ? "SUCCEEDED" : "FAILED"; perception = n > 0 ? "You work a broad strip of bark free from a tree and keep it." : "You look for a tree with workable bark, but find none within reach."; }
        else if (intent == Intent.MAKE_CHARCOAL) { boolean made = items.makeCharcoal(chronicle.id(), chronicle.location(), resolvedAt); outcome = made ? "SUCCEEDED" : "FAILED"; perception = made ? "You lift a piece of cooled charcoal from the spent fire." : "You search for usable charcoal, but the ground offers none."; }
        else if (intent == Intent.WRITE) { String[] r = writeOrDraw(chronicle, text, actionId, resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.SKETCH_MAP) { String[] r = sketchMap(chronicle, text, actionId, resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.EQUIP) { String[] r = equipByName(chronicle, text, resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.UNEQUIP) { String[] r = unequipByName(chronicle, text, resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.DROP) { String[] r = dropByName(chronicle, text, resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.PICK_UP) { String[] r = items.pickUp(chronicle.id(), chronicle.location(), text, resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.STORE) { String[] r = items.storeInContainer(chronicle.id(), chronicle.location(), text, resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.OPEN_CONTAINER) { String[] r = items.setContainerAccess(chronicle.id(), chronicle.location(), text, "OPEN", resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.CLOSE_CONTAINER) { String v = text.toLowerCase(Locale.ROOT); String st = (v.contains("seal") || v.contains("stopper") || v.contains("tightly") || v.contains("tie shut") || v.contains("tie it shut")) ? "SEALED" : "CLOSED"; String[] r = items.setContainerAccess(chronicle.id(), chronicle.location(), text, st, resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.DESIGNATE) { String[] r = designate(chronicle, text, actionId, resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.REFINE) { String[] r = refineByName(chronicle, text, resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.REPAIR_ITEM) { String[] r = items.repairNamedItem(chronicle.id(), chronicle.location(), text, resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.REPAIR_STRUCTURE) { String[] r = construction.repairStructure(chronicle.id(), chronicle.location(), text, resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.DISMANTLE) { String[] r = construction.dismantle(chronicle.id(), chronicle.location(), text, resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.INSPECT) { String[] r = assembly.inspect(chronicle.id(), text); outcome = r[0]; perception = r[1]; }
        // The examination verbs (#25): a focused read of one subject — a reachable item the text names,
        // else the place itself — at a depth set by the relevant mastery. Perception (EXAMINE) sees the
        // surface, insight (ANALYZE) reads what a thing is and does, knowledge (INVESTIGATE) infers its
        // origin. Distinct from OBSERVE, which surveys the whole surroundings rather than one subject.
        else if (intent == Intent.EXAMINE)     { String[] r = examination.examine(chronicle.id(), chronicle.location(), text, ExaminationService.Mode.INSPECT);     outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.ANALYZE)     { String[] r = examination.examine(chronicle.id(), chronicle.location(), text, ExaminationService.Mode.ANALYZE);     outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.INVESTIGATE) { String[] r = examination.examine(chronicle.id(), chronicle.location(), text, ExaminationService.Mode.INVESTIGATE); outcome = r[0]; perception = r[1]; }
        // The non-visual senses (#65): grounded in the actual weather, water, fire, life, and ground here.
        else if (intent == Intent.SEARCH) { String[] r = examination.sense(chronicle.id(), chronicle.location(), text, ExaminationService.Sense.SEARCH); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.LISTEN) { String[] r = examination.sense(chronicle.id(), chronicle.location(), text, ExaminationService.Sense.LISTEN); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.SMELL)  { String[] r = examination.sense(chronicle.id(), chronicle.location(), text, ExaminationService.Sense.SMELL);  outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.FEEL)   { String[] r = examination.sense(chronicle.id(), chronicle.location(), text, ExaminationService.Sense.FEEL);   outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.READ)    { String[] r = readDocument(chronicle, text); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.MEASURE) { String[] r = examination.measure(chronicle.id(), chronicle.location(), text); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.REWORK) { String[] r = assembly.rework(chronicle.id(), text, resolvedAt); outcome = r[0]; perception = r[1]; }
        // A personal physical act: time passes and the body pays for it. The narrator
        // witnesses without comment; the physiology tick will, in time, do the rest.
        else if (intent == Intent.PERSONAL_ACT) { physiology.applyPersonalActExertion(chronicle.id()); perception = inputClassifier.narrate(ActionInputClassifier.InputClass.PERSONAL_PHYSICAL_ACT, text); }
        // A sexual or violent contact attempt with a live animal, routed into the
        // encounter system with no preparation at all — the animal answers in kind.
        else if (intent == Intent.AGGRESSION_WILDLIFE) { WildlifeEncounterService.EncounterResult result = wildlife.confront(chronicle.id(), chronicle.location(), actionId, resolvedAt, -20); outcome = result.outcome(); perception = result.narration(); }
        // Venting at scenery: a minute and a little energy, and nothing else moves.
        else if (intent == Intent.AGGRESSION_INANIMATE) { physiology.applyMinorExertion(chronicle.id(), 2); perception = inputClassifier.narrate(ActionInputClassifier.InputClass.AGGRESSION_TOWARD_INANIMATE, text); }
        else {
            // A staged assembly (V58) is tried before a single-shot process: "build a
            // bow" or "raise a drying rack" advances the next stage of a multi-stage
            // thing, where a material process could only ever do it in one act. Like
            // processes, assemblies live in data, so a new one is playable by migration
            // alone. A null here means the text names no assembly.
            String[] a = assembly.advance(chronicle.id(), chronicle.location(), text, resolvedAt);
            if (a != null) { intent = Intent.ADVANCE_ASSEMBLY; outcome = a[0]; perception = a[1]; }
            else {
                // Before calling an action unresolvable, see whether it names a material
                // process (V52). Those live in a table rather than in this chain, so a new
                // material chain becomes playable by migration alone — the classifier never
                // has to learn about planks or tanning or pitch. Only a genuine non-match
                // falls through to the flat witness line.
                String[] r = items.runProcess(chronicle.id(), chronicle.location(), text, resolvedAt);
                if ("SUCCEEDED".equals(r[0]) || !r[1].startsWith("You turn the material over")) {
                    intent = Intent.PROCESS_MATERIAL; outcome = r[0]; perception = r[1];
                } else {
                    // Deterministic miss. The runtime authoring pipeline (DR-0021) may compose it from
                    // EXISTING processes, or — if authoring is enabled — author a new scoped mechanic under
                    // the physics gate + QA. Inert with AI off (empty) — the game behaves exactly as before.
                    String[] composed = authoring.attempt(chronicle.id(), chronicle.location(), text, resolvedAt).orElse(null);
                    if (composed != null) { intent = Intent.PROCESS_MATERIAL; outcome = composed[0]; perception = composed[1]; }
                    else { outcome = "FAILED"; String[] pool = items.isMaterialWork(text) ? MATERIAL_UNRESOLVED : UNRESOLVED_ATTEMPT; perception = pool[Math.floorMod(text.hashCode(), pool.length)]; }
                }
            }
        }
        // The world's turn (V44). While the chronicle was occupied, anything hunting
        // this ground had the chance to reach them. It is checked only for acts that
        // take real time and leave the body exposed — not for a moment's equipping,
        // and never after an encounter the chronicle already fought through.
        String attention = attentionLevel(text, intent);
        if (exposesToWildlife(intent) && minutes >= 10) {
            // A disengage this turn is an active break from contact; hiding/going to ground is a concealed one.
            boolean breakingContact = intent == Intent.DISENGAGE;
            String lower = text.toLowerCase(Locale.ROOT);
            boolean concealed = breakingContact && (lower.contains("hide") || lower.contains("conceal") || lower.contains("go to ground"));
            String ambush = wildlife.passiveEncounter(chronicle.id(), chronicle.location(), actionId, resolvedAt, attention, breakingContact, concealed);
            if (ambush != null) perception = perception + " " + ambush;
        }
        // Bucket D (#27) — the physical cost of the work itself, on top of the passive metabolic tick:
        // hard labour tires and dirties the body, so felling and hauling are not free the way standing
        // still is. A failed attempt is still effort, at half. Recovery and self-costing acts (rest,
        // sleep, eat, drink, wash, personal/aggression) carry no labour cost — they run their own
        // physiology. Applied before the body snapshot is read, so the drain shows in the frame delta.
        Labor labor = laborOf(intent);
        boolean failed = "FAILED".equals(outcome);
        physiology.applyLabor(chronicle.id(), failed ? (labor.energy() + 1) / 2 : labor.energy(), failed ? labor.hygiene() / 2 : labor.hygiene());
        // Bucket B — the narration contract: wrap the deterministic core in a clause of setting, so the
        // world is present in the prose and not just the act. Success and failure alike are grounded;
        // the punctuation rule (weather when felt/changing, the land on deliberate looking) lives in the
        // NarrationEngine so it lands when it means something rather than tagging every line. OBSERVE is
        // excluded — its own survey prose already IS the setting, in far more detail.
        if (intent != Intent.OBSERVE) perception = groundPerception(perception, chronicle.location(), attention, beforeWeather, resolvedAt);
        // chronicle_action is append-only IMMUTABLE history (the prevent_chronicle_action_mutation
        // trigger blocks any UPDATE/DELETE). Persist the deterministic prose ONCE, here, and never
        // touch the row again — the source of truth stays untouched. The death coda and the Simulation
        // Agent's sentence are added afterward and captured in a SEPARATE overlay row (below), never a
        // write-back. This also makes the (paid) AI call the last fallible thing resolve() does: every
        // operation that can throw a hard persistence error runs and commits-in-transaction BEFORE a
        // token is spent, so a DB failure — or a retry of one — costs nothing.
        narration.validate(perception);
        String deterministicNarration = perception; // the durable source of truth, before coda/AI
        jdbc.update("INSERT INTO chronicle_action (id, chronicle_id, resolved_at, action_text, intent_type, outcome, duration_minutes, narration, idempotency_key) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", actionId, chronicle.id(), resolvedTs, text.trim(), intent.name(), outcome, minutes, perception, idempotencyKey);
        jdbc.update("INSERT INTO chronicle_action_effect (action_id, effect_domain, effect_type, payload) VALUES (?, 'TIME', 'TIME_ADVANCED', jsonb_build_object('minutes', ?))", actionId, minutes);
        if (gatherEffectType != null) jdbc.update("INSERT INTO chronicle_action_effect (action_id, effect_domain, effect_type, payload) VALUES (?, 'ITEM', ?, jsonb_build_object(?, ?))", actionId, gatherEffectType, gatherPayloadKey, gatherCount);
        jdbc.update("INSERT INTO chronicle_event (chronicle_id, occurred_at, event_type, payload) VALUES (?, ?, 'CHRONICLE_ACTION_RESOLVED', jsonb_build_object('actionId', ?::text, 'intent', ?, 'outcome', ?))", chronicle.id(), resolvedTs, actionId.toString(), intent.name(), outcome);
        if ("SUCCEEDED".equals(outcome) && intent == Intent.CRAFT_BASKET) discoveries.record(chronicle.id(), "WOVEN_BASKET", actionId, resolvedAt);
        if ("SUCCEEDED".equals(outcome) && intent == Intent.BUILD_FIRE_PIT) discoveries.record(chronicle.id(), "STONE_FIRE_PIT", actionId, resolvedAt);
        // Personal acts and venting build no capability — there is no skill in them.
        boolean buildsCapability = intent != Intent.PERSONAL_ACT && intent != Intent.AGGRESSION_INANIMATE && intent != Intent.AGGRESSION_WILDLIFE;
        if ("SUCCEEDED".equals(outcome) && buildsCapability) {
            // Every successful action feeds the capability family it actually exercises (GitHub #26):
            // hauling and breaking ground build LOAD, hunting and trapping build AIM, looking and
            // tracking build ATTENTION, travel builds LOCOMOTION, rest builds RECOVERY, and the skilled
            // hand-work of crafting/processing/writing builds FINE_MOTOR — no longer all lumped together.
            String domain = capabilityDomainOf(intent);
            capability.record(chronicle.id(), actionId, domain, minutes, "LOAD".equals(domain) ? .18 : .05, "RECOVERY".equals(domain) ? .75 : .45, resolvedAt);
        }
        // The base row is now durable. Everything below shapes the DISPLAYED narration (death coda,
        // AI sentence). It is written only to the separate overlay table — never back to the base row —
        // so nothing here can raise the immutability trigger.
        ChroniclePhysiologyService.BodyHudSnapshot afterBody = physiology.activeBody();
        // If the body is gone, the chronicle died this action: activeBody() only reports a
        // LIVING chronicle. Keep the last-living snapshot so the HUD has a final state
        // rather than a null the client would choke on, close the narration with a witnessed
        // ending, and flag the death so the client can send the player back to the shore.
        boolean died = afterBody == null;
        if (died) {
            afterBody = beforeBody;
            String cause = jdbc.query("SELECT death_cause FROM chronicle WHERE id=?", rs -> rs.next() ? rs.getString(1) : null, chronicle.id());
            perception = perception + deathCoda(cause);
        }
        PerceptionFrame frame = buildFrame(chronicle, intent, outcome, perception, resolvedAt, beforeBody, afterBody, beforeWeather, attention);
        // The Simulation Agent's voice (Task #21) — the last fallible thing resolve() does, and the only
        // network call. On moments the router judges worth one, it appends a single atmospheric sentence
        // on top of the deterministic prose. The router is a pure, free function that gates ~90% of
        // actions away from the network; refine() is total and, when the feature is off or the model
        // fails, returns the deterministic prose unchanged — so the world's own narration always stands.
        boolean aiContributed = false;
        int stateChanges = frame.sinceLastFrame() == null ? 0 : frame.sinceLastFrame().size();
        if (narrationRouter.shouldUseAI(intent.name(), outcome, attention, text, stateChanges, 0, null, died, false)) {
            String refined = simulationNarrator.refine(frame, perception);
            if (!refined.equals(perception)) {
                perception = refined;
                aiContributed = true;
                frame = new PerceptionFrame(frame.intent(), frame.outcome(), frame.location(), frame.timeOfDay(), frame.weather(), frame.attention(), frame.nearbyObjects(), frame.physiology(), frame.sinceLastFrame(), perception);
            }
        }
        // Persist the DISPLAYED narration as an overlay so history, the journey archive, and the PDF
        // export show exactly what the player saw — the AI sentence and/or the death coda — instead of
        // reverting to the bare deterministic prose on reload. Only when it actually differs. This is a
        // fresh INSERT into a separate, trigger-free table whose only FK (action_id) was just satisfied,
        // so it cannot raise the immutability error; record the model for a later narration-quality
        // review (null when only the death coda, not AI, changed the text).
        if (!perception.equals(deterministicNarration)) {
            jdbc.update("INSERT INTO chronicle_action_narration (action_id, narration, model) VALUES (?, ?, ?)",
                actionId, perception, aiContributed ? simulationNarrator.modelName() : null);
        }
        return new ActionResult(actionId, intent.name(), outcome, minutes, resolvedAt, perception, afterBody, frame, died);
    }
    @Transactional(readOnly = true)
    public NarrationPage narrationHistory(Instant before, UUID beforeId, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 50));
        UUID chronicle = jdbc.query("SELECT id FROM chronicle WHERE life_state='LIVING'", rs -> rs.next() ? rs.getObject(1, UUID.class) : null);
        if (chronicle == null) return new NarrationPage(List.of(), false);
        if ((before == null) != (beforeId == null)) throw new IllegalArgumentException("Narration cursor requires both time and action identity.");
        // COALESCE the overlay over the base narration so scroll-back shows the enriched prose the
        // player saw live (AI sentence / death coda) when one was stored, and the deterministic prose
        // otherwise. The base row is the join anchor and the fallback.
        List<NarrationEntry> entries = before == null
                ? jdbc.query("SELECT ca.id, ca.resolved_at, COALESCE(can.narration, ca.narration) FROM chronicle_action ca LEFT JOIN chronicle_action_narration can ON can.action_id = ca.id WHERE ca.chronicle_id = ? AND ca.narration IS NOT NULL ORDER BY ca.resolved_at DESC, ca.id DESC LIMIT ?", (rs, row) -> new NarrationEntry(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant(), rs.getString(3)), chronicle, limit + 1)
                : jdbc.query("SELECT ca.id, ca.resolved_at, COALESCE(can.narration, ca.narration) FROM chronicle_action ca LEFT JOIN chronicle_action_narration can ON can.action_id = ca.id WHERE ca.chronicle_id = ? AND ca.narration IS NOT NULL AND (ca.resolved_at, ca.id) < (?, ?) ORDER BY ca.resolved_at DESC, ca.id DESC LIMIT ?", (rs, row) -> new NarrationEntry(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant(), rs.getString(3)), chronicle, java.sql.Timestamp.from(before), beforeId, limit + 1);
        boolean hasMore = entries.size() > limit;
        if (hasMore) entries = entries.subList(0, limit);
        return new NarrationPage(List.copyOf(entries), hasMore);
    }
    /**
     * A full read of the surroundings: the place's given name, its terrain, the
     * hour and weather, what stands here, what the neighbouring ground hints at,
     * and how well the chronicle knows this spot. This is the player's primary way
     * to orient and decide where to explore — the narrator describes, never advises.
     */
    private String survey(ActiveChronicle chronicle, Instant at) {
        UUID loc = chronicle.location();
        java.util.Map<String,Object> here = jdbc.queryForMap("SELECT world_id, grid_x, grid_y, biome, elevation FROM world_chunk WHERE id=?", loc);
        UUID world = (UUID) here.get("world_id"); int gx=(int)here.get("grid_x"); int gy=(int)here.get("grid_y"); String biome=(String)here.get("biome");
        StringBuilder s = new StringBuilder();
        // The settlement's named zones (V70/F8): the one you stand in, and the others you have raised here — the
        // shape of a place you built, not a bare chunk. current_zone can be stale from another chunk, so it only
        // counts as "here" when it is actually one of this chunk's named zones.
        String currentZone = jdbc.query("SELECT current_zone FROM chronicle WHERE id=?", rs -> rs.next() ? rs.getString(1) : null, chronicle.id());
        java.util.List<String> zones = jdbc.query("SELECT name FROM chronicle_named_location WHERE chronicle_id=? AND chunk_id=? ORDER BY name", (rs, i) -> rs.getString(1), chronicle.id(), loc);
        boolean hereIsCurrent = currentZone != null && zones.contains(currentZone);
        if (hereIsCurrent) s.append("This is ").append(currentZone).append(", a place you named and made your own. ");
        java.util.List<String> others = zones.stream().filter(z -> !(hereIsCurrent && z.equals(currentZone))).toList();
        if (!others.isEmpty()) s.append(others.size() == 1 ? "Nearby stands " : "Nearby stand ").append(joinAnd(others)).append(" — the marks of a settlement you have raised here. ");
        s.append(biomeDescription(biome)).append(" ");
        s.append(timeOfDay(at)).append(" ");
        String weather = jdbc.query("SELECT weather_kind,intensity FROM world_weather WHERE world_id=?", rs -> rs.next() ? weatherPhrase(rs.getString(1), rs.getInt(2)) : null, world);
        if (weather != null) s.append(weather).append(" ");
        // What stands on this ground.
        Integer sites = jdbc.queryForObject("SELECT COUNT(*) FROM ecology_site WHERE chunk_id=?", Integer.class, loc);
        Integer builds = jdbc.queryForObject("SELECT COUNT(*) FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE'", Integer.class, loc);
        Integer markers = jdbc.queryForObject("SELECT COUNT(*) FROM location_marker WHERE chunk_id=?", Integer.class, loc);
        Integer carcasses = jdbc.queryForObject("SELECT COUNT(*) FROM world_object WHERE current_location_id=? AND object_type='CARCASS' AND lifecycle_state='ACTIVE'", Integer.class, loc);
        if (builds != null && builds > 0) s.append("Structures you raised stand here. ");
        if (markers != null && markers > 0) s.append("A marker you left catches your eye, quietly confirming this is a place you have been. ");
        if (sites != null && sites > 0) s.append("The ground shows signs of living things that pass through or feed here. ");
        if (carcasses != null && carcasses > 0) s.append("A fallen animal lies nearby, not yet returned to the earth. ");
        // Loose objects left on this ground — anything dropped or set down here, so a dropped thing stays
        // visible and can be taken up again rather than seeming to vanish (#29/#41).
        java.util.List<String> ground = jdbc.query(
            "SELECT w.display_name FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "WHERE w.current_location_id=? AND w.current_owner_id IS NULL AND w.lifecycle_state='ACTIVE' " +
            "ORDER BY w.display_name LIMIT 6", (rs, i) -> rs.getString(1).toLowerCase(Locale.ROOT), loc);
        if (!ground.isEmpty()) s.append(ground.size() == 1 ? "On the ground here lies " : "On the ground here lie ")
            .append(joinAnd(ground)).append(ground.size() == 1 ? ", left where it was set down. " : ", left where they were set down. ");
        // What lies around — read from the neighbouring chunks, so the chronicle can choose a direction.
        String around = neighbourHints(world, gx, gy);
        if (!around.isEmpty()) s.append(around);
        // How well this ground is known.
        Integer visits = jdbc.queryForObject("SELECT COALESCE((SELECT visit_count FROM chronicle_chunk_visit WHERE chronicle_id=? AND chunk_id=?),0)", Integer.class, chronicle.id(), loc);
        if (visits != null && visits >= 5) s.append("You know this ground well; your feet have worn a familiarity into it. ");
        // What actually LIVES here (#37/#33): flora, wildlife, fish, insects the chunk really holds, named and
        // scaled by the eye that looks — a deliberate survey is high-attention, sharpened by perception mastery.
        String life = examination.presentLife(loc, Math.min(1.0, 0.7 + capability.familiarity(chronicle.id(), "ATTENTION")));
        if (!life.isEmpty()) s.append(life).append(" ");
        return s.toString().trim();
    }
    private String biomeDescription(String biome) {
        return switch (biome == null ? "" : biome) {
            case "TEMPERATE_FOREST" -> "Tall trees close overhead, their trunks dark with damp and the floor deep in leaf litter.";
            case "WETLAND" -> "The ground is soft and waterlogged, reeds standing in slow, dark water.";
            case "RIVER_BANK" -> "A river runs past a bank of smoothed stone and packed earth.";
            case "CLAY_DEPOSIT" -> "The earth here is heavy and grey-brown, slick clay breaking the surface.";
            case "MOUNTAIN" -> "Bare rock rises in broken shelves, the air thin and cold against exposed stone.";
            case "HIGHLAND" -> "High, open ground rolls away in coarse grass and outcrops of weathered rock.";
            case "GRASSLAND" -> "Open grass runs to every horizon, bending in long waves under the wind.";
            case "OCEAN" -> "Water stretches beyond reach, grey and restless to the edge of sight.";
            default -> "The land around you is plain and unremarkable, holding little at first glance.";
        };
    }
    private String timeOfDay(Instant at) {
        int h = at.atZone(java.time.ZoneOffset.UTC).getHour();
        if (h < 5) return "It is deep night, the dark near total.";
        if (h < 8) return "Early light is only beginning to reach the ground.";
        if (h < 12) return "The morning is up, the light clear and growing.";
        if (h < 15) return "The day stands at its height, the light full overhead.";
        if (h < 19) return "The light is lengthening toward evening.";
        if (h < 22) return "Dusk is settling, colour draining from the land.";
        return "Night has closed in, and little can be made out at a distance.";
    }
    private String weatherPhrase(String kind, int intensity) {
        String strength = intensity >= 66 ? "heavy " : intensity >= 33 ? "" : "light ";
        return switch (kind == null ? "" : kind) {
            case "CLEAR" -> "The sky is clear.";
            case "OVERCAST" -> "A flat grey overcast holds the sky.";
            case "RAIN" -> "A " + strength + "rain falls, ticking against leaf and stone.";
            case "STORM" -> "A storm drives " + strength + "rain sidelong on a hard wind.";
            case "SNOW" -> "A " + strength + "snow drifts down, muffling the ground.";
            default -> "";
        };
    }
    /** Directional hints drawn from adjacent chunks, so a survey reveals what lies nearby without naming an action to take. */
    private String neighbourHints(UUID world, int gx, int gy) {
        int[][] dirs = { {0,-1}, {0,1}, {1,0}, {-1,0} };
        String[] names = { "to the north", "to the south", "to the east", "to the west" };
        StringBuilder b = new StringBuilder();
        for (int i=0;i<dirs.length;i++) {
            String nb = jdbc.query("SELECT biome FROM world_chunk WHERE world_id=? AND grid_x=? AND grid_y=?", rs -> rs.next() ? rs.getString(1) : null, world, gx+dirs[i][0], gy+dirs[i][1]);
            String hint = neighbourPhrase(nb);
            if (hint != null) b.append(hint).append(" ").append(names[i]).append(". ");
        }
        return b.toString();
    }
    private String neighbourPhrase(String biome) {
        return switch (biome == null ? "" : biome) {
            case "RIVER_BANK", "WETLAND" -> "You catch the sound of water";
            case "MOUNTAIN", "HIGHLAND" -> "The ground rises toward higher, broken rock";
            case "TEMPERATE_FOREST" -> "The trees grow denser";
            case "GRASSLAND" -> "The land opens into grass";
            case "OCEAN" -> "You sense open water and a colder air";
            case "CLAY_DEPOSIT" -> "The earth looks heavier and greyer";
            default -> null;
        };
    }
    /** Give the current chunk a name and a role, or rename it. The chronicle's sense of place is built from these designations. */
    /** The named zone in the chronicle's CURRENT chunk that the text refers to, or null — so "go to the Tool
     *  Shed" is a short walk within the settlement, not an inter-chunk journey (V70/F8). */
    private String matchLocalZone(ActiveChronicle chronicle, String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return jdbc.query("SELECT name FROM chronicle_named_location WHERE chronicle_id=? AND chunk_id=? ORDER BY length(name) DESC",
            rs -> { while (rs.next()) { String n = rs.getString(1); if (lower.contains(n.toLowerCase(Locale.ROOT))) return n; } return null; },
            chronicle.id(), chronicle.location());
    }
    /** "a", "a and b", or "a, b, and c" — for listing a settlement's named zones. */
    private static String joinAnd(java.util.List<String> xs) {
        if (xs.isEmpty()) return "";
        if (xs.size() == 1) return xs.get(0);
        if (xs.size() == 2) return xs.get(0) + " and " + xs.get(1);
        return String.join(", ", xs.subList(0, xs.size() - 1)) + ", and " + xs.get(xs.size() - 1);
    }
    private String[] designate(ActiveChronicle chronicle, String text, UUID actionId, Instant at) {
        String name = extractDesignatedName(text);
        if (name == null || name.isBlank()) return new String[]{"FAILED", "You mean to give this place a name, but no clear name forms."};
        String value = text.toLowerCase(Locale.ROOT);
        String purpose = value.contains("sleep") ? "SLEEPING" : (value.contains("urinat")||value.contains("latrine")||value.contains("defecat")||value.contains("toilet")) ? "SANITATION" : (value.contains("drink")||value.contains("water")) ? "WATER" : (value.contains("store")||value.contains("storage")) ? "STORAGE" : (value.contains("craft")||value.contains("work")||value.contains("forge")||value.contains("manufactur")) ? "WORKSHOP" : (value.contains("archive")||value.contains("library")||value.contains("knowledge")) ? "KNOWLEDGE" : null;
        boolean memorize = value.contains("memoriz") || value.contains("memoris") || value.contains("remember") || value.contains("commit to memory") || value.contains("fix in") || value.contains("by heart");
        java.sql.Timestamp ts = java.sql.Timestamp.from(at);
        // Many named zones per chunk now (V70/F8): conflict on the name, so a chronicle may name several
        // distinct spots in one settlement, and re-naming the same one updates it. Standing at the place you
        // just named makes it your current zone.
        jdbc.update("INSERT INTO chronicle_named_location (chronicle_id,chunk_id,name,purpose_tag,designated_at,source_action_id,memorized,last_visited_at) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT (chronicle_id,chunk_id,name) DO UPDATE SET purpose_tag=EXCLUDED.purpose_tag, designated_at=EXCLUDED.designated_at, source_action_id=EXCLUDED.source_action_id, memorized=chronicle_named_location.memorized OR EXCLUDED.memorized, last_visited_at=EXCLUDED.last_visited_at", chronicle.id(), chronicle.location(), name, purpose, ts, actionId, memorize, ts);
        jdbc.update("UPDATE chronicle SET current_zone=? WHERE id=?", name, chronicle.id());
        boolean markerHere = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM location_marker WHERE chunk_id=?)", Boolean.class, chronicle.location()));
        String tail = memorize
            ? (markerHere ? " You fix it firmly in memory, and with your marker already standing here, you will find your way back to it without fail." : " You fix it firmly in memory — though without a marker or a map, memory alone may dim if you stay away too long.")
            : " But you leave no marker and do not commit the way to memory; unless you return often, this name may fade from you.";
        return new String[]{"SUCCEEDED", "You fix a name to this place: " + name + "." + tail};
    }
    private String extractDesignatedName(String text) {
        String raw = null;
        Matcher a = DESIGNATE_NAME.matcher(text);
        if (a.find()) raw = a.group(1);
        else { Matcher b = DESIGNATE_FALLBACK.matcher(text); if (b.find()) raw = b.group(1); }
        if (raw == null) return null;
        raw = raw.trim().replaceAll("[\\.\\!\\?\"']+$", "").trim();
        return raw.length() > 60 ? raw.substring(0, 60).trim() : raw;
    }
    private String move(ActiveChronicle chronicle, String action, UUID actionId, Instant occurredAt) {
        Direction direction = Direction.from(action);
        if (direction == null) return "You shift through the wet ground, but do not commit to a direction.";
        UUID destination = jdbc.query("SELECT next.id FROM world_chunk current JOIN world_chunk next ON next.world_id=current.world_id AND next.grid_x=current.grid_x+? AND next.grid_y=current.grid_y+? WHERE current.id=?", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, direction.dx, direction.dy, chronicle.location());
        if (destination == null) return "The ground gives way toward the edge of what you can cross. You turn back before leaving the land behind.";
        java.sql.Timestamp occurredTs = java.sql.Timestamp.from(occurredAt);
        jdbc.update("UPDATE world_object SET current_location_id=?, updated_at=? WHERE id=?", destination, occurredTs, chronicle.id());
        jdbc.update("UPDATE chronicle SET current_zone=NULL WHERE id=?", chronicle.id()); // left the settlement's zones behind
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'MOVED',jsonb_build_object('fromLocationId',?::text,'toLocationId',?::text,'direction',?))", chronicle.id(), occurredTs, chronicle.location().toString(), destination.toString(), direction.name());
        jdbc.update("INSERT INTO chronicle_event (chronicle_id,occurred_at,event_type,payload) VALUES (?,?,'CHRONICLE_MOVED',jsonb_build_object('fromLocationId',?::text,'toLocationId',?::text,'direction',?))", chronicle.id(), occurredTs, chronicle.location().toString(), destination.toString(), direction.name());
        recordVisit(chronicle.id(), destination, occurredAt);
        return "You travel " + direction.description + ", the ground shifting under you as you go.";
    }
    /** Register the chronicle's presence in a chunk — the raw material of route memory and the decay clock on named places. */
    private void recordVisit(UUID chronicle, UUID chunk, Instant at) {
        java.sql.Timestamp ts = java.sql.Timestamp.from(at);
        jdbc.update("INSERT INTO chronicle_chunk_visit (chronicle_id,chunk_id,visit_count,last_visited_at) VALUES (?,?,1,?) ON CONFLICT (chronicle_id,chunk_id) DO UPDATE SET visit_count=chronicle_chunk_visit.visit_count+1, last_visited_at=EXCLUDED.last_visited_at", chronicle, chunk, ts);
        jdbc.update("UPDATE chronicle_named_location SET last_visited_at=? WHERE chronicle_id=? AND chunk_id=?", ts, chronicle, chunk);
    }
    /**
     * Decide whether the chronicle can find its way to a place named in the action,
     * and how far it is. A place is locatable only if it is on a carried map, marked
     * and memorized, walked often enough to be routine, or memorized and visited
     * recently. A name without any of these has faded, and returns null.
     */
    private TravelPlan planTravel(ActiveChronicle chronicle, String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        java.util.List<java.util.Map<String,Object>> named = jdbc.queryForList("SELECT nl.chunk_id, nl.name, nl.memorized, nl.last_visited_at, c.grid_x, c.grid_y FROM chronicle_named_location nl JOIN world_chunk c ON c.id=nl.chunk_id WHERE nl.chronicle_id=? ORDER BY length(nl.name) DESC", chronicle.id());
        java.util.Map<String,Object> cur = jdbc.queryForMap("SELECT grid_x, grid_y FROM world_chunk WHERE id=?", chronicle.location());
        int cx=(int)cur.get("grid_x"), cy=(int)cur.get("grid_y");
        for (java.util.Map<String,Object> n : named) {
            String name = (String) n.get("name");
            if (!lower.contains(name.toLowerCase(Locale.ROOT))) continue;
            UUID chunk = (UUID) n.get("chunk_id");
            boolean memorized = Boolean.TRUE.equals(n.get("memorized"));
            java.sql.Timestamp last = (java.sql.Timestamp) n.get("last_visited_at");
            boolean recent = last != null && last.toInstant().isAfter(java.time.Instant.now().minus(java.time.Duration.ofDays(4)));
            boolean markerHere = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM location_marker WHERE chunk_id=?)", Boolean.class, chunk));
            boolean onMap = Boolean.TRUE.equals(jdbc.queryForObject("WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') SELECT EXISTS(SELECT 1 FROM reachable x JOIN literature_document d ON d.object_id=x.id JOIN literature_revision rv ON rv.id=d.current_revision_id WHERE d.document_kind='MAP' AND rv.content ILIKE ?)", Boolean.class, chronicle.id(), "%"+name+"%"));
            Integer visits = jdbc.queryForObject("SELECT COALESCE((SELECT visit_count FROM chronicle_chunk_visit WHERE chronicle_id=? AND chunk_id=?),0)", Integer.class, chronicle.id(), chunk);
            boolean routine = visits != null && visits >= 5;
            boolean locatable = onMap || (markerHere && memorized) || routine || (memorized && recent);
            if (!locatable) continue;
            int gx=(int)n.get("grid_x"), gy=(int)n.get("grid_y");
            int distance = Math.max(Math.abs(gx-cx), Math.abs(gy-cy));
            String reason = onMap ? "map" : routine ? "routine" : markerHere ? "marker" : "memory";
            return new TravelPlan(chunk, distance, reason);
        }
        return null;
    }
    private String[] travelTo(ActiveChronicle chronicle, TravelPlan plan, Instant at) {
        if (plan == null) return new String[]{"FAILED", "You try to fix the place in your mind and make for it, but you cannot call the way to mind clearly enough to set out. Some places, once, are not places you can find again."};
        if (plan.destination().equals(chronicle.location())) return new String[]{"SUCCEEDED", "You are already at the place you meant to reach."};
        java.sql.Timestamp ts = java.sql.Timestamp.from(at);
        jdbc.update("UPDATE world_object SET current_location_id=?, updated_at=? WHERE id=?", plan.destination(), ts, chronicle.id());
        jdbc.update("UPDATE chronicle SET current_zone=NULL WHERE id=?", chronicle.id()); // arrived at a new place; old zones are behind
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'MOVED',jsonb_build_object('fromLocationId',?::text,'toLocationId',?::text,'wayfinding',?))", chronicle.id(), ts, chronicle.location().toString(), plan.destination().toString(), plan.reason());
        jdbc.update("INSERT INTO chronicle_event (chronicle_id,occurred_at,event_type,payload) VALUES (?,?,'CHRONICLE_MOVED',jsonb_build_object('fromLocationId',?::text,'toLocationId',?::text,'wayfinding',?))", chronicle.id(), ts, chronicle.location().toString(), plan.destination().toString(), plan.reason());
        recordVisit(chronicle.id(), plan.destination(), at);
        String how = switch (plan.reason()) { case "map" -> "following the trails you sketched on your map"; case "routine" -> "along a way your feet have walked many times"; case "marker" -> "guided by the marker you once left"; default -> "holding the place firmly in memory"; };
        return new String[]{"SUCCEEDED", "You set out, " + how + ", and come at last to the place you meant to reach."};
    }
    /**
     * Leave a physical marker at the current place — a blaze carved on a tree, a
     * cairn of stones, a driven stake. A marker makes a spot recognizable and can
     * later anchor a name; on its own it carries no name.
     */
    private String[] markLandmark(ActiveChronicle chronicle, String text, UUID actionId, Instant at) {
        String v = text.toLowerCase(Locale.ROOT);
        String kind; String need;
        if (v.contains("cairn") || v.contains("pile") || v.contains("stack") || (v.contains("stone") && !v.contains("carve"))) { kind = "CAIRN"; need = "field_stone"; }
        else if (v.contains("stake") || v.contains("post") || v.contains("stick") || v.contains("stave")) { kind = "STAKE"; need = "dry_branch"; }
        else { kind = "BLAZE"; need = "tool"; }
        if (kind.equals("CAIRN")) { if (!items.hasAtLeast(chronicle.id(),"field_stone",3)) return new String[]{"FAILED","You cast about for stones to pile, but you do not have enough to raise anything that would stand and be seen."}; for (int i=0;i<3;i++) items.consumeOne(chronicle.id(),"field_stone",at); }
        else if (kind.equals("STAKE")) { if (!items.hasAtLeast(chronicle.id(),"dry_branch",1)) return new String[]{"FAILED","You have nothing to drive into the ground as a marker."}; items.consumeOne(chronicle.id(),"dry_branch",at); }
        else { if (!items.hasCuttingTool(chronicle.id())) return new String[]{"FAILED","You set a hand to the bark, but with no blade you can cut no lasting mark."}; }
        String shape = extractShape(v);
        UUID id = UUID.randomUUID();
        String label = kind.equals("CAIRN") ? "Stone cairn" : kind.equals("STAKE") ? "Driven stake" : "Carved blaze";
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'MARKER',?,?)", id, label, chronicle.location());
        jdbc.update("INSERT INTO location_marker (object_id,chunk_id,marker_kind,description,created_by_chronicle_id,created_at) VALUES (?,?,?,?,?,?)", id, chronicle.location(), kind, shape, chronicle.id(), java.sql.Timestamp.from(at));
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'MARKED',jsonb_build_object('kind',?))", id, java.sql.Timestamp.from(at), kind);
        String made = kind.equals("CAIRN") ? "You stack stones into a cairn that will stand and be seen" : kind.equals("STAKE") ? "You drive a stake firmly into the ground" : "You cut a clear blaze into the bark of a tree";
        // If the same act also names the place, register it — a marked, named, and
        // (if asked) memorized spot is the surest kind to find one's way back to.
        String name = namesInMarking(v) ? extractDesignatedName(text) : null;
        if (name != null && !name.isBlank()) {
            boolean memorize = v.contains("memoriz")||v.contains("memoris")||v.contains("remember")||v.contains("commit to memory")||v.contains("by heart");
            jdbc.update("INSERT INTO chronicle_named_location (chronicle_id,chunk_id,name,purpose_tag,designated_at,source_action_id,memorized,last_visited_at) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT (chronicle_id,chunk_id,name) DO UPDATE SET designated_at=EXCLUDED.designated_at, source_action_id=EXCLUDED.source_action_id, memorized=chronicle_named_location.memorized OR EXCLUDED.memorized, last_visited_at=EXCLUDED.last_visited_at", chronicle.id(), chronicle.location(), name, null, java.sql.Timestamp.from(at), actionId, memorize, java.sql.Timestamp.from(at));
            return new String[]{"SUCCEEDED", made + (shape==null?"":", in the shape of a "+shape) + ", and name this place " + name + ". Marked and named" + (memorize?" and fixed in memory":"") + ", it is a place you will find again."};
        }
        return new String[]{"SUCCEEDED", made + (shape==null?"":", in the shape of a "+shape) + " — a mark on this place that will outlast your passing. It bears no name until you give it one."};
    }
    private boolean namesInMarking(String v) { return v.contains(" as ")||v.contains("name it")||v.contains("name this")||v.contains("call it")||v.contains("call this")||v.contains("named"); }
    private String extractShape(String v) { for (String s : new String[]{"triangle","circle","cross","square","arrow","spiral","line","star","diamond","chevron"}) if (v.contains(s)) return s; return null; }
    private int durationFor(String action, Intent intent) {
        Matcher match = DURATION.matcher(action);
        if (match.find()) { int amount = Integer.parseInt(match.group(1)); int minutes = match.group(2).toLowerCase(Locale.ROOT).startsWith("h") ? amount * 60 : amount; return Math.max(1, Math.min(minutes, 24 * 60)); }
        return switch (intent) { case OBSERVE -> 10; case REST -> 60; case SLEEP -> 480; case GATHER_FIBER, GATHER_STONE, GATHER_BERRIES, GATHER_BRANCHES -> 25; case GATHER_CLAY -> 20; case GATHER_STONE_SLAB -> 30; case GATHER_PLANT, FORAGE_GROUND -> 20; case FELL_TREE -> 60; case RAID_HIVE -> 15; case COLLECT_INSECTS -> 20; case FISH -> 45; case SNARE -> 25; case TRACK -> 25; case SCOUT -> 20; case TAME -> 30; case LURE -> 10; case SET_TRAP -> 35; case CHECK_TRAP -> 10; case CRAFT_GARMENT -> 90; case GATHER_MINERAL -> 40; case CRAFT_FIRE_TOOL -> 30; case PROCESS_MATERIAL -> 45; case EAT, DRINK, FEED_FIRE, EXTINGUISH_FIRE -> 5; case WARM_BODY, DRY_BODY, COOL_BODY, SHELTER_BODY, BANK_FIRE, COLLECT_WATER -> 10; case FILTER_WATER -> 15; case BOIL_WATER -> 20; case STRETCH -> 5; case MAKE_BED -> 20; case MAINTAIN_CAMP -> 25; case PLACE_WINDBREAK, PLACE_COVER -> 20; case LIGHT_FIRE -> 20; case COOK_MEAT, TREAT_WOUND, CONFRONT_WILDLIFE, HARVEST_CARCASS -> 10; case EDIT_DOCUMENT, WRITE -> 15; case SKETCH_MAP -> 30; case STRIP_BARK -> 15; case MAKE_CHARCOAL -> 10; case CRAFT_BASKET -> 45; case CRAFT_NET -> 90; case CRAFT_BELT -> 40; case CRAFT_SPEAR, CRAFT_HATCHET -> 35; case CRAFT_FIRE_KIT -> 25; case CRAFT_TINDER -> 10; case CRAFT_DESK, CRAFT_WORKSTATION -> 60; case CRAFT_CHAIR -> 40; case CRAFT_SHELF -> 50; case BUILD_FIRE_PIT, START_LEAN_TO -> 30; case BUILD_ALARM -> 25; case BUILD_FENCE -> 40; case BUILD_LOOKOUT -> 45; case BUILD_FUEL_RACK -> 35; case WORK_LEAN_TO -> 45; case DISMANTLE -> 45; case ABANDON_LEAN_TO, RESUME_LEAN_TO -> 5; case MOVE -> 30; case DISENGAGE -> 10; case MARK -> 15; case EQUIP, UNEQUIP, DROP -> 5; case DESIGNATE -> 10; case REFINE -> 30; case REPAIR_ITEM -> 20; case REPAIR_STRUCTURE -> 35; case ADVANCE_ASSEMBLY -> 45; case INSPECT -> 5; case EXAMINE -> 5; case ANALYZE -> 10; case INVESTIGATE -> 15; case SEARCH -> 10; case LISTEN, SMELL, FEEL -> 5; case READ -> 15; case MEASURE -> 10; case REWORK -> 30; case PERSONAL_ACT -> 20; case AGGRESSION_WILDLIFE -> 5; case AGGRESSION_INANIMATE -> 1; default -> 5; };
    }
    private Intent classify(String action) {
        String value=action.toLowerCase(Locale.ROOT);
        if(DOCUMENT_EDIT.matcher(action).matches()) return Intent.EDIT_DOCUMENT;
        if((value.contains("craft")||value.contains("make"))&&value.contains("spear")) return Intent.CRAFT_SPEAR;
        if((value.contains("craft")||value.contains("make"))&&value.contains("knife")) return Intent.CRAFT_KNIFE;
        if((value.contains("craft")||value.contains("make"))&&value.contains("hammer")) return Intent.CRAFT_HAMMER;
        if((value.contains("craft")||value.contains("make"))&&(value.contains("pickaxe")||value.contains("pick axe"))) return Intent.CRAFT_PICKAXE;
        // A hatchet is the accessible felling tool: a single hafted stone edge from raw
        // materials, unlike the two-handed stone axe which needs prepared components.
        if((value.contains("craft")||value.contains("make")||value.contains("knap"))&&value.contains("hatchet")) return Intent.CRAFT_HATCHET;
        if((value.contains("craft")||value.contains("make")||value.contains("carve")||value.contains("prepare"))&&((value.contains("fire")&&(value.contains("kit")||value.contains("drill")||value.contains("board")))||value.contains("hearth")||value.contains("spindle"))) return Intent.CRAFT_FIRE_KIT;
        // Charred tinder is its own thing (V49) — made by smothering fiber rather than
        // teasing it loose — so it goes to the ignition-kit path, not the tinder nest.
        if((value.contains("make")||value.contains("prepare")||value.contains("gather")||value.contains("form")||value.contains("bundle"))&&value.contains("tinder")&&!value.contains("char")) return Intent.CRAFT_TINDER;
        // A "drying rack" is a staged structure (V58), not a shelf — let it fall through
        // to the assembly engine rather than being caught by "rack" here.
        // Workstations (V69) before the generic desk/table rule, which also matches "bench"/"table": a
        // woodworking/stoneworking bench, a workbench, or a loom is a workstation (it eases the crafts it
        // serves); a plain "bench" or "table" is still a desk.
        if((value.contains("craft")||value.contains("make")||value.contains("build")||value.contains("construct")||value.contains("assemble")||value.contains("set up"))&&(value.contains("loom")||value.contains("workbench")||value.contains("work bench")||((value.contains("woodworking")||value.contains("stoneworking")||value.contains("weaving"))&&(value.contains("bench")||value.contains("table")||value.contains("station"))))) return Intent.CRAFT_WORKSTATION;
        if((value.contains("craft")||value.contains("make")||value.contains("build")||value.contains("construct")||value.contains("assemble"))&&(value.contains("shelf")||value.contains("shelves")||value.contains("rack")||value.contains("archive"))&&!value.contains("drying")&&!value.contains("fuel rack")&&!value.contains("wood rack")&&!value.contains("firewood rack")&&!value.contains("log rack")&&!value.contains("kindling rack")) return Intent.CRAFT_SHELF;
        if((value.contains("craft")||value.contains("make")||value.contains("build")||value.contains("construct")||value.contains("assemble"))&&(value.contains("desk")||value.contains("table")||value.contains("workbench")||value.contains("bench"))) return Intent.CRAFT_DESK;
        if((value.contains("craft")||value.contains("make")||value.contains("build")||value.contains("construct")||value.contains("assemble"))&&(value.contains("chair")||value.contains("stool")||value.contains("seat"))) return Intent.CRAFT_CHAIR;
        // A primitive utility belt (#35): a fibre strap with tool loops. A making verb + "belt" — never the
        // wearing of one (that carries no craft verb and falls to EQUIP below).
        if((value.contains("make")||value.contains("craft")||value.contains("assemble")||value.contains("fashion")||value.contains("weave")||value.contains("build"))&&value.contains("belt")&&!value.contains("belt out")) return Intent.CRAFT_BELT;
        // Physical logistics (#29/#40/#41). Storing something IN a container is checked before DROP and before
        // the gather verbs, so "put the stones in the basket" is containment, not dropping or gathering. It
        // needs a store verb, an in/into/inside, and a container noun together.
        boolean containerNoun = value.contains("basket")||value.contains("container")||value.contains("pouch")||value.contains("bag")||value.contains("sack")||value.contains("pot")||value.contains("crate")||value.contains("chest")||value.contains("quiver")||value.contains("box")||value.contains("pannier")||value.contains("backpack")||value.contains("storage");
        // STORE (#67 store/pack): put/stow/cache/stockpile something into a container, or the container-less
        // storage verbs that imply the settlement's store ("put it away", "cache the meat", "stockpile the wood").
        if((value.contains(" in ")||value.contains(" into ")||value.contains(" inside "))&&(value.contains("put")||value.contains("place")||value.contains("store")||value.contains("stow")||value.contains("stash")||value.contains("load")||value.contains("pack")||value.contains("drop"))&&containerNoun) return Intent.STORE;
        if((value.contains("put")&&value.contains("away"))||value.contains("cache")||value.contains("stockpile")||value.contains("put in storage")||value.contains("stow away")||value.contains("stash away")) return Intent.STORE;
        // PICK_UP (#67 take/retrieve/unpack): explicit retrieval verbs, or "take/get/remove/unpack X out of/from
        // the <container/storage/ground>" — distinct from gathering raw growth from the world.
        if(value.contains("pick up")||value.contains("pick it up")||value.contains("pick them up")||value.contains("pick it back")||value.contains("picked up")||value.contains("grab")||value.contains("retrieve")||value.contains("recover")||value.contains("take back")||value.contains("take it back")||(value.contains("fetch")&&!value.contains("water"))||value.contains("lift the")||value.contains("lift it")||value.contains("lift up")
           ||((value.contains("take")||value.contains("get")||value.contains("remove")||value.contains("pull")||value.contains("unpack")||value.contains("empty"))&&(value.contains(" out of ")||value.contains(" from the ")||value.contains(" from storage")||value.contains(" off the ground")||value.contains(" from where"))&&(containerNoun||value.contains("ground")||value.contains("storage")||value.contains("where i")||value.contains("where it")))) return Intent.PICK_UP;
        // Container access (#67): open/close/seal a reachable container. Scoped to a container noun so the common
        // verbs open/close/cover/seal do not collide with reading, sheltering, or other uses.
        if((value.contains("open")||value.contains("unfasten")||value.contains("uncover")||value.contains("unlatch")||value.contains("unseal")||value.contains("unstopper")||value.contains("take the lid off")||value.contains("remove the lid"))&&containerNoun) return Intent.OPEN_CONTAINER;
        if((value.contains("close")||value.contains("shut")||value.contains("cover")||value.contains("fasten")||value.contains("seal")||value.contains("stopper")||value.contains("put the lid on")||value.contains("put a lid on")||value.contains("lid"))&&containerNoun) return Intent.CLOSE_CONTAINER;
        // Taking a construction apart (#70 dismantle/salvage) — before the lean-to check, so "dismantle the
        // lean-to" is a dismantle rather than a build stage. Recovers only a fraction of the materials.
        if(value.contains("dismantle")||value.contains("take apart")||value.contains("pull down")||value.contains("tear down")||value.contains("salvage")||value.contains("reclaim")||(value.contains("recover")&&value.contains("material"))) return Intent.DISMANTLE;
        if(value.contains("lean-to") || value.contains("lean to")) return classifyLeanTo(value);
        // Garment work before the generic craft rules, so "sew a hide coat" is not
        // swallowed by the furniture or tool branches.
        if((value.contains("sew")||value.contains("stitch")||value.contains("craft")||value.contains("make")||value.contains("weave"))&&(value.contains("coat")||value.contains("cloak")||value.contains("legging")||value.contains("tunic")||value.contains("boots")||value.contains("garment")||value.contains("clothing")||(value.contains("hide")&&value.contains("wear")))) return Intent.CRAFT_GARMENT;
        // Making a piece of ignition kit must be heard before the rule that treats
        // naming a technique as asking for fire — "carve a fire bow" is preparation,
        // not an attempt to light something.
        if((value.contains("craft")||value.contains("make")||value.contains("carve")||value.contains("cut")||value.contains("build")||value.contains("prepare")||value.contains("shape")||value.contains("pack"))
           &&(value.contains("fire bow")||value.contains("bearing block")||value.contains("socket")||value.contains("handhold")||value.contains("plough board")||value.contains("plow board")||value.contains("fire saw")||value.contains("char tinder")||value.contains("charred")||value.contains("ember bundle")
              ||((value.contains("bow")||value.contains("plough")||value.contains("plow"))&&value.contains("fire")))) return Intent.CRAFT_FIRE_TOOL;
        // Prospecting before the ignition rules, so "search the rocks for pyrite" is
        // heard as looking for the mineral rather than as trying to strike a light
        // with one the chronicle does not yet have.
        if((value.contains("search")||value.contains("look for")||value.contains("prospect")||value.contains("dig for")||value.contains("find")||value.contains("gather")||value.contains("collect")||value.contains("split")||value.contains("break"))
           // "ore" needs a word boundary — without it "forest" matches, and gathering
           // mushrooms in a forest was being heard as prospecting for ore.
           // "salt" makes rock/sea salt reachable by phrasing (gather sea salt, dig for rock salt);
           // it can't steal "salt the fish/meat" — that carries no gathering verb and falls through
           // to the preservation process.
           &&(value.contains("flint")||value.contains("chert")||value.contains("obsidian")||value.contains("pyrite")||value.contains("quartz")||value.contains("crystal")||value.contains("mineral")||value.contains("salt")||value.contains("ochre")||value.contains("soapstone")||value.contains("sandstone")||value.contains("pumice")||value.contains("granite")||value.contains("basalt")||value.contains("cobble")||value.matches("(?s).*\\bore\\b.*")||value.contains("tool stone"))) return Intent.GATHER_MINERAL;
        // Fire management (#71): put out, bank, or tend a fire — checked before the ignition rules so "put out
        // the fire" is not read as making one.
        if(value.contains("extinguish")||value.contains("put out the fire")||value.contains("put the fire out")||value.contains("douse")||value.contains("smother the fire")||value.contains("stamp out the fire")||value.contains("quench the fire")||((value.contains("put out")||value.contains("kill"))&&value.contains("fire"))) return Intent.EXTINGUISH_FIRE;
        if((value.contains("bank")||value.contains("cover the coals")||value.contains("cover the embers")||value.contains("preserve the ember")||value.contains("rake the coals"))&&(value.contains("fire")||value.contains("coal")||value.contains("ember"))) return Intent.BANK_FIRE;
        if((value.contains("tend")||value.contains("keep the fire")||value.contains("keep it going")||value.contains("maintain"))&&word(value,"fire")) return Intent.FEED_FIRE;
        // Naming a real ignition technique IS asking for fire (V49). A player who
        // writes "strike flint against pyrite" or "spin the bow drill" should not
        // have to also say the word "fire" to be understood.
        if(value.contains("bow drill")||value.contains("hand drill")||value.contains("fire plough")||value.contains("fire plow")||value.contains("fire saw")||value.contains("fire piston")||value.contains("pyrite")||value.contains("tinder nest")
           ||((value.contains("strike")||value.contains("spark")||value.contains("scrape"))&&(value.contains("flint")||value.contains("steel")||value.contains("stone")))
           ||((value.contains("focus")||value.contains("lens")||value.contains("magnify"))&&(value.contains("sun")||value.contains("tinder")))
           ||(value.contains("ember")&&(value.contains("carry")||value.contains("transfer")||value.contains("bring")||value.contains("nurse")))) return Intent.LIGHT_FIRE;
        if((value.contains("check")||value.contains("inspect")||value.contains("look at")||value.contains("empty")||value.contains("collect from")||value.contains("return to"))&&(value.contains("trap")||value.contains("snare")||value.contains("deadfall"))) return Intent.CHECK_TRAP;
        // Placing a trap and working a snare by hand both mention "snare", so the
        // deciding signal is whether the chronicle is LEAVING something behind. A
        // setting/building verb means a persistent placed trap (V46); a bare
        // "snare a rabbit" is the immediate hand-worked attempt (V42).
        if((value.contains("build")||value.contains("set")||value.contains("make")||value.contains("place")||value.contains("construct")||value.contains("lay"))&&(value.contains("deadfall")||value.contains("pit trap")||value.contains("fish trap")||value.contains("cage trap")||value.contains("box trap")||value.contains("snare")||(value.contains("trap")&&!value.contains("check")))) return Intent.SET_TRAP;
        if(value.contains("lure")||value.contains("bait the")||((value.contains("leave")||value.contains("put")||value.contains("place")||value.contains("set"))&&(value.contains("bait")||value.contains("draw them")||value.contains("draw it")))) return Intent.LURE;
        if(value.contains("tame")||value.contains("befriend")||value.contains("domesticate")||value.contains("gain its trust")||value.contains("earn its trust")||((value.contains("approach")||value.contains("offer")||value.contains("feed")||value.contains("hold out"))&&(value.contains("calm")||value.contains("slow")||value.contains("gentl")||value.contains("quiet")||value.contains("trust")||value.contains("goat")||value.contains("rabbit")||value.contains("fowl")||value.contains("turtle")||value.contains("hedgehog")||value.contains("pigeon")||value.contains("deer")||value.contains("reindeer")||value.contains("duck")))) return Intent.TAME;
        if(value.contains("track")||value.contains("follow the trail")||value.contains("read the ground")||value.contains("look for sign")||value.contains("look for tracks")||((value.contains("print")||value.contains("spoor")||value.contains("scat")||value.contains("droppings")||value.contains("trail"))&&(value.contains("find")||value.contains("read")||value.contains("follow")||value.contains("search")||value.contains("look")))) return Intent.TRACK;
        // Scan the boundary of the ground for a way clear of danger before moving into it (#128/#123: grounded
        // evidence before forced contact). Reads a predator ONE tile out by directional sense — scent on the
        // wind, boundary trees scored, prey gone quiet — never a map. Distinct from OBSERVE (reads THIS ground)
        // and TRACK (sign on THIS ground); owns the boundary / escape-route / scouting phrasing.
        if(value.contains("scout")||value.contains("observe the boundary")||value.contains("read the boundary")||value.contains("check the boundary")||value.contains("survey the boundary")||value.contains("escape route")||value.contains("survey the escape")||value.contains("safe route")||value.contains("safe way")||value.contains("way out")||value.contains("which way is safe")||value.contains("scan the ridge")||value.contains("scan the treeline")||value.contains("scan the tree line")||value.contains("scan the horizon")||((value.contains("scan")||value.contains("check the way")||value.contains("look"))&&(value.contains("for danger")||value.contains("for a way out")))) return Intent.SCOUT;
        // "salt the fish", "gut the fish", "weave a fish trap" all contain "fish" but
        // are processing, not angling. Defer to the material-process matcher, which
        // agrees on category, keyword and subject before it claims anything; only text
        // that resolves to no process is heard as an attempt to catch one.
        // Mending a worn/broken item (#69 repair/fix/mend/reinforce/sharpen) — checked before the net/fish block
        // so "repair my fishing net" is a repair, not angling. Distinct from REPAIR_LEAN_TO (a shelter, routed by
        // the lean-to check above) and from REFINE (improving an already-sound thing).
        // Repairing / maintaining a standing structure here (#70) — before item repair, and scoped to structure
        // nouns, so "mend the fence" or "weatherproof the hut" works a construction, not a carried tool.
        if((value.contains("repair")||value.contains("mend")||value.contains("patch")||value.contains("shore up")||value.contains("reinforce")||value.contains("weatherproof")||value.contains("maintain")||(word(value,"fix")&&!value.contains("memory")&&!value.contains("mind")))
           &&(value.contains("hut")||value.contains("wall")||value.contains("roof")||value.contains("thatch")||value.contains("fence")||value.contains("gate")||value.contains("hearth")||value.contains("bridge")||value.contains("platform")||value.contains("screen")||value.contains("catchment")||value.contains("wood store")||value.contains("landing")||value.contains("structure")||value.contains("daub"))) return Intent.REPAIR_STRUCTURE;
        if((value.contains("repair")||value.contains("mend")||value.contains("patch")||value.contains("reinforce")||value.contains("sharpen")||value.contains("darn")||(word(value,"fix")&&!value.contains("memory")&&!value.contains("mind")&&!value.contains("place")))&&!value.contains("shelter")&&!value.contains("frame")&&!value.contains("lean")) return Intent.REPAIR_ITEM;
        // "fishing net" / "weave a fish net" contain "fish" but are CRAFTING a net, not angling (#36/#43/#44).
        // Making a net is an explicit craft (CRAFT_NET) — a mesh knotted from cordage — distinct from USING a
        // net to fish, which stays FISH. The using verbs (cast/throw/haul/set/with the net) keep it angling.
        boolean usingNet = value.contains("net")&&(value.contains("cast")||value.contains("throw")||value.contains("haul")||value.contains("set the net")||value.contains("use the net")||value.contains("with the net")||value.contains("with a net"));
        boolean craftingNet = value.contains("net")&&!usingNet&&(value.contains("weave")||value.contains("craft")||value.contains("make")||value.contains("knot")||value.contains("braid")||value.contains("tie")||value.contains("assemble")||value.contains("mesh"));
        if(craftingNet) return Intent.CRAFT_NET;
        if((usingNet||(value.contains("fish")&&!value.contains("landing")&&!value.contains("jetty"))||value.contains("angle")||((value.contains("catch")||value.contains("spear"))&&(value.contains("trout")||value.contains("perch")||value.contains("pike")||value.contains("carp")||value.contains("eel")||value.contains("catfish")||value.contains("crayfish"))))&&!items.actionMatchesProcess(action)) return Intent.FISH;
        if(value.contains("snare")||value.contains("set a trap")||value.contains("set trap")||((value.contains("trap")||value.contains("noose"))&&(value.contains("rabbit")||value.contains("hare")||value.contains("bird")||value.contains("fowl")||value.contains("small")||value.contains("run")))) return Intent.SNARE;
        if((value.contains("raid")||value.contains("harvest")||value.contains("smoke")||value.contains("rob")||value.contains("take")||value.contains("collect")||value.contains("gather"))&&(value.contains("hive")||value.contains("nest")||value.contains("honey")||value.contains("beeswax")||value.contains("bees")||value.contains("hornet"))) return Intent.RAID_HIVE;
        if((value.contains("collect")||value.contains("gather")||value.contains("catch")||value.contains("dig")||value.contains("pick")||value.contains("forage")||value.contains("harvest"))&&(value.contains("insect")||value.contains("silk")||value.contains("cocoon")||value.contains("silkworm")||word(value,"ant")||word(value,"ants")||value.contains("chitin")||value.contains("grasshopper")||value.contains("cricket")||value.contains("earthworm")||word(value,"worm")||value.contains("spider")||word(value,"grub")||value.contains("larva"))) return Intent.COLLECT_INSECTS;
        // Felling needs a felling verb — bare "log" is not one. "split the oak log into
        // planks" is log *processing*, and the two-axis matcher claims it (split_planks);
        // only text that resolves to no process is heard as an attempt to fell (#17).
        if((value.contains("fell")||value.contains("cut down")||value.contains("chop down")||value.contains("drop the tree"))&&(value.contains("tree")||value.contains("oak")||value.contains("birch")||value.contains("pine")||value.contains("ash")||value.contains("willow")||value.contains("maple")||value.contains("hazel")||value.contains("spruce")||value.contains("juniper"))&&!items.actionMatchesProcess(action)) return Intent.FELL_TREE;
        if((value.contains("gather")||value.contains("pick")||value.contains("collect")||value.contains("harvest")||value.contains("forage"))&&(value.contains("mushroom")||value.contains("fungi")||value.contains("herb")||value.contains("plant")||value.contains("berries")||value.contains("flower")||value.contains("leaf")||value.contains("root")||value.contains("nettle")||value.contains("yarrow")||value.contains("comfrey")||value.contains("mint")||value.contains("dandelion")||value.contains("garlic")||value.contains("burdock")||value.contains("watercress")||value.contains("cattail")||value.contains("reed")||value.contains("bulrush")||value.contains("chanterelle")||value.contains("porcini")||value.contains("oyster")||value.contains("polypore")||value.contains("lion")||value.contains("hazel rod")||value.contains("hazel")&&value.contains("rod")||value.contains("willow")&&value.contains("branch")||value.contains("pine resin")||value.contains("maple sap")||value.contains("rose hip")||value.contains("elderberry")||value.contains("hawthorn")||value.contains("juniper berry")||value.contains("vine")||value.contains("sapling")||value.contains("straw")||value.contains("young tree")||value.contains("meadow grass")||value.contains("milkweed")||value.contains("flax")||value.contains("hemp")||value.contains("acorn")||value.contains("hazelnut")||value.contains("walnut")||value.contains("chestnut")||value.contains("pine nut")||value.contains("wild onion")||value.contains("wild grain")||value.contains("grain head")||value.contains("rhizome")||value.contains("chamomile")||value.contains("pine needle")||value.contains("wild rice")||value.contains("morel")||value.contains("crab apple")||value.contains("sloe")||value.contains("bilberry")||value.contains("bramble")||value.contains("fatwood")||value.contains("big leaf")||value.contains("broad leaf")||value.contains("dry grass")||value.contains("flexible root")||value.contains("bast"))&&!value.contains("fiber")) return Intent.GATHER_PLANT;
        if(value.contains("clay")&&(value.contains("gather")||value.contains("dig")||value.contains("collect")||value.contains("find")||value.contains("get")||value.contains("scoop")||value.contains("pull"))) return Intent.GATHER_CLAY;
        if(value.contains("slab")&&(value.contains("gather")||value.contains("split")||value.contains("pry")||value.contains("cut")||value.contains("make")||value.contains("get")||value.contains("collect")||value.contains("quarry")||value.contains("shape")||value.contains("break"))) return Intent.GATHER_STONE_SLAB;
        // Everyday hand-gathered stock (#68 gather aliases): the specific gathers used to accept only gather/
        // collect; forage/harvest/take-all/gather-up now reach them too. A named material takes precedence over
        // a generic "gather".
        boolean gatherVerb = value.contains("gather")||value.contains("collect")||value.contains("forage")||value.contains("harvest")||value.contains("take all")||value.contains("gather up");
        if(gatherVerb&&(value.contains("fiber")||value.contains("fibre"))) return Intent.GATHER_FIBER;
        if(gatherVerb&&(value.contains("branch")||value.contains("stick")||value.contains("deadwood")||value.contains("firewood")||value.contains("kindling"))) return Intent.GATHER_BRANCHES;
        if(gatherVerb&&(value.contains("berry")||value.contains("berries"))) return Intent.GATHER_BERRIES;
        if(gatherVerb&&(value.contains("stone")||value.contains("rock")||value.contains("pebble"))&&!value.contains("slab")&&!items.actionMatchesProcess(action)) return Intent.GATHER_STONE;
        // WASH is BODY washing (#32) — scoped to the self so "wash the sediment / rinse the fleece / pan the
        // gravel" (#68 material prep) falls through to the process catalogue instead of being heard as bathing.
        if(value.contains("bathe")||value.contains("take a bath")||value.contains("have a bath")||value.contains("clean myself")||value.contains("wash myself")||value.contains("wash up")||value.contains("wash my ")||value.contains("wash off")||((value.contains("wash")||value.contains("rinse"))&&(value.contains("hands")||value.contains("face")||value.contains("body")||value.contains("skin")||value.contains("in the stream")||value.contains("in the river")||value.contains("in the water")))) return Intent.WASH;
        // Body-care against the environment (#66): warm/dry/cool/shelter/stretch. Scoped so they read as body
        // acts, not material processing ("dry the herbs" is a PROCESS; "dry off" is warming the body).
        if(value.contains("stretch")||value.contains("loosen my")||value.contains("loosen up")||value.contains("work the stiffness")||value.contains("limber up")) return Intent.STRETCH;
        // Camp upkeep (#71): laying a bed off the ground, and tending the whole site. make_bed is gated on
        // bedding nouns (not "bed down", which is sleeping) and excludes the raised-platform assembly phrase.
        if((value.contains("make")||value.contains("prepare")||value.contains("lay")||value.contains("build")||value.contains("gather")||value.contains("arrange"))&&(value.contains("bedding")||value.contains("a bed")||value.contains("the bed")||value.contains("sleeping mat")||value.contains("bed of")||value.contains("pallet"))&&!value.contains("platform")) return Intent.MAKE_BED;
        // A perimeter trip-line alarm (#126/#127): a line strung low with anything that clatters, so nothing
        // crosses into the camp unheard. Distinctive nouns ('alarm', 'trip-line', a 'warning'/'noise' line) own
        // the intent; placed before MAINTAIN_CAMP so "protect the camp with a trip-line alarm" rigs one.
        if(value.contains("alarm")||value.contains("trip-line")||value.contains("trip line")||value.contains("tripline")||((value.contains("warning")||value.contains("noise"))&&value.contains("line"))) return Intent.BUILD_ALARM;
        // A perimeter fence (#127) — a barrier a predator must breach, not merely a warning. word("fence") avoids
        // the "defence"/"offence" substring trap; a build verb or a perimeter phrase confirms the intent. Placed
        // before MAINTAIN_CAMP so "fence the camp" rigs a barrier rather than tidying the site.
        if((word(value,"fence")||word(value,"fences")||value.contains("palisade")||value.contains("stockade")||value.contains("wattle wall"))&&(value.contains("build")||value.contains("raise")||value.contains("set up")||value.contains("put up")||value.contains("weave")||value.contains("erect")||value.contains("make")||value.contains("construct")||value.contains("throw up")||value.contains("fence off")||value.contains("fence the")||value.contains("fence around")||value.contains("around the camp")||value.contains("around the perimeter"))) return Intent.BUILD_FENCE;
        // A raised lookout (#127) — a lashed pole stand a Chronicle climbs to see past the near treeline, extending
        // the boundary scout a chunk further. The lookout noun plus a build verb; placed before MAINTAIN_CAMP and
        // before the SCOUT rule's bare "look" never catches it (it needs "for danger"/"for a way out").
        if((value.contains("lookout")||value.contains("look-out")||value.contains("watch post")||value.contains("watchtower")||value.contains("watch tower")||value.contains("watch platform")||value.contains("watch stand")||value.contains("vantage point")||value.contains("observation post"))&&(value.contains("build")||value.contains("raise")||value.contains("set up")||value.contains("put up")||value.contains("make")||value.contains("construct")||value.contains("erect")||value.contains("lash"))) return Intent.BUILD_LOOKOUT;
        // A covered fuel rack (#127) — a roofed stand that keeps kindling and firewood dry so a fire will light
        // even in the rain. A rack noun with a build verb, or the plain intent to keep the fuel/wood dry.
        if(((value.contains("fuel rack")||value.contains("wood rack")||value.contains("firewood rack")||value.contains("log rack")||value.contains("kindling rack")||value.contains("woodshed")||value.contains("wood shelter"))&&(value.contains("build")||value.contains("raise")||value.contains("make")||value.contains("set up")||value.contains("put up")||value.contains("construct")||value.contains("erect")))
           ||((value.contains("keep")||value.contains("store")||value.contains("stack")||value.contains("shelter"))&&(value.contains("firewood")||value.contains("kindling")||value.contains("the fuel")||value.contains("the wood"))&&(value.contains("dry")||value.contains("out of the rain")||value.contains("out of the wet")||value.contains("off the ground")))) return Intent.BUILD_FUEL_RACK;
        if((value.contains("tidy")||value.contains("arrange")||value.contains("straighten")||value.contains("set in order")||value.contains("order the")||value.contains("clean up")||value.contains("maintain")||value.contains("look after")||value.contains("protect"))&&(value.contains("camp")||value.contains("campsite")||value.contains("supplies")||value.contains("shelter site"))) return Intent.MAINTAIN_CAMP;
        // Bare-hand cover (#195): a windbreak/brush screen leant against the wind — no tool, partial protection.
        if((value.contains("windbreak")||value.contains("wind break")||value.contains("brush screen")||value.contains("wind screen")||value.contains("reed screen against"))&&(value.contains("make")||value.contains("build")||value.contains("raise")||value.contains("set up")||value.contains("put up")||value.contains("weave")||value.contains("lean")||value.contains("erect"))) return Intent.PLACE_WINDBREAK;
        // Bare-hand partial covers (#195): sunshade / rain cover / groundsheet / stone ring — a placing verb plus the
        // cover named. Kept after MAKE_BED and before COOL_BODY/SHELTER_BODY so "rest in the shade" (no placing verb)
        // stays a body act, while "rig a sunshade" places one.
        if(coverKindOf(value)!=null&&(value.contains("make")||value.contains("build")||value.contains("raise")||value.contains("set up")||value.contains("put up")||value.contains("rig")||value.contains("lean")||value.contains("prop")||value.contains("lay")||value.contains("spread")||value.contains("pitch")||value.contains("erect")||value.contains("place")||value.contains("set out")||value.contains("arrange"))) return Intent.PLACE_COVER;
        if(value.contains("warm up")||value.contains("warm myself")||value.contains("get warm")||value.contains("warm my hands")||value.contains("warm by the fire")||value.contains("warm at the fire")||(value.contains("warm")&&value.contains("fire"))) return Intent.WARM_BODY;
        if(value.contains("dry off")||value.contains("dry myself")||value.contains("dry my ")||value.contains("get dry")||value.contains("warm and dry")||value.contains("dry out by")) return Intent.DRY_BODY;
        if(value.contains("cool off")||value.contains("cool down")||value.contains("cool myself")||value.contains("rest in the shade")||value.contains("rest in shade")||value.contains("get out of the heat")||value.contains("get out of the sun")||value.contains("get into the shade")) return Intent.COOL_BODY;
        if(value.contains("take shelter")||value.contains("take cover")||value.contains("get under cover")||value.contains("get out of the rain")||value.contains("get out of the weather")||value.contains("shelter from")||value.contains("shelter myself")||value.contains("get under the shelter")) return Intent.SHELTER_BODY;
        // Water handling (#71): collect / boil / filter — before the gather and drink rules so "collect water"
        // is filling a vessel, not gathering, and "boil water" reaches its handler rather than a process miss.
        if(value.contains("boil water")||value.contains("boil the water")||value.contains("boil some water")||value.contains("heat water to a boil")||value.contains("boil it to make it safe")) return Intent.BOIL_WATER;
        // Pour water through a filter to clarify it — but MAKING a filter ("make a bark and charcoal filter")
        // is a craft, so defer to the material process when the text names one rather than filtering here.
        if((value.contains("filter")||value.contains("strain")||value.contains("clarify")||value.contains("purify"))&&value.contains("water")&&!items.actionMatchesProcess(value)) return Intent.FILTER_WATER;
        if(value.contains("collect water")||value.contains("fetch water")||value.contains("draw water")||value.contains("gather water")||value.contains("fill container")||value.contains("scoop water")||((value.contains("fill")||value.contains("refill"))&&(value.contains("waterskin")||value.contains("water skin")||value.contains("bucket")||value.contains("vessel")||value.contains("jar")||value.contains("with water")||value.contains("flask")||value.contains("gourd")))) return Intent.COLLECT_WATER;
        if(value.contains("charcoal")&&(value.contains("make")||value.contains("take")||value.contains("gather")||value.contains("get")||value.contains("collect"))&&!items.actionMatchesProcess(value)) return Intent.MAKE_CHARCOAL;
        if(value.contains("bark")&&!value.contains("loose")&&(value.contains("strip")||value.contains("peel")||value.contains("gather")||value.contains("cut")||value.contains("collect")||value.contains("pull"))) return Intent.STRIP_BARK;
        // Ambient ground scavenge (#133): search the forest floor / under a log for small survival materials — the
        // bare-hand pickup of the #192 litter (twigs, leaf litter/tinder, loose bark, shed feather/fur, driftwood,
        // reeds). Placed after the specific gathers (branches/strip-bark/tinder) and before the generic SEARCH, so a
        // pointed scavenge yields material while a bare "look around" still just perceives.
        if((value.contains("search")||value.contains("look for")||value.contains("forage")||value.contains("scavenge")||value.contains("comb the")||value.contains("comb under")||value.contains("pick up")||value.contains("collect")||value.contains("gather"))
           &&(value.contains("forest floor")||value.contains("leaf litter")||value.contains("under a log")||value.contains("under the log")||value.contains("under logs")||value.contains("loose bark")||value.contains("shed feather")||value.contains("loose feather")||value.contains("shed fur")||value.contains("loose fur")||value.contains("driftwood")||value.contains("deadwood")||value.contains("windfall")||value.contains("twig")||value.contains("kindling")||value.contains("tinder")||value.contains("dry wood")||value.contains("dry branch")||value.contains("shed antler")||value.contains("antler")||value.contains("gnawed bone")||value.contains("loose bone")||value.contains("bone on the ground")||value.contains("bone off the ground")||value.contains("scavenge bone")||value.contains("scavenge for bone")))
            return Intent.FORAGE_GROUND;
        // Terrain crossing (#72): wading/fording/swimming/climbing toward a direction is movement to the next ground.
        if(Direction.from(value)!=null && (value.contains("wade")||value.contains("ford")||value.contains("swim")||value.contains("cross")||value.contains("climb")||value.contains("scramble")||value.contains("clamber")||value.contains("traverse"))) return Intent.MOVE;
        // Breaking off from danger (#72): retreat/flee/hide. "hide" scoped ("hide from"/"hide myself") so tanning
        // or working an animal hide is untouched.
        if(value.contains("retreat")||value.contains("back away")||value.contains("fall back")||value.contains("withdraw")||word(value,"flee")||value.contains("run away")||value.contains("run from")||value.contains("escape from")||value.contains("get away from")||value.contains("hide from")||value.contains("hide myself")||value.contains("conceal myself")||value.contains("go to ground")) return Intent.DISENGAGE;
        if((value.contains("go to")||value.contains("head to")||value.contains("head for")||value.contains("make for")||value.contains("travel to")||value.contains("walk to")||value.contains("return to")||value.contains("go back to")||value.contains("head back to")||value.contains("journey to")||value.contains("set out for"))&&Direction.from(value)==null) return Intent.TRAVEL;
        // "carve" alone is not marking — it is how half the tools and containers in the
        // world are made. Only treat carving as a trail-mark when it does not resolve to
        // a material process; the deliberate marking words (blaze, cairn, landmark) stand
        // on their own regardless.
        if((value.contains("carve")&&!items.actionMatchesProcess(action))||value.contains("blaze")||value.contains("cairn")||value.contains("landmark")||value.contains("drive a stake")||value.contains("leave a marker")||value.contains("leave a mark")||((value.contains("mark")||value.contains("pile")||value.contains("stack"))&&(value.contains("tree")||value.contains("stone")||value.contains("stake")||value.contains("post")))) return Intent.MARK;
        // Reworking a flawed build, and inspecting quality (M3b), before the generic
        // OBSERVE catch in classifyLegacy — "inspect" alone is an OBSERVE word, so these
        // require a quality/assembly context to claim it.
        if(value.contains("rework")||value.contains("redo")||value.contains("start over")||(value.contains("undo")&&value.contains("work"))) return Intent.REWORK;
        if((value.contains("inspect")||value.contains("examine")||value.contains("check")||value.contains("assess"))&&(value.contains("quality")||value.contains("grade")||value.contains("workmanship")||value.contains("craftsmanship")||value.contains("defect")||value.contains("flaw")||value.contains("material")||value.contains("my work")||value.contains("my gear")||value.contains("the bow")||value.contains("the assembly")||value.contains("how it")||value.contains("how the"))) return Intent.INSPECT;
        // The examination verbs (#25). ANALYZE/INVESTIGATE claim their own words (nothing else means them),
        // and resolve any named subject themselves. EXAMINE claims a FOCUSED "inspect/examine <this/my
        // object>" — a POINTED determiner marks one held/indicated thing. "inspect the clearing" and bare
        // "look around" stay OBSERVE, the whole-surroundings survey; "the …" is too often scenery to claim.
        if(value.contains("analyze")||value.contains("analyse")) return Intent.ANALYZE;
        if(value.contains("investigate")) return Intent.INVESTIGATE;
        // The examination verbs scope to one subject (#25/#33). A pointed determiner (this/that/my...) always
        // marks one, but so does "inspect/examine the <thing>" — the E2E defect (#33) was that "inspect the
        // branch" fell through to the whole-scene OBSERVE. Only genuine SCENERY ("the area/clearing/around")
        // stays OBSERVE; a named subject routes to EXAMINE, which resolves the reachable item the text names
        // (or a specific feature) and falls back to the place gracefully when there is no such subject.
        boolean examineScenery = value.contains("area")||value.contains("clearing")||value.contains("surrounding")||value.contains("around")||value.contains("horizon")||value.contains("distance")||value.contains("landscape")||value.contains("terrain")||value.contains("the view")||value.contains("the scene")||value.contains("whole place")||value.contains("everything");
        // Identifying (#65 identify) is a focused naming — it resolves to the same subject-scoped EXAMINE.
        if(value.contains("identify")||value.contains("what is this")||value.contains("what kind of")||value.contains("what sort of")||value.contains("tell apart")||value.contains("distinguish")) return Intent.EXAMINE;
        if((value.contains("inspect")||value.contains("examine"))&&(value.contains(" this ")||value.contains(" that ")||value.contains(" my ")||value.contains(" these ")||value.contains(" those "))) return Intent.EXAMINE;
        if((value.contains("inspect")||value.contains("examine"))&&!examineScenery&&(value.contains(" the ")||value.contains(" a ")||value.contains(" an ")||value.contains(" its ")||value.contains(" his ")||value.contains(" her ")||value.contains(" their "))) return Intent.EXAMINE;
        // The non-visual senses (#65). LISTEN/SMELL/FEEL own their verbs; SEARCH is a careful going-over of the
        // ground — placed after the mineral-prospecting (GATHER_MINERAL) and tracking (TRACK) rules above, which
        // claim their specific searches ("search the rocks for flint", "look for tracks") first.
        if(value.contains("listen")) return Intent.LISTEN;
        if(value.contains("smell")||value.contains("sniff")) return Intent.SMELL;
        if(value.contains("feel the")||value.contains("feel around")||value.contains("feel it")||value.contains("touch the")||value.contains("touch it")||value.contains("run my hand")||value.contains("test the surface")||value.contains("by touch")) return Intent.FEEL;
        if(value.contains("search")||value.contains("look for")||value.contains("hunt for")||value.contains("check beneath")||value.contains("check under")||value.contains("look under")||value.contains("turn over")||value.contains("comb through")||value.contains("rummage")||value.contains("forage through")||value.contains("sift through the")) return Intent.SEARCH;
        // Reading a written document (#65 read/review_record). Word-boundary "read" so "bread" doesn't match;
        // "read the ground/tracks" already resolved to TRACK above.
        if(word(value,"read")||word(value,"reread")||word(value,"peruse")||word(value,"consult")||value.contains("study the writing")||value.contains("study the tablet")||value.contains("study the page")||value.contains("study the journal")||value.contains("unfold and read")||value.contains("check the contents")||((value.contains("review")||value.contains("study"))&&(value.contains("tablet")||value.contains("journal")||value.contains("page")||value.contains("record")||value.contains("writing")||value.contains("document")||value.contains("note")||value.contains("inscription")))) return Intent.READ;
        // Estimation (#65 measure): weigh/count/pace-out/depth. Word-boundary the short verbs so "account"⊅count.
        if(value.contains("measure")||value.contains("pace out")||value.contains("pace off")||word(value,"weigh")||value.contains("how heavy")||word(value,"heft")||value.contains("how many")||word(value,"count")||word(value,"tally")||value.contains("how far")||value.contains("how deep")||value.contains("test the depth")||value.contains("estimate the distance")||value.contains("gauge")) return Intent.MEASURE;
        if(value.contains("refine")||value.contains("improve")||value.contains("upgrade")||value.contains("revise")||value.contains("enhance")||(value.contains("add")&&value.contains("holder"))) return Intent.REFINE;
        if(value.contains("designate")||value.contains("christen")||((value.contains("name")||value.contains("call")||value.contains("establish")||value.contains("found")||value.contains("mark"))&&(value.contains("this place")||value.contains("this area")||value.contains("this spot")||value.contains("this location")||value.contains("here as")||value.contains("this as")||value.contains("this the")))) return Intent.DESIGNATE;
        // DROP / place (#67): set an object down on the ground here. STORE (into a container) and the trap/
        // marker verbs are already claimed above, so a bare place/lay/set-down here is a plain drop.
        if(value.contains("drop")||value.contains("leave behind")||value.contains("set down")||value.contains("put down")||value.contains("discard")||value.contains("lay down")||value.contains("lay it down")||value.contains("lay them down")||((value.contains("place")||value.contains("set")||value.contains("put")||value.contains("lay")||value.contains("leave"))&&(value.contains("on the ground")||value.contains("down here")||value.contains(" aside")))) return Intent.DROP;
        if(value.contains("unequip")||value.contains("take off")||value.contains("remove my")||value.contains("remove the")||value.contains("doff")) return Intent.UNEQUIP;
        if((value.contains("equip")||value.contains("wear")||value.contains("put on")||value.contains("wield")||value.contains("hold my")||value.contains("hold the")||value.contains("carry on my back")||value.contains("sling"))) return Intent.EQUIP;
        if(!action.contains(":")&&(value.contains("map")||value.contains("chart")||value.contains("cartograph"))&&(value.contains("sketch")||value.contains("draw")||value.contains("make")||value.contains("chart")||value.contains("create")||value.contains("update")||value.contains("plot")||value.contains("survey")||value.contains("map out"))) return Intent.SKETCH_MAP;
        if(action.contains(":")&&(value.contains("write")||value.contains("draw")||value.contains("sketch")||value.contains("record")||value.contains("inscribe")||value.contains("mark ")||value.contains("note"))) return Intent.WRITE;
        return classifyLegacy(action);
    }
    /** Whole-word containment: word("smoke the meat","eat") is false, word("take a nap","nap") is true. */
    private static boolean word(String haystack, String w) { return haystack.matches("(?s).*\\b" + w + "\\b.*"); }
    private Intent classifyLeanTo(String value) {
        if(value.contains("repair")) return Intent.REPAIR_LEAN_TO;
        if(value.contains("abandon")||value.contains("leave")) return Intent.ABANDON_LEAN_TO;
        if(value.contains("resume")||value.contains("return to")) return Intent.RESUME_LEAN_TO;
        return (value.contains("work") || value.contains("continue") || value.contains("build") || value.contains("weave") || value.contains("bind")) ? Intent.WORK_LEAN_TO : Intent.START_LEAN_TO;
    }
    private Intent classifyLegacy(String action) { String value = action.toLowerCase(Locale.ROOT); if ((value.contains("cook") || value.contains("roast") || value.contains("grill") || value.contains("bake") || value.contains("broil") || value.contains("simmer") || value.contains("stew") || value.contains("braise")) && (value.contains("meat") || value.contains("game") || value.contains("flesh") || value.contains("carcass"))) return Intent.COOK_MEAT; if ((value.contains("harvest") || value.contains("butcher") || value.contains("skin")) && (value.contains("carcass") || value.contains("remains") || value.contains("animal"))) return Intent.HARVEST_CARCASS; if ((value.contains("bind") || value.contains("bandage") || value.contains("dress")) && (value.contains("wound") || value.contains("injury") || value.contains("bleeding"))) return Intent.TREAT_WOUND; if ((value.contains("feed") || value.contains("stoke") || value.contains("add wood")) && value.contains("fire")) return Intent.FEED_FIRE; if ((value.contains("light")||value.contains("ignite")) && value.contains("fire")) return Intent.LIGHT_FIRE; if (value.contains("fire pit") || value.contains("firepit")) return Intent.BUILD_FIRE_PIT; if ((value.contains("fight")||value.contains("attack")||value.contains("strike")) && (value.contains("animal")||value.contains("wildlife")||value.contains("creature"))) return Intent.CONFRONT_WILDLIFE; if ((value.contains("weave") || value.contains("craft") || value.contains("make")) && value.contains("basket") && !value.contains("burden") && !value.contains("pack") && !value.contains("large") && !value.contains("big") && !value.contains("pannier") && !value.contains("carrying")) return Intent.CRAFT_BASKET; if ((value.contains("gather")||value.contains("collect")) && value.contains("fiber")) return Intent.GATHER_FIBER; if ((value.contains("gather")||value.contains("collect")) && (value.contains("branch")||value.contains("stick"))) return Intent.GATHER_BRANCHES; if ((value.contains("gather")||value.contains("collect")) && (value.contains("berry")||value.contains("berries"))) return Intent.GATHER_BERRIES; if ((value.contains("gather")||value.contains("collect")) && (value.contains("stone")||value.contains("rock"))) return Intent.GATHER_STONE; if (word(value,"eat")||value.contains("consume")) return Intent.EAT; if (value.contains("drink")) return Intent.DRINK; if (Direction.from(value) != null && (value.contains("walk") || value.contains("travel") || value.contains("go ") || value.contains("move"))) return Intent.MOVE; if (value.contains("observe") || value.contains("look") || value.contains("inspect") || value.contains("survey") || value.contains("scout") || value.contains("scan") || value.contains("explore") || value.contains("examine") || value.contains("study the") || value.contains("take in")) return Intent.OBSERVE; if ((value.contains("sleep") && !value.contains("platform")) || word(value,"nap") || value.contains("lie down to sleep") || value.contains("bed down") || value.contains("go to sleep")) return Intent.SLEEP; if (value.contains("rest") || value.contains("wait")) return Intent.REST; if (value.contains("urinate") || value.contains("pee")) return Intent.URINATE; if (value.contains("defecate") || value.contains("poop")) return Intent.DEFECATE; return Intent.UNKNOWN; }
    /**
     * Eat whatever food is to hand. A food the player explicitly names wins ("eat the oyster
     * mushroom"); otherwise cooked meat, then raw meat — both spoilage-tracked through the food
     * service — then any other food the chronicle carries: foraged mushrooms, plants, berries, dried
     * stores. Only truly having nothing edible in reach fails. (GitHub #24: EAT previously knew only
     * cooked/raw meat and wild berries, so a foraged mushroom could never be eaten.)
     */
    private String[] eat(UUID chronicle, String text, UUID actionId, Instant at) {
        String named = items.namedFoodInReach(chronicle, text.toLowerCase(Locale.ROOT));
        // A named non-meat food is eaten directly; meat always routes through the spoilage-tracked service below.
        if (named != null && !named.contains("meat")) return eatItem(chronicle, named, actionId, at);
        FoodPreservationService.Consumption cooked = food.consume(chronicle, "cooked_game_meat", at);
        if (cooked.consumed()) { physiology.eatCookedMeal(chronicle, cooked.grade()); if (cooked.spoiled()) physiology.applyFoodborneIllness(chronicle, actionId, at); return new String[]{"SUCCEEDED", "The cooked meat is warm and dense, and the meal settles heavily but well."}; }
        FoodPreservationService.Consumption raw = food.consume(chronicle, "raw_game_meat", at);
        if (raw.consumed()) { physiology.eat(chronicle, raw.grade()); if (raw.spoiled()) physiology.applyFoodborneIllness(chronicle, actionId, at); return new String[]{"SUCCEEDED", "The raw meat is cold and difficult to swallow, but it settles the immediate emptiness."}; }
        String any = named != null ? named : items.anyFoodInReach(chronicle);
        if (any != null) return eatItem(chronicle, any, actionId, at);
        return new String[]{"FAILED", "You search through everything you can reach and come up with nothing to eat — no cooked meat, no raw, no forage. There is simply no food here to hand."};
    }
    private String[] eatItem(UUID chronicle, String itemKey, UUID actionId, Instant at) {
        // Read the grade of the very item about to be eaten (a finer-cooked stew nourishes a little more, #271)
        // before it is consumed and gone.
        com.devosphere.draugr.quality.QualityGrade grade = items.gradeOfNextConsumed(chronicle, itemKey);
        items.consumeOne(chronicle, itemKey, at);
        // A forage marked poisonous (death cap, fly agaric) nourishes nothing — it sickens. The world
        // applies physics, not a warning: the player had to know which mushroom before they ate it.
        if (items.isPoisonousForage(itemKey)) {
            physiology.applyFoodborneIllness(chronicle, actionId, at);
            return new String[]{"SUCCEEDED", "You eat it. The taste turns sharp, then bitter, and a cold unease is spreading through your gut before you have finished."};
        }
        physiology.eat(chronicle, grade);
        return new String[]{"SUCCEEDED", eatProse(itemKey)};
    }
    /** Witness-stance prose for eating a foraged food, keyed loosely by what it is. Must name no Body-HUD state (NarrationPolicy / DR-0010). */
    private String eatProse(String itemKey) {
        if (itemKey.contains("berr")) return "The berries break softly between your teeth, leaving a faint sweetness behind.";
        if (itemKey.contains("mushroom") || itemKey.contains("porcini") || itemKey.contains("chanterelle")) return "The mushroom is earthy and dense on the tongue, and you chew it down slowly.";
        if (itemKey.contains("honey")) return "The honey is thick and over-sweet, and a brief warmth follows it down.";
        if (itemKey.contains("meat") || itemKey.contains("pemmican")) return "You work the food down slowly, and the immediate emptiness eases.";
        return "You eat what you foraged. It is plain, but there is something in it worth the chewing.";
    }
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
        boolean wantsStone = value.contains("stone") || value.contains("slab");
        UUID surface = null; boolean onStone = false;
        if (wantsStone) { surface = items.findReachable(chronicle.id(), "stone_slab"); onStone = surface != null; }
        if (surface == null) surface = items.findReachable(chronicle.id(), "bark_sheet");
        if (surface == null) surface = items.findReachable(chronicle.id(), "animal_hide");
        if (surface == null) { surface = items.findReachable(chronicle.id(), "stone_slab"); onStone = surface != null; }
        if (surface == null) return new String[]{"FAILED", "You have nothing suitable to write on, and the charcoal marks only your fingers."};
        literature.createFromSurface(surface, chronicle.id(), actionId, at, kind, kind.equals("MAP") ? "Hand-drawn map" : "Written record", content);
        if (onStone) return new String[]{"SUCCEEDED", kind.equals("MAP") ? "You grind the map into the face of the stone slab, slow and deliberate, a mark that will outlast you." : "You grind your words into the stone slab, slow and deliberate — a record cut to endure."};
        return new String[]{"SUCCEEDED", kind.equals("MAP") ? "You scratch a rough map onto the surface, marking what you know of the land." : "You press charcoal to the surface and set down your first written words."};
    }
    private boolean referencesExisting(String value) { return value.matches("(?s).*\\b(my|the)\\b.*\\b(journal|record|map|note|book|writing|log|diary|chronicle)\\b.*"); }
    /**
     * Draw a map from the chronicle's own geographic knowledge, rather than from a
     * layout the player supplied. Fidelity follows real cartography: a well-travelled
     * hand who has memorized and marked places, and revised the map before, produces
     * something close to true; a newcomer working from vague memory produces a rough,
     * error-prone sketch. A player who instead types their own map layout after a
     * colon takes the WRITE path and it is recorded verbatim — their own survey work.
     */
    private String[] sketchMap(ActiveChronicle chronicle, String text, UUID actionId, Instant at) {
        if (!items.hasAtLeast(chronicle.id(), "charcoal", 1)) return new String[]{"FAILED", "Without anything to draw with, the map stays only in your mind."};
        String value = text.toLowerCase(Locale.ROOT);
        UUID existing = literature.reachableDocumentOfKind(chronicle.id(), "MAP");
        boolean update = existing != null && (value.contains("update") || value.contains("revise") || value.contains("improve") || referencesExisting(value));
        int priorRevisions = 0;
        if (update) { Integer rc = jdbc.queryForObject("SELECT COALESCE(r.revision_number,0) FROM literature_document d LEFT JOIN literature_revision r ON r.id=d.current_revision_id WHERE d.object_id=?", Integer.class, existing); priorRevisions = rc == null ? 0 : rc; }
        String content = generateMapSketch(chronicle, actionId, Math.min(0.4, priorRevisions * 0.08));
        if (update) { literature.revise(existing, chronicle.id(), actionId, at, LiteratureService.Edit.REPLACE, content, null); return new String[]{"SUCCEEDED", "You work over your map again, correcting a line here, placing a name there. Each pass makes it a little truer."}; }
        UUID surface = items.findReachable(chronicle.id(), "bark_sheet");
        if (surface == null) surface = items.findReachable(chronicle.id(), "animal_hide");
        if (surface == null) surface = items.findReachable(chronicle.id(), "stone_slab");
        if (surface == null) return new String[]{"FAILED", "You have nothing suitable to draw a map on, and the charcoal marks only your fingers."};
        literature.createFromSurface(surface, chronicle.id(), actionId, at, "MAP", "Hand-drawn map", content);
        return new String[]{"SUCCEEDED", "You set down a map of the country you have walked, drawn as clearly as memory allows."};
    }
    private String generateMapSketch(ActiveChronicle chronicle, UUID actionId, double revisionBonus) {
        java.util.List<java.util.Map<String,Object>> places = jdbc.queryForList("SELECT nl.name, nl.memorized, c.grid_x, c.grid_y, (SELECT COUNT(*) FROM location_marker m WHERE m.chunk_id=nl.chunk_id) AS markers, COALESCE((SELECT visit_count FROM chronicle_chunk_visit v WHERE v.chronicle_id=nl.chronicle_id AND v.chunk_id=nl.chunk_id),0) AS visits FROM chronicle_named_location nl JOIN world_chunk c ON c.id=nl.chunk_id WHERE nl.chronicle_id=?", chronicle.id());
        if (places.isEmpty()) return "A few uncertain strokes cross the surface, but you have named and fixed too few places to draw a map worth the name.";
        double drawing = capability.familiarity(chronicle.id(), "FINE_MOTOR");
        long solid = places.stream().filter(p -> Boolean.TRUE.equals(p.get("memorized")) || ((Number)p.get("markers")).intValue() > 0).count();
        double dataQuality = (double) solid / places.size();
        double breadth = Math.min(1.0, places.size() / 8.0);
        double accuracy = Math.max(0.15, Math.min(0.95, 0.20 + dataQuality * 0.40 + breadth * 0.15 + drawing * 3.0 + revisionBonus));
        java.util.Map<String,Object> anchor = places.stream().max(java.util.Comparator.comparingInt(p -> ((Number)p.get("visits")).intValue())).orElse(places.get(0));
        int ax = (int) anchor.get("grid_x"), ay = (int) anchor.get("grid_y"); String anchorName = (String) anchor.get("name");
        StringBuilder b = new StringBuilder("Map sketch, centred on ").append(anchorName).append(".\n");
        java.util.Random rnd = new java.util.Random(actionId.getMostSignificantBits() ^ actionId.getLeastSignificantBits());
        for (java.util.Map<String,Object> p : places) {
            String name = (String) p.get("name");
            if (name.equals(anchorName)) { b.append("- ").append(name).append(" (centre)\n"); continue; }
            int dx = (int) p.get("grid_x") - ax, dy = (int) p.get("grid_y") - ay;
            int dist = Math.max(Math.abs(dx), Math.abs(dy));
            String dir = compass(dx, dy);
            if (rnd.nextDouble() > accuracy) { // the chronicle's memory of this place is imperfect
                double e = rnd.nextDouble();
                if (e < 0.30) continue; // forgotten off the map entirely
                else if (e < 0.65) b.append("- ").append(name).append(": roughly ").append(rotateCompass(dir, rnd.nextBoolean())).append(", perhaps ").append(Math.max(1, dist + rnd.nextInt(3) - 1)).append(" off (unsure)\n");
                else b.append("- ").append(name).append(": somewhere ").append(dir).append(" (position uncertain)\n");
            } else {
                b.append("- ").append(name).append(": ").append(dir).append(", about ").append(dist).append(" off\n");
            }
        }
        b.append(accuracy >= 0.75 ? "The proportions feel true; this is a map you could set your course by." : accuracy >= 0.45 ? "Some of it is guesswork, but the shape of the land is here." : "Much of this is uncertain — a rough impression more than a faithful record.");
        return b.toString();
    }
    /** Eight-point compass from a grid offset; grid y increases southward. */
    private String compass(int dx, int dy) { String ns = dy < 0 ? "north" : dy > 0 ? "south" : ""; String ew = dx > 0 ? "east" : dx < 0 ? "west" : ""; String c = ns + ew; return c.isEmpty() ? "at the centre" : c; }
    private String rotateCompass(String dir, boolean clockwise) { String[] ring = {"north","northeast","east","southeast","south","southwest","west","northwest"}; for (int i=0;i<ring.length;i++) if (ring[i].equals(dir)) return ring[(i + (clockwise?1:ring.length-1)) % ring.length]; return dir; }
    private String[] equipByName(ActiveChronicle chronicle, String text, Instant at) {
        java.util.List<java.util.Map<String,Object>> candidates = jdbc.queryForList("WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') SELECT w.id,w.display_name,c.body_position,c.layer FROM reachable r JOIN world_object w ON w.id=r.id JOIN item_instance i ON i.object_id=w.id JOIN item_equipment_compatibility c ON c.item_key=i.item_key LEFT JOIN equipment_attachment e ON e.item_id=w.id WHERE e.item_id IS NULL ORDER BY w.display_name", chronicle.id());
        if (candidates.isEmpty()) return new String[]{"FAILED","You have nothing unequipped that can be worn or wielded."};
        String lower = text.toLowerCase(Locale.ROOT);
        // Candidate rows for the item the text actually names (one row per compatible slot).
        var named = candidates.stream().filter(c->lower.contains(((String)c.get("display_name")).toLowerCase(Locale.ROOT))).collect(java.util.stream.Collectors.toList());
        var pool = named.isEmpty() ? candidates : named;
        // Honour a hand the player names ("on my left hand"); otherwise take the first slot.
        String preferredPos = lower.contains("left") ? "HAND_LEFT" : (lower.contains("right") ? "HAND_RIGHT" : null);
        var match = pool.stream().filter(c->preferredPos!=null && preferredPos.equals(c.get("body_position"))).findFirst().orElse(pool.get(0));
        UUID item = (UUID) match.get("id"); String pos = (String) match.get("body_position"); String layer = (String) match.get("layer");
        try { items.equip(item, pos, layer); return new String[]{"SUCCEEDED","You settle the "+match.get("display_name")+" into place."}; }
        catch (Exception e) { return new String[]{"FAILED","The "+match.get("display_name")+" cannot be fitted there — something else is already in the way."}; }
    }
    private String[] unequipByName(ActiveChronicle chronicle, String text, Instant at) {
        java.util.List<java.util.Map<String,Object>> equipped = jdbc.queryForList("SELECT w.id,w.display_name FROM equipment_attachment e JOIN world_object w ON w.id=e.item_id WHERE e.chronicle_id=? AND w.lifecycle_state='ACTIVE'", chronicle.id());
        if (equipped.isEmpty()) return new String[]{"FAILED","You have nothing equipped to remove."};
        String lower = text.toLowerCase(Locale.ROOT);
        var match = equipped.stream().filter(r->lower.contains(((String)r.get("display_name")).toLowerCase(Locale.ROOT))).findFirst().orElse(null);
        if (match == null) return new String[]{"FAILED","You are not wearing or holding anything by that name."};
        boolean done = items.unequip((UUID)match.get("id"), at);
        return done ? new String[]{"SUCCEEDED","You remove the "+match.get("display_name")+" and hold it."} : new String[]{"FAILED","The "+match.get("display_name")+" cannot be removed right now."};
    }
    private String[] dropByName(ActiveChronicle chronicle, String text, Instant at) {
        java.util.List<java.util.Map<String,Object>> carried = jdbc.queryForList("WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') SELECT w.id,w.display_name FROM reachable r JOIN world_object w ON w.id=r.id JOIN item_instance i ON i.object_id=w.id ORDER BY w.display_name", chronicle.id());
        if (carried.isEmpty()) return new String[]{"FAILED","You are not carrying anything to leave behind."};
        String lower = text.toLowerCase(Locale.ROOT);
        var match = carried.stream().filter(r->lower.contains(((String)r.get("display_name")).toLowerCase(Locale.ROOT))).findFirst().orElse(null);
        if (match == null) return new String[]{"FAILED","You are not carrying anything by that name."};
        items.drop((UUID)match.get("id"), chronicle.location(), at);
        return new String[]{"SUCCEEDED","You set the "+match.get("display_name")+" down and leave it where it lies."};
    }
    /** Improve an existing carried item in place — the organic "Revision II, III" evolution, not a new object. Its identity is kept; only the workmanship and name advance. */
    private String[] refineByName(ActiveChronicle chronicle, String text, Instant at) {
        java.util.List<java.util.Map<String,Object>> carried = jdbc.queryForList("WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') SELECT w.id,w.display_name FROM reachable r JOIN world_object w ON w.id=r.id JOIN item_instance i ON i.object_id=w.id ORDER BY length(w.display_name) DESC", chronicle.id());
        if (carried.isEmpty()) return new String[]{"FAILED","You have nothing on you to work on and improve."};
        String lower = text.toLowerCase(Locale.ROOT);
        var match = carried.stream().filter(c->lower.contains(baseName((String)c.get("display_name")).toLowerCase(Locale.ROOT))).findFirst().orElse(null);
        if (match == null) return new String[]{"FAILED","You have nothing by that name to improve."};
        UUID id = (UUID) match.get("id"); String base = baseName((String) match.get("display_name"));
        Integer priorRefines = jdbc.queryForObject("SELECT COUNT(*) FROM object_transition WHERE object_id=? AND transition_type='REFINED'", Integer.class, id);
        int revision = (priorRefines == null ? 0 : priorRefines) + 2; // the original is implicitly Revision I; the first improvement makes Revision II
        String newName = base + " Revision " + toRoman(revision);
        jdbc.update("UPDATE world_object SET display_name=? WHERE id=?", newName, id);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'REFINED',jsonb_build_object('revision',?))", id, java.sql.Timestamp.from(at), revision);
        // Record WHAT changed as a thick-object modification (V68), so the improvement is a readable part of the
        // object's history — "added a stone hammer holder" — not just an anonymous revision bump (#25 / Phase-0
        // Utility Belt Revision III). The chronicle reads this back when it investigates the thing.
        jdbc.update("INSERT INTO object_modification (object_id,occurred_at,note) VALUES (?,?,?)", id, java.sql.Timestamp.from(at), refinementNote(text, revision));
        return new String[]{"SUCCEEDED","You work over the "+base+", reinforcing and improving it. It is now the "+newName+"."};
    }
    /** A readable note of what a REFINE actually changed — the added feature if the player named one, else a generic improvement. */
    private String refinementNote(String text, int revision) {
        int i = text.toLowerCase(Locale.ROOT).indexOf("add ");
        if (i >= 0) {
            String what = text.substring(i + 4).trim();
            if (!what.isEmpty()) { String s = "Added " + what; return s.length() > 190 ? s.substring(0, 190) : s; }
        }
        return "Reinforced and improved to Revision " + toRoman(revision);
    }
    private String baseName(String displayName) { return displayName.replaceAll("(?i)\\s+Revision\\s+[IVXLC0-9]+$", "").trim(); }
    private String toRoman(int n) { if(n<=0) return String.valueOf(n); int[] v={100,90,50,40,10,9,5,4,1}; String[] s={"C","XC","L","XL","X","IX","V","IV","I"}; StringBuilder b=new StringBuilder(); for(int i=0;i<v.length;i++) while(n>=v[i]){b.append(s[i]);n-=v[i];} return b.toString(); }
    /**
     * Read a reachable written document (#65 read/review_record): the one the text names by title or kind, else
     * the only one in reach. A map is redirected to the Chronicle Map (maps are not read as pages); a blank
     * surface says so; an unreachable or unnamed one fails specifically. Read-only.
     */
    private String[] readDocument(ActiveChronicle chronicle, String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        java.util.List<LiteratureService.DocumentView> docs = literature.reachable(chronicle.id());
        if (docs.isEmpty()) return new String[]{"FAILED", "You have nothing written within reach to read."};
        LiteratureService.DocumentView doc = docs.stream().filter(d -> d.title() != null && lower.contains(d.title().toLowerCase(Locale.ROOT))).findFirst()
            .orElseGet(() -> docs.stream().filter(d -> d.kind() != null && lower.contains(d.kind().toLowerCase(Locale.ROOT))).findFirst()
            .orElse(docs.size() == 1 ? docs.get(0) : null));
        if (doc == null) return new String[]{"FAILED", "You cannot tell which writing you mean — name the one to read."};
        if ("MAP".equalsIgnoreCase(doc.kind())) return new String[]{"SUCCEEDED", "That is a map — you unfold and study it in your Chronicle Map, not as a page of text."};
        if (doc.revisionId() == null || doc.revisionNumber() == 0) return new String[]{"SUCCEEDED", "You turn " + (doc.title() == null ? "the surface" : "the " + doc.title()) + " over, but nothing has been set down on it yet."};
        LiteratureService.RevisionView rev = literature.current(doc.id(), chronicle.id());
        String content = rev == null ? null : rev.content();
        if (content == null || content.isBlank()) return new String[]{"SUCCEEDED", "The page is blank."};
        return new String[]{"SUCCEEDED", "You read " + (doc.title() == null ? "the writing" : doc.title()) + ":\n\n" + content};
    }
    private void reviseDocument(UUID chronicleId, UUID actionId, Instant resolvedAt, String text) { Matcher match=DOCUMENT_EDIT.matcher(text); if(!match.matches()) throw new IllegalArgumentException("Unrecognized document edit."); UUID documentId=UUID.fromString(match.group(2)); LiteratureService.Edit edit="append".equalsIgnoreCase(match.group(1))?LiteratureService.Edit.APPEND:LiteratureService.Edit.REPLACE; if(!literature.documentReachable(documentId,chronicleId)) throw new IllegalArgumentException("The document is not physically reachable."); literature.revise(documentId,chronicleId,actionId,resolvedAt,edit,match.group(3),null); }
    /** Which bare-hand cover (#195) an action text names, or null if none. Shared by classify and dispatch so the
     *  two never disagree on which cover is meant. Checked most-specific first. */
    private static String coverKindOf(String value) {
        if(value.contains("stone ring")||value.contains("fire ring")||value.contains("ring of stones")||value.contains("hearth ring")||value.contains("stone circle")) return "STONE_RING";
        if(value.contains("rain cover")||value.contains("rain fly")||value.contains("rain tarp")||value.contains("cover from the rain")||value.contains("cover against the rain")||value.contains("rain shelter")) return "RAIN_COVER";
        if(value.contains("groundsheet")||value.contains("ground sheet")||value.contains("ground cloth")||value.contains("ground mat")) return "GROUNDSHEET";
        if(value.contains("sunshade")||value.contains("sun shade")||value.contains("shade from the sun")||value.contains("sun awning")||value.contains("awning")||value.contains("shade overhead")) return "SUNSHADE";
        if(value.contains("alarm")||value.contains("trip line")||value.contains("trip-line")||value.contains("tripwire")||value.contains("trip wire")||value.contains("warning line")||value.contains("warning rattle")||value.contains("noise line")||value.contains("perimeter line")) return "CAMP_ALARM";
        return null;
    }
    private record ActiveChronicle(UUID id, UUID location) { } private record TravelPlan(UUID destination, int distance, String reason) { } private enum Intent { OBSERVE, MOVE, TRAVEL, MARK, REST, SLEEP, GATHER_FIBER, GATHER_STONE, GATHER_BERRIES, GATHER_BRANCHES, GATHER_CLAY, GATHER_STONE_SLAB, GATHER_PLANT, FELL_TREE, RAID_HIVE, COLLECT_INSECTS, FISH, SNARE, TRACK, SCOUT, TAME, LURE, SET_TRAP, CHECK_TRAP, CRAFT_GARMENT, GATHER_MINERAL, CRAFT_FIRE_TOOL, PROCESS_MATERIAL, SKETCH_MAP, EAT, DRINK, COLLECT_WATER, BOIL_WATER, FILTER_WATER, WASH, WARM_BODY, DRY_BODY, COOL_BODY, SHELTER_BODY, STRETCH, TREAT_WOUND, EDIT_DOCUMENT, WRITE, STRIP_BARK, MAKE_CHARCOAL, LIGHT_FIRE, FEED_FIRE, EXTINGUISH_FIRE, BANK_FIRE, COOK_MEAT, CONFRONT_WILDLIFE, HARVEST_CARCASS, DISENGAGE, CRAFT_BASKET, CRAFT_SPEAR, CRAFT_KNIFE, CRAFT_HAMMER, CRAFT_PICKAXE, CRAFT_HATCHET, CRAFT_FIRE_KIT, CRAFT_TINDER, CRAFT_DESK, CRAFT_CHAIR, CRAFT_SHELF, CRAFT_WORKSTATION, CRAFT_NET, CRAFT_BELT, BUILD_FIRE_PIT, BUILD_ALARM, BUILD_FENCE, BUILD_LOOKOUT, BUILD_FUEL_RACK, START_LEAN_TO, WORK_LEAN_TO, ABANDON_LEAN_TO, RESUME_LEAN_TO, REPAIR_LEAN_TO, REPAIR_ITEM, REPAIR_STRUCTURE, DISMANTLE, EQUIP, UNEQUIP, DROP, PICK_UP, STORE, OPEN_CONTAINER, CLOSE_CONTAINER, DESIGNATE, REFINE, ADVANCE_ASSEMBLY, INSPECT, EXAMINE, ANALYZE, INVESTIGATE, SEARCH, LISTEN, SMELL, FEEL, READ, MEASURE, REWORK, URINATE, DEFECATE, PERSONAL_ACT, AGGRESSION_WILDLIFE, AGGRESSION_INANIMATE, MAKE_BED, MAINTAIN_CAMP, PLACE_WINDBREAK, PLACE_COVER, FORAGE_GROUND, UNKNOWN }
    private enum Direction { NORTH(0,-1,"north"), SOUTH(0,1,"south"), EAST(1,0,"east"), WEST(-1,0,"west"); final int dx; final int dy; final String description; Direction(int dx,int dy,String description){this.dx=dx;this.dy=dy;this.description=description;} static Direction from(String action){String value=action.toLowerCase(Locale.ROOT); for(Direction direction:values()) if(value.matches(".*\\b"+direction.description+"\\b.*")) return direction; return null;} }
    /**
     * The structured perception frame — the seam every future Simulation Agent reads
     * from. Where {@code perception} is the finished player-facing prose, this frame
     * is the machine-legible truth behind it: the raw intent and outcome, where the
     * chronicle stood, the hour and weather in unembellished terms, what physically
     * shared that ground, and which items the act touched. An AI narrator receives
     * this and only this, and must witness it without advising. Physiology is carried
     * as the same Body HUD snapshot the player sees, alongside {@code sinceLastFrame} —
     * the qualitative transitions the tick and this action wrought since the previous
     * frame, so the passage of time is surfaced rather than silently swallowed.
     */
    /**
     * Ground a deterministic core in a clause of setting (the world's weather and, on deliberate
     * attention, the look of the land) via the {@link com.devosphere.draugr.narration.NarrationEngine}.
     * One light read of the chunk's biome and the world's weather; the engine holds the punctuation rule.
     */
    private String groundPerception(String core, UUID location, String attention, String beforeWeather, Instant at) {
        java.util.Map<String,Object> env = jdbc.queryForMap(
            "SELECT wc.biome, wc.elevation, wc.moisture, wc.grid_y, wg.height_chunks, ww.weather_kind, " +
            "COALESCE(ww.ambient_temperature_c,18.0) AS t, COALESCE(ww.wind_speed_kph,6) AS w " +
            "FROM world_chunk wc JOIN world_genesis wg ON wg.world_id=wc.world_id " +
            "LEFT JOIN world_weather ww ON ww.world_id=wc.world_id WHERE wc.id=?", location);
        String biome = (String) env.get("biome");
        String globalKind = (String) env.get("weather_kind");
        // The prose reports the weather as FELT here (#28), derived from the chunk's own geography: altitude
        // cools it, latitude shifts it, humidity biases rain↔snow — so the same front that rains below falls
        // as snow on the peak. A front CHANGING is a global event, so that flag stays global.
        com.devosphere.draugr.simulation.BiomeClimate.Local local = com.devosphere.draugr.simulation.BiomeClimate.at(
            biome, ((Number) env.get("elevation")).intValue(), ((Number) env.get("moisture")).intValue(),
            ((Number) env.get("grid_y")).intValue(), ((Number) env.get("height_chunks")).intValue(),
            globalKind, ((Number) env.get("t")).doubleValue(), ((Number) env.get("w")).intValue());
        boolean weatherChanged = beforeWeather != null && globalKind != null && !beforeWeather.equals(globalKind);
        return narrationEngine.ground(core, biome, timeOfDayLabel(at), local.kind(), attention, weatherChanged);
    }
    private PerceptionFrame buildFrame(ActiveChronicle chronicle, Intent intent, String outcome, String perception, Instant at, ChroniclePhysiologyService.BodyHudSnapshot before, ChroniclePhysiologyService.BodyHudSnapshot after, String beforeWeather, String attention) {
        UUID loc = chronicle.location();
        java.util.Map<String,Object> here = jdbc.queryForMap("SELECT world_id, grid_x, grid_y, biome FROM world_chunk WHERE id=?", loc);
        UUID world = (UUID) here.get("world_id");
        String named = jdbc.query("SELECT name FROM chronicle_named_location WHERE chunk_id=? AND chronicle_id=? LIMIT 1", rs -> rs.next() ? rs.getString(1) : null, loc, chronicle.id());
        LocationView location = new LocationView(loc, named, (String) here.get("biome"), (int) here.get("grid_x"), (int) here.get("grid_y"));
        WeatherView weather = jdbc.query("SELECT weather_kind, intensity FROM world_weather WHERE world_id=?", rs -> rs.next() ? new WeatherView(rs.getString(1), rs.getInt(2)) : null, world);
        // ATTENTION scales what the frame reveals. A chronicle heads-down on a task
        // (LOW) witnesses only what the act touches, not the carcass in the treeline
        // they never looked at — the ignorance the design intends. Moving takes the
        // surroundings in passing (MODERATE); deliberate looking reveals all (HIGH).
        List<String> nearby = "LOW".equals(attention) ? List.of() : jdbc.query(
            "SELECT object_type, COUNT(*) FROM world_object WHERE current_location_id=? AND lifecycle_state='ACTIVE' AND id<>? GROUP BY object_type ORDER BY object_type",
            (rs, row) -> rs.getString(1).toLowerCase(Locale.ROOT) + ":" + rs.getInt(2), loc, chronicle.id());
        List<StateChange> sinceLast = physiologyDelta(before, after);
        if (beforeWeather != null && weather != null && !beforeWeather.equals(weather.kind())) sinceLast.add(new StateChange("weather", beforeWeather, weather.kind()));
        return new PerceptionFrame(intent.name(), outcome, location, timeOfDayLabel(at), weather, attention, List.copyOf(nearby), after, List.copyOf(sinceLast), perception);
    }
    /**
     * How much of the world the chronicle was actually attending to, read from the
     * action text. Deliberate perception — looking, scanning, searching, doing a task
     * "carefully" or "warily" — is HIGH. Moving through the country takes it in
     * passing (MODERATE). A single heads-down task with no such cue is LOW: the
     * narrator witnesses the act and little else. This is the seam that lets a player
     * who does not look remain, dangerously, uninformed.
     */
    /**
     * The capability family an action exercises, so repetition builds the RELEVANT mastery (GitHub #26).
     * LOAD = strength/carrying, AIM = precision at a target, ATTENTION = perception, LOCOMOTION = travel,
     * RECOVERY = rest, FINE_MOTOR = skilled hand-work. One family per intent; the growth itself is slow,
     * hidden, and lifelong (CapabilityAdaptationService).
     */
    private String capabilityDomainOf(Intent intent) {
        return switch (intent) {
            case FELL_TREE, GATHER_STONE, GATHER_STONE_SLAB, GATHER_MINERAL, GATHER_CLAY, GATHER_FIBER,
                 GATHER_BRANCHES, GATHER_BERRIES, GATHER_PLANT, FORAGE_GROUND, HARVEST_CARCASS, RAID_HIVE, COLLECT_INSECTS,
                 BUILD_FIRE_PIT, BUILD_FENCE, BUILD_LOOKOUT, BUILD_FUEL_RACK, START_LEAN_TO, WORK_LEAN_TO, REPAIR_LEAN_TO, REPAIR_STRUCTURE, DISMANTLE, PLACE_WINDBREAK, PLACE_COVER -> "LOAD";
            case CONFRONT_WILDLIFE, FISH, SNARE, SET_TRAP, CHECK_TRAP, LURE, TAME -> "AIM";
            case OBSERVE, TRACK, SCOUT, INSPECT, EXAMINE, SEARCH, LISTEN, SMELL, FEEL, MEASURE, MAINTAIN_CAMP -> "ATTENTION";
            case READ -> "KNOWLEDGE"; // reading a record builds knowledge, not perception
            case ANALYZE -> "INSIGHT";       // #25: reading what a thing is and does builds understanding
            case INVESTIGATE -> "KNOWLEDGE"; // #25: inferring origin/provenance builds knowledge
            case MOVE, TRAVEL, DISENGAGE -> "LOCOMOTION";
            case REST, SLEEP, WARM_BODY, DRY_BODY, COOL_BODY, SHELTER_BODY, STRETCH -> "RECOVERY";
            default -> "FINE_MOTOR"; // crafts, processing, assembly, writing, fire-tending, handling gear, wound care
        };
    }
    /** The physical cost of an action beyond the passive tick (GitHub #27): energy spent, hygiene lost. */
    private record Labor(int energy, int hygiene) { }
    private Labor laborOf(Intent intent) {
        return switch (intent) {
            // Heavy, dirty labour — swinging, hauling, breaking ground, butchering, building, fighting.
            case FELL_TREE, GATHER_MINERAL, GATHER_STONE, GATHER_STONE_SLAB, GATHER_CLAY, HARVEST_CARCASS,
                 CONFRONT_WILDLIFE, PROCESS_MATERIAL, ADVANCE_ASSEMBLY, BUILD_FIRE_PIT, BUILD_FENCE, BUILD_LOOKOUT, BUILD_FUEL_RACK, START_LEAN_TO,
                 WORK_LEAN_TO, REPAIR_LEAN_TO, REPAIR_STRUCTURE, DISMANTLE -> new Labor(12, 8);
            // Steady work — foraging, fishing, trapping, tending, and the bench crafts.
            case GATHER_FIBER, GATHER_BRANCHES, GATHER_BERRIES, GATHER_PLANT, FORAGE_GROUND, RAID_HIVE, COLLECT_INSECTS,
                 FISH, SNARE, SET_TRAP, CHECK_TRAP, TRACK, TAME, LURE, STRIP_BARK, MAKE_CHARCOAL, LIGHT_FIRE,
                 COOK_MEAT, MOVE, TRAVEL, REFINE, REWORK, CRAFT_BASKET, CRAFT_SPEAR, CRAFT_KNIFE, CRAFT_HAMMER,
                 CRAFT_PICKAXE, CRAFT_HATCHET, CRAFT_FIRE_KIT, CRAFT_TINDER, CRAFT_GARMENT, CRAFT_FIRE_TOOL,
                 CRAFT_DESK, CRAFT_CHAIR, CRAFT_SHELF, CRAFT_WORKSTATION, CRAFT_NET, CRAFT_BELT, REPAIR_ITEM,
                 MAKE_BED, MAINTAIN_CAMP, PLACE_WINDBREAK, PLACE_COVER, BUILD_ALARM -> new Labor(6, 3);
            // Light acts — looking, marking, writing, handling gear, dressing a wound, tending a fire.
            case OBSERVE, SCOUT, MARK, DESIGNATE, EQUIP, UNEQUIP, DROP, PICK_UP, STORE, OPEN_CONTAINER, CLOSE_CONTAINER, COLLECT_WATER, BOIL_WATER, FILTER_WATER, WRITE, EDIT_DOCUMENT, SKETCH_MAP, INSPECT,
                 EXAMINE, ANALYZE, INVESTIGATE, SEARCH, LISTEN, SMELL, FEEL, READ, MEASURE, TREAT_WOUND, FEED_FIRE, EXTINGUISH_FIRE, BANK_FIRE -> new Labor(2, 0);
            // Rest, sleep, eat, drink, wash, relief, personal/aggression acts run their own physiology.
            default -> new Labor(0, 0);
        };
    }
    /**
     * Fresh water the Chronicle can reach here — a wetland, a river bank, or a freshwater spring/stream site
     * at this chunk (#32: bathing/drinking used to succeed only in WETLAND, so a stream or river bank failed).
     * The ocean is salt and does not count.
     */
    private boolean waterInReach(UUID location) {
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
        if ("WETLAND".equals(biome) || "RIVER_BANK".equals(biome)) return true;
        Integer sites = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ecology_site WHERE chunk_id=? AND (site_kind ILIKE '%spring%' OR site_kind ILIKE '%stream%' OR site_kind ILIKE '%river%' OR site_kind ILIKE '%freshwater%')",
            Integer.class, location);
        return sites != null && sites > 0;
    }
    /** Whether raw water here is safe to drink untreated (#71): moving water — a river bank, spring, or stream —
     *  is clean; standing water (a wetland) is not, and drinking it raw carries a gut-illness risk. */
    /** Deep night, when there is no working light to see fine detail by (#75): before dawn or after dusk. */
    private static boolean isDark(java.time.Instant at) {
        int h = at.atZone(java.time.ZoneOffset.UTC).getHour();
        return h < 6 || h >= 20;
    }
    /** Intents that are fine, close, sight-dependent work — impossible in the dark without a light (#75). */
    private static boolean isSightWork(Intent intent) {
        return switch (intent) {
            case WRITE, EDIT_DOCUMENT, READ, EXAMINE, ANALYZE, INVESTIGATE, MEASURE, SKETCH_MAP -> true;
            default -> false;
        };
    }
    private boolean safeWaterSource(UUID location) {
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
        if ("RIVER_BANK".equals(biome)) return true;
        Integer moving = jdbc.queryForObject("SELECT COUNT(*) FROM ecology_site WHERE chunk_id=? AND (site_kind ILIKE '%spring%' OR site_kind ILIKE '%stream%' OR site_kind ILIKE '%river%' OR site_kind ILIKE '%freshwater%')", Integer.class, location);
        return moving != null && moving > 0;
    }
    /** A fire burning within reach here — for warming and drying (#66). */
    private boolean fireInReach(UUID location) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM fire_state fs JOIN world_object w ON w.id=fs.construction_id WHERE w.current_location_id=? AND fs.active=true)", Boolean.class, location));
    }
    /** A completed, standing shelter here — for getting out of the weather (#66). Any roofed,
     * enclosing form counts (#61): a lean-to, or the huts whose walls and roof actually keep weather off. */
    private boolean shelterInReach(UUID location) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind IN ('LEAN_TO','WATTLE_AND_DAUB_HUT','EARTH_SHELTERED_HUT','LOG_CABIN') AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND w.lifecycle_state='ACTIVE')", Boolean.class, location));
    }
    /**
     * The effort/skill yield bonus for a gather (#68): 0–2 extra units where the source holds them. A careful,
     * thorough, take-it-all wording (Layer 2) plus the LOAD mastery (Layer 3) win a little more; a bare command
     * from an unpractised hand gets the biome baseline. Capacity and source depletion still cap the actual take.
     */
    private int gatherBonus(String text, UUID chronicle) {
        double spec = SuccessModel.specificity(text, GATHER_SIGNALS);
        double fam = capability.familiarity(chronicle, "LOAD");
        int bonus = (int) Math.round(spec * 1.5 + Math.min(0.5, fam * 5.0));
        return Math.max(0, Math.min(2, bonus));
    }
    private String attentionLevel(String text, Intent intent) {
        if (intent == Intent.OBSERVE) return "HIGH";
        String v = text.toLowerCase(Locale.ROOT);
        for (String cue : ATTENTION_CUES) if (v.contains(cue)) return "HIGH";
        if (intent == Intent.MOVE || intent == Intent.TRAVEL) return "MODERATE";
        return "LOW";
    }
    /**
     * What the tick and this action together changed in the body since the previous
     * frame, aspect by aspect. Only genuine transitions are reported — hunger sliding
     * from Hungry to Starving, energy climbing from Fatigued to Rested. The frame
     * carries these for a Simulation Agent to weave into sensory narration; the
     * qualitative Body HUD, not the prose, remains the authoritative physiology display.
     */
    private List<StateChange> physiologyDelta(ChroniclePhysiologyService.BodyHudSnapshot a, ChroniclePhysiologyService.BodyHudSnapshot b) {
        List<StateChange> d = new java.util.ArrayList<>();
        if (a == null || b == null) return d;
        addChange(d, "health", a.health(), b.health());
        addChange(d, "condition", a.condition(), b.condition());
        addChange(d, "hunger", a.hunger(), b.hunger());
        addChange(d, "thirst", a.thirst(), b.thirst());
        addChange(d, "energy", a.energy(), b.energy());
        addChange(d, "temperature", a.temperature(), b.temperature());
        addChange(d, "wetness", a.wetness(), b.wetness());
        addChange(d, "bladder", a.bladder(), b.bladder());
        addChange(d, "bowel", a.bowel(), b.bowel());
        addChange(d, "hygiene", a.hygiene(), b.hygiene());
        return d;
    }
    private void addChange(List<StateChange> d, String aspect, String from, String to) { if (from != null && !from.equals(to)) d.add(new StateChange(aspect, from, to)); }
    /**
     * Whether an act leaves the chronicle open to being reached by something hunting
     * this ground. Sustained outdoor work with the hands and eyes occupied does;
     * a moment spent equipping, dropping, or naming a place does not, and an act
     * that was already a wildlife encounter is not doubled.
     */
    /**
     * The multiplier wet weather puts on an ignition attempt (#127): rain or storm quarters the odds, but dry
     * kindling off a covered fuel rack at the chunk eases that to a light penalty — the surroundings are still
     * wet, but you start from something that will catch. Fair weather, or a method that needs no dry, leaves the
     * odds untouched. Package-private so the fuel-rack regression can assert the exact multiplier deterministically.
     */
    double wetFireOdds(UUID location, boolean requiresDry, String weather) {
        if (!requiresDry || weather == null || !(weather.equals("RAIN") || weather.equals("STORM"))) return 1.0;
        boolean dryFuel = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "WHERE w.current_location_id=? AND cp.project_kind='FUEL_RACK' AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND w.lifecycle_state='ACTIVE')",
            Boolean.class, location));
        return dryFuel ? 0.7 : 0.25;
    }
    private boolean exposesToWildlife(Intent intent) {
        return switch (intent) {
            case EQUIP, UNEQUIP, DROP, DESIGNATE, MARK, REFINE, EDIT_DOCUMENT, INSPECT,
                 EXAMINE, ANALYZE, INVESTIGATE, SCOUT,
                 CONFRONT_WILDLIFE, HARVEST_CARCASS, PERSONAL_ACT, AGGRESSION_WILDLIFE,
                 AGGRESSION_INANIMATE, UNKNOWN -> false;
            default -> true;
        };
    }
    /** Route an insect colony's hazard to the body: stings and bites wound, venom sickens. */
    private void applyInsectHazard(UUID chronicle, PhysicalItemService.InsectHarvest r, UUID actionId, Instant at) {
        if (r.hazardSeverity() <= 0 || r.hazardKind() == null) return;
        String source = r.hazardKind().toLowerCase(Locale.ROOT);
        if ("STING".equals(r.hazardKind()) || "BITE".equals(r.hazardKind())) physiology.applyInjury(chronicle, r.hazardSeverity(), actionId, at, source);
        else physiology.applyIllness(chronicle, r.hazardSeverity(), actionId, at, source);
    }
    /** A terse, machine-facing hour label for the frame, distinct from the prose the survey narrates. */
    private String timeOfDayLabel(Instant at) {
        int h = at.atZone(java.time.ZoneOffset.UTC).getHour();
        if (h < 5) return "NIGHT";
        if (h < 8) return "DAWN";
        if (h < 12) return "MORNING";
        if (h < 15) return "MIDDAY";
        if (h < 19) return "AFTERNOON";
        if (h < 22) return "DUSK";
        return "NIGHT";
    }
    public record ActionResult(UUID actionId, String intent, String outcome, int durationMinutes, Instant resolvedAt, String perception, ChroniclePhysiologyService.BodyHudSnapshot body, PerceptionFrame frame, boolean died) { }

    /** A witnessed closing line for a chronicle that died this action, by cause. */
    private static String deathCoda(String cause) {
        String c = cause == null ? null : switch (cause) {
            case "Critical Dehydration" -> "thirst";
            case "Critical Starvation" -> "hunger";
            case "Critical Blood Loss" -> "blood loss";
            case "Fatal Trauma" -> "trauma";
            case "Severe Hypothermia" -> "cold";
            case "Severe Hyperthermia" -> "heat";
            default -> "sickness";
        };
        return (c == null ? " " : " The " + c + " takes the last of you. ") + "Your chronicle's journey ends here.";
    }
    public record PerceptionFrame(String intent, String outcome, LocationView location, String timeOfDay, WeatherView weather, String attention, List<String> nearbyObjects, ChroniclePhysiologyService.BodyHudSnapshot physiology, List<StateChange> sinceLastFrame, String narration) { }
    public record LocationView(UUID chunkId, String name, String biome, int gridX, int gridY) { }
    public record WeatherView(String kind, int intensity) { }
    /** A single qualitative transition between the previous frame and this one — e.g. hunger "Hungry" → "Starving". */
    public record StateChange(String aspect, String from, String to) { }
    public record NarrationEntry(UUID id, Instant occurredAt, String narration) { }
    public record NarrationPage(List<NarrationEntry> entries, boolean hasMore) { }
}
