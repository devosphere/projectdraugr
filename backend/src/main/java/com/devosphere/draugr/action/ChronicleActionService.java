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
import java.util.Map;
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
        "You try, and the effort goes into the air. The ground and everything on it is exactly as it was.",
        "You begin, and get as far as beginning. Nothing within reach takes the shape of what you were after.",
        "You spend the effort and come back with only the effort. Nothing here has moved for it.",
        "It does not come to anything. You stand a moment with the intention still on you and nothing to put it into."};
    // A recognised piece of material/world work the world cannot yet resolve (#68): a grounded "no way comes to
    // you" that names the material effort, rather than the flat gibberish line above. The routing miss is still
    // recorded (inside runProcess) so the gap is on the backlog for review.
    private static final String[] MATERIAL_UNRESOLVED = {
        "You work the material over, turning it for a way in, but no method for what you meant comes to your hands here.",
        "You set to it in earnest, but the working of it into that is beyond what your hands and knowledge can find on this ground.",
        "You handle and test it, feeling for the trick of it, but the way to make what you intend does not come to you yet.",
        "You turn it over and try it two or three ways. Each one stops at the same place, and you set it down again.",
        "The stuff of it is willing enough; it is the working that will not come. You leave it as you found it.",
        "You get your hands properly into it and still cannot find the step that would take it further."};
    private final JdbcTemplate jdbc; private final SimulationTickService ticks; private final ChroniclePhysiologyService physiology; private final NarrationPolicy narration; private final PhysicalItemService items; private final CapabilityAdaptationService capability; private final ConstructionService construction; private final ChronicleDiscoveryService discoveries; private final WildlifeEncounterService wildlife; private final FireService fire; private final LiteratureService literature; private final FoodPreservationService food; private final ActionInputClassifier inputClassifier; private final AssemblyService assembly; private final NarrationRouter narrationRouter; private final SimulationNarrator simulationNarrator; private final com.devosphere.draugr.narration.NarrationEngine narrationEngine; private final com.devosphere.draugr.ai.RuntimeAuthoringService authoring; private final ExaminationService examination;
    private final com.devosphere.draugr.people.ContactService contact;
    private final com.devosphere.draugr.people.TradeService trade;
    private final com.devosphere.draugr.people.ConductService conduct;
    private final com.devosphere.draugr.people.AgreementService agreement;
    private final com.devosphere.draugr.people.CompanionService companions;
    private final com.devosphere.draugr.people.AudienceService audience;
    private final com.devosphere.draugr.people.MembershipService membership;
    private final com.devosphere.draugr.people.ClaimService claims;
    public ChronicleActionService(JdbcTemplate jdbc, SimulationTickService ticks, ChroniclePhysiologyService physiology, NarrationPolicy narration, PhysicalItemService items, CapabilityAdaptationService capability, ConstructionService construction, ChronicleDiscoveryService discoveries, WildlifeEncounterService wildlife, FireService fire, LiteratureService literature, FoodPreservationService food, ActionInputClassifier inputClassifier, AssemblyService assembly, NarrationRouter narrationRouter, SimulationNarrator simulationNarrator, com.devosphere.draugr.narration.NarrationEngine narrationEngine, com.devosphere.draugr.ai.RuntimeAuthoringService authoring, ExaminationService examination, com.devosphere.draugr.people.ContactService contact, com.devosphere.draugr.people.TradeService trade, com.devosphere.draugr.people.ConductService conduct, com.devosphere.draugr.people.AgreementService agreement, com.devosphere.draugr.people.CompanionService companions, com.devosphere.draugr.people.AudienceService audience, com.devosphere.draugr.people.MembershipService membership, com.devosphere.draugr.people.ClaimService claims) { this.claims = claims; this.membership = membership; this.audience = audience; this.companions = companions; this.agreement = agreement; this.conduct = conduct; this.trade = trade; this.contact = contact; this.jdbc = jdbc; this.ticks = ticks; this.physiology = physiology; this.narration = narration; this.items=items; this.capability=capability; this.construction=construction; this.discoveries=discoveries; this.wildlife=wildlife; this.fire=fire; this.literature=literature; this.food=food; this.inputClassifier=inputClassifier; this.assembly=assembly; this.narrationRouter=narrationRouter; this.simulationNarrator=simulationNarrator; this.narrationEngine=narrationEngine; this.authoring=authoring; this.examination=examination; }

    @Transactional
    public ActionResult resolve(String text) { return resolve(text, null); }

    /**
     * Work through a written procedure step by step (#38).
     *
     * <p>The action composer takes 2,500 characters and the resolver matched exactly one process to the whole of
     * it. A Chronicle who wrote "build a hearth board, then carve a spindle, then form a tinder nest, then spin
     * the bow drill until it catches" had one of those four steps happen and the other three silently discarded.
     * The player typed a lot for nothing, which is the failure #38 names.
     *
     * <p>Each declared step is now resolved in order, in this one transaction, and the world is told about every
     * one of them. The rules that keep this safe:
     * <ul>
     *   <li><b>Order is the player's.</b> Steps run as written; nothing is reordered and nothing is invented.</li>
     *   <li><b>A step that fails stops the plan.</b> The work already done stands — it really happened — and the
     *       report says plainly where it stopped and what was not attempted. Later steps are never quietly
     *       skipped, and never completed on the strength of a step that failed.</li>
     *   <li><b>Each step is a real action</b> with its own time, labour and history row, because that is what it
     *       costs. A plan is a way of writing four actions at once, not a way of getting them cheaply.</li>
     *   <li><b>A single-step text is untouched</b> — it goes straight down the old path.</li>
     * </ul>
     *
     * <p>Replay is safe: each step derives its own idempotency key from the plan's, so resubmitting a whole
     * procedure replays step for step instead of doing the work twice.
     *
     * <p><b>What stops is set aside, not lost.</b> When a plan stops at a failed step or at the step limit, the
     * steps not yet done are kept in {@code chronicle_plan_remainder}, in the player's order and words, and a
     * bare "carry on" takes them up once the player has put right what stopped them. Without that, the undone
     * steps existed only in the narration and had to be retyped from memory — the same wasted typing #38 was
     * opened for, moved one turn later.
     */
    @Transactional
    public ActionResult resolvePlan(String text, UUID idempotencyKey) {
        if (text != null && ActionPlan.isResumeRequest(text)) {
            ActionResult resumed = takeUpWhatWasSetAside(idempotencyKey);
            // With nothing set aside, "carry on" reaches the world exactly as it always did.
            if (resumed != null) return resumed;
        }
        List<String> steps = ActionPlan.steps(text);
        if (steps.size() <= 1) return resolve(text, idempotencyKey);

        // A resubmitted procedure replays step for step; it must not also rewrite what was set aside.
        boolean replay = alreadyRecorded(stepKey(idempotencyKey, "step", 0));
        // Writing a new procedure is moving on from whatever was set aside before it.
        if (!replay) endOpenRemainder("REPLACED");
        return workThrough(steps, idempotencyKey, "step", replay, "");
    }

    /** The steps of a set-aside plan, worked again from where they stopped; null when nothing was set aside. */
    private ActionResult takeUpWhatWasSetAside(UUID idempotencyKey) {
        UUID chronicle = jdbc.query("SELECT id FROM chronicle WHERE life_state='LIVING'", rs -> rs.next() ? rs.getObject(1, UUID.class) : null);
        if (chronicle == null) return null;
        org.springframework.jdbc.core.RowMapper<java.util.Map.Entry<UUID, List<String>>> row = (rs, n) ->
            java.util.Map.entry(rs.getObject(1, UUID.class), List.of((String[]) rs.getArray(2).getArray()));
        // The same resume submitted twice is one resume: find the plan it already took up, and replay it.
        java.util.Map.Entry<UUID, List<String>> plan = idempotencyKey == null ? null : jdbc.query(
            "SELECT id, steps FROM chronicle_plan_remainder WHERE resume_key = ?", row, idempotencyKey).stream().findFirst().orElse(null);
        boolean replay = plan != null;
        if (plan == null) plan = jdbc.query(
            "SELECT id, steps FROM chronicle_plan_remainder WHERE chronicle_id = ? AND state = 'OPEN'", row, chronicle).stream().findFirst().orElse(null);
        if (plan == null) return null;
        if (!replay) jdbc.update("UPDATE chronicle_plan_remainder SET state='RESUMED', resume_key=?, resumed_at=? WHERE id=?",
                idempotencyKey, java.sql.Timestamp.from(ticks.current().simulatedAt()), plan.getKey());
        return workThrough(plan.getValue(), idempotencyKey, "resume", replay, "You take up what you set aside.");
    }

    /** Work the steps in order, stopping where one fails; keep what was not done unless this is a replay. */
    private ActionResult workThrough(List<String> steps, UUID idempotencyKey, String tag, boolean replay, String opening) {
        List<String> attempted = steps.size() > ActionPlan.MAX_STEPS ? steps.subList(0, ActionPlan.MAX_STEPS) : steps;
        StringBuilder told = new StringBuilder(opening);
        ActionResult last = null;
        int done = 0;
        String outcome = "SUCCEEDED";
        List<String> undone = List.of();
        String stoppedBecause = null;
        for (int i = 0; i < attempted.size(); i++) {
            ActionResult step = resolve(attempted.get(i), stepKey(idempotencyKey, tag, i));
            last = step;
            if (told.length() > 0) told.append(" ");
            told.append(step.perception());
            if ("SUCCEEDED".equals(step.outcome()) || "NO_EFFECT".equals(step.outcome())) {
                done++;
                // A step can end the life that was going to carry out the rest. There is no going on from here,
                // and the next resolve would find no living Chronicle at all.
                if (step.died()) { if (done < attempted.size()) outcome = "PARTIAL"; break; }
                continue;
            }
            // Stopped. Say so, and say what was left — never let the report imply the rest happened.
            outcome = done > 0 ? "PARTIAL" : "FAILED";
            int remaining = steps.size() - (i + 1);
            told.append(" You get no further than that. ");
            told.append(done == 0
                ? "The rest of what you set out to do is still ahead of you"
                : "What you did before this stands, but the " + remaining + " step" + (remaining == 1 ? "" : "s")
                  + " after it went undone");
            told.append(", and until this part comes right there is no going on to them.");
            // The failed step is kept with the rest: it is the part that has to come right first.
            undone = steps.subList(i, steps.size());
            stoppedBecause = "FAILED_STEP";
            break;
        }
        if ("SUCCEEDED".equals(outcome) && steps.size() > ActionPlan.MAX_STEPS) {
            outcome = "PARTIAL";
            told.append(" You have worked as far through that as one stretch of effort will carry, and set the rest aside for now.");
            undone = steps.subList(ActionPlan.MAX_STEPS, steps.size());
            stoppedBecause = "STEP_LIMIT";
        }
        if (!replay) {
            if (last.died()) endOpenRemainder("ENDED");
            else if (!undone.isEmpty()) setAside(undone, stoppedBecause, last, idempotencyKey, tag);
        }
        // The plan reports as the last step that actually ran: its identity, its clock, and the body as it stands.
        return new ActionResult(last.actionId(), last.intent(), outcome, last.durationMinutes(), last.resolvedAt(),
                told.toString(), last.body(), last.frame(), last.died());
    }

    private static UUID stepKey(UUID idempotencyKey, String tag, int i) {
        return idempotencyKey == null ? null
            : UUID.nameUUIDFromBytes((idempotencyKey + ":" + tag + ":" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private boolean alreadyRecorded(UUID key) {
        return key != null && Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM chronicle_action WHERE idempotency_key = ?)", Boolean.class, key));
    }

    private void endOpenRemainder(String state) {
        jdbc.update("UPDATE chronicle_plan_remainder SET state = ? WHERE state = 'OPEN' " +
                    "AND chronicle_id IN (SELECT id FROM chronicle WHERE life_state = 'LIVING')", state);
    }

    private void setAside(List<String> undone, String stoppedBecause, ActionResult last, UUID idempotencyKey, String tag) {
        UUID chronicle = jdbc.query("SELECT id FROM chronicle WHERE life_state='LIVING'", rs -> rs.next() ? rs.getObject(1, UUID.class) : null);
        if (chronicle == null) return;
        UUID id = idempotencyKey == null ? UUID.randomUUID()
            : UUID.nameUUIDFromBytes((idempotencyKey + ":" + tag + ":remainder").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        jdbc.update(con -> {
            java.sql.PreparedStatement ps = con.prepareStatement(
                "INSERT INTO chronicle_plan_remainder (id, chronicle_id, steps, stopped_because, set_aside_at, source_action_id) " +
                "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING");
            ps.setObject(1, id);
            ps.setObject(2, chronicle);
            ps.setArray(3, con.createArrayOf("text", undone.toArray()));
            ps.setString(4, stoppedBecause);
            ps.setTimestamp(5, java.sql.Timestamp.from(last.resolvedAt()));
            ps.setObject(6, last.actionId());
            return ps;
        });
    }

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
        // Meeting a people (#112). An act of contact is recognised only when a people's isle is within sight of
        // this ground, so no phrasing is taken from anything else anywhere in the world — and prose cannot make a
        // wolf into someone to parley with, because the community must be a PEOPLE (V344) to be in reach at all.
        // Moving, travelling and fighting keep their meaning even beside an isle.
        com.devosphere.draugr.people.ContactService.Act contactAct = null; UUID contactWith = null;
        // Whether the words were MEANT for people who are not there (#37). Every gate below recognises the act
        // first and then looks for a community in reach; when there is none it quietly drops the act and lets the
        // phrase fall through to UNKNOWN, where a Chronicle who said "greet the strangers" was answered with
        // "It does not come to anything. You stand a moment with the intention still on you and nothing to put it
        // into" — a crafting miss, in reply to speech. The game heard them perfectly well; it just had nobody
        // to carry it to, and would not say so.
        boolean meantForPeople = false;
        com.devosphere.draugr.people.TradeService.Act tradeAct = null;
        com.devosphere.draugr.people.AgreementService.Act agreementAct = null;
        com.devosphere.draugr.people.AudienceService.Act audienceAct = null;
        com.devosphere.draugr.people.MembershipService.Act membershipAct = null;
        com.devosphere.draugr.people.ClaimService.Act claimAct = null;
        // What a people remembers (#114). Theft, threats, harm and seizure are recognised before anything else and
        // even over the aggression pre-pass: striking a person on their isle is not a fight with an animal, and is
        // not left to the wildlife encounter to resolve. Only within sight of an isle, as with contact.
        com.devosphere.draugr.people.ConductService.Act conductAct = null;
        // HOW THEY REGARD YOU (#114/#37), recognised BEFORE the conduct pre-pass below, so that a question about
        // the relationship can never be carried out as an act upon it. Swept on a real isle, six of six of these
        // reached nothing — over community_relation, which carries the standing, the last thing that moved it,
        // an obligation in plain words, how much of each other's speech you have worked out and the date of
        // first contact, and over native_event, which keeps everything either of you has done.
        if (conduct != null) {
            String askedAboutStanding = text.toLowerCase(Locale.ROOT);
            if (askedAboutStanding.contains("how do they regard")||askedAboutStanding.contains("do they trust")
                ||askedAboutStanding.contains("am i welcome")||askedAboutStanding.contains("what do they think of me")
                ||askedAboutStanding.contains("have i wronged")||askedAboutStanding.contains("what do they remember")
                ||askedAboutStanding.contains("how do i stand")||askedAboutStanding.contains("where do i stand")
                ||askedAboutStanding.contains("do they like me")||askedAboutStanding.contains("do they hate me")
                ||askedAboutStanding.contains("what do i owe them")||askedAboutStanding.contains("do i owe them")
                ||askedAboutStanding.contains("are we on good terms")||askedAboutStanding.contains("how are things with them"))
                intent = Intent.CHECK_STANDING;
        }
        if (conduct != null && intent != Intent.MOVE && intent != Intent.TRAVEL && intent != Intent.CHECK_STANDING) {
            // recogniseHere, not the static recognise (#106): an offence whose phrase names no object — "tie up
            // the", "seize the", "drag the" — must be told who it is about, from the people vocabulary or from the
            // names the world gave this community. Without that, tying up a BUNDLE was an assault on a person.
            conductAct = conduct.recogniseHere(text);
            if (conductAct != null) { contactWith = contact.communityInReach(chronicle.location()); if (contactWith != null) intent = Intent.CONDUCT_TOWARD_PEOPLE; else { conductAct = null; meantForPeople = true; } }
        }
        // Travelling together (#113). Asking is done on the isle, face to face; parting can be done anywhere the two
        // of you stand. Recognised even over MOVE/TRAVEL, because "travel with me" is a question, not a journey.
        com.devosphere.draugr.people.CompanionService.Act companionAct = null;
        if (companions != null && conductAct == null) {
            companionAct = com.devosphere.draugr.people.CompanionService.recognise(text);
            if (companionAct == com.devosphere.draugr.people.CompanionService.Act.END_COMPANIONSHIP && companions.companionOf(chronicle.id()) != null) intent = Intent.COMPANION_PEOPLE;
            else if (companionAct == com.devosphere.draugr.people.CompanionService.Act.ASK_TO_TRAVEL_TOGETHER
                     && (contactWith = contact.communityInReach(chronicle.location())) != null) intent = Intent.COMPANION_PEOPLE;
            else companionAct = null;
        }
        if (contact != null && conductAct == null && companionAct == null && intent != Intent.MOVE && intent != Intent.TRAVEL && intent != Intent.AGGRESSION_WILDLIFE && intent != Intent.CONFRONT_WILDLIFE) {
            // What they ask in return (#211): the claim standing between you and them.
            claimAct = claims == null ? null : com.devosphere.draugr.people.ClaimService.recognise(text);
            // A place among them (#113): asking for one, drawing a share, giving it up.
            membershipAct = membership == null || claimAct != null ? null : com.devosphere.draugr.people.MembershipService.recognise(text);
            // Who speaks for them and who does what (#121): asked of the people, not of their goods.
            audienceAct = audience == null || membershipAct != null || claimAct != null ? null : com.devosphere.draugr.people.AudienceService.recognise(text);
            // Work first (#113): "offer to work for them for two fish" is an agreement, not an exchange of goods.
            agreementAct = agreement == null || audienceAct != null || membershipAct != null || claimAct != null ? null : com.devosphere.draugr.people.AgreementService.recognise(text);
            // Trade next (#113): "offer them my knife for two mats" is an exchange, not a gift, and names more.
            tradeAct = agreementAct != null || audienceAct != null || membershipAct != null || claimAct != null ? null : com.devosphere.draugr.people.TradeService.recognise(text);
            contactAct = tradeAct == null && agreementAct == null && audienceAct == null && membershipAct == null && claimAct == null ? com.devosphere.draugr.people.ContactService.recognise(text) : null;
            if (claimAct != null || membershipAct != null || audienceAct != null || agreementAct != null || tradeAct != null || contactAct != null) contactWith = contact.communityInReach(chronicle.location());
            if (contactWith == null) {
                meantForPeople = meantForPeople || claimAct != null || membershipAct != null || audienceAct != null
                    || agreementAct != null || tradeAct != null || contactAct != null;
                claimAct = null; membershipAct = null; audienceAct = null; agreementAct = null; tradeAct = null; contactAct = null;
            }
            else intent = claimAct != null ? Intent.SETTLE_CLAIM
                : membershipAct != null ? Intent.JOIN_PEOPLE
                : audienceAct != null ? Intent.ADDRESS_PEOPLE
                : agreementAct == com.devosphere.draugr.people.AgreementService.Act.DO_WORK ? Intent.WORK_FOR_PEOPLE
                : agreementAct != null ? Intent.AGREE_WITH_PEOPLE : tradeAct != null ? Intent.TRADE_WITH_PEOPLE : Intent.CONTACT_PEOPLE;
        }
        // Travel time is resolved before the tick advances, because it scales with
        // the distance to a place the chronicle can actually find its way to.
        TravelPlan travel = intent == Intent.TRAVEL ? planTravel(chronicle, text) : null;
        // "go to the Tool Shed" names a zone in the CURRENT settlement — a short walk within the chunk, not an
        // inter-chunk journey (V70/F8). Reachability is unaffected (it's chunk-wide either way).
        String localZone = intent == Intent.TRAVEL ? matchLocalZone(chronicle, text) : null;
        // Riding (#108/#106/#100) is not a state with a mount and a dismount — it is HOW YOU TRAVEL. A Chronicle
        // setting out with a tamed rideable beast, a harness to guide it by, and a way up if they are laden goes
        // at better than twice walking pace. One who does not qualify walks, and is told nothing about it,
        // because walking is not a failure. Asked at the ORIGIN, which is where a rider mounts.
        UUID mount = (intent == Intent.TRAVEL && localZone == null && travel != null)
            ? items.beastToRide(chronicle.id(), chronicle.location()) : null;
        // What the journey costs is the country it crosses (#77/#155, V335): the going of the ground on the line
        // between here and there, rather than a flat eighteen minutes a chunk over meadow, fen and mountainside
        // alike. A ridden beast keeps the same share of the walking time it always did, so hard country is hard
        // for a rider too — it is faster, not level.
        int minutes = localZone != null ? 5
            : (intent == Intent.TRAVEL
                ? (travel == null ? 20
                    : mount != null ? Math.max(10, (int) Math.round(travel.distance() * travel.minutesPerChunk()
                                                                    * (RIDDEN_MINUTES_PER_DISTANCE / (double) FLAT_MINUTES_PER_DISTANCE)))
                                    : Math.max(15, travel.distance() * travel.minutesPerChunk()))
                : durationFor(text, intent));
        // A completed tool shed at the settlement keeps tools and made stock to hand and out of the weather, so a
        // Chronicle no longer opens each fabrication or repair by hunting for what they need — the setting-up is
        // shorter (#207 heritage TOOL_SHED, "reduces preparation time"). A fair no-op when no shed stands here.
        if (localZone == null && intent != Intent.TRAVEL && speededByToolShed(intent) && hasToolShed(chronicle.location())) {
            minutes = Math.max(5, (int) Math.round(minutes * 0.85));
        }
        // A steady desk standing on this ground — the wooden_desk a Chronicle can build, "a broad, steady work
        // surface" — is the documentation workstation the world's heritage names (KNOWLEDGE_STATION): putting marks
        // to a page goes quicker with a proper surface to work at than balancing the page on your knee. It also
        // gives the built desk something to do — it read against nothing before. Fair no-op when none stands here.
        if (localZone == null && easedByDesk(intent) && hasWritingSurface(chronicle.location())) {
            minutes = Math.max(5, (int) Math.round(minutes * 0.85));
        }
        // A utility belt worn at the waist keeps the tools a Chronicle reaches for most in a row of loops at hand,
        // so each craft or repair opens without hunting the pack for them — the worn, portable cousin of the tool
        // shed's setting-up cut (#57/#207). Closes the utility_belt dead-read: its making has always claimed
        // "consistent placement cuts preparation time", but nothing read it. Stacks modestly with a shed; a fair
        // no-op when none is worn.
        if (localZone == null && intent != Intent.TRAVEL && speededByToolShed(intent) && wearsUtilityBelt(chronicle.id())) {
            minutes = Math.max(5, (int) Math.round(minutes * 0.92));
        }
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
        if (isSightWork(intent, text) && tooDarkForFineWork(chronicle.location(), resolvedAt) && !fireInReach(chronicle.location()) && !items.consumePortableLight(chronicle.id(), resolvedAt)) {
            outcome = "FAILED";
            // Say which of the two it was. A Chronicle standing in a gale with a pouch full of candles is not
            // short of light — the weather is taking it — and being told so is what points at the lantern cover.
            //
            // And say so when it is the ROCK and not the hour. Being told "it is too dark" at midday would read
            // as a fault in the world rather than a fact about where the Chronicle is standing.
            perception = items.weatherWouldGutterALight(chronicle.id())
                ? "It is too dark to see the fine of it, and the weather takes any flame you try to strike before it "
                + "has caught. Without a fire in reach or a light you can hood against this, the work must wait."
                : !isDark(resolvedAt)
                ? "The daylight gets a few paces into the rock and no further, and you are past it. Close work here "
                + "wants a fire or a light in your hand, whatever the hour outside."
                : "It is too dark to see the fine of it. With no fire and no light to work by, this is not something your hands can do by feel alone.";
        }
        else if (intent == Intent.CONDUCT_TOWARD_PEOPLE) { String[] r = conduct.act(chronicle.id(), chronicle.location(), contactWith, conductAct, text, resolvedAt, contact.armed(chronicle.id()), actionId); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.SETTLE_CLAIM) { String[] r = claims.act(chronicle.id(), contactWith, claimAct, resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.JOIN_PEOPLE) { String[] r = membership.act(chronicle.id(), chronicle.location(), contactWith, membershipAct, resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.ADDRESS_PEOPLE) { String[] r = audience.act(chronicle.id(), contactWith, audienceAct, resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.COMPANION_PEOPLE) { String[] r = companions.act(chronicle.id(), chronicle.location(), contactWith, companionAct, text, resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.AGREE_WITH_PEOPLE || intent == Intent.WORK_FOR_PEOPLE) { String[] r = agreement.act(chronicle.id(), chronicle.location(), contactWith, agreementAct, text, resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.TRADE_WITH_PEOPLE) { String[] r = trade.act(chronicle.id(), contactWith, tradeAct, text, resolvedAt, contact.armed(chronicle.id())); outcome = r[0]; perception = r[1]; }
        // Said to people who are not there (#37). Not a failure to understand — the act was recognised and
        // there was simply no one to receive it — so the refusal says that, and says what would answer it.
        else if (intent == Intent.UNKNOWN && meantForPeople) {
            outcome = "FAILED";
            perception = "You put that to people, and there are none within reach of this ground — no isle on it "
                + "or on any beside it, and nobody within hail. The words go out over empty country.";
        }
        else if (intent == Intent.CONTACT_PEOPLE) { String[] r = contact.act(chronicle.id(), chronicle.location(), contactWith, contactAct, text, resolvedAt, actionId); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.OBSERVE) {
            // Said plainly, this names a thing to climb (#37). It reached the ordinary ground-level survey and
            // reported SUCCEEDED on open grass running flat to every horizon — the Chronicle was told what they
            // saw from up a tree that is not there. Where one does stand, the act is real and the survey is what
            // they see from it.
            String climbing = climbsSomething(text) ? (standingTreesHere(chronicle.location())
                ? "You get a boot into the crook of a trunk and haul yourself up until the branches thin. "
                : null) : "";
            if (climbing == null) {
                outcome = "FAILED";
                perception = "You look for something to climb and there is nothing here that will take your weight — "
                    + "no trunk, no standing timber, nothing above the height of your own head.";
            } else perception = climbing + survey(chronicle, resolvedAt);
        }
        else if (intent == Intent.MOVE) {
            // A haul tired the team in silence, which made draft gear that does not fit the animal
            // indistinguishable from gear that does (#106). This is the commoner of the two hauling boundaries:
            // "travel north" is a MOVE, so a keeper who never says the word "journey" arrives here every time.
            perception = move(chronicle, text, actionId, resolvedAt);
            String hauledOn = items.workDraftBeasts(chronicle.id());
            if (!hauledOn.isEmpty()) perception = perception + hauledOn;
            // Onto a people's isle (#114): uninvited is trespass, and a people who hate you do not wait to be spoken to.
            if (conduct != null) {
                UUID arrived = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle.id());
                String met = arrived == null || arrived.equals(chronicle.location()) ? null : conduct.onArrival(chronicle.id(), arrived, resolvedAt, actionId);
                if (met != null) perception = perception + " " + met;
            }
            perception = withCompanion(chronicle.id(), perception, resolvedAt);
        }
        else if (intent == Intent.TRAVEL) {
            if (localZone != null) { jdbc.update("UPDATE chronicle SET current_zone=? WHERE id=?", localZone, chronicle.id()); perception = "You cross the settlement to " + localZone + ", a short walk over ground you know by heart."; }
            else {
                String[] r = travelTo(chronicle, travel, resolvedAt); outcome = r[0]; perception = r[1];
                // A haul used to tire the team in silence, which made draft gear that does not fit the animal
                // indistinguishable from gear that does (#106). Only on a journey that happened.
                String hauled = items.workDraftBeasts(chronicle.id());
                if ("SUCCEEDED".equals(outcome) && !hauled.isEmpty()) perception = perception + hauled;
                // The journey's cost is moved, not removed: a ridden beast takes it. Draft fatigue already gates
                // haulage, so a keeper who rides everywhere finds their draft team useless when they need it.
                if (mount != null && "SUCCEEDED".equals(outcome)) {
                    items.tireRiddenBeast(mount, travel.distance());
                    perception = perception.replace("You set out,", "You set out at a ride,");
                }
                perception = withCompanion(chronicle.id(), perception, resolvedAt);
            }
        }
        // Tending a sick beast (#106/#108). V299 gave stock sickness with two ways out — clean ground and time —
        // and both are things a keeper does to the GROUND. This is the one they do to the animal.
        else if (intent == Intent.GROOM_ANIMAL) { String[] r = items.groomAnimal(chronicle.id(), resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.TEND_ANIMAL) { String[] r = items.tendSickAnimal(chronicle.id(), resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.MARK) { String[] r = markLandmark(chronicle, text, actionId, resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.URINATE || intent == Intent.DEFECATE) {
            boolean bowel = intent == Intent.DEFECATE;
            physiology.applyRelief(chronicle.id(), bowel);
            UUID waste = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id, object_type, display_name, current_location_id) VALUES (?, 'WASTE', ?, ?)", waste, bowel ? "Human waste" : "Urine-soaked ground", chronicle.location());
            // The WASTE object above has existed since the beginning and nothing has ever read one, while
            // butchering, kept livestock and a monster's lair all foul the ground they are on (#77/#218). A pit
            // dug for the purpose takes it; on bare ground it stays where it is left.
            boolean pit = wildlife.somethingTakesReliefAt(chronicle.location());
            wildlife.recordRefuse(chronicle.location(), wildlife.reliefRefuse(chronicle.location(), bowel), resolvedAt);
            perception = "You take a brief moment away from the immediate ground around you."
                + (pit ? " The pit takes it, and covered over it goes no further than that."
                       : campIsGettingFoul(chronicle.location())
                         ? " The ground hereabouts is beginning to smell of use, and there is no pit dug to take any of it."
                         : "");
        } else if (intent == Intent.REST) {
            physiology.rest(chronicle.id(), minutes); items.restDraftBeasts(chronicle.id());
            // #30 — this line said "while the forest continues around you" wherever the Chronicle happened to be:
            // on open grassland, on a beach, on a mountainside, inside a cave. Narration must witness the world,
            // and a wood that is not there is the plainest way of failing that. The span IS known — the player may
            // have asked for two hours — and saying it is the difference between a pause and an afternoon.
            perception = "You remain still for " + humanSpan(minutes) + ", and the place goes on around you without you.";
            perception += bittenWhileStill(chronicle, resolvedAt, minutes);
        }
        else if (intent == Intent.SLEEP) {
            boolean safe = physiology.sleep(chronicle.id(), minutes); items.restDraftBeasts(chronicle.id());
            perception = safe
                ? "You lie down under cover and let sleep take you for " + humanSpan(minutes)
                  + ". You wake to a changed sky, the deep tiredness lifted from your limbs."
                : "You settle onto the bare ground and drift through " + humanSpan(minutes)
                  + " of broken, shallow sleep, waking stiff and only half-rested as the light shifts.";
            perception += bittenWhileStill(chronicle, resolvedAt, minutes);
            // Stock left loose overnight are what predators come for (#108). A sleep spans the dark hours, so this
            // is where an unpenned herd is thinned — and waking to a gap in the flock is how a keeper learns of it.
            String lost = wildlife.raidUnprotectedStock(chronicle.id(), resolvedAt, isDark(resolvedAt) || isDark(resolvedAt.plus(java.time.Duration.ofMinutes(minutes))));
            if (lost != null) perception += " The stock are uneasy in the grey light, and short: something came in the night and took one of them. There is blood on the ground where nothing stood between them and it.";
        }
        else if (intent == Intent.GATHER_FIBER) { int bundles=items.gatherPlantFiber(chronicle.id(),chronicle.location(),resolvedAt,gatherBonus(text,chronicle.id())); outcome=bundles>0?"SUCCEEDED":"FAILED"; perception=bundles>0?"You patiently separate usable plant fiber from the living growth around you."+tally(bundles,"bundle","bundles"):"You search through the growth, but leave it as it is."; gatherEffectType="PLANT_FIBER_GATHERED"; gatherPayloadKey="bundles"; gatherCount=bundles; }
        else if (intent == Intent.GATHER_STONE) {
            // A pick breaks stone out of the ground far faster than bare hands prising at it — a proper metal pick
            // (bronze/iron) quarries a real load, a knapped hammer or wooden pick rather less (#183). The metal a
            // Chronicle smelts thus earns them stone at scale too, beside finer ore. Fair no-op with no tool.
            boolean metalPick = items.hasAtLeast(chronicle.id(),"bronze_pickaxe",1) || items.hasAtLeast(chronicle.id(),"iron_pickaxe",1);
            boolean stonePick = !metalPick && (items.hasAtLeast(chronicle.id(),"primitive_pickaxe",1) || items.hasAtLeast(chronicle.id(),"stone_hammer",1));
            int quarry = metalPick ? 3 : stonePick ? 1 : 0;
            int stones=items.gatherFieldStones(chronicle.id(),chronicle.location(),resolvedAt,gatherBonus(text,chronicle.id())+quarry); outcome=stones>0?"SUCCEEDED":"FAILED"; perception=stones>0?(metalPick?"You drive the pick into the ground and lever out a good load of stone, far more than bare hands could win.":"You work loose a few stones from the ground and carry them with you.")+tally(stones,"stone","stones"):"You turn over the ground for a while, then leave it undisturbed."; gatherEffectType="FIELD_STONE_GATHERED"; gatherPayloadKey="stones"; gatherCount=stones; }
        else if (intent == Intent.GATHER_BERRIES) { int berries=items.gatherWildBerries(chronicle.id(),chronicle.location(),resolvedAt,gatherBonus(text,chronicle.id())); outcome=berries>0?"SUCCEEDED":"FAILED"; perception=berries>0?"You gather ripe berries from the living growth."+tally(berries,"berry","berries"):"You search the low growth carefully, then let it settle back into place."; gatherEffectType="WILD_BERRIES_GATHERED"; gatherPayloadKey="berries"; gatherCount=berries; }
        else if (intent == Intent.GATHER_BRANCHES) { int branches=items.gatherDryBranches(chronicle.id(),chronicle.location(),resolvedAt,gatherBonus(text,chronicle.id())); outcome=branches>0?"SUCCEEDED":"FAILED"; perception=branches>0?(standingTreesHere(chronicle.location())?"You gather dry branches from beneath the trees.":"You work along the low scrub and the wind-broken ground, and gather what dry wood it has to give.")+tally(branches,"branch","branches"):"You search the leaf litter for dry wood, then leave with empty hands."; gatherEffectType="DRY_BRANCH_GATHERED"; gatherPayloadKey="branches"; gatherCount=branches; }
        else if (intent == Intent.GATHER_CLAY) { boolean shovel=items.hasAtLeast(chronicle.id(),"wooden_shovel",1); boolean stick=!shovel&&items.hasAtLeast(chronicle.id(),"digging_stick",1); int dig=shovel?2:(stick?1:0); int lumps=items.gatherClay(chronicle.id(),chronicle.location(),resolvedAt,gatherBonus(text,chronicle.id())+dig); outcome=lumps>0?"SUCCEEDED":"FAILED"; perception=lumps>0?(shovel?"You bite the shovel deep into the bank and turn out heavy lumps of wet clay by the load.":stick?"You lever the earth open with the digging stick and prise free dense lumps of wet clay.":"You work the earth with your hands and pull free dense lumps of wet clay.")+tally(lumps,"lump","lumps"):"You search the ground for workable clay, but the earth here holds nothing useful."; gatherEffectType="CLAY_GATHERED"; gatherPayloadKey="lumps"; gatherCount=lumps; }
        else if (intent == Intent.GATHER_STONE_SLAB) { int slabs=items.gatherStoneSlab(chronicle.id(),chronicle.location(),resolvedAt); outcome=slabs>0?"SUCCEEDED":"FAILED"; perception=slabs>0?"You work broad, flat slabs of stone free from the rock and take up their considerable weight."+tally(slabs,"slab","slabs"):"You search the rock for a slab flat enough to work, but nothing here breaks away clean."; gatherEffectType="STONE_SLAB_GATHERED"; gatherPayloadKey="slabs"; gatherCount=slabs; }
        else if (intent == Intent.GATHER_PLANT) { String[] r=items.gatherPlant(chronicle.id(),chronicle.location(),text,resolvedAt); outcome=r[0]; perception=r[1]; }
        else if (intent == Intent.PLANT_TREE) { String[] r=items.plantTree(chronicle.id(),chronicle.location(),resolvedAt); outcome=r[0]; perception=r[1]; }
        else if (intent == Intent.FORAGE_GROUND) { String[] r=items.forageGround(chronicle.id(),chronicle.location(),text,resolvedAt); outcome=r[0]; perception=r[1]; }
        else if (intent == Intent.FELL_TREE) { String[] r=items.fellTree(chronicle.id(),chronicle.location(),resolvedAt); outcome=r[0]; perception=r[1]; }
        else if (intent == Intent.COPPICE) { String[] r=items.coppice(chronicle.id(),chronicle.location(),resolvedAt); outcome=r[0]; perception=r[1]; }
        else if (intent == Intent.TILL_GROUND) { String[] r=items.tillGround(chronicle.id(),chronicle.location(),resolvedAt); outcome=r[0]; perception=r[1]; }
        else if (intent == Intent.SOW) { String[] r=items.sowCrop(chronicle.id(),chronicle.location(),resolvedAt); outcome=r[0]; perception=r[1]; }
        else if (intent == Intent.HARVEST_CROP) { String[] r=items.harvestCrop(chronicle.id(),chronicle.location(),resolvedAt); outcome=r[0]; perception=r[1]; }
        else if (intent == Intent.TAKE_STOCK_OF_CAMP) perception = campStocktake(chronicle.location());
        else if (intent == Intent.READ_THE_SKY) perception = skyReading(chronicle.location(), resolvedAt);
        else if (intent == Intent.WHICH_WAY) perception = whichWay(chronicle.location(), text, resolvedAt);
        else if (intent == Intent.CHECK_FIRE) perception = fire.fireReading(chronicle.id(), chronicle.location(), resolvedAt, darkHoursLeft(resolvedAt));
        else if (intent == Intent.CHECK_CROP) { String[] r = items.cropStanding(chronicle.id(), chronicle.location(), resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.CHECK_STANDING) {
            // Null when no people are in reach, so the honest answer is that there is nobody to stand with.
            String how = conduct == null ? null : conduct.standingReading(chronicle.id(), chronicle.location(), resolvedAt);
            perception = how != null ? how
                : "There are no people within reach of this ground to have a view of you — no isle on it or on "
                + "any beside it. How you stand with anybody is a thing to be found out where they live.";
        }
        else if (intent == Intent.TAKE_STOCK_OF_GEAR) perception = items.gearStocktake(chronicle.id(), text);
        else if (intent == Intent.TAKE_STOCK_OF_FOOD) perception = items.foodStocktake(chronicle.id(), chronicle.location(), resolvedAt);
        else if (intent == Intent.JUDGE_HAULAGE) perception = items.judgeHaulage(chronicle.id());
        else if (intent == Intent.JUDGE_WATER) { String[] r = judgeWater(chronicle.location()); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.LINE_GARMENT) { String[] r = items.lineGarment(chronicle.id(), text, resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.WATER_CROP) { String[] r = items.waterCrop(chronicle.id(), chronicle.location(), resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.SCARE_BIRDS) { String[] r = items.scareBirdsFromCrop(chronicle.id(), chronicle.location(), resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.JUDGE_CROSSING) { String[] r = judgeCrossing(chronicle, text); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.HIDE_TRAIL) { String[] r = wildlife.hideTrail(chronicle.id(), chronicle.location(), resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.WEED_CROP) { String[] r=items.tendCrop(chronicle.id(),chronicle.location(),resolvedAt); outcome=r[0]; perception=r[1]; }
        else if (intent == Intent.CLEAR_LAND) { String[] r=items.clearLand(chronicle.id(),chronicle.location(),resolvedAt); outcome=r[0]; perception=r[1]; }
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
        else if (intent == Intent.BUILD_PEN) { String[] r=construction.buildPen(chronicle.id(),chronicle.location(),text,resolvedAt); outcome=r[0]; perception=r[1]; }
        else if (intent == Intent.TAKE_ANIMAL_YIELD) { WildlifeEncounterService.EncounterResult r=wildlife.takeTamedYield(chronicle.id(),resolvedAt,text); outcome=r.outcome(); perception=r.narration(); }
        else if (intent == Intent.FEED_ANIMAL) { String[] r=items.feedDraftBeasts(chronicle.id(),resolvedAt); outcome=r[0]; perception=r[1]; }
        else if (intent == Intent.BUILD_LOOKOUT) { String[] r=construction.buildLookout(chronicle.id(),chronicle.location(),resolvedAt); outcome=r[0]; perception=r[1]; }
        else if (intent == Intent.BUILD_FUEL_RACK) { String[] r=construction.buildFuelRack(chronicle.id(),chronicle.location(),resolvedAt); outcome=r[0]; perception=r[1]; }
        else if (intent == Intent.BUILD_LATRINE) { String[] r=construction.buildLatrine(chronicle.id(),chronicle.location(),resolvedAt); outcome=r[0]; perception=r[1]; }
        else if (intent == Intent.BUILD_TOOL_SHED) { String[] r=construction.buildToolShed(chronicle.id(),chronicle.location(),resolvedAt); outcome=r[0]; perception=r[1]; }
        else if (intent == Intent.BUILD_SMOKE_VENT) { String[] r=construction.buildSmokeVent(chronicle.id(),chronicle.location(),resolvedAt); outcome=r[0]; perception=r[1]; }
        else if (intent == Intent.BUILD_STORAGE_AREA) { String[] r=construction.buildStorageArea(chronicle.id(),chronicle.location(),resolvedAt); outcome=r[0]; perception=r[1]; }
        else if (intent == Intent.RESTORE_HABITAT) { String[] r=wildlife.restoreHabitat(chronicle.location(),30,resolvedAt); outcome=r[0]; perception=r[1]; }
        else if (intent == Intent.FISH) { WildlifeEncounterService.EncounterResult r=wildlife.fish(chronicle.id(),chronicle.location(),actionId,resolvedAt,text); outcome=r.outcome(); perception=r.narration(); }
        else if (intent == Intent.SNARE) { WildlifeEncounterService.EncounterResult r=wildlife.snare(chronicle.id(),chronicle.location(),actionId,resolvedAt); outcome=r.outcome(); perception=r.narration(); }
        else if (intent == Intent.RAID_NEST) { WildlifeEncounterService.EncounterResult r = wildlife.robNest(chronicle.id(), chronicle.location(), resolvedAt, actionId); outcome = r.outcome(); perception = r.narration(); }
        else if (intent == Intent.RAID_HIVE) { PhysicalItemService.InsectHarvest r=items.raidHive(chronicle.id(),chronicle.location(),text,resolvedAt); outcome=r.outcome(); perception=r.narration(); applyInsectHazard(chronicle.id(),r,actionId,resolvedAt); }
        else if (intent == Intent.COLLECT_INSECTS) { PhysicalItemService.InsectHarvest r=items.collectInsects(chronicle.id(),chronicle.location(),text,resolvedAt); outcome=r.outcome(); perception=r.narration(); applyInsectHazard(chronicle.id(),r,actionId,resolvedAt); }
        else if (intent == Intent.EAT) { String[] r = eat(chronicle.id(), text, actionId, resolvedAt); outcome = r[0]; perception = r[1]; if("SUCCEEDED".equals(outcome)) wildlife.recordRefuse(chronicle.location(),2,resolvedAt); }
        else if (intent == Intent.COOK_MEAT) {
            // The fire cooks four things now (V396), so the prose must say which. It called a cooked fish
            // "the meat" otherwise, which is the catalogue token in prose form: a new food that nothing names.
            FireService.CookedAtFire done=fire.cookOverFire(chronicle.id(),chronicle.location(),resolvedAt,text);
            int cooked=done.pieces();
            String it=done.what()==null?"meat":done.what();
            if(cooked>1) perception="You lay several pieces of "+it+" out over the steady heat and turn them until each darkens through — a batch done at once.";
            else if(cooked==1) perception="You hold the "+it+" over the steady heat until its surface changes and darkens.";
            else {outcome="FAILED";perception=done.hadFire()
                ? "You have nothing raw within reach that a fire could cook — nothing to lay over the heat."
                : "There is no fire burning here to cook anything over.";}
            if(cooked>=1) wildlife.recordRefuse(chronicle.location(),4,resolvedAt); }
        else if (intent == Intent.DRINK) {
            // The safest water you carry first (boiled > filtered > raw), else raw from a source in reach. Raw and
            // standing water carry a gut-illness risk that accumulates; boiled water and a clean moving source do not (#71).
            // A water ladle (#78) draws the clearer water off the top instead of dipping the whole vessel into the
            // silt, so it takes the edge off drinking untreated water — never as safe as boiling or filtering, but
            // a real, cheap improvement on a careless gulp. Closes the water_ladle dead-craft (#257).
            boolean ladle = items.hasAtLeast(chronicle.id(), "water_ladle", 1);
            // A silver cup (#188) keeps untreated water sweet — the bright metal holds off the rot, so raw or
            // standing water drunk from it sits far easier in the gut than from a skin: the safest way to drink
            // untreated water short of boiling. Best of the untreated-water eases; ripple-safe when none is carried.
            boolean silver = items.hasAtLeast(chronicle.id(), "silver_cup", 1);
            String carried = items.bestWaterCarried(chronicle.id());
            if (carried != null) {
                items.consumeOne(chronicle.id(), carried, resolvedAt); physiology.drink(chronicle.id());
                if ("clean_water".equals(carried)) perception = "You drink your fill of the boiled water — clean, flat, and safe.";
                else if ("filtered_water".equals(carried)) {
                    // A fired clay filter (charcoal and sand packed in a hard-fired vessel, #59/#78) clarifies better
                    // than the improvised bark-and-charcoal cone (#141), so water run through it is cleaner to drink —
                    // the terminal edge that makes the costly fired filter worth firing over the free stand-in.
                    boolean firedFilter = items.hasAtLeast(chronicle.id(), "clay_water_filter", 1);
                    physiology.applyWaterborneRisk(chronicle.id(), firedFilter ? 1 : 2);
                    perception = firedFilter ? "You drink the filtered water; run through the fired clay filter it comes clearer still — cleaner than a bark cone could leave it, though short of a boil." : "You drink the filtered water; it runs clearer than it was, though not beyond all doubt.";
                }
                else { physiology.applyWaterborneRisk(chronicle.id(), silver ? 2 : ladle ? 3 : 5); perception = silver ? "You drink the raw water from the silver cup; something in the bright metal keeps it sweeter than it has any right to be, and it sits easy." : ladle ? "You draw the raw water with the ladle, skimming the clearer water off the top; it eases the dryness and sits a little easier for the care." : "You drink the raw water you carry. It eases the dryness, but sits uneasy in the gut."; }
            } else if (waterInReach(chronicle.location())) {
                physiology.drink(chronicle.id());
                // Named for the water actually drunk from (#37): it said "the standing water" at a fast stream.
                String drunkFrom = waterNamed(chronicle.location());
                if (safeWaterSource(chronicle.location())) perception = "You drink from " + drunkFrom + " and let the cold settle in your throat.";
                else { DrawTreatment through = drawTreatment(chronicle.location()); physiology.applyWaterborneRisk(chronicle.id(), Math.max(1, (silver ? 3 : ladle ? 4 : 6) - (through == null ? 0 : through.clarifies()))); perception = through != null ? (naturalWaterHere(chronicle.location())
                        // The usual case: a stream or a spring is the water, and the structure is what it is drawn
                        // through on the way to the mouth.
                        ? "You draw the water off through the " + through.name() + " beside " + drunkFrom + " and drink; it comes clearer than the source itself, though it has not been boiled."
                        // A well or a catchment on dry ground IS the water, so it cannot be beside it (#77). This
                        // read "through the well beside the water here", which invents a second source.
                        : "You draw water from the " + through.name() + " and drink; it comes up clearer than any standing pool would, though it has not been boiled.") : silver ? "You dip the silver cup into " + drunkFrom + " and drink; the bright metal keeps it from turning the gut as it otherwise would." : ladle ? "You dip the ladle into " + drunkFrom + " and draw from above the silt; it is still not clean, but the gut will fare better than from a careless gulp." : "You drink from " + drunkFrom + ". It eases the dryness, but it is not clean, and the gut will know it."; }
            } else { outcome = "FAILED"; perception = noWaterToDrink(chronicle.location(), beforeWeather); }
        }
        else if (intent == Intent.COLLECT_WATER) {
            if (!waterInReach(chronicle.location())) { outcome = "FAILED"; perception = "There is no water here to fill from — no stream, spring, or standing water within reach."; }
            else if (!items.hasWaterVessel(chronicle.id())) { outcome = "FAILED"; perception = "The water is here, but nothing you carry will hold it; what you scoop up runs out between your fingers."; }
            else {
                // A sand filter bed (#77) is drawn off beneath the sand, so what fills the vessel is already filtered.
                DrawTreatment bed = drawTreatment(chronicle.location());
                boolean filtered = bed != null && bed.filtered();
                int n = filtered ? items.makeWater(chronicle.id(), "filtered_water", "Filtered water", 3, resolvedAt) : items.makeWater(chronicle.id(), "raw_water", "Raw water", 3, resolvedAt);
                outcome = n > 0 ? "SUCCEEDED" : "FAILED";
                perception = n == 0 ? "Your vessels are already brimful; there is no room for more water."
                    : filtered ? "You fill your vessel at the draw below the " + bed.name() + "; the water comes through the sand clear — filtered, though not yet boiled."
                    : "You fill your vessel with water from the source here — raw, and carrying whatever the source carries.";
            }
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
        else if (intent == Intent.MAINTAIN_CAMP) { String[] r = construction.maintainCamp(chronicle.id(), chronicle.location(), resolvedAt, text); outcome = r[0]; perception = r[1]; if ("SUCCEEDED".equals(outcome)) physiology.settleCamp(chronicle.id()); }
        else if (intent == Intent.PLACE_WINDBREAK) { String[] r = construction.placeWindbreak(chronicle.id(), chronicle.location(), resolvedAt); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.PLACE_COVER) { String[] r = construction.placeCover(chronicle.id(), chronicle.location(), resolvedAt, coverKindOf(text.toLowerCase(Locale.ROOT))); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.TREAT_WOUND) { if (physiology.bindWound(chronicle.id(), items, actionId, resolvedAt)) perception="You press and bind the wounded place until the immediate bleeding eases."; else { outcome="FAILED";
            // Say which of the two it was (#37). bindWound returns false for a body with nothing open AND for a
            // body with nothing to bind it with, and the one sentence covered both — so a Chronicle bleeding for
            // want of a strip of fibre was told the same thing as one who was not hurt at all.
            perception = physiology.hasOpenWound(chronicle.id())
                ? "You have nothing to bind it with — no poultice, no roll of bandage, not so much as a length of plant fibre. The blood keeps its own time."
                : "You go over yourself and find nothing open and nothing bleeding."; } }
        else if (intent == Intent.EDIT_DOCUMENT) { try { reviseDocument(chronicle.id(), actionId, resolvedAt, text); perception="Your marks remain on the physical page."; } catch (IllegalArgumentException | IllegalStateException ignored) { outcome="FAILED"; perception="You handle the page for a while, then set it aside unchanged."; } }
        else if (intent == Intent.CONFRONT_WILDLIFE) { double spec=SuccessModel.specificity(text,HUNT_SIGNALS); double fam=capability.familiarity(chronicle.id(),"AIM"); int tactic=(int)Math.round(spec*30 + Math.min(0.20,fam*3.0)*100); // The TEXT, so the hunt closes with what the hunter named (#37): "hunt the deer" in a wood that
            // also held a boar closed with the boar, because the quarry was picked carnivores-first and the
            // words were never read. The most consequential act in the game, answered about the wrong animal.
            WildlifeEncounterService.EncounterResult result=wildlife.confront(chronicle.id(),chronicle.location(),actionId,resolvedAt,tactic,text); outcome=result.outcome(); perception=result.narration(); wildlife.recordEmissionDrift(chronicle.location(),"PREDATION",40,resolvedAt); }
        else if (intent == Intent.HARVEST_CARCASS) { WildlifeEncounterService.HarvestResult result=wildlife.harvest(chronicle.id(),chronicle.location(),actionId,resolvedAt); outcome=result.outcome(); perception=result.narration(); if("SUCCEEDED".equals(outcome)) wildlife.recordRefuse(chronicle.location(),wildlife.butcheryRefuse(chronicle.location()),resolvedAt); }
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
        else if (intent == Intent.BANK_FIRE) { if(fire.bank(chronicle.id(),chronicle.location(),resolvedAt)) perception=items.hasAtLeast(chronicle.id(),"fire_poker",1)?"You work the poker through the fire, rake the coals into a tight heap and cover them with ash. Banked like this the embers will hold low and long, and can be woken again later.":"You rake the coals into a tight heap and cover them with ash, so the embers will hold low and long and can be woken again later."; else {outcome="FAILED"; perception="There is no fire here to bank down.";} }
        else if (intent == Intent.START_LEAN_TO) { if (construction.startLeanTo(chronicle.id(), chronicle.location(), actionId, resolvedAt)) perception="You mark out a low shelter frame against the weather."; else { outcome="FAILED"; perception="The ground holds the same unfinished frame you found there."; } }
        else if (intent == Intent.WORK_LEAN_TO) {
            // "build a lean-to" is the most natural way to ask for one, and classifyLeanTo routes any "build" to
            // WORK — so the first shelter a Chronicle ever asks for was answered with "The unfinished frame
            // remains as it was", about a frame that did not exist. workLeanTo returns false for three unrelated
            // reasons (no frame, no branches, no fibre) and all three read as that one sentence.
            //
            // So: if nothing stands here, they are starting one, whatever verb they used. If something does, the
            // refusal names what the work actually wants.
            if (!construction.unfinishedLeanToHere(chronicle.location())) {
                if (construction.startLeanTo(chronicle.id(), chronicle.location(), actionId, resolvedAt))
                    perception = "There is nothing here to work on yet, so you begin one: you mark out a low shelter frame against the weather.";
                else { outcome = "FAILED"; perception = "The ground here already holds a frame you have not finished."; }
            }
            else if (construction.workLeanTo(chronicle.id(), chronicle.location(), resolvedAt))
                perception = "You bind the shelter frame a little further into place.";
            else { outcome = "FAILED"; perception = "The frame stands waiting, but binding another course into it takes branches and fibre, and you have not both to hand."; }
        }
        else if (intent == Intent.ABANDON_LEAN_TO) { if (construction.abandonLeanTo(chronicle.location(),resolvedAt)) perception="You leave the unfinished frame where it stands."; else { outcome="FAILED"; perception="There is no unfinished frame here to leave behind."; } }
        else if (intent == Intent.RESUME_LEAN_TO) { if (construction.resumeLeanTo(chronicle.location(),resolvedAt)) perception="You return to the old frame and set your hands to it again."; else { outcome="FAILED"; perception="You find no abandoned frame here to take up again."; } }
        else if (intent == Intent.REPAIR_LEAN_TO) { if(construction.repairLeanTo(chronicle.id(),chronicle.location(),resolvedAt)) perception="You tighten the frame and replace the worst of its weathered bindings."; else {outcome="FAILED";perception="You work over the shelter for a while, then leave it unchanged.";} }
        // Material sufficiency is checked before invoking the crafting methods.
        // Those methods are @Transactional and throw when material is missing;
        // letting that throw reach the shared transaction would mark it
        // rollback-only even though we catch it, turning an ordinary failed
        // attempt into a hard rollback. Prechecking keeps failed attempts graceful.
        else if (intent == Intent.CRAFT_BASKET) { if (items.basketWeaveUnitsInReach(chronicle.id()) >= 8) { items.craftBasket(); perception="Flexible lengths — withies, rush, fibre, vine, or cordage — tighten row on row beneath your hands until a rough basket holds its shape."; } else { outcome="FAILED"; perception="You have too little flexible stock within reach to weave a basket — it wants eight lengths of split withy, reed, rush, fibre, vine, or cordage, and the loose few you hold fall away from one another."; } }
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
            // A sewing table (V272). It was defined, given an item_source, and then left out of this list, so there
            // has never been a way to have one -- while fifty-three sewing and leatherwork recipes asked for no
            // station at all. Lighter than the other benches: it holds cloth and hide flat, not timber and stone.
            else if (v.contains("sewing") || v.contains("leatherwork")) { ok = items.craftFurniture(chronicle.id(), chronicle.location(), "sewing_table", "Sewing work table", 3, 0, false, 0, 0, resolvedAt); made = "sewing table"; }
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
            else if (!items.hasAtLeast(chronicle.id(), "fiber_cordage", need)) { outcome = "FAILED"; perception = "A net wants far more cordage than you have twisted. What you hold would not reach across a corner of the mesh, let alone the whole of it."; }
            else if (landing && !items.hasAtLeast(chronicle.id(), "dry_branch", 2)) { outcome = "FAILED"; perception = "You have the cordage for the mesh, but nothing in reach will bend into a hoop for it: what you turn over in your hands is either too brittle to curve or too slight to hold a shape."; }
            else { items.craftFishingNet(landing, resolvedAt); perception = landing ? "You bend a branch into a hoop and knot the cordage across it, row by row, until a landing net hangs ready in your hand." : "You knot the cordage row on row, working the mesh even and true, until a fishing net lies finished across your knees."; }
        }
        else if (intent == Intent.CRAFT_BELT) {
            // The primitive utility belt (#35): a fibre strap with tool loops. Cordage for the strap, plant
            // fibre for the loops, a blade to cut and fit them; it equips to the waist once made.
            if (!items.hasCuttingTool(chronicle.id())) { outcome = "FAILED"; perception = "You lay out the fibre to make a belt, but with no blade to cut and fit the strap, it will not come together."; }
            else if (!items.hasAtLeast(chronicle.id(), "fiber_cordage", 2) || !items.hasAtLeast(chronicle.id(), "plant_fiber", 2)) { outcome = "FAILED"; perception = "A belt wants a length of twisted cordage for the strap and plant fibre for the loops, and you have not both to hand. Twist cordage and gather fibre first."; }
            else { items.craftUtilityBelt(resolvedAt); perception = "You cut and fit a length of cordage into a waist strap and work plant fibre into a row of loops along it. A rough but real utility belt, ready to carry what your hands use most."; }
        }
        else if (intent == Intent.BUILD_FIRE_PIT) {
            if (construction.buildFirePit(chronicle.id(), chronicle.location(), actionId, resolvedAt))
                perception = "You settle stone into a low, deliberate ring. The fire pit remains where you made it.";
            else {
                // Say the count (#37). "You set a few stones apart, then leave them where they lie" told a
                // Chronicle holding two stones that nothing had happened, and left them to guess whether the
                // problem was the stones, the ground, or the asking. A ring takes four.
                outcome = "FAILED";
                int have = items.reachCountAt(chronicle.id(), chronicle.location(), "field_stone");
                perception = "You set out what stone you have — " + have + " — and a ring wants "
                    + com.devosphere.draugr.construction.ConstructionService.FIRE_PIT_STONES
                    + ". You leave them where they lie.";
            }
        }
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
        else if (intent == Intent.EMPTY_CONTAINER) { String[] r = items.emptyContainer(chronicle.id(), chronicle.location(), text, resolvedAt); outcome = r[0]; perception = r[1]; }
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
        else if (intent == Intent.SENSE_BODY) { perception = bodyReading(beforeBody); }
        else if (intent == Intent.BREEDING_PROSPECTS) { String[] r = items.breedingProspects(chronicle.id(), chronicle.location(), text); outcome = r[0]; perception = r[1]; }
        else if (intent == Intent.CHECK_STOCK) { String[] r = items.stockWelfare(chronicle.id(), chronicle.location(), text); outcome = r[0]; perception = r[1]; }
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
        // What this act does to the ground it was done on (#215/#216). Every intent has a card in
        // activity_impact — including the ones that mark nothing, which have to say why — and this is the only
        // place a footprint is decided. It replaced a switch here and three recordings written inline into the
        // felling, coppicing and clearing dispatch lines: four places, none of which could say what the other
        // hundred and twenty-one intents did, because the answer was "nothing" by omission rather than by
        // decision. Only successful work leaves a mark.
        if ("SUCCEEDED".equals(outcome)) markTheGround(intent, text, chronicle.location(), resolvedAt);
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
        // #217 — overexertion. Driving heavy labour when the body is already spent strains it: swing an axe or
        // break ground on an empty tank and something pulls or wrenches — a real injury with a recovery need, not
        // just more tiredness, and never a silent reset. Judged on the energy the Chronicle ENTERED the act with
        // (read before the labour drain below); heavy labour is uniquely Labor(12,·); combat is its own injury
        // vector, and a failed half-effort attempt does not overexert. The read runs only for heavy labour.
        boolean heavyLabour = !failed && labor.energy() >= 12 && intent != Intent.CONFRONT_WILDLIFE;
        Integer energyEntering = heavyLabour ? jdbc.queryForObject("SELECT energy_level FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, chronicle.id()) : null;
        physiology.applyLabor(chronicle.id(), items, failed ? (labor.energy() + 1) / 2 : labor.energy(), failed ? labor.hygiene() / 2 : labor.hygiene());
        if (energyEntering != null && energyEntering < 20) {
            physiology.applyStrain(chronicle.id(), 6, actionId, resolvedAt);
        }
        // Bucket B — the narration contract: wrap the deterministic core in a clause of setting, so the
        // world is present in the prose and not just the act. Success and failure alike are grounded;
        // the punctuation rule (weather when felt/changing, the land on deliberate looking) lives in the
        // NarrationEngine so it lands when it means something rather than tagging every line. OBSERVE is
        // excluded — its own survey prose already IS the setting, in far more detail.
        // An attempt the world could not resolve is exactly the moment a person stops and looks up. That line
        // was landing at LOW attention, which adds nothing at all — so the one narration a player sees when the
        // world cannot help them was the barest in the game, the flat "nothing here answers to the attempt"
        // #30 calls out by name. Ground it in the actual place, light and weather. Setting only: it still names
        // no prerequisite and suggests no action, because the rule against hinting does not bend for a failure.
        // The mechanical attention (what wildlife makes of the Chronicle) is deliberately left alone.
        String narrationAttention = (intent == Intent.UNKNOWN && "FAILED".equals(outcome)) ? "HIGH" : attention;
        if (intent != Intent.OBSERVE) perception = groundPerception(perception, chronicle.location(), narrationAttention, beforeWeather, resolvedAt);
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
        // A people's isle within sight (#112): what can be seen of it from here, and nothing that could not.
        String isle = contact == null ? null : contact.glimpse(loc, at);
        if (isle != null) s.append(isle).append(" ");
        // What stands on this ground.
        Integer sites = jdbc.queryForObject("SELECT COUNT(*) FROM ecology_site WHERE chunk_id=? AND site_category='WILDLIFE'", Integer.class, loc);
        // NOT counted here any more: see standingHere below. Kept for the marker/site sentences that follow.
        Integer builds = jdbc.queryForObject("SELECT COUNT(*) FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE'", Integer.class, loc);
        Integer markers = jdbc.queryForObject("SELECT COUNT(*) FROM location_marker WHERE chunk_id=?", Integer.class, loc);
        Integer carcasses = jdbc.queryForObject("SELECT COUNT(*) FROM world_object WHERE current_location_id=? AND object_type='CARCASS' AND lifecycle_state='ACTIVE'", Integer.class, loc);
        // What stands here, BY NAME (#37). This said "Structures you raised stand here." — one sentence for a
        // windbreak and for a byre, a well, a latrine and a drying rack together, while construction_kind has
        // carried every one of their names all along. It also counted a ruin as standing, because it asked for
        // state='COMPLETED' and never for integrity: a collapsed hut read exactly like a sound one.
        String standing = standingHere(loc);
        if (!standing.isEmpty()) s.append(standing).append(" ");
        if (markers != null && markers > 0) s.append("A marker you left catches your eye, quietly confirming this is a place you have been. ");
        if (sites != null && sites > 0) s.append("The ground shows signs of living things that pass through or feed here. ");
        // Name what this ground plainly shows (#37). A spring, a clay bed, a flint field, a standing ruin are all
        // visible to anyone standing on them, but perception only ever said "signs of living things" — the Chronicle
        // could not tell what water or ground they were actually on. Only RESOURCE and RUIN sites are named: wildlife
        // sites stay unnamed here because presentLife already names the creatures actually present, and monster lairs
        // are never named outright — that would be a hint handed to the player rather than a thing witnessed, and
        // presentLife already gives their sign for a keen enough eye.
        java.util.List<String> plainSites = jdbc.query(
            "SELECT DISTINCT site_kind FROM ecology_site WHERE chunk_id=? AND site_category IN ('RESOURCE','RUIN') ORDER BY site_kind LIMIT 4",
            (rs, row) -> rs.getString(1).toLowerCase(Locale.ROOT), loc);
        if (!plainSites.isEmpty()) s.append("This ground holds ").append(joinAnd(plainSites)).append(". ");
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
        // What the ground itself holds (#181): the minerals this chunk can yield, and how worked any seam already is,
        // so a Chronicle can read likely ground before committing labour to it.
        String geology = groundGeology(loc);
        if (!geology.isEmpty()) s.append(geology);
        // How wooded this ground is (#200/#201): standing timber the Chronicle can fell, and whether a stand has
        // been thinned or cut out — so a wood can be read before an axe is set to it, like a seam before a pick.
        String woodland = groundWoodland(loc);
        if (!woodland.isEmpty()) s.append(woodland);
        // How well the water here still fishes (#181/#36): thick, workable, fished thin, or fished out — so a stretch
        // can be read before a line is cast, like a seam before a pick or a wood before an axe.
        String water = groundWater(loc, at);
        if (!water.isEmpty()) s.append(water);
        // Smoke in the air (#219): a fire burning on this ground, or a plume drifted in from the next ground over —
        // the grounded sensory evidence of a hazard nearby, read before it is anything but a smell on the wind.
        String air = groundAir(loc, at);
        if (!air.isEmpty()) s.append(air);
        // Fire burning dangerously close to what will catch (#219): the warning read BEFORE it takes hold, so a
        // Chronicle can bank or move the fire rather than meet the consequence unwarned.
        String fireRisk = groundFireHazard(loc, at);
        if (!fireRisk.isEmpty()) s.append(fireRisk);
        // A sown crop growing here (#162): its stage read so a Chronicle knows a green stand from one ripe to reap.
        String crop = groundCrop(loc, at);
        if (!crop.isEmpty()) s.append(crop);
        return s.toString().trim();
    }
    /** How a sown crop stands here (#162 agriculture): green, ripening, or ripe and ready to reap — read from its
     *  elapsed growing-time against its maturity, so a Chronicle can tell when to bring in the harvest. Read-only. */
    private String groundCrop(UUID loc, Instant at) {
        java.util.Map<String,Object> crop = jdbc.query(
            "SELECT sown_at, maturity_days, (weeded_at IS NOT NULL) AS weeded FROM crop_stand WHERE chunk_id=? AND harvested=false ORDER BY sown_at LIMIT 1",
            rs -> rs.next() ? java.util.Map.of("sown", rs.getTimestamp(1).toInstant(), "days", rs.getInt(2), "weeded", rs.getBoolean(3)) : null, loc);
        if (crop == null) return "";
        long grown = java.time.Duration.between((Instant) crop.get("sown"), at).toDays();
        int maturity = (int) crop.get("days");
        long daysPastRipe = grown - maturity;
        // Beyond the clean window (14 days ripe) the heads begin to shatter — the warning to reap it before it is
        // lost, read the same way a fire's danger is read before it takes hold.
        if (daysPastRipe > 14) return "A stand of sown grain stands ripe and beginning to go over — the first heads are shattering, and unless it is reaped soon the birds and weather will have it. ";
        if (daysPastRipe >= 0) return "A stand of sown grain stands ripe on this ground, the heads heavy and ready to reap. ";
        // While it grows, weeds crowd in unless the stand is worked (#165): the prompt to tend it for a fuller harvest.
        String weeds = ((boolean) crop.get("weeded")) ? "" : "Weeds are creeping in among the young stems; worked clean, the stand would fill out a fuller harvest. ";
        double frac = maturity > 0 ? (double) grown / maturity : 1.0;
        return (frac >= 0.6 ? "A stand of sown grain grows on this ground, ripening toward harvest. "
             : "A stand of sown grain grows on this ground, still green. ") + weeds;
    }
    /** A fire burning dangerously (#219 fire containment): a roaring, unbanked hearth in dry weather with thatch or a
     *  loose fuel stock close by — the grounded warning that, left untended, it could catch, read before it ever does.
     *  Mirrors the gate the fire tick scorches on (FireService.scorchNearbyFlammables). Read-only; names no action. */
    private String groundFireHazard(UUID loc, Instant at) {
        boolean roaring = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM fire_state fs JOIN construction_project cp ON cp.object_id=fs.construction_id JOIN world_object w ON w.id=cp.object_id " +
            "WHERE w.current_location_id=? AND w.lifecycle_state='ACTIVE' AND fs.active=true AND fs.fuel_minutes>=120)", Boolean.class, loc));
        if (!roaring) return "";
        String weather = jdbc.query("SELECT ww.weather_kind FROM world_weather ww JOIN world_chunk wc ON wc.world_id=ww.world_id WHERE wc.id=?",
            rs -> rs.next() ? rs.getString(1) : null, loc);
        if (!("CLEAR".equals(weather) || "OVERCAST".equals(weather))) return "";
        String structure = jdbc.query(
            "SELECT cp.project_kind FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "WHERE w.current_location_id=? AND w.lifecycle_state='ACTIVE' AND cp.state='COMPLETED' AND cp.integrity_percent>0 " +
            "AND cp.project_kind IN ('LEAN_TO','FUEL_RACK','BRUSH_FENCE') LIMIT 1", rs -> rs.next() ? rs.getString(1) : null, loc);
        boolean fuel = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM world_object w JOIN item_instance i ON i.object_id=w.id WHERE w.current_location_id=? " +
            "AND w.current_owner_id IS NULL AND w.lifecycle_state='ACTIVE' AND i.item_key IN ('dry_branch','tinder_nest','wood_shaving'))", Boolean.class, loc));
        if (structure == null && !fuel) return "";
        String risk = structure != null
            ? switch (structure) {
                case "LEAN_TO" -> "the thatch of the lean-to";
                case "FUEL_RACK" -> "the drying wood on the fuel rack";
                case "BRUSH_FENCE" -> "the piled brush of the fence";
                default -> "the flammable structure"; }
            : "the loose fuel piled beside it";
        return "The fire here burns high and unbanked, and " + risk + " stands close enough to catch — left roaring and untended, it could take hold. ";
    }
    /** Woodsmoke in the air here (#219): a recent burn on this ground (SMOKE) or a plume drifted from a neighbouring
     *  working (SMOKE_DRIFT), read from the disturbance provenance while it is still fresh enough to hang in the air.
     *  Read-only; it names no action, only what the Chronicle smells and sees on the wind. */
    private String groundAir(UUID loc, Instant at) {
        String kind = jdbc.query(
            "SELECT source_kind FROM chunk_disturbance_event WHERE chunk_id=? AND source_kind IN ('SMOKE','SMOKE_DRIFT') " +
            "AND occurred_at >= ? ORDER BY occurred_at DESC LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : null, loc, java.sql.Timestamp.from(at.minus(java.time.Duration.ofHours(4))));
        if (kind == null) return "";
        return "SMOKE_DRIFT".equals(kind)
            ? "A haze of woodsmoke drifts across the ground here, carried in from a fire burning somewhere close by. "
            : "Woodsmoke hangs over this ground — a fire has been burning here. ";
    }
    /** How well the water here still fishes (#181/#36): only for a chunk whose biome actually holds fish, read from
     *  the recorded stock (restocked over time) or the full stretch a water not yet worked carries. Read-only. */
    private String groundWater(UUID loc, Instant at) {
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, loc);
        Integer aquatic = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wildlife_species WHERE movement_class='AQUATIC' AND biome_affinity ILIKE ?",
            Integer.class, "%" + biome + "%");
        if (aquatic == null || aquatic == 0) return "";
        // Read the stock as a share of what this particular stretch holds when full, so a naturally thin water does
        // not read as depleted, nor a teeming one as merely workable (#181/#36 richness).
        int full = com.devosphere.draugr.ecology.WildlifeEncounterService.fishStockSeedFor(loc);
        int fish = wildlife.fishRemaining(loc, at);
        double frac = full > 0 ? (double) fish / full : 0.0;
        // The water is named for what it is (#37) — a slow reach, a still pond — where it used to be "the water here".
        String named = com.devosphere.draugr.ecology.FreshWater.capitalised(waterNamed(loc));
        return (fish <= 0 ? named + " is fished out, empty of anything worth taking."
              : frac < 0.25 ? named + " is fished thin — the take would be poor."
              : frac < 0.70 ? named + " holds fish enough to work."
              : named + " is thick with fish.") + " ";
    }
    /** How wooded this ground is (#200/#201): the tree stand that grows here — from a recorded stand where one
     *  exists, or the full natural stand a wooded biome carries until it is first cut — and whether it stands thick,
     *  is thinned, or has been cut out. Read-only; it names no action to take. */
    private String groundWoodland(UUID loc) {
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, loc);
        java.util.Map<String,Object> stand = jdbc.query(
            "SELECT cf.flora_key, cf.quantity, cf.capacity FROM chunk_flora cf JOIN flora_definition fd ON fd.flora_key=cf.flora_key " +
            "WHERE cf.chunk_id=? AND fd.organism_type='TREE' ORDER BY cf.quantity DESC LIMIT 1",
            rs -> rs.next() ? java.util.Map.of("key", rs.getString(1), "qty", rs.getInt(2), "cap", rs.getInt(3)) : null, loc);
        String species; int qty; int full;
        if (stand != null) { species = (String) stand.get("key"); qty = (int) stand.get("qty"); full = Math.max(1, (int) stand.get("cap")); }
        else {
            // No recorded stand yet — a wooded biome carries a full natural stand until it is first cut, and the stand
            // has its own density (PhysicalItemService.natStandFor). The species is the one a fell here would take.
            species = switch (biome != null ? biome : "") {
                case "FOREST", "TEMPERATE_FOREST" -> "oak";
                case "MOUNTAIN" -> "spruce";
                case "HIGHLAND" -> "pine";
                case "WETLAND", "RIVER_BANK" -> "willow";
                default -> null;
            };
            if (species == null) return "";
            full = com.devosphere.draugr.item.PhysicalItemService.natStandFor(loc);
            qty = full; // an uncut wood stands at its full natural density
        }
        String tree = species.replace('_', ' ');
        // Read the stand as a share of what this ground carries when full, so a naturally sparse wood is not read as
        // thinned nor a deep one as merely workable (#200/#201 stand density).
        double frac = full > 0 ? (double) qty / full : 0.0;
        return (qty <= 0 ? "The " + tree + " stand here is cut out, only stumps and low brush remaining."
              : frac < 0.25 ? "The " + tree + " here is thinned — only a few trees still stand."
              : frac < 0.70 ? "A stand of " + tree + " grows here, enough to work if it is not stripped bare."
              : "The " + tree + " grows thick here, a full stand of timber.") + " ";
    }
    /** What the ground here can yield (#181): the minerals whose affinity matches this chunk, and — for any seam
     *  already opened — whether it still holds, runs thin, or is worked out. Read-only; it names no action to take. */
    private String groundGeology(UUID loc) {
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, loc);
        java.util.List<String> affinity = new java.util.ArrayList<>();
        if (biome != null) affinity.add("%" + biome + "%");
        for (String deposit : jdbc.queryForList(
                "SELECT DISTINCT CASE WHEN lower(site_kind) LIKE '%salt%' THEN 'SALT_DEPOSIT' " +
                "WHEN lower(site_kind) LIKE '%clay%' THEN 'CLAY_DEPOSIT' END FROM ecology_site " +
                "WHERE chunk_id=? AND site_category='RESOURCE' AND (lower(site_kind) LIKE '%salt%' OR lower(site_kind) LIKE '%clay%')",
                String.class, loc)) {
            if (deposit != null) affinity.add("%" + deposit + "%");
        }
        if (affinity.isEmpty()) return "";
        String affinityOr = affinity.stream().map(a -> "d.biome_affinity ILIKE ?").collect(java.util.stream.Collectors.joining(" OR "));
        // #160: what the eye reads off this ground and what a hand can get out of it have to be the same list.
        // A mineral bound to a province is in the ground only where that province is, so naming obsidian on an
        // ordinary mountain would promise a Chronicle something gatherMineral then refuses them — the survey
        // lying to the player about the world it is surveying. The same condition, in both places.
        //
        // And where a province IS here, its mineral is named first. It is the rarest thing about this ground and
        // the whole reason the site is worth walking to; buried under field stone and cobbles by a plain rarity
        // sort, it would be the one fact the survey failed to mention.
        String provinceHere =
            "EXISTS (SELECT 1 FROM mineral_province p JOIN ecology_site s ON s.site_kind = p.site_kind " +
            "         WHERE p.mineral_key = d.mineral_key AND s.chunk_id = ?)";
        java.util.List<Object> args = new java.util.ArrayList<>();
        // Positional, in the order the ?s appear in the text below: provinceHere in the select list, the deposit
        // join, the affinity clauses, then provinceHere again in the where. The first two are both the chunk.
        args.add(loc);
        args.add(loc);
        args.addAll(affinity);
        args.add(loc);
        java.util.List<java.util.Map<String,Object>> minerals = jdbc.queryForList(
            "SELECT d.display_name, d.mineral_key, d.rarity, md.remaining_units, " + provinceHere + " AS at_its_province " +
            "FROM mineral_definition d " +
            "LEFT JOIN mineral_deposit md ON md.mineral_key=d.mineral_key AND md.chunk_id=? " +
            "WHERE (" + affinityOr + ") AND (" +
            "  NOT EXISTS (SELECT 1 FROM mineral_province p WHERE p.mineral_key = d.mineral_key) OR " + provinceHere + ") " +
            "ORDER BY at_its_province DESC, d.rarity DESC LIMIT 4", args.toArray());
        if (minerals.isEmpty()) return "";
        java.util.List<String> likely = new java.util.ArrayList<>();
        java.util.List<String> worked = new java.util.ArrayList<>();
        for (java.util.Map<String,Object> m : minerals) {
            String nm = ((String) m.get("display_name")).toLowerCase(Locale.ROOT);
            Object rem = m.get("remaining_units");
            if (rem == null) {
                // Untouched ground: read how rich this particular seam looks, from the deterministic seam size a fell
                // here would open (#181). Rich ground reads richer; poor ground, poorer.
                int seed = com.devosphere.draugr.item.PhysicalItemService.mineralSeedFor(
                    loc, (String) m.get("mineral_key"), ((Number) m.get("rarity")).doubleValue());
                likely.add(seed >= 55 ? "a rich vein of " + nm : seed >= 30 ? nm : "a thin showing of " + nm);
            } else {
                int r = ((Number) rem).intValue();
                worked.add(r <= 0 ? "the " + nm + " here is worked out"
                        : r <= 15 ? "the " + nm + " seam is running thin"
                        : "the " + nm + " seam still holds");
            }
        }
        StringBuilder g = new StringBuilder();
        if (!likely.isEmpty()) g.append("The ground here carries the look of ").append(joinAnd(likely)).append(". ");
        if (!worked.isEmpty()) g.append("You can tell ").append(joinAnd(worked)).append(". ");
        return g.toString();
    }
    private String biomeDescription(String biome) {
        return switch (biome == null ? "" : biome) {
            case "TEMPERATE_FOREST" -> "Tall trees close overhead, their trunks dark with damp and the floor deep in leaf litter.";
            case "WETLAND" -> "The ground is soft and waterlogged, reeds standing in slow, dark water.";
            case "RIVER_BANK" -> "A river runs past a bank of smoothed stone and packed earth.";
            case "COAST" -> "The ground gives out to shingle and wrack, and open water runs from it to the edge of sight.";
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
            case "COAST" -> "You taste salt on the air and hear water working at a shore";
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
    /**
     * What the Chronicle says this ground is for (V295) — read from {@code district_purpose}, not from a chain of
     * literals here.
     *
     * <p>V47 wrote down the nine purposes the Wolf Kingdom used, saying in as many words that it existed so a
     * future chronicle would not have to re-derive the vocabulary. It was re-derived: this method used to write
     * SLEEPING, WATER, STORAGE, WORKSHOP, KNOWLEDGE and SANITATION against a catalogue holding RESIDENTIAL,
     * DRINKING, WAREHOUSE, HEAVY_MANUFACTURING, TEXTILE, LIBRARY, ARSENAL, PARK and SANITATION. One word
     * overlapped, and `district_purpose` was the only table in the schema no Java file so much as named.
     *
     * <p>Longest phrase wins, so "drinking water" is a draw rather than merely water, and the migration refuses
     * to let two purposes claim the same phrase — a substring collision steals a designation exactly the way it
     * steals a process.
     */
    private String purposeNamedIn(String value) {
        return jdbc.query(
            "SELECT dp.purpose_tag FROM district_purpose dp, " +
            "  LATERAL unnest(string_to_array(dp.keywords, ',')) AS phrase " +
            "WHERE position(btrim(phrase) in ?) > 0 AND btrim(phrase) <> '' " +
            "ORDER BY length(btrim(phrase)) DESC LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : null, value);
    }

    private String[] designate(ActiveChronicle chronicle, String text, UUID actionId, Instant at) {
        String name = extractDesignatedName(text);
        if (name == null || name.isBlank()) return new String[]{"FAILED", "You mean to give this place a name, but no clear name forms."};
        String value = text.toLowerCase(Locale.ROOT);
        String purpose = purposeNamedIn(value);
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
    /**
     * Words that are the SENTENCE's furniture rather than anybody's name for anywhere (#37).
     *
     * <p>"name this place" contains no name at all. The fallback pattern's optional demonstrative group makes
     * "this place" match, leaves nothing for the required tail, backtracks to "this", and captures the word
     * "place" — so the world answered "You fix a name to this place: place." and wrote that into
     * chronicle_named_location and the Chronicle's current zone. A confidently wrong answer, and a durable one:
     * the place is now called place.
     *
     * <p>Held as a set rather than another alternative in the regex, because the regex must keep capturing these
     * words when a real name follows them ("name this place the Long Meadow" is still the Long Meadow).
     */
    private static final java.util.Set<String> NOT_A_NAME = java.util.Set.of(
        "place", "area", "spot", "ground", "here", "this", "that", "it", "them", "thing",
        "this place", "this area", "this spot", "this ground", "the place", "the area", "the spot");

    private String extractDesignatedName(String text) {
        String raw = null;
        Matcher a = DESIGNATE_NAME.matcher(text);
        if (a.find()) raw = a.group(1);
        else { Matcher b = DESIGNATE_FALLBACK.matcher(text); if (b.find()) raw = b.group(1); }
        if (raw == null) return null;
        raw = raw.trim().replaceAll("[\\.\\!\\?\"']+$", "").trim();
        // A request to name somewhere is not itself a name (#37). Refused rather than accepted, so the Chronicle
        // is told no name formed instead of being left with a place called "place".
        if (NOT_A_NAME.contains(raw.toLowerCase(Locale.ROOT))) return null;
        return raw.length() > 60 ? raw.substring(0, 60).trim() : raw;
    }
    private String move(ActiveChronicle chronicle, String action, UUID actionId, Instant occurredAt) {
        Direction direction = Direction.from(action);
        String said = action.toLowerCase(Locale.ROOT);
        // Going back the way you came (#37). object_transition has recorded the direction, the from and the to of
        // every single move since the table existed, and nothing had ever read it for this: "retrace my steps",
        // "go back the way I came" and "follow my own tracks back" all reached nothing, and the last of those was
        // answered by TRACK — which hunts animal sign, so a player asking to go back the way they came was shown
        // somebody else's feathers in the low growth.
        //
        // The step is reversed rather than a bearing guessed: the record says which way the last one went, and
        // the way back is the opposite of it. If the ground has changed under you — a cave mouth closed, water
        // risen — the ordinary move checks below still apply, because this only chooses the direction.
        if (direction == null && BACKTRACKING.matcher(said).find()) {
            String last = jdbc.query(
                "SELECT payload->>'direction' FROM object_transition " +
                "WHERE object_id=? AND transition_type='MOVED' AND payload->>'toLocationId' = ?::text " +
                "ORDER BY occurred_at DESC LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null, chronicle.id(), chronicle.location().toString());
            if (last == null)
                return "You have not come to this ground from anywhere — there is no step behind you to take back.";
            Direction came = Direction.valueOf(last);
            direction = Direction.of(-came.dx, -came.dy);
            if (direction == null)
                return "You cannot work out which way you came onto this ground.";
        }
        // A crossing names the WATER, not a bearing (#37). "wade across", "swim across" and "cross to the other
        // side" all reached nothing, while "can I get across here" answered in detail — the game could JUDGE a
        // crossing and not make one. judgeCrossing finds the water by looking at the neighbouring ground; so does
        // this, with the same query, so the act and the judgement can never disagree about what is there.
        if (direction == null && CROSSING_VERB.matcher(said).find()) {
            Toward water = toward(chronicle.location(), "OCEAN|WETLAND", null);
            if (water.refusal() != null) return water.refusal();
            direction = water.direction();
        }
        // Climbing, and following a feature (#37). Same shape as the crossing above and the same helper: the
        // GROUND names the direction. "climb the hill", "follow the stream", "follow the shore" and "go down into
        // the valley" all reached nothing, because the move rule wanted a bearing and a hill is not a bearing —
        // yet which way the hill lies is a thing world_chunk has always known.
        //
        // A named kind of ground decides it (the same vocabulary WHICH_WAY answers from, so what the game SAYS
        // lies north is what walking north reaches). Failing a name, bare "up" and "down" go by the elevation.
        if (direction == null && wantsGroundStep(said)) {
            String biomes = null;
            for (String[] kind : GROUND_ASKED_FOR)
                if (groundNamed(said, kind[0]) && (biomes == null || kind[0].length() > biomes.length())) biomes = kind[1];
            // Down beats up only when it is said: "climb down" descends, and a bare "climb" or "scramble" is up.
            Boolean upward = biomes != null ? null
                : said.contains("down") || said.contains("descend") ? Boolean.FALSE
                : said.contains("up") || said.contains("ascend") ? Boolean.TRUE
                : CLIMBING_VERB.matcher(said).find() ? Boolean.TRUE : null;
            if (biomes == null && upward == null)
                return "You could climb, or follow something, but you have not said what.";
            Toward it = toward(chronicle.location(), biomes, upward);
            if (it.refusal() != null) return it.refusal();
            direction = it.direction();
        }
        if (direction == null) return "You shift through the wet ground, but do not commit to a direction.";
        UUID destination = jdbc.query("SELECT next.id FROM world_chunk current JOIN world_chunk next ON next.world_id=current.world_id AND next.grid_x=current.grid_x+? AND next.grid_y=current.grid_y+? WHERE current.id=?", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, direction.dx, direction.dy, chronicle.location());
        if (destination == null) return "The ground gives way toward the edge of what you can cross. You turn back before leaving the land behind.";
        String intoTheRock = caveEntry(chronicle.location(), destination);
        if (intoTheRock != null) return intoTheRock;
        String crossing = waterCrossing(chronicle.id(), destination);
        if (crossing != null) return crossing;
        java.sql.Timestamp occurredTs = java.sql.Timestamp.from(occurredAt);
        jdbc.update("UPDATE world_object SET current_location_id=?, updated_at=? WHERE id=?", destination, occurredTs, chronicle.id());
        jdbc.update("UPDATE chronicle SET current_zone=NULL WHERE id=?", chronicle.id()); // left the settlement's zones behind
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'MOVED',jsonb_build_object('fromLocationId',?::text,'toLocationId',?::text,'direction',?))", chronicle.id(), occurredTs, chronicle.location().toString(), destination.toString(), direction.name());
        jdbc.update("INSERT INTO chronicle_event (chronicle_id,occurred_at,event_type,payload) VALUES (?,?,'CHRONICLE_MOVED',jsonb_build_object('fromLocationId',?::text,'toLocationId',?::text,'direction',?))", chronicle.id(), occurredTs, chronicle.location().toString(), destination.toString(), direction.name());
        recordVisit(chronicle.id(), destination, occurredAt);
        return "You travel " + direction.description + wayItWalked(destination) + ".";
    }
    /**
     * What the hands actually came away with (#30).
     *
     * <p>Every gather in the game announced itself in the same words whether it yielded one berry or nine — "a
     * small handful of ripe berries", "a few dry branches" — while the number it had just computed, which is the
     * whole physical outcome of the act and varies with the richness of the ground, the tool in hand and the
     * season, went unsaid. A player watched their inventory to find out what had happened, which is the opposite
     * of narration witnessing the act.
     *
     * <p>It reports the count and nothing else: no advice on where to gather better, no hint at a tool. Abundance
     * is remarked on only where it is plainly true of what was just taken.
     */
    private String tally(int count, String singular, String plural) {
        if (count <= 1) return " Just the one " + singular + ".";
        if (count >= 8) return " " + count + " " + plural + " in all — this ground is thick with it.";
        return " " + count + " " + plural + " in all.";
    }
    /**
     * How long the act took, as a person would say it (#30).
     *
     * <p>Resting and sleeping already know their span exactly — the player may have asked for two hours, and
     * {@code durationFor} settles it either way — and then said nothing about it, so a five-minute pause and an
     * afternoon on your back read identically. Rounded the way speech rounds, because "fifty-three minutes" is a
     * clock talking, not a body.
     */
    private String humanSpan(int minutes) {
        if (minutes < 10) return "a few minutes";
        if (minutes < 45) return minutes + " minutes";
        if (minutes < 90) return "an hour";
        return Math.round(minutes / 60.0) + " hours";
    }
    /**
     * What the ground you have just walked into is like to walk on (#30).
     *
     * <p>Every step in the world answered with the same eleven words — "the ground shifting under you as you go" —
     * whether the Chronicle had walked into a meadow, a fen or a cave. Repeated a hundred times in a playthrough it
     * is the most-read sentence in the game and the emptiest, which is precisely the robotic narration this ticket
     * is about.
     *
     * <p>The clause is not decoration invented per biome: it is {@code terrain_going.note}, the same row that
     * decides what the country COSTS to cross (V335). One definition of what this ground is like to walk over,
     * read by the clock and by the prose, so the sentence a player reads can never disagree with the time the
     * journey took. Ground with no going recorded keeps the line it always had.
     */
    /**
     * Whether a tree actually stands on this ground (#37) — the same question {@code ResourceEcologyService}
     * already asks to decide how much deadfall a chunk sheds, asked of the same rows, so the prose and the yield
     * can never disagree about whether there is a wood here.
     *
     * <p>Gathering firewood said "from beneath the trees" on open grassland running flat to every horizon, which
     * is the defect #30 named: narration must witness the world, and a wood that is not there is the plainest way
     * of failing that. Wooded biomes carry an unrecorded natural stand, matching the deadfall rule and fellTree's
     * lazy seeding — so a forest with no chunk_flora row yet still has trees in it, exactly as it does for yield.
     */
    /** Whether the words name getting UP something rather than merely looking about (#37). A bare "look around"
     *  is not a climb; "climb a tree to look around" is, and on bare ground it is a claim the world must refuse. */
    private static boolean climbsSomething(String text) {
        String v = text.toLowerCase(Locale.ROOT);
        return (v.contains("climb") || v.contains("shin up") || v.contains("get up into"))
            && (word(v, "tree") || word(v, "trees") || v.contains("trunk") || v.contains("branches"));
    }

    private boolean standingTreesHere(UUID location) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM chunk_flora cf JOIN flora_definition fd ON fd.flora_key=cf.flora_key " +
            "  WHERE cf.chunk_id=? AND fd.organism_type='TREE' AND cf.quantity > 0) " +
            " OR (NOT EXISTS(SELECT 1 FROM chunk_flora cf2 JOIN flora_definition fd2 ON fd2.flora_key=cf2.flora_key " +
            "                 WHERE cf2.chunk_id=? AND fd2.organism_type='TREE') " +
            "     AND EXISTS(SELECT 1 FROM world_chunk wc WHERE wc.id=? AND wc.biome IN " +
            "                ('FOREST','TEMPERATE_FOREST','MOUNTAIN','HIGHLAND','WETLAND','RIVER_BANK')))",
            Boolean.class, location, location, location));
    }

    private String wayItWalked(UUID destination) {
        String note = jdbc.query(
            "SELECT g.note FROM world_chunk c JOIN terrain_going g ON g.biome = c.biome WHERE c.id = ?",
            rs -> rs.next() ? rs.getString(1) : null, destination);
        if (note == null || note.isBlank()) return ", the ground shifting under you as you go";
        String clause = note.trim();
        if (clause.endsWith(".")) clause = clause.substring(0, clause.length() - 1);
        return " — " + Character.toLowerCase(clause.charAt(0)) + clause.substring(1);
    }
    /**
     * The rock has one way in (#158), or null when the step is not into a cave at all.
     *
     * <p>A cave interior is the chamber behind an entrance — the generator only ever makes one where the rock is
     * closed to the sky — so it is not something a Chronicle can walk into off an open mountainside. You go in
     * through the mouth, or along the passage from another chamber. Without this the biome would be a label: a
     * dark, sheltered square you could step onto from the summit as easily as from the doorway, which is not a
     * cave, only a differently-worded piece of mountain.
     *
     * <p>Coming back out is unrestricted, and deliberately so. Nothing about rock stops you leaving.
     */
    private String caveEntry(UUID from, UUID destination) {
        String into = jdbc.query("SELECT biome FROM world_chunk WHERE id=?", rs -> rs.next() ? rs.getString(1) : null, destination);
        if (!"CAVE_INTERIOR".equals(into)) return null;
        String standingOn = jdbc.query("SELECT biome FROM world_chunk WHERE id=?", rs -> rs.next() ? rs.getString(1) : null, from);
        if ("CAVE_MOUTH".equals(standingOn) || "CAVE_INTERIOR".equals(standingOn)) return null;
        return "The rock stands unbroken in front of you. Whatever hollow is behind it, there is no way into it "
             + "from here — a cave is entered at its mouth.";
    }

    /**
     * Water that has to be crossed rather than walked over (#156/#157), or null when the ground takes an ordinary step.
     *
     * <p>{@code move} used to hand over any adjacent chunk without ever asking what it was, so a Chronicle could
     * walk off a beach and stand in the open sea carrying a hundred kilos of stone. Nothing in the world said no.
     *
     * <p>What replaces that is not a wall. Open water can be swum — people have always swum — but not while
     * loaded: the load is what drowns you, which is why anybody crossing water puts it down first. So the sea
     * refuses a burdened Chronicle and takes an unburdened one, and the threshold is a fraction of what they can
     * carry on land rather than a fixed weight, because a stronger body swims a heavier load.
     *
     * <p>A marsh is the same question with a gentler answer. Soft ground will take a walker and will not take a
     * walker with a heavy pack — you sink to the thigh and there is nothing to push off — so the marsh allows far
     * more than the sea does and still has a limit. A shallow ford is the place where neither rule applies, which
     * is what a ford IS, and why fen causeways were built at all.
     *
     * <p>Both thresholds are fractions of what this body can shoulder on dry land rather than fixed weights,
     * because a stronger Chronicle swims and wades a heavier load than a weaker one.
     */
    /**
     * Whether the water ahead will let you over, and what is stopping you if it will not (#37).
     *
     * <p>The world sounds its own depth beautifully — <i>"it shelves off gradually, past a safe wade before
     * long"</i> — and then had no answer for "can I cross here". Crossing exists: {@link #waterCrossing} refuses
     * a Chronicle carrying more than a quarter of their capacity into the sea or three quarters into a fen, a
     * ford lifts the refusal outright, and a laid timber way lifts it over peat. All of it was reachable only by
     * walking into the water and being told no.
     *
     * <p>Answered from those same readers, and answered as a JUDGEMENT: it never moves the Chronicle. The same
     * rule as asking whether water is safe to drink, which used to be answered by drinking it (V392).
     */
    private String[] judgeCrossing(ActiveChronicle chronicle, String text) {
        Direction named = Direction.from(text.toLowerCase(Locale.ROOT));
        java.util.List<java.util.Map<String,Object>> ahead = jdbc.queryForList(
            "SELECT next.id, next.biome, " +
            "  EXISTS(SELECT 1 FROM ecology_site es WHERE es.chunk_id=next.id AND es.site_kind ILIKE '%ford%') AS ford " +
            "FROM world_chunk here JOIN world_chunk next ON next.world_id=here.world_id " +
            "  AND abs(next.grid_x-here.grid_x) + abs(next.grid_y-here.grid_y) = 1 " +
            (named == null ? "" : "  AND next.grid_x = here.grid_x + ? AND next.grid_y = here.grid_y + ? ") +
            "WHERE here.id=? AND next.biome IN ('OCEAN','WETLAND') ORDER BY next.biome",
            named == null ? new Object[]{chronicle.location()} : new Object[]{named.dx, named.dy, chronicle.location()});

        if (ahead.isEmpty())
            return new String[]{"SUCCEEDED", named == null
                ? "You look about for water that would have to be crossed, and there is none within a step of "
                  + "this ground — whatever lies ahead, it is walked over rather than waded."
                : "There is no water to the " + named.description.trim() + " — that ground is walked over, not waded."};

        java.util.Map<String,Object> water = ahead.get(0);
        UUID over = (UUID) water.get("id");
        boolean sea = "OCEAN".equals(water.get("biome"));
        String what = sea ? "the open water" : "the fen";
        if (Boolean.TRUE.equals(water.get("ford")))
            return new String[]{"SUCCEEDED", "There is a ford at " + what + " — the bottom comes up hard and shallow, "
                + "and you would cross it dryshod with whatever you are carrying."};
        if (!sea && laidCrossingAt(over))
            return new String[]{"SUCCEEDED", "A laid way runs out over " + what + ", pegged and planked. It will "
                + "carry you and your load both, which is the whole reason such roads were ever built."};

        var load = items.currentLoad(chronicle.id());
        int capacity = load.sustainedMassCapacityGrams();
        int allowed = sea ? capacity / 4 : capacity * 3 / 4;
        if (capacity <= 0 || load.massGrams() <= allowed)
            return new String[]{"SUCCEEDED", sea
                ? "You could swim " + what + " with what you have on you, though everything you carry goes into it with you."
                : "You could wade " + what + " with what you have on you — slow going, and every step found before it is taken."};

        int overweight = load.massGrams() - allowed;
        return new String[]{"SUCCEEDED", "Not with this load. " + (sea
            ? "The bottom falls away out there, and what you are carrying would take you down with it"
            : "The bog would have you before halfway, and what is on your back is the reason")
            + " — about " + (overweight / 1000) + " kg too much for it. Set that down, or find a ford."};
    }

    private String waterCrossing(UUID chronicle, UUID destination) {
        String biome = jdbc.query("SELECT biome FROM world_chunk WHERE id=?", rs -> rs.next() ? rs.getString(1) : null, destination);
        if (biome == null) return null;
        boolean ford = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM ecology_site WHERE chunk_id=? AND site_kind ILIKE '%ford%')", Boolean.class, destination));
        if (ford) return null; // a ford is where the water is crossed dryshod; that is the whole of what it is
        boolean sea = "OCEAN".equals(biome), marsh = "WETLAND".equals(biome);
        if (!sea && !marsh) return null;
        // The built counterpart of a ford (#77, V333). Until now only a crossing the WORLD happened to place could
        // lift the refusal, so a camp on the wrong side of a fen either found one or carried nothing across for
        // ever. A laid timber way — pegs driven in crossed pairs, a rail in the crotch, planks pinned along it —
        // makes the bog carry a full load, which is the whole reason such roads were ever built. Read from
        // construction_kind rather than named here, and NOT offered to the sea: a plank road on peat is not a span
        // over a channel. A rotted way carries nothing, which is what happens to a fen track nobody repairs.
        if (marsh && laidCrossingAt(destination)) return null;
        var load = items.currentLoad(chronicle);
        int capacity = load.sustainedMassCapacityGrams();
        if (capacity <= 0) return null;
        int allowed = sea ? capacity / 4 : capacity * 3 / 4;
        if (load.massGrams() <= allowed) return null;
        return sea
            ? "You wade out until the bottom falls away, and what you are carrying takes you straight down with it. "
            + "You struggle back to the shallows and stand there dripping. Not with this load."
            : "You start across the soft ground and sink to the knee, then the thigh. Loaded as you are there is no "
            + "bottom to push off. You work your way back to firmer ground — this wants a ford, a causeway, or a "
            + "lighter back.";
    }
    /** A sound built way laid over the soft ground here, standing and whole enough to walk loaded (#77, V333). */
    private boolean laidCrossingAt(UUID chunk) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN construction_kind ck ON ck.project_kind=cp.project_kind " +
            "JOIN world_object w ON w.id=cp.object_id " +
            "WHERE w.current_location_id=? AND ck.crosses_soft_ground AND cp.state='COMPLETED' " +
            "  AND cp.integrity_percent>0 AND w.lifecycle_state='ACTIVE')", Boolean.class, chunk));
    }

    /**
     * The first water on the way that this body, loaded as it is, cannot get across (#77).
     *
     * <p>A single step into a fen has been refused a laden Chronicle since #156/#157 — soft ground will not take a
     * walker with a heavy pack — but a JOURNEY asked nothing at all. Naming a place on the far side of the same fen
     * and setting out carried a Chronicle straight over it with a full load on their back, so the rule held for the
     * step and not for the day's walk, which is the one place it matters most.
     *
     * <p>The line is sampled the way its cost is (V335): one point per chunk of distance, so what refuses passage is
     * the country actually crossed. The ground the Chronicle is already standing on is skipped — they are on it, so
     * it plainly took them. A ford or a laid causeway on the line lets the journey through exactly as it lets a step
     * through, which is what makes building one across the fen worth the timber.
     *
     * @return the refusal to tell the player, or null when the way is passable for what they are carrying.
     */
    private String impassableOnTheWay(UUID chronicle, UUID from, UUID to) {
        java.util.List<UUID> line = jdbc.queryForList(
            "WITH a AS (SELECT world_id, grid_x, grid_y FROM world_chunk WHERE id=?), " +
            "     b AS (SELECT grid_x, grid_y FROM world_chunk WHERE id=?), " +
            "     d AS (SELECT GREATEST(ABS(b.grid_x-a.grid_x), ABS(b.grid_y-a.grid_y)) AS n FROM a, b) " +
            "SELECT c.id FROM d, generate_series(1, d.n) s, a, b " +
            "  JOIN world_chunk c ON TRUE " +
            " WHERE c.world_id = a.world_id AND d.n > 0 " +
            "   AND c.grid_x = ROUND(a.grid_x + (b.grid_x - a.grid_x)::numeric * s / d.n) " +
            "   AND c.grid_y = ROUND(a.grid_y + (b.grid_y - a.grid_y)::numeric * s / d.n) " +
            " ORDER BY s", UUID.class, from, to);
        for (UUID step : line) {
            String refusal = waterCrossing(chronicle, step);
            if (refusal != null) return refusal;
        }
        return null;
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
            // Measured on the world's clock, not this machine's. last_visited_at is stamped in simulated time by
            // recordVisit, so comparing it to the wall clock asked whether the visit was recent in OUR days —
            // meaningless in a world whose clock runs at its own rate and sits years from today's date, and it
            // could make a place walked yesterday unfindable or one last seen a decade ago fresh in mind.
            boolean recent = last != null
                && last.toInstant().isAfter(ticks.current().simulatedAt().minus(java.time.Duration.ofDays(4)));
            boolean markerHere = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM location_marker WHERE chunk_id=?)", Boolean.class, chunk));
            boolean onMap = Boolean.TRUE.equals(jdbc.queryForObject("WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') SELECT EXISTS(SELECT 1 FROM reachable x JOIN literature_document d ON d.object_id=x.id JOIN literature_revision rv ON rv.id=d.current_revision_id WHERE d.document_kind='MAP' AND rv.content ILIKE ?)", Boolean.class, chronicle.id(), "%"+name+"%"));
            Integer visits = jdbc.queryForObject("SELECT COALESCE((SELECT visit_count FROM chronicle_chunk_visit WHERE chronicle_id=? AND chunk_id=?),0)", Integer.class, chronicle.id(), chunk);
            boolean routine = visits != null && visits >= 5;
            boolean locatable = onMap || (markerHere && memorized) || routine || (memorized && recent);
            if (!locatable) continue;
            int gx=(int)n.get("grid_x"), gy=(int)n.get("grid_y");
            int distance = Math.max(Math.abs(gx-cx), Math.abs(gy-cy));
            String reason = onMap ? "map" : routine ? "routine" : markerHere ? "marker" : "memory";
            return new TravelPlan(chunk, distance, reason, goingBetween(chronicle.location(), cx, cy, gx, gy, distance));
        }
        return null;
    }
    /**
     * How hard the country between here and there is to walk, in minutes per chunk (#77/#155, V335).
     *
     * <p>A journey used to cost eighteen minutes a chunk whatever it crossed, so a meadow, a fen and a mountainside
     * were the same walk. The line between the two places is sampled at one point per chunk of distance and the
     * going of the ground under each point averaged, so the cost is the country actually crossed rather than the
     * two ends of it: a road round the head of a marsh is not the same journey as one straight through it.
     *
     * <p>Falls back to the historical flat rate when the ground has no going recorded, so a biome added to the
     * generator before it is added to the table behaves exactly as it always did instead of faulting mid-journey.
     *
     * <p>A WAY SOMEBODY LAID takes time off the ground it lies on (#77, V336), so the labour of building a road is
     * repaid every time anybody walks it — which is the only reason roads have ever been built. Read per sampled
     * chunk, so a path laid over the worst mile of a route improves that mile and no other; a sound way only, so a
     * road nobody repairs stops being a road. It can never bring any ground below the going of open grass: a path
     * through a wood makes the wood walkable, it does not make it a meadow.
     */
    private int goingBetween(UUID from, int cx, int cy, int gx, int gy, int distance) {
        if (distance <= 0) return FLAT_MINUTES_PER_DISTANCE;
        Double average = jdbc.query(
            "SELECT AVG(GREATEST((SELECT MIN(minutes_per_chunk) FROM terrain_going), " +
            "                    g.minutes_per_chunk - COALESCE(laid.eased, 0)))::float8 " +
            "  FROM generate_series(0, ?) s " +
            "  JOIN world_chunk c ON c.world_id=(SELECT world_id FROM world_chunk WHERE id=?) " +
            "   AND c.grid_x = ROUND(?::numeric + (?::numeric - ?::numeric) * s / ?::numeric) " +
            "   AND c.grid_y = ROUND(?::numeric + (?::numeric - ?::numeric) * s / ?::numeric) " +
            "  JOIN terrain_going g ON g.biome = c.biome " +
            "  LEFT JOIN LATERAL (SELECT MAX(ck.eases_going) AS eased FROM construction_project cp " +
            "     JOIN construction_kind ck ON ck.project_kind = cp.project_kind " +
            "     JOIN world_object o ON o.id = cp.object_id " +
            "    WHERE o.current_location_id = c.id AND cp.state = 'COMPLETED' AND cp.integrity_percent > 0 " +
            "      AND o.lifecycle_state = 'ACTIVE') laid ON TRUE",
            rs -> rs.next() ? (Double) rs.getObject(1) : null,
            distance, from, cx, gx, cx, distance, cy, gy, cy, distance);
        return average == null ? FLAT_MINUTES_PER_DISTANCE : Math.max(5, (int) Math.round(average));
    }
    private String[] travelTo(ActiveChronicle chronicle, TravelPlan plan, Instant at) {
        if (plan == null) return new String[]{"FAILED", "You try to fix the place in your mind and make for it, but you cannot call the way to mind clearly enough to set out. Some places, once, are not places you can find again."};
        if (plan.destination().equals(chronicle.location())) return new String[]{"SUCCEEDED", "You are already at the place you meant to reach."};
        // Water on the way stops a journey as surely as it stops a step (#77). Knowing where a place is has never
        // been the same as being able to get to it with what you are carrying, and until now it was: the load rule
        // held for one pace into a fen and not for a day's walk across one.
        String barred = impassableOnTheWay(chronicle.id(), chronicle.location(), plan.destination());
        if (barred != null) return new String[]{"FAILED",
            "You set out, and the way brings you up against water. " + barred + " You come back the way you went, "
            + "no nearer the place you meant to reach."};
        java.sql.Timestamp ts = java.sql.Timestamp.from(at);
        jdbc.update("UPDATE world_object SET current_location_id=?, updated_at=? WHERE id=?", plan.destination(), ts, chronicle.id());
        jdbc.update("UPDATE chronicle SET current_zone=NULL WHERE id=?", chronicle.id()); // arrived at a new place; old zones are behind
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'MOVED',jsonb_build_object('fromLocationId',?::text,'toLocationId',?::text,'wayfinding',?))", chronicle.id(), ts, chronicle.location().toString(), plan.destination().toString(), plan.reason());
        jdbc.update("INSERT INTO chronicle_event (chronicle_id,occurred_at,event_type,payload) VALUES (?,?,'CHRONICLE_MOVED',jsonb_build_object('fromLocationId',?::text,'toLocationId',?::text,'wayfinding',?))", chronicle.id(), ts, chronicle.location().toString(), plan.destination().toString(), plan.reason());
        recordVisit(chronicle.id(), plan.destination(), at);
        String how = switch (plan.reason()) { case "map" -> "following the trails you sketched on your map"; case "routine" -> "along a way your feet have walked many times"; case "marker" -> "guided by the marker you once left"; default -> "holding the place firmly in memory"; };
        // What the journey was like is not invented for the sentence: it is the going the clock just charged the
        // Chronicle for (#30/V335). A walk over open grass and a scramble over forty minutes of mountain a chunk
        // arrived with the same nine words, which told a player nothing about the day they had just spent — and a
        // laid way now shortens that number, so the line moves when the road does.
        String country = plan.minutesPerChunk() <= 17 ? ", over ground that gave you no trouble,"
            : plan.minutesPerChunk() >= 34 ? ", and the country between took everything you had,"
            : plan.minutesPerChunk() >= 26 ? ", over ground that made you work for it," : ",";
        return new String[]{"SUCCEEDED", "You set out, " + how + country + " and come at last to the place you meant to reach."};
    }
    /**
     * Leave a physical marker at the current place — a blaze carved on a tree, a
     * cairn of stones, a driven stake. A marker makes a spot recognizable and can
     * later anchor a name; on its own it carries no name.
     */
    private String[] markLandmark(ActiveChronicle chronicle, String text, UUID actionId, Instant at) {
        String v = text.toLowerCase(Locale.ROOT);
        String kind; String need;
        // A bundle made for this comes first (#75). The three kinds below are all improvisations — stones you
        // gather, a branch you cut, a blaze you need a blade for — and the catalogue carries a
        // tracking_marker_bundle, "a bundle to mark a trail", which nothing read. So a Chronicle could make the
        // one thing meant for marking a way and still be told they had nothing to mark with, or need a knife to
        // do it. Named explicitly rather than used as a silent fallback: a marker bundle is what you reach for
        // when marking a trail, and stones are what you reach for when you have no bundle.
        if (v.contains("trail") || v.contains("marker") || v.contains("tag") || v.contains("flag")) { kind = "TRAIL_MARK"; need = "tracking_marker_bundle"; }
        else if (v.contains("cairn") || v.contains("pile") || v.contains("stack") || (v.contains("stone") && !v.contains("carve"))) { kind = "CAIRN"; need = "field_stone"; }
        else if (v.contains("stake") || v.contains("post") || v.contains("stick") || v.contains("stave")) { kind = "STAKE"; need = "dry_branch"; }
        else { kind = "BLAZE"; need = "tool"; }
        if (kind.equals("TRAIL_MARK")) { if (!items.hasAtLeast(chronicle.id(),"tracking_marker_bundle",1)) return new String[]{"FAILED","You reach for a trail marker and find none — the bundle is spent, or you never made one. Stones or a cut stake would do instead."}; items.consumeOne(chronicle.id(),"tracking_marker_bundle",at); }
        else if (kind.equals("CAIRN")) { if (!items.hasAtLeast(chronicle.id(),"field_stone",3)) return new String[]{"FAILED","You cast about for stones to pile, but you do not have enough to raise anything that would stand and be seen."}; for (int i=0;i<3;i++) items.consumeOne(chronicle.id(),"field_stone",at); }
        else if (kind.equals("STAKE")) { if (!items.hasAtLeast(chronicle.id(),"dry_branch",1)) return new String[]{"FAILED","You have nothing to drive into the ground as a marker."}; items.consumeOne(chronicle.id(),"dry_branch",at); }
        else { if (!items.hasCuttingTool(chronicle.id())) return new String[]{"FAILED","You set a hand to the bark, but with no blade you can cut no lasting mark."}; }
        String shape = extractShape(v);
        UUID id = UUID.randomUUID();
        String label = kind.equals("TRAIL_MARK") ? "Trail marker" : kind.equals("CAIRN") ? "Stone cairn" : kind.equals("STAKE") ? "Driven stake" : "Carved blaze";
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'MARKER',?,?)", id, label, chronicle.location());
        jdbc.update("INSERT INTO location_marker (object_id,chunk_id,marker_kind,description,created_by_chronicle_id,created_at) VALUES (?,?,?,?,?,?)", id, chronicle.location(), kind, shape, chronicle.id(), java.sql.Timestamp.from(at));
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'MARKED',jsonb_build_object('kind',?))", id, java.sql.Timestamp.from(at), kind);
        String made = kind.equals("TRAIL_MARK") ? "You set a marker from the bundle where it will be seen from the way you came"
            : kind.equals("CAIRN") ? "You stack stones into a cairn that will stand and be seen" : kind.equals("STAKE") ? "You drive a stake firmly into the ground" : "You cut a clear blaze into the bark of a tree";
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
    /**
     * How long the act takes (#216).
     *
     * <p>Two rules, and only the second is catalogue. A player who writes an explicit span — "rest for two
     * hours", "work at it for forty minutes" — gets that span, clamped between a minute and a day; that is
     * parsing and stays here. When they did not say, the act's own impact card answers.
     *
     * <p>That per-intent default was the fourth and last switch over the Intent enum in this file, alongside the
     * three the card has already absorbed. Four lists over the same 128 values, each ending in a {@code
     * default}, is four independent chances to forget an intent — and forgetting is silent, because a default
     * arm looks exactly like a decision. Both defects found while moving them were of that kind and neither was
     * visible from inside the switch that was wrong: {@code COPPICE} cost nothing while taking the canopy down,
     * and {@code REPAIR_LEAN_TO} spent twelve energy and eight hygiene in five minutes.
     *
     * <p>Five minutes when a card is missing, which is what the switch's own default gave, so a catalogue gap
     * behaves as it always did rather than faulting mid-action.
     */
    private int durationFor(String action, Intent intent) {
        Matcher match = DURATION.matcher(action);
        if (match.find()) { int amount = Integer.parseInt(match.group(1)); int minutes = match.group(2).toLowerCase(Locale.ROOT).startsWith("h") ? amount * 60 : amount; return Math.max(1, Math.min(minutes, 24 * 60)); }
        Integer carded = jdbc.query("SELECT duration_minutes FROM activity_impact WHERE intent_key = ?",
            rs -> rs.next() ? rs.getInt(1) : null, intent.name());
        return carded == null ? 5 : carded;
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
        if((value.contains("craft")||value.contains("make")||value.contains("carve")||value.contains("prepare"))&&((value.contains("fire")&&(value.contains("kit")||value.contains("drill")||value.contains("board")))||(value.contains("hearth")&&!value.contains("clay hearth")&&!value.contains("clay-lined"))||value.contains("spindle"))) return Intent.CRAFT_FIRE_KIT;
        // Charred tinder is its own thing (V49) — made by smothering fiber rather than
        // teasing it loose — so it goes to the ignition-kit path, not the tinder nest.
        if((value.contains("make")||value.contains("prepare")||value.contains("gather")||value.contains("form")||value.contains("bundle"))&&value.contains("tinder")&&!value.contains("char")) return Intent.CRAFT_TINDER;
        // A "drying rack" is a staged structure (V58), not a shelf — let it fall through
        // to the assembly engine rather than being caught by "rack" here.
        // Workstations (V69) before the generic desk/table rule, which also matches "bench"/"table": a
        // woodworking/stoneworking bench, a workbench, or a loom is a workstation (it eases the crafts it
        // serves); a plain "bench" or "table" is still a desk.
        // A furnace is not a workstation (#37). This claimed "build a bloomery furnace" -- a keyword the
        // furnace itself declares -- and answered with a workbench's requirements, branches and fibre, when a
        // furnace wants clay. The bloomery is the station the ENTIRE metal chain turns on: smelt_copper and
        // alloy_bronze both name it, so nothing metal is reachable without one.
        if(!value.contains("furnace")&&!value.contains("bloomery")
           &&(value.contains("craft")||value.contains("make")||value.contains("build")||value.contains("construct")||value.contains("assemble")||value.contains("set up"))&&(value.contains("loom")||value.contains("workbench")||value.contains("work bench")||((value.contains("woodworking")||value.contains("stoneworking")||value.contains("weaving")||value.contains("sewing")||value.contains("leatherwork"))&&(value.contains("bench")||value.contains("table")||value.contains("station"))))) return Intent.CRAFT_WORKSTATION;
        if((value.contains("craft")||value.contains("make")||value.contains("build")||value.contains("construct")||value.contains("assemble"))&&(value.contains("shelf")||value.contains("shelves")||value.contains("rack")||value.contains("archive"))&&!value.contains("drying")&&!value.contains("fuel rack")&&!value.contains("wood rack")&&!value.contains("firewood rack")&&!value.contains("log rack")&&!value.contains("kindling rack")&&!value.contains("hay rack")&&!value.contains("fodder rack")&&!value.contains("smoke rack")) return Intent.CRAFT_SHELF;
        if((value.contains("craft")||value.contains("make")||value.contains("build")||value.contains("construct")||value.contains("assemble"))&&(value.contains("desk")||value.contains("table")||value.contains("workbench")||value.contains("bench"))&&!value.contains("sleeping bench")) return Intent.CRAFT_DESK;
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
        // "put it by" is the oldest English for storing a thing (#106), and the only phrasing of the fodder chain
        // that still reached nothing — `store the hay` worked and `put the hay by` did not. Matched on the
        // sentence ENDING in "by", which is what distinguishes it from placing something beside something else:
        // "put the pot by the fire" carries on past the preposition and means nowhere near this.
        // Keeping seed back for next year IS putting grain by (#37): sowing consumes the grain a Chronicle
        // carries, so seed held for a season is grain in a store and nothing else. Both reached nothing.
        if((value.contains("save")||value.contains("keep")||value.contains("hold back"))
           &&value.contains("seed")&&!value.contains("sow")&&!value.contains("plant")) return Intent.STORE;
        if((value.contains("put")&&value.contains("away"))||value.contains("cache")||value.contains("stockpile")||value.contains("put in storage")||value.contains("stow away")||value.contains("stash away")
           ||(value.contains("put")&&value.strip().endsWith(" by"))) return Intent.STORE;
        // "store the food" with no container named (#37). The rules above want a container noun or the words
        // "away"; a bare storage verb — the most ordinary way to say it — reached nothing.
        //
        // Anchored on store/stow/stash as a VERB, because as a NOUN the same word is a building: a wood store, a
        // log store, a fodder store, a hay store are all assemblies with their own keywords, and "designate this
        // the store ground" is a DESIGNATE. A first cut of this rule used word(value,"store") and took all five
        // of them — caught by noJavaIntentShadowsAnAssemblysOwnKeywords, which is exactly the #513 trap it is
        // there to catch. No assembly keyword begins with the bare verb, so the phrase must.
        String storeVerb = value.trim();
        if((storeVerb.startsWith("store ")||storeVerb.startsWith("stow ")||storeVerb.startsWith("stash ")
            ||value.contains(" store the ")||value.contains(" stow the ")||value.contains(" stash the ")
            ||value.contains(" store my ")||value.contains(" stow my ")||value.contains(" stash my "))
           &&!value.contains("build")&&!value.contains("construct")&&!value.contains("make")&&!value.contains("raise")
           &&!value.contains("set up")&&!value.contains("put up")&&!value.contains("erect")&&!value.contains("dig")) return Intent.STORE;
        // The rest of how a person says it (#37). "store the meat", "put it away", "stow it", "cache it" and
        // "put it in the basket" all worked; these seven reached nothing at all, across every thing you would
        // put by — 26 of 75 phrasing/thing pairs dead, over a mechanism that was finished.
        //
        // Gated on a thing worth keeping, so that "bring the stock in" stays the animals' own rule and "bring
        // in the harvest" stays the crop's.
        //
        // Fuel is included, because firewood is a thing you lay up — but the three phrasings GATHER_BRANCHES
        // claims for it are held back by name, since "bring in more wood" is going out to get some and "bring
        // the firewood in" is putting away what you already gathered. Leaving fuel out entirely left seven of
        // these phrasings dead for it; taking the gather's phrasings would have broken the gather.
        if((value.contains("bring in")||value.contains("bring the")&&value.contains(" in")
            ||value.contains("take")&&value.contains("inside")||value.contains("set")&&value.contains(" by")
            ||value.contains("by for later")||value.contains("under cover")||value.contains("lay")&&value.contains(" up")
            ||value.contains(" in the store")||value.contains(" in my store"))
           &&(value.contains("meat")||value.contains("fish")||value.contains("grain")||value.contains("food")
              ||value.contains("hide")||value.contains("stores")||value.contains("catch")||value.contains("game")
              ||value.contains("firewood")||value.contains("fuel")||value.contains("wood"))
           // Each phrasing another rule claims for these things is held back BY NAME, with the rule that
           // claims it. "bring in more wood" is going out to get some and "bring in the grain" is reaping a
           // standing crop, while "bring the firewood in" and "bring the grain in" are putting away what you
           // already have. The local suite caught the grain one, which is what that regression test is for.
           &&!((value.contains("firewood")||value.contains("fuel")||value.contains("wood"))
               &&(value.contains("bring in")||value.contains("lay in")||value.contains("stock up")||value.contains("gather")))
           &&!((value.contains("grain")||value.contains("harvest"))
               &&(value.contains("bring in")||value.contains("reap")||value.contains("harvest the")))
           &&!value.contains("stock")&&!value.contains("herd")&&!value.contains("flock")&&!value.contains("crop")
           &&!value.contains("the field")&&!items.namesAKeptAnimal(value)) return Intent.STORE;
        // Taking everything out of a container (#37). The container system could open, close, seal, put a named
        // thing in and take a named thing out — and had no way to EMPTY one, so "empty the pot" reached nothing
        // and a Chronicle who could not remember what they had put by had to name each thing in turn.
        //
        // Before the access rules, or "empty the pot" would be read as closing it; and before the storage rules,
        // so that "empty" is never taken for a storage verb.
        if((value.contains("empty")||value.contains("turn out")||value.contains("tip out")||value.contains("take everything out")
            ||value.contains("take it all out")||value.contains("unpack everything")||value.contains("empty out"))
           &&(containerNoun||value.contains("container"))) return Intent.EMPTY_CONTAINER;
        // PICK_UP (#67 take/retrieve/unpack): explicit retrieval verbs, or "take/get/remove/unpack X out of/from
        // the <container/storage/ground>" — distinct from gathering raw growth from the world.
        if(value.contains("pick up")||value.contains("pick it up")||value.contains("pick them up")||value.contains("pick it back")||value.contains("picked up")||value.contains("grab")||value.contains("retrieve")||value.contains("recover")||value.contains("take back")||value.contains("take it back")||(value.contains("fetch")&&!value.contains("water"))||value.contains("lift the")||value.contains("lift it")||value.contains("lift up")
           ||((value.contains("take")||value.contains("get")||value.contains("remove")||value.contains("pull")||value.contains("unpack")||value.contains("empty"))&&(value.contains(" out of ")||value.contains(" from the ")||value.contains(" from storage")||value.contains(" off the ground")||value.contains(" from where"))&&(containerNoun||value.contains("ground")||value.contains("storage")||value.contains("where i")||value.contains("where it")))) return Intent.PICK_UP;
        // Container access (#67): open/close/seal a reachable container. Scoped to a container noun so the common
        // verbs open/close/cover/seal do not collide with reading, sheltering, or other uses.
        if(value.contains("take the lid off")||value.contains("remove the lid")||value.contains("lift the lid")||value.contains("get the lid off")) return Intent.OPEN_CONTAINER;
        if((value.contains("open")||value.contains("unfasten")||value.contains("uncover")||value.contains("unlatch")||value.contains("unseal")||value.contains("unstopper"))&&containerNoun) return Intent.OPEN_CONTAINER;
        if((value.contains("close")||value.contains("shut")||value.contains("cover")||value.contains("fasten")||value.contains("seal")||value.contains("stopper")||value.contains("put the lid on")||value.contains("put a lid on")||value.contains("the lid on")||value.contains("its lid"))&&containerNoun) return Intent.CLOSE_CONTAINER;
        // Taking a construction apart (#70 dismantle/salvage) — before the lean-to check, so "dismantle the
        // lean-to" is a dismantle rather than a build stage. Recovers only a fraction of the materials.
        if(value.contains("dismantle")||value.contains("take apart")||value.contains("pull down")||value.contains("tear down")||value.contains("salvage")||value.contains("reclaim")||(value.contains("recover")&&value.contains("material"))) return Intent.DISMANTLE;
        if(value.contains("lean-to") || value.contains("lean to")) return classifyLeanTo(value);
        // Garment work before the generic craft rules, so "sew a hide coat" is not
        // swallowed by the furniture or tool branches.
        if((value.contains("sew")||value.contains("stitch")||value.contains("craft")||value.contains("make")||value.contains("weave"))&&(value.contains("coat")||value.contains("cloak")||value.contains("legging")||value.contains("trouser")||value.contains("tunic")||value.contains("boot")||value.contains("shoe")||value.contains("garment")||value.contains("clothing")||(value.contains("hide")&&value.contains("wear")))
           // A snowshoe is not a shoe (#37). Adding "shoe" in #708 so that "sew a pair of shoes" would reach the
           // maker also handed it "snowshoes", which CONTAINS it -- so asking for snowshoes made a pair of hide
           // boots, silently and successfully, which is the worst way for an answer to be wrong. They are their
           // own two processes (make_snowshoe_left/right) and belong to the material matcher, not to garments.
           &&!value.contains("snowshoe")) return Intent.CRAFT_GARMENT;
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
           &&(value.contains("flint")||value.contains("chert")||value.contains("obsidian")||value.contains("pyrite")||value.contains("quartz")||value.contains("crystal")||value.contains("mineral")||value.contains("salt")||value.contains("ochre")||value.contains("soapstone")||value.contains("sandstone")||value.contains("pumice")||value.contains("granite")||value.contains("basalt")||value.contains("slate")||value.contains("hammerstone")||value.contains("cobble")||value.contains("gravel")||value.contains("river sand")||value.contains("sand bar")||value.matches("(?s).*\\bore\\b.*")||value.contains("tool stone")
           // ...or the catalogue itself knows the name. The literals above are a hand-kept copy of part of
           // mineral_definition, and a copy goes stale the moment a mineral is added: V305's fire clay is dug
           // by nobody without this, and limestone — never in the list at all — has been undiggable by name
           // since it was catalogued. Asking the table instead is the same fix this codebase has needed
           // everywhere else a capability was declared in data and named as literals in code.
           ||items.namesADugMineral(value))) return Intent.GATHER_MINERAL;
        // Fire management (#71): put out, bank, or tend a fire — checked before the ignition rules so "put out
        // the fire" is not read as making one.
        // "smother it" and "kick it out" reached nothing (#37): the rule wanted the word "fire" beside the verb,
        // and a person standing over one says "it". Gated on the verb being unambiguous about killing a flame —
        // smother, stamp out and douse are nothing else's words — so "it" is safe to allow.
        if(value.contains("extinguish")||value.contains("put out the fire")||value.contains("put the fire out")||value.contains("douse")||value.contains("smother the fire")||value.contains("stamp out the fire")||value.contains("quench the fire")||((value.contains("put out")||value.contains("kill"))&&value.contains("fire"))
           ||value.contains("smother it")||value.contains("smother the")||value.contains("stamp it out")||value.contains("kick it out")
           ||value.contains("put it out")||value.contains("quench it")) return Intent.EXTINGUISH_FIRE;
        // And "bank it for the night" and "damp it down", which are how banking is actually said.
        if((value.contains("bank")||value.contains("cover the coals")||value.contains("cover the embers")||value.contains("preserve the ember")||value.contains("rake the coals"))&&(value.contains("fire")||value.contains("coal")||value.contains("ember"))
           ||value.contains("bank it")||value.contains("bank them")||value.contains("damp it down")||value.contains("damp down the")
           ||value.contains("let it burn down")||value.contains("let the fire burn down")) return Intent.BANK_FIRE;
        if((value.contains("tend")||value.contains("keep the fire")||value.contains("keep it going")||value.contains("maintain")
            // Act eleven, with wolves close: "build up the fire against them" reached nothing at all, while
            // "feed the fire" worked. Both are the same act, and this is the one a person says under pressure.
            ||value.contains("build up")||value.contains("pile more")||value.contains("more wood on")
            // "build the fire up" puts the noun between the verb and the particle, which "build up" misses.
            ||(value.contains("build")&&value.contains(" up")))
           &&word(value,"fire")) return Intent.FEED_FIRE;
        // ...and the ones that name the WOOD instead of the fire (#37). "put more wood on" reached nothing while
        // "stoke the fire" worked, because the rule above wants the word "fire" beside the verb — and a person
        // standing over one talks about the wood. Nothing else in this world is something you put more of "on".
        if((value.contains("more wood")||value.contains("more fuel")||value.contains("another branch")
            ||value.contains("more branches")||value.contains("more sticks"))
           &&(value.contains("on")||value.contains("put")||value.contains("add")||value.contains("throw"))
           &&!value.contains("gather")&&!value.contains("collect")&&!value.contains("find")
           &&!value.contains("need")&&!value.contains("enough")&&!value.contains("how much")) return Intent.FEED_FIRE;
        // Naming a real ignition technique IS asking for fire (V49). A player who
        // writes "strike flint against pyrite" or "spin the bow drill" should not
        // have to also say the word "fire" to be understood.
        if(value.contains("bow drill")||value.contains("hand drill")||value.contains("fire plough")||value.contains("fire plow")||value.contains("fire saw")||value.contains("fire piston")||value.contains("pyrite")||value.contains("tinder nest")
           ||((value.contains("strike")||value.contains("spark")||value.contains("scrape"))&&(value.contains("flint")||value.contains("steel")||value.contains("stone")))
           ||((value.contains("focus")||value.contains("lens")||value.contains("magnify"))&&(value.contains("sun")||value.contains("tinder")))
           ||(value.contains("ember")&&(value.contains("carry")||value.contains("transfer")||value.contains("bring")||value.contains("nurse")
              // Breathing a banked fire back up is how a fire is kept overnight, and it reached nothing (#37).
              ||value.contains("blow")||value.contains("fan")||value.contains("breathe")||value.contains("coax")))) return Intent.LIGHT_FIRE;
        // How the fire IS (#37). fire_state.fuel_minutes is the burning time remaining TO THE MINUTE — light()
        // sets it, feed() adds to it, bank() raises it and its ceiling, the tick counts it down every turn, the
        // body reads it when it decides how fast you lose heat, and the Auditor holds that a fire cannot be
        // alight with none of it left. NOT ONE SENTENCE COULD ASK. Placed before the acts, so tending keeps its
        // verbs and only the questions come here.
        if((value.contains("is the fire")||value.contains("how is the fire")||value.contains("still going")
            ||value.contains("still burning")||value.contains("still alight")||value.contains("any heat left")
            ||value.contains("how long will it burn")||value.contains("how long will the fire")
            ||value.contains("last the night")||value.contains("see the night out")||value.contains("enough wood")
            ||value.contains("enough fuel")||value.contains("how much firewood")||value.contains("how much fuel")
            ||value.contains("check the fire")||value.contains("look at the fire")||value.contains("how is my fire")
            // What a roaring fire will do to what stands beside it (#219). construction_kind.flammable, the
            // 120-fuel-minute threshold and the dry sky are all simulated, and a keeper whose lean-to was
            // quietly losing 4% an hour to their own hearth was told nothing at all.
            ||value.contains("safe to leave it")||value.contains("safe to leave the fire")
            ||value.contains("will it spread")||value.contains("catch fire")||value.contains("too close to the fire"))
           &&!value.contains("light")&&!value.contains("put out")&&!value.contains("feed")) return Intent.CHECK_FIRE;
        // GETTING ONE GOING, by the words people use (#37). The rule above knows the METHODS and the legacy rule
        // knows "light"/"ignite" — so `light a fire` worked and `start a fire`, `make a fire`, `kindle a fire`
        // and `get a fire going` ALL REACHED NOTHING. Two of those are the commonest ways of saying the
        // commonest thing a cold person does.
        //
        // The fire-TOOL crafts run earlier and keep their sentences (a fire kit, a tinder bundle, a fire bow);
        // the pit is excluded by name here because BUILD_FIRE_PIT is claimed later, in the legacy rule.
        if(((value.contains("start")||value.contains("make")||value.contains("kindle")||value.contains("set")
             ||value.contains("get")||value.contains("build"))&&word(value,"fire")
            &&!value.contains("pit")&&!value.contains("firepit")&&!value.contains("hearth")&&!value.contains("ring")
            // "set fire to THEIR store" is arson against a people, which ConductService owns and prices. The
            // conduct pre-pass takes it whenever a community is in reach — but off the isle this rule would have
            // answered it by crouching down to light a campfire, which is not what was said.
            &&!value.contains("their")&&!value.contains("the village")&&!value.contains("the store")
            &&!value.contains("the houses")&&!value.contains("the hut"))
           ||value.contains("strike a spark")||value.contains("strike sparks")||value.contains("light the tinder")
           ||value.contains("get a blaze")||value.contains("coax it alight")||value.contains("coax a flame")) return Intent.LIGHT_FIRE;
        // Gathering FUEL, by the plain word for it (#37). `gather firewood`, `collect firewood`, `gather
        // branches` and `lay in firewood` all worked — and `gather wood`, `gather more wood`, `gather fuel` and
        // `get some firewood` reached nothing, because the rule wanted "firewood" or "branch" and a person
        // picking up sticks for the fire says "wood". The commonest survival act there is, half unsayable.
        //
        // Held as a WORD: "wood" sits inside "wooden", "woodland" and "deadwood", and gathering a wooden
        // component is a different act from picking up sticks. Found by a test written for the fire.
        if((value.contains("gather")||value.contains("collect")||value.contains("fetch")||value.contains("bring")
            ||value.contains("get ")||value.contains("pick up")||GATHERING_VERB.matcher(value).find())
           &&(word(value,"wood")||word(value,"fuel")||word(value,"firewood")||value.contains("more wood"))
           &&!value.contains("wooden")&&!value.contains("woodland")
           // The questions about fuel are CHECK_FIRE's, above, and the making verbs are the crafts'.
           &&!value.contains("how much")&&!value.contains("enough")&&!MAKING_SOMETHING.matcher(value).find()) return Intent.GATHER_BRANCHES;
        // Ringing a hearth with stone IS building the pit (#37). BUILD_FIRE_PIT is claimed by the words "fire
        // pit" and "firepit" only, so the act described rather than named reached nothing — and a ring of stone
        // is the first thing a fire needs, which every refusal above says.
        if((value.contains("ring")||value.contains("circle")||value.contains("lay out")||value.contains("set out"))
           &&(value.contains("stone")||value.contains("rock"))
           &&(word(value,"fire")||value.contains("hearth")||value.contains("flame"))) return Intent.BUILD_FIRE_PIT;
        if((value.contains("check")||value.contains("inspect")||value.contains("look at")||value.contains("empty")||value.contains("collect from")||value.contains("return to"))&&(value.contains("trap")||value.contains("snare")||value.contains("deadfall"))) return Intent.CHECK_TRAP;
        // Placing a trap and working a snare by hand both mention "snare", so the
        // deciding signal is whether the chronicle is LEAVING something behind. A
        // setting/building verb means a persistent placed trap (V46); a bare
        // "snare a rabbit" is the immediate hand-worked attempt (V42).
        // BUTCHERING, by the words somebody kneeling over a carcass uses (#37). `butcher the deer` and `take the
        // hide` worked; `skin it` reached the material matcher, which offered to SKIN A FISH, and `gut it`,
        // `break it down` and `carry the carcass` reached nothing. The fish has its own process and keeps it —
        // the exclusion is by name, because "skin the fish" is a real recipe and "skin it" over a carcass is not.
        if((value.contains("skin it")||value.contains("skin the")||value.contains("gut it")||value.contains("gut the")
            ||value.contains("break it down")||value.contains("break down the")||value.contains("dress out")
            ||value.contains("carry the carcass")||value.contains("drag the carcass")||value.contains("leave the offal"))
           &&!value.contains("fish")&&!MAKING_SOMETHING.matcher(value).find()
           &&!value.contains("hide boots")&&!value.contains("skin bag")) return Intent.HARVEST_CARCASS;
        // And ASKING what is on it is a look, not a taking: "what can I get off this" and "how much meat is on
        // it" were answered by neither. Examining a thing is the rule that exists for exactly this.
        if((value.contains("what can i get off")||value.contains("what will it give")||value.contains("how much meat")
            ||value.contains("what is on it")||value.contains("what is left on"))
           &&!value.contains("cook")&&!value.contains("eat")) return Intent.EXAMINE;
        // ASKING ABOUT A TRAP IS NOT SETTING ONE, and TAKING ONE UP is not building it (#37). The same family as
        // the lean-to question that built a hut: `is the snare still set` and `where should I set a snare` were
        // both answered by an ATTEMPT to build one, and `take up the snare` — which is removing it — reached the
        // hand-worked snaring instead. A question goes to the check, which walks the ground and reports what of
        // yours is standing on it, and that is exactly what all three of these want to know.
        if((value.startsWith("is ")||value.startsWith("where ")||value.startsWith("has ")||value.startsWith("are ")
            ||value.contains("still set")||value.contains("still standing")||value.contains("anything in")
            ||value.contains("take up")||value.contains("take in")||value.contains("pick up the snare")
            ||value.contains("lift the snare")||value.contains("pull up the snare"))
           &&(value.contains("snare")||value.contains("trap")||value.contains("deadfall"))
           &&!value.contains("bait")) return Intent.CHECK_TRAP;
        if((value.contains("build")||value.contains("set")||value.contains("make")||value.contains("place")||value.contains("construct")||value.contains("lay"))&&(value.contains("deadfall")||value.contains("pit trap")||value.contains("fish trap")||value.contains("cage trap")||value.contains("box trap")||value.contains("snare")||(value.contains("trap")&&!value.contains("check")))) return Intent.SET_TRAP;
        if(value.contains("lure")||value.contains("bait the")||((value.contains("leave")||value.contains("put")||value.contains("place")||value.contains("set"))&&(value.contains("bait")||value.contains("draw them")||value.contains("draw it")))) return Intent.LURE;
        // Taming by offering food, and the one case where the same words are not taming at all (#106). A keeper
        // feeding their OWN goat says "feed the goat", exactly as someone winning a wild one over does, and was
        // answered "it lets you come nearer than last time, and holds there, watching" — the approach to a wild
        // animal, offered to someone whose goat is already theirs. No wording tells the two apart; the data does,
        // so the feeding branch yields the sentence once a beast of that kind is kept, and FEED_ANIMAL takes it.
        //
        // Only the FEEDING branch yields. "tame the goat" is unambiguous whatever is in the pen, and a keeper who
        // says it about a second, wild goat means it.
        if(value.contains("tame")||value.contains("befriend")||value.contains("domesticate")||value.contains("gain its trust")||value.contains("earn its trust")
           ||(((value.contains("approach")||value.contains("offer")||value.contains("feed")||value.contains("hold out"))&&(value.contains("calm")||value.contains("slow")||value.contains("gentl")||value.contains("quiet")||value.contains("trust")||value.contains("goat")||value.contains("rabbit")||value.contains("fowl")||value.contains("turtle")||value.contains("hedgehog")||value.contains("pigeon")||value.contains("deer")||value.contains("reindeer")||value.contains("duck")))
              &&!items.keepsSuchABeast(value))) return Intent.TAME;
        // Marking a trail is not following one (#75). TRACK's trail branch fires on "trail" beside find/read/follow,
        // so "mark the trail so I can find my way back" — the most natural way to say it — was read as tracking on
        // the strength of the word "find". A marking verb beside a trail is marking; TRACK is left untouched.
        if((value.contains("mark")||value.contains("tag")||value.contains("flag"))&&value.contains("trail")) return Intent.MARK;
        // Covering a trail is the OPPOSITE of reading one (#37). TRACK owns the word "tracks", so "cover my
        // tracks" was answered by finding prints — success at the very thing the asker was trying to prevent.
        // Placed before it, and gated on a hiding verb so that reading a trail is untouched.
        if((value.contains("cover")||value.contains("hide")||value.contains("brush out")||value.contains("wipe out")
            ||value.contains("sweep away")||value.contains("erase")||value.contains("conceal"))
           &&(value.contains("track")||value.contains("trail")||value.contains("print")||value.contains("my sign")
              ||value.contains("where i")||value.contains("passage"))
           &&!value.contains("follow")&&!value.contains("read")) return Intent.HIDE_TRAIL;
        // Your OWN tracks are not quarry (#37). Placed above TRACK, which hunts animal sign and answered "follow
        // my own tracks back" with "you find feathers caught in the low growth" — a player asking to go back the
        // way they came was shown somebody else's feathers. object_transition has recorded the direction of every
        // move since the table existed, so the way back is a thing the world knows.
        if(BACKTRACKING.matcher(value).find()) return Intent.MOVE;
        if(value.contains("track")||value.contains("follow the trail")||value.contains("read the ground")||value.contains("look for sign")||value.contains("look for tracks")||((value.contains("print")||value.contains("spoor")||value.contains("scat")||value.contains("droppings")||value.contains("trail"))&&(value.contains("find")||value.contains("read")||value.contains("follow")||value.contains("search")||value.contains("look")))) return Intent.TRACK;
        // Scan the boundary of the ground for a way clear of danger before moving into it (#128/#123: grounded
        // evidence before forced contact). Reads a predator ONE tile out by directional sense — scent on the
        // wind, boundary trees scored, prey gone quiet — never a map. Distinct from OBSERVE (reads THIS ground)
        // and TRACK (sign on THIS ground); owns the boundary / escape-route / scouting phrasing.
        // Watching a dangerous place from cover is scouting, which is exactly the act that exists for it (#37).
        if(((value.contains("watch")||value.contains("observe")||value.contains("study"))&&(value.contains("lair")||value.contains("den")||value.contains("from cover")||value.contains("under cover")))
           ||value.contains("scout")||value.contains("observe the boundary")||value.contains("read the boundary")||value.contains("check the boundary")||value.contains("survey the boundary")||value.contains("escape route")||value.contains("survey the escape")||value.contains("safe route")||value.contains("safe way")||value.contains("way out")||value.contains("which way is safe")||value.contains("scan the ridge")||value.contains("scan the treeline")||value.contains("scan the tree line")||value.contains("scan the horizon")||((value.contains("scan")||value.contains("check the way")||value.contains("look"))&&(value.contains("for danger")||value.contains("for a way out")))) return Intent.SCOUT;
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
        // The words for putting an edge back on a tool (#37). The rule knew "sharpen" and "abrade", and `hone the
        // blade` and `whet the blade` reached the MATERIAL MATCHER instead — which offered to **forge a bronze
        // knife**. Asked to sharpen what they hold, the player was offered to make a new one; `grind the axe`,
        // `strop the knife` and `put an edge on it` reached nothing at all.
        //
        // As words where they are short or live inside other words: "hone" sits in "honey" and "whet" in
        // "whether", and this project has shipped the substring defect four times over.
        boolean mendsSomething = value.contains("repair")||value.contains("mend")||value.contains("patch")||value.contains("reinforce")||value.contains("sharpen")||value.contains("abrade")||value.contains("darn")
            ||word(value,"hone")||word(value,"honing")||word(value,"whet")||word(value,"whets")||word(value,"whetting")
            ||word(value,"strop")||word(value,"strops")||word(value,"stropping")
            ||(word(value,"grind")&&(value.contains("axe")||value.contains("blade")||value.contains("knife")||value.contains("edge")||value.contains("adze")||value.contains("chisel")))
            ||value.contains("put an edge")||value.contains("get an edge")||value.contains("take the burr")
            // Rehafting IS mending: a head is sound and the handle it sits on has split, which is the commonest
            // repair there is and reached nothing by any of its names.
            ||value.contains("rehaft")||value.contains("re-haft")||value.contains("new handle")
            ||value.contains("replace the handle")||value.contains("replace the haft")||value.contains("the handle again");
        // MAKING one of these things is the matcher's, not a mend. "assemble a honing kit" produces a
        // sharpening_kit and must SUCCEED at it, and "honing" in the name of the kit was all this rule needed to
        // take it away. Found by replaying every phrase the test tree gives the composer — before pushing, this
        // time, rather than 65 minutes later.
        //
        // Named as locals rather than threaded into one condition: the first cut put the new clause inside the
        // existing parentheses and closed the `if` a line early, which the compiler caught but which a reader
        // would not have. A condition this long is easier to get right in pieces.
        boolean fixesSomething = word(value,"fix")&&!value.contains("memory")&&!value.contains("mind")&&!value.contains("place");
        // CONSTRUCTING one, specifically — not MAKING_SOMETHING, which also holds repair, mend and dismantle
        // because it means "working on a thing" rather than "building one". Excluding that pattern excluded
        // repair itself and sent "repair my stone knife" and "mend the woven basket" to nothing at all, which the
        // routing regression caught at once. The lesson is to read what a shared predicate MEANS before reusing
        // it: its name said making, its body said working.
        boolean buildingOne = value.contains("assemble")||value.contains("craft")||value.contains("make ")
            ||value.contains("build")||value.contains("put together");
        if((mendsSomething&&!buildingOne&&!namesAnAssembly(value) || fixesSomething)
           &&!value.contains("shelter")&&!value.contains("frame")&&!value.contains("lean")) return Intent.REPAIR_ITEM;
        // "fishing net" / "weave a fish net" contain "fish" but are CRAFTING a net, not angling (#36/#43/#44).
        // Making a net is an explicit craft (CRAFT_NET) — a mesh knotted from cordage — distinct from USING a
        // net to fish, which stays FISH. The using verbs (cast/throw/haul/set/with the net) keep it angling.
        boolean usingNet = value.contains("net")&&(value.contains("cast")||value.contains("throw")||value.contains("haul")||value.contains("set the net")||value.contains("use the net")||value.contains("with the net")||value.contains("with a net"));
        boolean craftingNet = value.contains("net")&&!usingNet&&(value.contains("weave")||value.contains("craft")||value.contains("make")||value.contains("knot")||value.contains("braid")||value.contains("tie")||value.contains("assemble")||value.contains("mesh"));
        if(craftingNet) return Intent.CRAFT_NET;
        // Fishing is an ACT, not a noun (#37). This asked only whether the sentence contained "fish", so every
        // sentence about a fish you had already caught was answered by sending you back to the water:
        //
        //   eat the fish        -> FISH      cook the fish     -> FISH
        //   carry the fish      -> FISH      count the fish    -> FISH
        //   look at the fish    -> FISH      bring the fish in -> FISH
        //
        // Now the sentence must be about going after one: fishing as a verb, a line or a rod, or a taking verb
        // against a fish or a named species. The bare word "fish" on its own still means it, because that is
        // what a person says when they mean to go — but "the fish" in the middle of a sentence does not.
        boolean fishingAct = value.trim().equals("fish") || value.contains("fishing")
            || value.contains("fish for") || value.contains("for fish") || value.contains("fish with") || value.contains("fish the ")
            || value.contains("cast a line") || value.contains("cast the line") || value.contains("a line in")
            || ((value.contains("catch")||value.contains("land ")||value.contains("hook")||value.contains("net ")
                 ||value.contains("spear")||value.contains("take")||value.contains("get"))
                && (value.contains("fish")||value.contains("trout")||value.contains("perch")||value.contains("pike")
                    ||value.contains("carp")||word(value,"eel")||value.contains("catfish")||value.contains("crayfish")));
        if((usingNet||(fishingAct&&!value.contains("landing")&&!value.contains("jetty")&&!value.contains("shellfish"))||value.contains("angle")||((value.contains("catch")||value.contains("spear"))&&(value.contains("trout")||value.contains("perch")||value.contains("pike")||value.contains("carp")||value.contains("eel")||value.contains("catfish")||value.contains("crayfish"))))&&!items.actionMatchesProcess(action)) return Intent.FISH;
        if(value.contains("snare")||value.contains("set a trap")||value.contains("set trap")||((value.contains("trap")||value.contains("noose"))&&(value.contains("rabbit")||value.contains("hare")||value.contains("bird")||value.contains("fowl")||value.contains("small")||value.contains("run")))) return Intent.SNARE;
        // A bird's nest and an insect's nest are the same word and not the same act (#122). The bird takes it when
        // the words name eggs or a bird and name nothing of a hive, so "rob the nest" beside a hive is still the hive.
        if((value.contains("raid")||value.contains("rob")||value.contains("take")||value.contains("collect")||value.contains("gather")||value.contains("steal"))
           &&(value.contains("nest")||value.contains("clutch"))
           &&(value.contains("egg")||value.contains("bird")||value.contains("fowl")||value.contains("duck")||value.contains("clutch"))
           &&!(value.contains("hive")||value.contains("honey")||value.contains("beeswax")||value.contains("bees")||value.contains("hornet")||value.contains("wasp"))) return Intent.RAID_NEST;
        if((value.contains("raid")||value.contains("harvest")||value.contains("smoke")||value.contains("rob")||value.contains("take")||value.contains("collect")||value.contains("gather"))&&(value.contains("hive")||value.contains("nest")||value.contains("honey")||value.contains("beeswax")||value.contains("bees")||value.contains("hornet"))) return Intent.RAID_HIVE;
        if((value.contains("collect")||value.contains("gather")||value.contains("catch")||value.contains("dig")||value.contains("pick")||value.contains("forage")||value.contains("harvest"))&&(value.contains("bait")||value.contains("insect")||value.contains("silk")||value.contains("cocoon")||value.contains("silkworm")||word(value,"ant")||word(value,"ants")||value.contains("chitin")||value.contains("shellfish")||word(value,"mussel")||value.contains("mussels")||word(value,"snail")||value.contains("snails")||word(value,"clam")||value.contains("clams")||value.contains("caddis")||value.contains("grasshopper")||value.contains("cricket")||value.contains("earthworm")||word(value,"worm")||word(value,"worms")||value.contains("spider")||word(value,"grub")||word(value,"grubs")||value.contains("larva"))) return Intent.COLLECT_INSECTS;
        // Felling needs a felling verb — bare "log" is not one. "split the oak log into
        // planks" is log *processing*, and the two-axis matcher claims it (split_planks);
        // only text that resolves to no process is heard as an attempt to fell (#17).
        // Coppicing cuts rods from living stools rather than felling the tree — a distinct woodland craft (#204).
        if(value.contains("coppice")||value.contains("cut rods")||value.contains("cut the rods")||value.contains("pollard")) return Intent.COPPICE;
        if((value.contains("fell")||value.contains("cut down")||value.contains("chop down")||value.contains("drop the tree"))&&(value.contains("tree")||value.contains("oak")||value.contains("birch")||value.contains("pine")||value.contains("ash")||value.contains("willow")||value.contains("maple")||value.contains("hazel")||value.contains("spruce")||value.contains("juniper"))&&!items.actionMatchesProcess(action)) return Intent.FELL_TREE;
        // Plant a tree seed to establish or restore a stand (#200/#204) — the counter-play to felling/clear-cutting.
        // A plant/sow/replant verb with a seed or tree noun; placed before GATHER_PLANT (which also matches
        // "acorn"/"sapling") so "plant an acorn" sows rather than forages — the distinguisher is the verb.
        // Till a seedbed (#165) — break/turn open ground before sowing. "till" whole-word (not "still"/"until"/"tiller").
        // Produce a tamed animal gives (#52/#79/#106): milking, gathering eggs, taking a fleece. Checked before the
        // gather intents so "collect the eggs" is husbandry rather than foraging the ground for them.
        // 'milk' must be whole-word: milkweed is a fibre plant that belongs to GATHER_PLANT, and a substring match
        // stole it. Wool is deliberately NOT a trigger noun either — "weave wool cloth" is weaving, not shearing —
        // so a fleece is asked for by the act (shear) or the thing taken (fleece).
        // 'milk' must be whole-word: milkweed is a fibre plant belonging to GATHER_PLANT, and a substring match stole
        // it. Wool is not a trigger noun — "weave wool cloth" is weaving — and a bare 'fleece' is not either, since
        // rinsing a fleece is washing it, not taking it off an animal. So a fleece needs a taking verb.
        // "wool" is what a keeper calls a fleece once it is off the animal (#106), and the rule knew only
        // "fleece": `shear the sheep` worked and `take the wool` reached nothing, over the same mechanism.
        if(word(value,"milk")||value.contains("shear")
           ||((value.contains("fleece")||word(value,"wool"))&&(value.contains("take")||value.contains("clip")||value.contains("cut")||value.contains("pull")))
           ||((value.contains("collect")||value.contains("gather")||value.contains("take")||value.contains("check"))
              &&(word(value,"egg")||word(value,"eggs")))) return Intent.TAKE_ANIMAL_YIELD;
        // Tending a sick beast (#106/#108). Needs BOTH a tending verb and an animal, so it cannot steal
        // "tend the crop" from WEED_CROP or "bind the wound" from TREAT_WOUND — the nouns keep them apart.
        // How the stock ARE (#106). wildlife_bond keeps hunger, thirst, fatigue and sickness on every tamed beast,
        // the tick moves all four, and haulage, breeding and yield are each gated on them — and the plainest
        // question a keeper asks reached nothing: "how is the goat", "is the goat sick", "check on the animals".
        // Worse, "water the animals" was answered "none of your draft beasts is hungry", which is an answer about
        // appetite to a question about thirst.
        //
        // Placed before tending, grooming and feeding, and sharing their one species vocabulary. It takes the
        // ASKING and the watering; the three acts below keep their verbs, so "tend the goat" still tends and
        // "feed the goat" still feeds. Thirst falls by place and by structure rather than by a bucket, so the
        // honest answer to watering is what the animals' water depends on — which is what this says.
        if((value.contains("how is")||value.contains("how are")||value.contains("how do the")||value.contains("check on")
            ||value.contains("check over")||value.contains("look over")||value.contains("look in on")||value.contains("see to the")
            ||value.contains("are they well")||value.contains("are they all right")||value.contains("what state")
            ||value.contains("do they need")||value.contains("what do they need")||value.contains("sick")||value.contains("ailing")
            ||value.contains("limping")||value.contains("lame")||value.contains("off its feed")||value.contains("off their feed")
            ||value.contains("thirsty")||value.contains("water the")||value.contains("give the")&&value.contains("water")
            ||value.contains("check the")&&(value.contains(" over")||value.contains("for tick"))
            // ...and what a beast can be HELD with (#106). animal_restraint works by being CARRIED and no
            // sentence could reach it, so these are answered with what you have for it and what it does. There
            // is no restrained state in this world and an invented one would change nothing and claim it had.
            ||value.contains("tether")||value.contains("hobble")||value.contains("halter")||value.contains("tie up")
            ||value.contains("restrain")||value.contains("lead the")||value.contains("catch the")||value.contains("hold the")
            ||value.contains("shut the")||value.contains("shut in")||value.contains("keep hold of"))
           &&namesABeast(value)
           // The acts keep their own verbs. Asking after a beast is not tending, grooming, feeding or milking one.
           &&!value.contains("tend")&&!value.contains("treat")&&!value.contains("groom")&&!value.contains("curry")
           &&!value.contains("feed the")&&!word(value,"milk")&&!value.contains("shear")
           // ...and BUILDING something is the assembly matcher's, never a question about the stock. A sick
           // animal shelter is a thing a keeper raises, and "sick" plus "animal" is all this rule needed to
           // steal all three of its own keywords — which noJavaIntentShadowsAnAssemblysOwnKeywords caught.
           &&!value.contains("build")&&!value.contains("raise")&&!value.contains("put up")
           &&!value.contains("make ")&&!value.contains("construct")&&!value.contains("erect")
           // ...and a sentence that IS an assembly's own keyword belongs to the assembly matcher, verb or no
           // verb. The bare "sick animal shelter" is the NAME of a thing a keeper raises, and no list of
           // building words would have caught it. Asked of the matcher, so that every assembly added after this
           // one is protected too — which a hand-kept list could never promise.
           &&!namesAnAssembly(value)) return Intent.CHECK_STOCK;
        // The dressing verbs belong here too (#106): tendSickAnimal works a poultice, an infusion or a dried herb
        // bundle into an ailing beast, which is exactly what "bandage the goat" and "put a poultice on the ewe"
        // ask for, and both reached nothing. Gated on the beast as everything in this block is, so "bind the
        // wound" and "bandage my arm" stay TREAT_WOUND — the noun is the whole of the difference.
        if((value.contains("tend")||value.contains("treat")||value.contains("doctor")||value.contains("physic")||value.contains("dose")||value.contains("nurse")
            ||value.contains("bandage")||value.contains("poultice")||value.contains("salve")||value.contains("dress the")||value.contains("bind the"))
           &&namesABeast(value)) return Intent.TEND_ANIMAL;
        // Combing out a coat (#106). Kept apart from TEND_ANIMAL by the verb: tending is for what ails a beast,
        // grooming is for the coat itself, and the two want different things in your hands. The coat, the mane and
        // the fleece are its own — a part of the animal rather than another name for it — so they are here.
        if((value.contains("groom")||value.contains("curry")||word(value,"brush")||word(value,"comb")||value.contains("brushing")||value.contains("combing"))
           &&(namesABeast(value)||value.contains("coat")||value.contains("mane")||value.contains("fleece"))) return Intent.GROOM_ANIMAL;
        // "give the goat some hay" and "put hay in the trough" are feeding (#106), and reached nothing: the rule
        // knew the verb "feed" and not the act of handing fodder over. The fodder chain was whole all along —
        // grass grows, drying it yields the bundle, and THIS is the rule that consumes it — so the words for its
        // last step belong here. Placed after the asking rule, so "give the goat water" still answers about
        // thirst rather than shaking out a bundle of dry grass at it.
        if((value.contains("feed")||value.contains("forage")||value.contains("graze")||value.contains("water the")
            ||((value.contains("give")||value.contains("put out")||value.contains("put ")||value.contains("set out")
                ||value.contains("fill"))&&(word(value,"hay")||value.contains("fodder")||value.contains("dry grass")
                ||value.contains("dried grass"))))
           &&namesABeast(value)) return Intent.FEED_ANIMAL;
        // ...and the same with no animal named, because a trough and a hay rack are only ever for stock: "put
        // hay in the trough" and "put out fodder" name the vessel instead of the beast, which is how a keeper
        // talks. Nothing else in this world holds fodder.
        if((word(value,"hay")||value.contains("fodder"))
           &&(value.contains("give")||value.contains("put out")||value.contains("set out")||value.contains("fill")
              ||value.contains("in the trough")||value.contains("the rack")||value.contains("the manger"))
           &&!value.contains("make ")&&!value.contains("cut ")&&!value.contains("dry ")&&!value.contains("cure ")
           &&!value.contains("build")&&!value.contains("store")) return Intent.FEED_ANIMAL;
        if(value.contains("clear")&&(value.contains("land")||value.contains("forest")||value.contains("brush")||value.contains("woods")||value.contains("woodland")||value.contains("trees")||value.contains("arable")||value.contains("for a field")||value.contains("for planting")||value.contains("ground for"))) return Intent.CLEAR_LAND;
        // Carry water to a growing stand (#37/#165). FEED_ANIMAL above already claims "water the" — but only
        // together with an animal noun, so a plot, a row or a seedling cannot be mistaken for a thirsty ox. Gated on
        // a crop noun for the same reason in the other direction.
        if((word(value,"water")||value.contains("irrigate")||value.contains("douse")||value.contains("damp down"))
           &&(value.contains("crop")||value.contains("field")||value.contains("seedling")||value.contains("seedbed")||value.contains("the plot")||value.contains("my plot")||value.contains("the rows")||value.contains("the row")||value.contains("the grain")||value.contains("my grain")||value.contains("barley")||value.contains("emmer")||value.contains("wheat")||value.contains("the stand"))
           &&!value.contains("animal")&&!value.contains("beast")&&!value.contains("stock")&&!value.contains("the herd")
           &&!items.namesAKeptAnimal(value)) return Intent.WATER_CROP;
        // Drive the birds off a standing crop (#37/#165). The harvest prose has always blamed them for a late
        // stand; this is the answer to it. "scare"/"chase"/"drive off" plus the birds, on a crop.
        if((value.contains("scare")||value.contains("chase")||value.contains("drive off")||value.contains("drive away")
            ||value.contains("shoo")||value.contains("frighten")||(value.contains("keep")&&value.contains(" off")))
           &&(value.contains("bird")||value.contains("crow")||value.contains("rook")||value.contains("sparrow")||value.contains("starling")||value.contains("pigeon"))
           &&((value.contains("crop")||value.contains("field")||value.contains("seedling")||value.contains("seedbed")||value.contains("the plot")||value.contains("my plot")||value.contains("the rows")||value.contains("the row")||value.contains("the grain")||value.contains("my grain")||value.contains("barley")||value.contains("emmer")||value.contains("wheat")||value.contains("the stand"))||value.contains("harvest")||value.contains("the heads"))) return Intent.SCARE_BIRDS;
        if(value.contains("pull the weeds")||value.contains("pull up the weeds")||value.contains("hoe between")
           ||value.contains("thin the seedling")||value.contains("thin out the")||value.contains("mulch the")) return Intent.WEED_CROP;
        if((word(value,"weed")||value.contains("tend")||value.contains("hoe the row")||value.contains("hoe the crop"))&&(value.contains("crop")||value.contains("field")||value.contains("stand")||value.contains("grain")||value.contains("seedbed")||value.contains("row")||value.contains("plot"))) return Intent.WEED_CROP;
        // TILLAGE, in pieces (#37). The condition had grown to one line of five nested groups, and threading a new
        // clause into it displaced the `earth sheltered` exclusion so that "work on the earth sheltered hut" became
        // breaking ground — which the routing regression caught at once, and which no reader would have. The same
        // lesson the repair rule taught an hour earlier: a condition this long is got right in pieces.
        boolean breakingGround = value.contains("break")||value.contains("turn")||value.contains("work")
            ||value.contains("prepare")||value.contains("hoe");
        boolean someGroundToBreak = value.contains("ground")||value.contains("soil")||value.contains("seedbed")
            ||value.contains("seed bed")||word(value,"earth")||value.contains("field")
            // The plot and the bed are what a person calls the ground they are breaking: "hoe the plot" and
            // "dig the bed over" reached nothing while "hoe the ground" worked.
            ||value.contains("the plot")||value.contains("the bed");
        // "dig the bed over" and "make a seedbed". "dig" is kept out of the GATHERING verbs because "dig a root
        // cellar" names a real root — but beside a bed or a plot it is plainly tillage, and so is making one.
        boolean diggingABed = (value.contains("dig")||value.contains("make"))
            &&(value.contains("seedbed")||value.contains("seed bed")||value.contains("the bed over")
               ||value.contains("over the bed")||value.contains("the plot over"))
            &&!namesAnAssembly(value);
        boolean notAnEarthHouse = !value.contains("earth sheltered")&&!value.contains("earth-sheltered");
        // Insects, grubs and worms are a grub hunt, not a seedbed — the ground and the verb are tillage's, the
        // object is not (#37, V401).
        boolean notAGrubHunt = !value.contains("insect")&&!value.contains("grub")&&!value.contains("worm")&&!value.contains("bait");
        if((word(value,"till")||value.contains("plough")||value.contains("plow")
            ||(breakingGround&&someGroundToBreak)||diggingABed)
           &&notAnEarthHouse&&notAGrubHunt) return Intent.TILL_GROUND;
        // Sow a grain crop (#162 agriculture) — before PLANT_TREE, which also claims "sow"+"seed": a crop needs
        // grain/cereal/field context, so tree-planting ("sow an acorn", "plant a sapling") still falls through to it.
        if((word(value,"sow")||value.contains("broadcast")||value.contains("plant"))&&(value.contains("grain")||value.contains("crop")||value.contains("cereal")||value.contains("wheat")||value.contains("barley")||value.contains("emmer")||value.contains("the field"))) return Intent.SOW;
        // Reap a ripe crop (#162) — "reap" stands alone; "harvest"/"bring in" need crop/field context so butchery
        // ("harvest the carcass") is untouched.
        // "harvest the grain" (#37). Reaping answered to "reap", to "the crop" and to "the field", and to nothing
        // a person would call the thing actually standing in the field. There is one crop in the world and it is
        // grain, so a Chronicle who sowed it and came back for it was told nothing at all.
        //
        // Scoped to "the grain" and "my grain", with the article doing real work: "harvest the wild grain" does
        // NOT contain "the grain" and still reaches GATHER_PLANT, which is right -- foraging wild grain off the
        // ground is not reaping a stand you sowed, and taking that phrase would have broken foraging to fix
        // farming. When a second crop exists this wants reading the ground rather than a literal, the way #79
        // replaced the kept-animal list with the catalogue.
        if(word(value,"reap")||((value.contains("harvest")||value.contains("bring in")||value.contains("cut ")||value.contains("get in"))
           // ...and by the NAME of what is standing there (#37). "harvest the barley" reached nothing while
           // "reap the grain" worked, because the rule knew "the grain" and not the grain's own name.
           &&(value.contains("crop")||value.contains("the field")||value.contains("the grain")||value.contains("my grain")||value.contains("the harvest")
              ||value.contains("barley")||value.contains("emmer")||value.contains("wheat")||value.contains("the ears")||value.contains("the stand")))
           ||value.contains("gather the ears")||value.contains("bring in the harvest")) return Intent.HARVEST_CROP;
        // The verbs a gather is actually asked for with (#37). Each family had grown its own three or four, so
        // "gather reeds" worked and "cut reeds" — the verb you hold a blade to do — reached nothing, and "pick
        // stones" reached nothing while "pick mushrooms" worked. One clause for all of them, or they drift again.
        //
        // Held out deliberately: "take" and "get", which mean a dozen other things and already reach a take of
        // what lies here; "grab", which reaches PICK_UP and should; and "crop", which is the noun for a sown
        // stand and would turn "crop the watercress" into watering a field. Felling and coppicing claim "cut
        // down" and "cut rods" above, so those stay theirs.
        boolean gatherVerb = value.contains("gather")||value.contains("collect")||value.contains("forage")
            ||value.contains("harvest")||value.contains("take all")||value.contains("gather up")
            ||GATHERING_VERB.matcher(value).find()||value.contains("dig up");
        // A tree named in the singular (#37). The nouns had "a tree" and "trees" but nothing for "an apple
        // tree", which contains neither. plantTree grows oak from an acorn and pine from a pine nut and nothing
        // else, and its refusal says so -- which is a far better answer to a Chronicle asking for an apple tree
        // than prose about failing to make something, because it names what CAN be put in the ground.
        // HOW THE CROP STANDS (#37/#164/#165/#166). Seven things decide what a harvest gives — a tilled seedbed,
        // the soil's fertility, whether animals grazed it, whether it was weeded, whether dry ground was watered,
        // what is working the flowers, and how long past ripe it stood — and harvestCrop weighs every one of them
        // while a player could learn NONE of them until the grain was in. The information arrived exactly one
        // moment after it could have been used, which is the "world knew and would not say" defect at its most
        // expensive. `how is the crop`, `is the barley ready`, `when can I harvest`, `is the soil any good`,
        // `is the plot worn out` and `is anything growing` all reached nothing.
        //
        // It also takes the fallow questions, because the ground mending itself IS the answer to them: fertility
        // climbs with every day nothing is taken off, faster on a floodplain, faster again beside a manure pit.
        // And "how long until harvest" off MEASURE, which answered "you pace it out and reckon by eye" and gave
        // no figure, while crop_stand holds the sowing date and the maturity in days.
        if(value.contains("how is the crop")||value.contains("how is the field")||value.contains("how is the plot")
           ||value.contains("how is the ground")||value.contains("how does the crop")
           ||value.contains("is anything growing")||value.contains("what did i sow")||value.contains("what is sown")
           ||value.contains("is the soil")||value.contains("how is the soil")||value.contains("plot worn")
           ||value.contains("soil worn")||value.contains("ground worn")||value.contains("grow anything")
           ||value.contains("until harvest")||value.contains("when can i harvest")||value.contains("when is it ripe")
           ||((value.contains("ready")||value.contains("ripe"))&&(value.contains("crop")||value.contains("barley")
              ||value.contains("emmer")||value.contains("grain")||value.contains("wheat")||value.contains("the stand")))
           ||value.contains("let the ground rest")||value.contains("let it rest")||value.contains("leave it fallow")
           ||value.contains("lie fallow")||value.contains("leave the ground")||value.contains("manure the")
           ||value.contains("spread muck")||value.contains("dung the")) return Intent.CHECK_CROP;
        // A seed is a crop seed or a tree seed and the word is the same (#37). PLANT_TREE claims the bare "seed"
        // and answered "gather acorns under an oak, or pine nuts from the cones" to a keeper holding a handful of
        // grain. No wording settles it; what they are CARRYING does — the same move the taming gate makes.
        if((value.contains("plant")||value.contains("sow")||value.contains("put in")||value.contains("put the seed")||value.contains("put some seed"))&&value.contains("seed")
           &&!value.contains("acorn")&&!value.contains("pine")&&!value.contains("sapling")&&!word(value,"tree")
           &&!value.contains("oak")&&!value.contains("birch")&&!value.contains("willow")
           &&items.carriesFieldSeed()) return Intent.SOW;
        if((value.contains("plant")||value.contains("sow")||value.contains("replant"))&&(value.contains("acorn")||value.contains("pine nut")||value.contains("pine_nut")||value.contains("seed")||value.contains("sapling")||value.contains("seedling")||value.contains("a tree")||word(value,"tree")||value.contains("an oak")||value.contains("a pine")||value.contains("some trees")||value.contains("trees"))) return Intent.PLANT_TREE;
        if((gatherVerb||value.contains("reap"))&&(value.contains("mushroom")||value.contains("fungi")||value.contains("herb")||value.contains("plant")||value.contains("berries")||value.contains("flower")||value.contains("leaf")||value.contains("root")||value.contains("nettle")||value.contains("yarrow")||value.contains("comfrey")||value.contains("mint")||value.contains("dandelion")||value.contains("garlic")||value.contains("burdock")||value.contains("watercress")||value.contains("cattail")||value.contains("reed")||value.contains("bulrush")||items.namesSomethingThatGrows(value)||value.contains("chanterelle")||value.contains("porcini")||value.contains("oyster")||value.contains("polypore")||value.contains("lion")||value.contains("hazel rod")||value.contains("hazel")&&value.contains("rod")||value.contains("willow")&&value.contains("branch")||value.contains("pine resin")||value.contains("maple sap")||value.contains("rose hip")||value.contains("elderberry")||value.contains("hawthorn")||value.contains("juniper berry")||value.contains("vine")||value.contains("sapling")||value.contains("straw")||value.contains("young tree")||value.contains("meadow grass")||value.contains("milkweed")||value.contains("flax")||value.contains("hemp")||value.contains("acorn")||value.contains("hazelnut")||value.contains("walnut")||value.contains("chestnut")||value.contains("pine nut")||value.contains("wild onion")||value.contains("wild grain")||value.contains("grain head")||value.contains("rhizome")||value.contains("chamomile")||value.contains("pine needle")||value.contains("wild rice")||value.contains("morel")||value.contains("crab apple")||value.contains("sloe")||value.contains("bilberry")||value.contains("bramble")||value.contains("fatwood")||value.contains("big leaf")||value.contains("broad leaf")||value.contains("dry grass")||value.contains("flexible root")||value.contains("bast"))&&!value.contains("fiber")&&!value.contains("bark")) return Intent.GATHER_PLANT;
        if(value.contains("clay")&&(gatherVerb||value.contains("dig")||value.contains("find")||value.contains("get")||value.contains("scoop"))) return Intent.GATHER_CLAY;
        if(value.contains("slab")&&(gatherVerb||value.contains("split")||value.contains("pry")||value.contains("make")||value.contains("get")||value.contains("quarry")||value.contains("shape")||value.contains("break"))) return Intent.GATHER_STONE_SLAB;
        // Everyday hand-gathered stock (#68 gather aliases): the specific gathers used to accept only gather/
        // collect; forage/harvest/take-all/gather-up now reach them too. A named material takes precedence over
        // a generic "gather".
        if(gatherVerb&&(value.contains("fiber")||value.contains("fibre"))) return Intent.GATHER_FIBER;
        if(gatherVerb&&(value.contains("branch")||value.contains("stick")||value.contains("deadwood")||value.contains("firewood")||value.contains("kindling"))) return Intent.GATHER_BRANCHES;
        if(gatherVerb&&(value.contains("berry")||value.contains("berries"))) return Intent.GATHER_BERRIES;
        if(gatherVerb&&(value.contains("stone")||value.contains("rock")||value.contains("pebble"))&&!value.contains("slab")&&!items.actionMatchesProcess(action)) return Intent.GATHER_STONE;
        // WASH is BODY washing (#32) — scoped to the self so "wash the sediment / rinse the fleece / pan the
        // gravel" (#68 material prep) falls through to the process catalogue instead of being heard as bathing.
        if(value.contains("wash the blood")||value.contains("wash blood")||value.contains("rinse the blood")
           ||value.contains("bathe")||value.contains("take a bath")||value.contains("have a bath")||value.contains("clean myself")||value.contains("wash myself")||value.contains("wash up")||value.contains("wash my ")||value.contains("wash off")||((value.contains("wash")||value.contains("rinse"))&&(value.contains("hands")||value.contains("face")||value.contains("body")||value.contains("skin")||value.contains("in the stream")||value.contains("in the river")||value.contains("in the water")))) return Intent.WASH;
        // Body-care against the environment (#66): warm/dry/cool/shelter/stretch. Scoped so they read as body
        // acts, not material processing ("dry the herbs" is a PROCESS; "dry off" is warming the body).
        // Stretching a HIDE is leatherwork, not a body loosening its limbs (#37): "stretch the hide" was a
        // process keyword that this took first.
        if((value.contains("stretch")&&!value.contains("hide")&&!value.contains("skin")&&!value.contains("pelt"))||value.contains("loosen my")||value.contains("loosen up")||value.contains("work the stiffness")||value.contains("limber up")) return Intent.STRETCH;
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
        if((word(value,"pen")||word(value,"pens")||word(value,"paddock")||word(value,"corral")||value.contains("animal enclosure")||value.contains("livestock enclosure"))&&(value.contains("build")||value.contains("raise")||value.contains("set up")||value.contains("put up")||value.contains("make")||value.contains("construct")||value.contains("erect")||value.contains("pen the"))) return Intent.BUILD_PEN;
        if(!value.contains("rail fence")&&(word(value,"fence")||word(value,"fences")||value.contains("palisade")||value.contains("stockade"))&&(value.contains("build")||value.contains("raise")||value.contains("set up")||value.contains("put up")||value.contains("weave")||value.contains("erect")||value.contains("make")||value.contains("construct")||value.contains("throw up")||value.contains("fence off")||value.contains("fence the")||value.contains("fence around")||value.contains("around the camp")||value.contains("around the perimeter"))) return Intent.BUILD_FENCE;
        // A raised lookout (#127) — a lashed pole stand a Chronicle climbs to see past the near treeline, extending
        // the boundary scout a chunk further. The lookout noun plus a build verb; placed before MAINTAIN_CAMP and
        // before the SCOUT rule's bare "look" never catches it (it needs "for danger"/"for a way out").
        if((value.contains("lookout")||value.contains("look-out")||value.contains("watch post")||value.contains("watchtower")||value.contains("watch tower")||value.contains("watch platform")||value.contains("watch stand")||value.contains("vantage point")||value.contains("observation post"))&&(value.contains("build")||value.contains("raise")||value.contains("set up")||value.contains("put up")||value.contains("make")||value.contains("construct")||value.contains("erect")||value.contains("lash"))) return Intent.BUILD_LOOKOUT;
        // A covered fuel rack (#127) — a roofed stand that keeps kindling and firewood dry so a fire will light
        // even in the rain. A rack noun with a build verb, or the plain intent to keep the fuel/wood dry.
        if(((value.contains("fuel rack")||value.contains("wood rack")||value.contains("firewood rack")||value.contains("log rack")||value.contains("kindling rack")||value.contains("woodshed")||value.contains("wood shelter"))&&(value.contains("build")||value.contains("raise")||value.contains("make")||value.contains("set up")||value.contains("put up")||value.contains("construct")||value.contains("erect")))
           ||((value.contains("keep")||value.contains("store")||value.contains("stack")||value.contains("shelter"))&&(value.contains("firewood")||value.contains("kindling")||value.contains("the fuel")||value.contains("the wood"))&&(value.contains("dry")||value.contains("out of the rain")||value.contains("out of the wet")||value.contains("off the ground")))) return Intent.BUILD_FUEL_RACK;
        // A camp latrine / refuse pit (#127/#218) — a screened pit dug well away from the living space that keeps
        // filth off the ground you live on. Distinctive sanitation nouns with a dig/build verb.
        if((value.contains("latrine")||value.contains("privy")||value.contains("cesspit")||value.contains("cess pit")||value.contains("refuse pit")||value.contains("waste pit")||value.contains("rubbish pit")||value.contains("toilet pit")||value.contains("midden"))&&(value.contains("dig")||value.contains("build")||value.contains("make")||value.contains("raise")||value.contains("set up")||value.contains("put up")||value.contains("construct")||value.contains("screen"))) return Intent.BUILD_LATRINE;
        // A camp tool shed (#207 heritage) — a walled, roofed store where tools and made stock are kept to hand and
        // out of the weather, so fabrication and repair no longer begin with hunting for them. Distinctive shed
        // nouns with a build verb; placed before MAINTAIN_CAMP so "build a tool shed" raises one rather than tidying
        // the site, and it needs no "store the wood dry" phrase (that stays with the fuel rack).
        if((value.contains("tool shed")||value.contains("tool-shed")||value.contains("tool store")||value.contains("tool hut")||value.contains("toolshed")||value.contains("storage shed")||value.contains("store shed")||value.contains("storage hut"))&&(value.contains("build")||value.contains("raise")||value.contains("make")||value.contains("set up")||value.contains("put up")||value.contains("construct")||value.contains("erect")||value.contains("re-roof")||value.contains("re-lay"))) return Intent.BUILD_TOOL_SHED;
        // A roof smoke-vent (#219): a hole cut through a shelter's roof to let the hearth-smoke out. Placed before the
        // material-process fallback so "cut a smoke hole" is a build, not stolen by the bare-"smoke" food-smoking
        // processes. A concrete build intent dispatches ahead of the process match, so no actionMatchesProcess gate
        // is needed here.
        if(((value.contains("smoke")&&(value.contains("vent")||value.contains("hole")||value.contains("louvre")||value.contains("louver")))||value.contains("smokehole")||value.contains("roof vent"))
           &&(value.contains("cut")||value.contains("build")||value.contains("make")||value.contains("open")||value.contains("raise")||value.contains("set")||value.contains("put")||value.contains("construct")||value.contains("re-daub")||value.contains("redaub"))) return Intent.BUILD_SMOKE_VENT;
        // A camp resource store (#207 heritage STORAGE_AREA) — a covered stockpile pen where a Chronicle sets down
        // what they bring in, so a fresh kill goes to the larder instead of riding on the body through predator
        // ground. Distinctive store nouns with a build verb; the bare "cache"/"stockpile" of the STORE intent is
        // deliberately NOT used here (those stow an item into a container), so no collision — placed before
        // MAINTAIN_CAMP so "build a storage area" raises one rather than tidying the site.
        if((value.contains("storage area")||value.contains("store area")||value.contains("storage pen")||value.contains("storehouse")||value.contains("store house")||value.contains("resource store")||value.contains("supply store")||value.contains("storage platform"))&&(value.contains("build")||value.contains("raise")||value.contains("make")||value.contains("set up")||value.contains("put up")||value.contains("construct")||value.contains("erect")||value.contains("re-roof")||value.contains("re-lay"))) return Intent.BUILD_STORAGE_AREA;
        // Restore disturbed ground (#207/#213) — the counter-play to the disturbance a Chronicle causes: clear the
        // churned earth and replant so the land grows quiet, and the wildlife return, sooner. A restore/replant
        // verb tied to the land, or a distinctive heal-the-land phrase.
        if(((value.contains("restore")||value.contains("replant")||value.contains("rehabilitat")||value.contains("make good"))&&(value.contains("land")||value.contains("ground")||value.contains("habitat")||value.contains("wild")||value.contains("forest")||value.contains("range")||value.contains("earth")||value.contains("here")||value.contains("this place")))||value.contains("let the land heal")||value.contains("let the ground recover")||value.contains("heal the land")||value.contains("mend the ground")||value.contains("mend the land")) return Intent.RESTORE_HABITAT;
        // Asking what food you have and how long it will keep (#37). Every one of these reached nothing while
        // the spoilage subsystem underneath had five tiers, a clock per object and an illness for getting it
        // wrong. Anchored on the QUESTION words, so that "smoke the meat" and "salt the fish" — which are how
        // you act on the answer — keep their own processes.
        if (value.contains("food store")||value.contains("food stock")||value.contains("check my food")
            ||value.contains("check the food")||value.contains("look at my food")||value.contains("what food")
            ||value.contains("how much food")||value.contains("check my supplies")||value.contains("check my stores")
            ||value.contains("take stock of the food")||value.contains("take stock of my food")
            ||value.contains("how long will the food")||value.contains("how long the food")
            ||value.contains("is anything going off")||value.contains("anything gone off")
            ||value.contains("will the meat keep")||value.contains("will it keep")
            ||value.contains("is the meat still good")||value.contains("still good to eat")
            ||value.contains("what have i got to eat")||value.contains("have i anything to eat")
            ||value.contains("go through the food")) return Intent.TAKE_STOCK_OF_FOOD;
        // Which way a kind of ground lies (#37). The movement axis swept at 26 of 42 sentences reaching nothing,
        // and this was among them though every piece of the answer sits in world_chunk: the neighbouring biomes,
        // their elevations, and the grid offsets that say which way each one lies. planTravel only knows places
        // the Chronicle has NAMED, so a KIND of ground could be asked for and never found.
        //
        // Gated on the sentence naming one of those kinds, which is what keeps it clear of READ_THE_SKY's "which
        // way is north" below — a bearing on the sun is not a bearing on the country. It also takes "how far is
        // the river" from MEASURE, which answered "you pace it out and reckon by eye" and then gave no figure at
        // all: a reckoning announced and not delivered.
        if((value.contains("which way")||value.contains("what direction")||value.contains("which direction")
            ||value.contains("where is the")||value.contains("where are the")||value.contains("how far is")
            ||value.contains("how far to")||value.contains("near here")||value.contains("nearby"))
           &&namesGroundAsked(value)) return Intent.WHICH_WAY;
        // What the sky is doing (#37, act fifteen). The hour, the month, the felt wind, the frost and whether
        // rain is in it are all simulated, and 22 of 35 phrasings about them reached nothing while "what is the
        // weather doing" answered well. world_weather.wind_speed_kph is the sharpest: a column the simulation
        // maintains, carried into BiomeClimate.Local as a wind FELT at this elevation, and unreachable by words.
        // FEEL already owns the season and the temperature, and IntentClassificationRegressionTest holds it to
        // them — "what time OF YEAR is it" is a season question and my "what time" took it. The hour and the
        // year are different questions and the guard was right to say so.
        if((value.contains("what time")&&!value.contains("time of year"))||value.contains("how long until dark")||value.contains("until dark")
           ||value.contains("is it getting dark")||value.contains("is it dark")||value.contains("how high is the sun")
           ||value.contains("where is the sun")||value.contains("how much light")||value.contains("light is left")
           ||value.contains("what month")||value.contains("is it near winter")||value.contains("how near winter")
           ||value.contains("what is the wind")||value.contains("is the wind")||value.contains("how hard is the wind")
           ||value.contains("is it going to rain")||value.contains("will it rain")||value.contains("is there frost")
           ||value.contains("will it freeze")||value.contains("read the sky")||value.contains("look at the sky")
           ||value.contains("what is the sky")||value.contains("which way is north")||value.contains("which way is south")||value.contains("take a bearing")) return Intent.READ_THE_SKY;
        // What you are carrying and what state it is in (#37, act fifteen). condition_state, use_count and
        // quality_grade have been on every item since the table existed, and the LOAD is computed on every
        // action — and "how heavy is my pack" was answered "you have nothing by that name in hand to weigh".
        //
        // Gated away from the acts: mending is REPAIR_ITEM's, equipping is EQUIP's. These are the questions.
        if((value.contains("what am i carrying")||value.contains("what do i carry")||value.contains("check my tools")
            ||value.contains("check my gear")||value.contains("look over my gear")||value.contains("what tools do i have")
            ||value.contains("what gear do i have")||value.contains("is anything broken")||value.contains("anything need mending")
            ||value.contains("what needs mending")||value.contains("how worn")||value.contains("how is my")
            // The other ways of asking how a tool is standing up (#37). use_count and condition_state are on
            // every item and "how worn is the knife" reached them; "is the knife blunt", "how sharp is the axe",
            // "is the axe worn", "will this hold" and "how much use is left in it" all reached nothing.
            ||value.contains("blunt")||value.contains("how sharp")||value.contains("still sharp")
            ||value.contains("will it hold")||value.contains("will this hold")||value.contains("how much use")
            ||value.contains("much use left")||value.contains("how is the knife")||value.contains("how is the axe")
            ||(value.contains("worn")&&(value.contains("the axe")||value.contains("the knife")||value.contains("the blade")||value.contains("my tool")))
            ||value.contains("am i carrying too much")||value.contains("how heavy is my")||value.contains("how much am i carrying")
            ||value.contains("take stock of my gear")||value.contains("what is in my pack")||value.contains("still good")&&value.contains("my ")||value.contains("still sound"))
           &&!value.contains("food")&&!value.contains("supplies")) return Intent.TAKE_STOCK_OF_GEAR;
        // Go round the camp and account for it (#37). Perception, not work. Placed before MAINTAIN_CAMP, which
        // claims the camp with a tidying verb — taking stock is not tidying, and must not be answered by sweeping.
        // "stock" is also the word for animals, so this needs the stocktaking PHRASE rather than the bare noun:
        // "water the stock" and "feed the stock" are somebody else's rule and stay that way.
        if((value.contains("take stock")||value.contains("taking stock")||value.contains("stocktake")
            ||value.contains("what have i built")||value.contains("what have i made here")
            ||value.contains("what stands here")||value.contains("what is standing here")
            ||((value.contains("look over")||value.contains("go round")||value.contains("account for")||value.contains("survey"))
               &&(value.contains("camp")||value.contains("what i have built")||value.contains("my work"))))
            // ...and asking whether what you built is still sound (#37), which is the same walk round the same
            // ground reading the same integrity_percent. "is the shelter sound" and "how is the shelter" reached
            // nothing, and "is the lean-to still good" was answered by STARTING TO BUILD ANOTHER ONE.
            ||((value.contains("sound")||value.contains("still good")||value.contains("still standing")
                ||value.contains("still hold")||value.contains("in good repair")||value.contains("how is"))
               &&(value.contains("shelter")||value.contains("lean-to")||value.contains("lean to")
                  ||value.contains("the hut")||value.contains("the roof")||value.contains("what i built")
                  ||value.contains("the pen")||value.contains("the fence")||value.contains("the rack")))
           &&!value.contains("animal")&&!value.contains("beast")&&!value.contains("the herd")&&!value.contains("the flock")
           &&!items.namesAKeptAnimal(value)) return Intent.TAKE_STOCK_OF_CAMP;
        // Asking WHETHER the water will let you over is a question, not a wade (#37). Placed before the
        // terrain-crossing rule, which turns "wade north" into movement: a question with a direction in it must
        // still be a question. Gated on a question shape AND water, so "wade north" itself is untouched.
        if((value.contains("can i cross")||value.contains("could i cross")||value.contains("is it safe to cross")
            ||value.contains("can i get across")||value.contains("can i wade")||value.contains("can i swim")
            ||value.contains("will it carry")||value.contains("is there a ford")||value.contains("crossable")
            ||((value.startsWith("can i")||value.startsWith("could i")||value.startsWith("is "))
               &&(value.contains("cross")||value.contains("wade")||value.contains("swim")||value.contains("ford"))))
           ) return Intent.JUDGE_CROSSING;
        // What the team can pull (#37). The draft subsystem is finished and almost entirely invisible: gear is
        // sized to the body, four vehicles have four beds, rough ground tires a team half again as hard, and
        // fatigue, hunger, thirst and conditioning all scale the draw. All of it is computed inside an UPDATE
        // that runs only when you WALK, so a keeper standing in their own camp could not ask any of it.
        //
        // And every sentence that asked was answered by a recipe for a cart:
        //
        //   pull the cart           -> "You have not got enough cart wheel within reach"
        //   load the cart           -> the same
        //   hitch the ox to the cart-> the same
        //   harness the ox          -> nothing at all
        //   yoke the oxen           -> nothing at all
        //
        // Gated on the draught rather than on the vehicle noun alone, so that making one is still making one.
        if((value.contains("harness")||value.contains("yoke")||value.contains("hitch")||value.contains("unhitch")
            ||value.contains("pull the")||value.contains("draw the")||value.contains("load the")||value.contains("unload the")
            ||value.contains("what can the")||value.contains("how is the team")||value.contains("how are the team")
            ||value.contains("can they pull")||value.contains("what will they pull")||value.contains("how much can they pull"))
           &&(value.contains("cart")||value.contains("travois")||value.contains("sledge")||value.contains("sled")
              ||value.contains("pack-saddle")||value.contains("pack saddle")||value.contains("team")||value.contains("beast")
              ||value.contains("ox")||value.contains("oxen")||value.contains("horse")||value.contains("donkey")||value.contains("yak"))
           &&!MAKING_SOMETHING.matcher(value).find()) return Intent.JUDGE_HAULAGE;
        // Asking WHETHER the water is safe is a question, not a drink (#37). It reached DRINK and was answered
        // by drinking the marsh water, which is the one outcome the asker was trying to avoid. Gated on a
        // question shape AND water, and placed before anything that drinks.
        if((value.contains("safe to drink")||value.contains("safe water")||value.contains("clean enough")
            ||((value.startsWith("is ")||value.startsWith("can i")||value.startsWith("will ")||value.contains("should i drink")
                ||value.contains("how is the")||value.contains("what is the"))
               &&(value.contains("water")||value.contains("stream")||value.contains("pool")||value.contains("spring"))
               &&(value.contains("safe")||value.contains("clean")||value.contains("drink")||value.contains("foul")||value.contains("ill"))))
           &&!value.contains("boil")) return Intent.JUDGE_WATER;
        // Stuff or line a garment already being worn (#37). Gated on a lining VERB plus a garment word, so
        // "gather dry grass" still gathers and "make a fur cloak" still makes one — the difference is that this
        // one is done TO something you have on.
        if((value.contains("stuff")||value.contains("pack")||value.contains("line ")||value.contains("line my")
            ||value.contains("insulate")||value.contains("wad"))
           &&(value.contains("boot")||value.contains("shoe")||value.contains("mitten")||value.contains("glove")
              ||value.contains("hood")||value.contains("cap")||value.contains("cloak")||value.contains("coat")
              ||value.contains("jerkin")||value.contains("vest")||value.contains("legging")||value.contains("sock")
              ||value.contains("garment")||value.contains("what i am wearing")||value.contains("my clothes"))
           &&!value.contains("make ")&&!value.contains("sew ")&&!value.contains("craft ")) return Intent.LINE_GARMENT;
        if((value.contains("tidy")||value.contains("arrange")||value.contains("straighten")||value.contains("set in order")||value.contains("order the")||value.contains("clean up")||value.contains("maintain")||value.contains("look after")||value.contains("protect"))&&(value.contains("camp")||value.contains("campsite")||value.contains("supplies")||value.contains("shelter site"))) return Intent.MAINTAIN_CAMP;
        // Mucking out is the SAME WORK on the SAME counter (#106). maintainCamp carries off `chunk_refuse`, which
        // is the very thing V299's stock sickness offers "clean ground" as a way out of — so a keeper clearing a
        // pen is doing exactly what this intent does, and "muck out the pen" reached nothing at all while "tidy
        // the camp" did it. Routed here rather than given a second implementation that would drift from this one.
        if((value.contains("muck out")||value.contains("muck the")||value.contains("shovel out")||value.contains("clear the dung")
            ||value.contains("clear the muck")||value.contains("clear the manure")||value.contains("clean out")
            ||((value.contains("clean")||value.contains("clear"))&&(value.contains("the pen")||value.contains("the coop")||value.contains("the stable")
               ||value.contains("the byre")||value.contains("the stall")||value.contains("the shelter")||value.contains("the fold"))))
           &&!value.contains("build")&&!value.contains("make ")) return Intent.MAINTAIN_CAMP;
        // Bare-hand cover (#195): a windbreak/brush screen leant against the wind — no tool, partial protection.
        if((value.contains("windbreak")||value.contains("wind break")||value.contains("brush screen")||value.contains("wind screen")||value.contains("reed screen against"))&&(value.contains("make")||value.contains("build")||value.contains("raise")||value.contains("set up")||value.contains("put up")||value.contains("weave")||value.contains("lean")||value.contains("erect"))) return Intent.PLACE_WINDBREAK;
        // Bare-hand partial covers (#195): sunshade / rain cover / groundsheet / stone ring — a placing verb plus the
        // cover named. Kept after MAKE_BED and before COOL_BODY/SHELTER_BODY so "rest in the shade" (no placing verb)
        // stays a body act, while "rig a sunshade" places one.
        if(coverKindOf(value)!=null&&(value.contains("make")||value.contains("build")||value.contains("raise")||value.contains("set up")||value.contains("put up")||value.contains("rig")||value.contains("lean")||value.contains("prop")||value.contains("lay")||value.contains("spread")||value.contains("pitch")||value.contains("erect")||value.contains("place")||value.contains("set out")||value.contains("arrange"))) return Intent.PLACE_COVER;
        if(value.contains("warm up")||value.contains("warm myself")||value.contains("get warm")||value.contains("warm my hands")||value.contains("warm by the fire")||value.contains("warm at the fire")||(value.contains("warm")&&value.contains("fire"))
           // Sitting or standing by a fire is what a person actually says, and act ten found it reaching nothing
           // while "warm my hands" worked. Gated on the fire so that "sit down" stays the posture question this
           // project has deliberately left alone.
           ||((value.contains("sit")||value.contains("stand")||value.contains("stay")||value.contains("rest"))
              &&(value.contains("by the fire")||value.contains("at the fire")||value.contains("near the fire")))
           ||value.contains("thaw")||value.contains("rub my hands")||value.contains("chafe my hands")) return Intent.WARM_BODY;
        // Act fourteen: three more ways of saying it, over the drying and warming that already work.
        if(value.contains("wring out")||value.contains("wring my")||value.contains("change into dry")
           ||value.contains("dry clothes")||value.contains("dry things")) return Intent.DRY_BODY;
        if(value.contains("rub the feeling")||value.contains("rub some feeling")||value.contains("get the feeling back")) return Intent.WARM_BODY;
        // Getting nearer the fire is warming yourself at it (#37). "sit by the fire" worked and "move closer to
        // the fire" reached nothing — the same act, said as a movement, and the move rule wanted a bearing.
        if((value.contains("closer to the fire")||value.contains("nearer the fire")||value.contains("nearer to the fire")
            ||value.contains("close to the fire")||value.contains("up to the fire"))
           &&!value.contains("too close")) return Intent.WARM_BODY;
        if(value.contains("dry off")||value.contains("dry myself")||value.contains("dry my ")
           ||(value.contains("get dry")&&!value.contains("dry grass")&&!value.contains("dry wood")&&!value.contains("dry tinder")&&!value.contains("dry branch"))
           ||value.contains("warm and dry")||value.contains("dry out by")) return Intent.DRY_BODY;
        if(value.contains("cool off")||value.contains("cool down")||value.contains("cool myself")||value.contains("rest in the shade")||value.contains("rest in shade")||value.contains("get out of the heat")||value.contains("get out of the sun")||value.contains("get into the shade")) return Intent.COOL_BODY;
        // Said plainly (#37). SHELTER_BODY knew "take shelter" and "get out of the rain" but not the shortest
        // way anybody puts it, so "go inside" reached nothing at all.
        if(value.contains("go inside")||value.contains("get inside")||value.contains("go indoors")||value.contains("get indoors")||value.contains("get under a roof")
           ||value.contains("take shelter")||value.contains("take cover")||value.contains("get under cover")||value.contains("get out of the rain")||value.contains("get out of the weather")||value.contains("shelter from")||value.contains("shelter myself")||value.contains("get under the shelter")
           // Act twelve, in a snowstorm on high ground: the two plainest ways to say it both reached nothing.
           ||value.contains("get out of the wind")||value.contains("out of the wind")
           ||value.contains("find shelter")||value.contains("look for shelter")||value.contains("get inside")) return Intent.SHELTER_BODY;
        // Water handling (#71): collect / boil / filter — before the gather and drink rules so "collect water"
        // is filling a vessel, not gathering, and "boil water" reaches its handler rather than a process miss.
        if(value.contains("boil water")||value.contains("boil the water")||value.contains("boil some water")||value.contains("heat water to a boil")||value.contains("boil it to make it safe")) return Intent.BOIL_WATER;
        // Pour water through a filter to clarify it — but MAKING a filter ("make a bark and charcoal filter")
        // is a craft, so defer to the material process when the text names one rather than filtering here.
        if((value.contains("filter")||value.contains("strain")||value.contains("clarify")||value.contains("purify"))&&value.contains("water")&&!items.actionMatchesProcess(value)) return Intent.FILTER_WATER;
        // "find water" (#37). Looking for water and drawing it collapse to the same act the moment there is
        // water to draw, and COLLECT_WATER already answers honestly when there is none — "no stream, no spring,
        // only what the sky gives". That is a far better answer than silence. ("look for water" is left to
        // SEARCH, which claims "look for" and answers about what it finds.)
        if(value.contains("find water")||value.contains("find some water")||value.contains("find a stream")
           ||value.contains("collect water")||value.contains("fetch water")||value.contains("draw water")||value.contains("gather water")||value.contains("fill container")||value.contains("scoop water")||((value.contains("fill")||value.contains("refill"))&&(value.contains("waterskin")||value.contains("water skin")||value.contains("bucket")||value.contains("vessel")||value.contains("jar")||value.contains("with water")||value.contains("flask")||value.contains("gourd")))) return Intent.COLLECT_WATER;
        if(value.contains("charcoal")&&(value.contains("make")||value.contains("take")||value.contains("gather")||value.contains("get")||value.contains("collect"))&&!items.actionMatchesProcess(value)) return Intent.MAKE_CHARCOAL;
        if(value.contains("bark")&&!value.contains("loose")&&(gatherVerb||value.contains("strip")||value.contains("peel")||value.contains("take"))) return Intent.STRIP_BARK;
        // Ambient ground scavenge (#133): search the forest floor / under a log for small survival materials — the
        // bare-hand pickup of the #192 litter (twigs, leaf litter/tinder, loose bark, shed feather/fur, driftwood,
        // reeds). Placed after the specific gathers (branches/strip-bark/tinder) and before the generic SEARCH, so a
        // pointed scavenge yields material while a bare "look around" still just perceives.
        if((value.contains("search")||value.contains("look for")||value.contains("forage")||value.contains("scavenge")||value.contains("comb the")||value.contains("comb under")||value.contains("pick up")||value.contains("collect")||value.contains("gather"))
           &&(value.contains("forest floor")||value.contains("leaf litter")||value.contains("under a log")||value.contains("under the log")||value.contains("under logs")||value.contains("loose bark")||value.contains("shed feather")||value.contains("loose feather")||value.contains("shed fur")||value.contains("loose fur")||value.contains("driftwood")||value.contains("deadwood")||value.contains("windfall")||value.contains("twig")||value.contains("kindling")||value.contains("tinder")||value.contains("dry wood")||value.contains("dry branch")||value.contains("shed antler")||value.contains("antler")||value.contains("gnawed bone")||value.contains("loose bone")||value.contains("bone on the ground")||value.contains("bone off the ground")||value.contains("scavenge bone")||value.contains("scavenge for bone")))
            return Intent.FORAGE_GROUND;
        // Terrain crossing (#72): wading/fording/swimming/climbing toward a direction is movement to the next ground.
        if(Direction.from(value)!=null && (value.contains("wade")||value.contains("ford")||value.contains("swim")||value.contains("cross")||value.contains("climb")||value.contains("scramble")||value.contains("clamber")||value.contains("traverse"))) return Intent.MOVE;
        // A crossing with no bearing in it (#37). "can I get across here" answered in detail — the load, the
        // ford, a laid way — and "wade across", "swim across" and "cross to the other side" reached nothing: the
        // game could JUDGE a crossing and not make one. The water names the direction, and move() asks the same
        // query the judgement does, so the two can never disagree about what is there.
        //
        // Gated on the sentence being about getting OVER something, so "cross my arms" and "I am cross" are
        // nobody's crossing, and on no bearing being named, since the rule above already has those.
        if(CROSSING_VERB.matcher(value).find()
           &&(value.contains("across")||value.contains(" over")||value.contains("other side")||value.contains("far side")
              ||value.contains("the river")||value.contains("the stream")||value.contains("the water")
              ||value.contains("the fen")||value.contains("the marsh")||value.contains("the bog"))) return Intent.MOVE;
        // Climbing, and following a feature (#37). Both name a piece of GROUND rather than a bearing, which the
        // move rule wanted — so "climb the hill", "follow the stream", "follow the shore" and "go down into the
        // valley" all reached nothing, though which way the hill lies is a thing world_chunk has always known.
        //
        // Gated on a kind of ground being named, or on a bare up/down, so "follow the trail" is still TRACK's
        // hunt and "follow them" is still the people's. The move itself resolves the bearing from the same
        // vocabulary WHICH_WAY answers from, so what the game says lies north is what walking north reaches.
        if(wantsGroundStep(value)) return Intent.MOVE;
        // "head east" reached NOTHING while "go north", "walk south" and "go west" all worked (#37). The move rule
        // knows walk/travel/go/move; "head" was only ever read as part of "head to" and "head for", which are
        // TRAVEL's and want a place rather than a bearing — so one of the commonest ways of saying the commonest
        // thing a player does fell between the two rules. Gated on a direction being named, so "head for the high
        // ground" and "head back to camp" are still journeys, and as words, because "head" sits inside
        // "arrowhead" and "headsperson".
        if(Direction.from(value)!=null && (word(value,"head")||word(value,"heading")||word(value,"headed")
            ||value.contains("set off")||value.contains("set out")||value.contains("strike out")||value.contains("push on"))) return Intent.MOVE;
        // Breaking off from danger (#72): retreat/flee/hide. "hide" scoped ("hide from"/"hide myself") so tanning
        // or working an animal hide is untouched.
        // Keeping away from something is how most encounters should end (#37). DISENGAGE knew retreat, flee and
        // hide, and none of the words for the choice a sensible Chronicle actually makes about a monster's lair.
        if(value.contains("avoid")||value.contains("steer clear")||value.contains("keep away")||value.contains("keep clear")||value.contains("wide berth")
           ||value.contains("retreat")||value.contains("back away")||value.contains("fall back")||value.contains("withdraw")||word(value,"flee")||value.contains("run away")||value.contains("run from")||value.contains("escape from")||value.contains("get away from")||value.contains("hide from")||value.contains("hide myself")||value.contains("conceal myself")||value.contains("go to ground")) return Intent.DISENGAGE;
        // "go to sleep" is not a journey (#37). TRAVEL claims any "go to", and SLEEP declares the exact phrase
        // "go to sleep" — in classifyLegacy, which runs after the whole chain, so it never got a look. A
        // Chronicle asking to sleep was told "you cannot call the way to mind", which is a travel failure in
        // answer to lying down. Bed is the same: you go to bed where you already are.
        if(!value.contains("go to sleep")&&!value.contains("go to bed")
           &&(value.contains("go to")||value.contains("head to")||value.contains("head for")||value.contains("make for")||value.contains("travel to")||value.contains("walk to")||value.contains("return to")||value.contains("go back to")||value.contains("head back to")||value.contains("journey to")||value.contains("set out for"))&&Direction.from(value)==null) return Intent.TRAVEL;
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
        // Asking after your own body (#37). The Body HUD had every word of this — Injured, Hypothermic, Soaked,
        // Exhausted — and a Chronicle who asked got prose about the reeds ("examine myself" resolved to OBSERVE
        // and described the ground) or "it is too dark to see the fine of it". The thing that kills you was the
        // one thing you could not ask about.
        if(value.contains("how am i")||value.contains("how do i feel")||value.contains("how is my health")
           ||value.contains("check my injur")||value.contains("check my wound")||value.contains("check my body")||value.contains("check myself")
           ||value.contains("examine my body")||value.contains("inspect my body")||value.contains("examine myself")||value.contains("look at myself")
           ||value.contains("take stock of myself")||value.contains("assess myself")||value.contains("assess my condition")
           ||value.contains("am i hurt")||value.contains("am i injured")||value.contains("am i sick")||value.contains("am i ill")
           ||value.contains("what shape am i")
           // Act ten and act twelve: each of these is a person asking the body a question it can answer, and each
           // reached nothing. The body knows every one of its own pressures.
           ||value.contains("what do i need")||value.contains("am i freezing")||value.contains("am i starving")
           ||value.contains("am i too cold")||value.contains("how am i holding")||value.contains("how bad is it")&&value.contains("wound")
           // Act fourteen swept the one domain the earlier acts never did — what a person says ABOUT THEMSELVES
           // — and 28 of 41 reached nothing while "am I ill" answered well. Every one of these asks after
           // something the body tracks by name (injury_severity, blood_loss_ml, pain_level, illness_severity,
           // core_temperature_c, wetness_level, sleep_debt_hours, energy_level) and that bodyReading already
           // says out loud, down to "the wet has got through your clothes" and "your legs are going out from
           // under you". Nothing here is new. It was the asking that failed.
           ||value.contains("how bad is the cut")||value.contains("how bad is the wound")
           ||value.contains("am i bleeding")||value.contains("still bleeding")||value.contains("stopped bleeding")
           ||value.contains("is the bleeding")||value.contains("is it swelling")||value.contains("can i walk on")
           ||value.contains("how sick")||value.contains("do i have a fever")||value.contains("have i got a fever")
           ||value.contains("am i feverish")||value.contains("am i getting cold")||value.contains("am i cold")
           ||value.contains("are my feet wet")||value.contains("am i wet")||value.contains("going numb")
           ||value.contains("how tired")||value.contains("can i keep going")||value.contains("what state am i")
           ||value.contains("how do i feel")||value.contains("how am i bearing")
           // Whether the night will be survivable (#37). ChroniclePhysiologyService already weighs the weather,
           // the fire's fuel_minutes, a lean-to and what is worn when it decides how fast a body loses heat —
           // and "will I be warm enough tonight" and "am I dry" reached nothing.
           ||value.contains("warm enough")||value.contains("cold enough to")||value.contains("am i dry")
           ||value.contains("freeze tonight")||value.contains("see the night out myself")
           ||value.contains("survive the night")||value.contains("get through the night")) return Intent.SENSE_BODY;
        // Whether the stock will get in calf (#37). Not an act — breeding is simulated and happens on its own
        // when the conditions are right — but a question with five real answers behind it, none of which the
        // keeper could see. Before this, "breed the goats" was caught by the bestiality filter and answered as
        // an assault on the animal, which is the worst kind of wrong answer there is.
        if((value.contains("breed")||value.contains("in calf")||value.contains("in kid")||value.contains("in lamb")||value.contains("put to the"))
           &&!value.contains("breed with")) return Intent.BREEDING_PROSPECTS;
        if(value.contains("listen")) return Intent.LISTEN;
        if(value.contains("smell")||value.contains("sniff")) return Intent.SMELL;
        if(value.contains("feel the")||value.contains("feel around")||value.contains("feel it")||value.contains("touch the")||value.contains("touch it")||value.contains("run my hand")||value.contains("test the surface")||value.contains("by touch")) return Intent.FEEL;
        // Asking after the weather IS an act of the body — you read the air by standing in it (#37). None of
        // these reached anything at all before: "what season is it" and "how cold is it" both fell through to
        // UNKNOWN and came back with a crafting miss, which is prose about failing to make something in answer
        // to a question about the sky. FEEL answers them because FEEL is the sense that takes the reading.
        if(value.contains("how cold")||value.contains("how warm")||value.contains("how hot")||value.contains("what season")||value.contains("which season")||value.contains("season is it")||value.contains("time of year")||value.contains("the temperature")||value.contains("how is the air")||value.contains("what is the weather")||value.contains("what's the weather")||value.contains("how is the weather")||value.contains("what is the sky")
           ||value.contains("check the weather")||value.contains("check the sky")||value.contains("look at the weather")) return Intent.FEEL;
        if(value.contains("search")||value.contains("look for")||value.contains("hunt for")||value.contains("check beneath")||value.contains("check under")||value.contains("look under")||value.contains("turn over")||value.contains("comb through")||value.contains("rummage")||value.contains("forage through")||value.contains("sift through the")) return Intent.SEARCH;
        // Reading a written document (#65 read/review_record). Word-boundary "read" so "bread" doesn't match;
        // "read the ground/tracks" already resolved to TRACK above.
        if(word(value,"read")||word(value,"reread")||word(value,"peruse")||word(value,"consult")||value.contains("study the writing")||value.contains("study the tablet")||value.contains("study the page")||value.contains("study the journal")||value.contains("unfold and read")||value.contains("check the contents")||((value.contains("review")||value.contains("study"))&&(value.contains("tablet")||value.contains("journal")||value.contains("page")||value.contains("record")||value.contains("writing")||value.contains("document")||value.contains("note")||value.contains("inscription")))) return Intent.READ;
        // Estimation (#65 measure): weigh/count/pace-out/depth. Word-boundary the short verbs so "account"⊅count.
        // "how long have I been here" is the plainest way to ask it and reached nothing at all: the rule below
        // wants a measuring word, and that sentence has none (#37). Gated on the same elapsed-time shape the
        // answer itself reads, so it takes nothing that was not already about time.
        if(reckonsElapsedTime(value)) return Intent.MEASURE;
        // "how long", "how wide", "how thick" and "how tall" joined them (#37). The rule knew how far, how deep,
        // how heavy and how many, and not the commonest measurement there is: "how long is this plank" reached
        // nothing at all. Found by a test written for something else, which is the usual way.
        //
        // Safe here because the two rules that own a "how long" about TIME both run earlier — the sky reading
        // takes "how long until dark", and reckonsElapsedTime takes "how long have I been here" on the line
        // above. A length question is what is left.
        if(value.contains("measure")||value.contains("pace out")||value.contains("pace off")||word(value,"weigh")||value.contains("how heavy")||word(value,"heft")||value.contains("how many")||word(value,"count")||word(value,"tally")||value.contains("how far")||value.contains("how deep")
           ||(value.contains("how long")
              // ...except where there is nothing behind the number. Healing time, infection and a broken bone
              // are deliberately unmodelled, and TheQuestionsABodyCanAnswerIntegrationTest holds that they
              // stay honest misses — "you pace it out and reckon by eye" over a wound is a near-miss dressed
              // as an answer, which is worse than nothing.
              &&!value.contains("heal")&&!value.contains("mend itself")&&!value.contains("to knit"))
           ||value.contains("how wide")||value.contains("how thick")||value.contains("how tall")||value.contains("how big")
           ||value.contains("test the depth")||value.contains("estimate the distance")||value.contains("gauge")) return Intent.MEASURE;
        if(value.contains("refine")||value.contains("improve")||value.contains("upgrade")||value.contains("revise")||value.contains("enhance")||(value.contains("add")&&value.contains("holder"))) return Intent.REFINE;
        if(value.contains("designate")||value.contains("christen")||((value.contains("name")||value.contains("call")||value.contains("establish")||value.contains("found")||value.contains("mark"))&&(value.contains("this place")||value.contains("this area")||value.contains("this spot")||value.contains("this location")||value.contains("here as")||value.contains("this as")||value.contains("this the")))) return Intent.DESIGNATE;
        // DROP / place (#67): set an object down on the ground here. STORE (into a container) and the trap/
        // marker verbs are already claimed above, so a bare place/lay/set-down here is a plain drop.
        if(value.contains("drop")||value.contains("leave behind")||value.contains("set down")||value.contains("put down")||value.contains("discard")||value.contains("lay down")||value.contains("lay it down")||value.contains("lay them down")||((value.contains("place")||value.contains("set")||value.contains("put")||value.contains("lay")||value.contains("leave"))&&(value.contains("on the ground")||value.contains("down here")||value.contains(" aside")))) return Intent.DROP;
        if(value.contains("unequip")||value.contains("take off")||value.contains("remove my")||value.contains("remove the")||value.contains("doff")) return Intent.UNEQUIP;
        // Treating a hurt where the sentence names the PART rather than the hurt (#37, act fourteen). The
        // treatment rule knew bind, bandage, dress, clean, tend, treat, see to and wash — against wound, injury,
        // bleeding, cut and gash. So "splint my leg" and "cauterise it" reached nothing over a mechanism that
        // works perfectly from "bind the wound", because nothing in them is a word for a wound.
        //
        // Above EQUIP, which otherwise answers "splint my leg" with "you have nothing unequipped that can be
        // worn or wielded" — a wrong answer in the even voice of a true one.
        // "bandage" joins them (#106). Found while giving the animal rules their dressing verbs: `bandage my arm`
        // reached NOTHING, because the legacy rule pairs bandage only with a word for a wound and this one did
        // not know the verb at all — so the body part was useless to it. Only "bandage", deliberately: the noun
        // group below ends in a bare `" it"`, and bind, wash, clean and dress would each steal a sentence from
        // somebody — `bind the planks` is lashing, `wash it` is washing. Nothing else in this world is bandaged.
        if((value.contains("splint")||value.contains("stitch")||value.contains("cauteris")||value.contains("cauteriz")||value.contains("sling")
            ||value.contains("bandage")
            ||value.contains("staunch")||value.contains("change the dressing")||value.contains("press on"))
           &&(value.contains("wound")||value.contains("cut")||value.contains("bleeding")||value.contains("gash")
              ||value.contains("my leg")||value.contains("my arm")||value.contains("my ankle")||value.contains("my wrist")
              ||value.contains("my ribs")||value.contains("my hand")||value.contains("my foot")||value.contains("my shoulder")
              ||value.contains("my knee")||value.contains("my side")||value.contains("dressing")||value.contains(" it"))) return Intent.TREAT_WOUND;
        if((value.contains("equip")||value.contains("wear")||value.contains("put on")||value.contains("wield")||value.contains("hold my")||value.contains("hold the")||value.contains("carry on my back"))) return Intent.EQUIP;
        // "sling" also means equip (sling it over a shoulder) — but not when 'sling' is part of a thing being made,
        // i.e. a sling stone or a sling pouch ("shape a sling stone", "sew a sling stone pouch"). Those route to their
        // craft recipes; every other use of 'sling' is the equip verb.
        if(value.contains("sling") && !value.contains("sling stone") && !value.contains("sling pouch")) return Intent.EQUIP;
        if(!action.contains(":")&&(value.contains("map")||value.contains("chart")||value.contains("cartograph"))&&(value.contains("sketch")||value.contains("draw")||value.contains("make")||value.contains("chart")||value.contains("create")||value.contains("update")||value.contains("plot")||value.contains("survey")||value.contains("map out"))) return Intent.SKETCH_MAP;
        if(action.contains(":")&&(value.contains("write")||value.contains("draw")||value.contains("sketch")||value.contains("record")||value.contains("inscribe")||value.contains("mark ")||value.contains("note"))) return Intent.WRITE;
        return classifyLegacy(action);
    }
    /** Whole-word containment, delegating to the one definition of it — see {@link com.devosphere.draugr.narration.Words}. */
    private static boolean word(String haystack, String w) { return com.devosphere.draugr.narration.Words.word(haystack, w); }

    /**
     * The kinds of ground a person asks the way to, and the biomes each one means (#37).
     *
     * <p>Spoken word first, longest spoken form first within a kind, so "high ground" is not read as "ground".
     * Every biome the generator makes is reachable through one of these; a kind nobody has a word for would be a
     * place that cannot be asked after, which is the defect this answer exists to end.
     */
    private static final String[][] GROUND_ASKED_FOR = {
        {"open water", "OCEAN"}, {"the sea", "OCEAN"}, {"the ocean", "OCEAN"},
        {"the shore", "COAST"}, {"the coast", "COAST"}, {"the beach", "COAST"},
        {"the river", "RIVER_BANK"}, {"the stream", "RIVER_BANK"}, {"the bank", "RIVER_BANK"},
        {"fresh water", "RIVER_BANK|WETLAND"}, {"freshwater", "RIVER_BANK|WETLAND"},
        {"drinking water", "RIVER_BANK|WETLAND"}, {"water", "RIVER_BANK|WETLAND|COAST|OCEAN"},
        {"the marsh", "WETLAND"}, {"the fen", "WETLAND"}, {"the bog", "WETLAND"},
        {"marshland", "WETLAND"}, {"wetland", "WETLAND"}, {"the reeds", "WETLAND"},
        {"high ground", "HIGHLAND|MOUNTAIN"}, {"higher ground", "HIGHLAND|MOUNTAIN"},
        {"the hills", "HIGHLAND"}, {"the hill", "HIGHLAND"}, {"the uplands", "HIGHLAND"},
        {"the mountain", "MOUNTAIN"}, {"the mountains", "MOUNTAIN"}, {"the ridge", "HIGHLAND|MOUNTAIN"},
        {"the woods", "TEMPERATE_FOREST"}, {"the wood", "TEMPERATE_FOREST"}, {"the forest", "TEMPERATE_FOREST"},
        {"the trees", "TEMPERATE_FOREST"}, {"timber", "TEMPERATE_FOREST"},
        {"open ground", "GRASSLAND"}, {"the grassland", "GRASSLAND"}, {"the meadow", "GRASSLAND"},
        {"grazing", "GRASSLAND"}, {"pasture", "GRASSLAND"}, {"the plain", "GRASSLAND"},
        {"a cave", "CAVE_MOUTH|CAVE_INTERIOR"}, {"the cave", "CAVE_MOUTH|CAVE_INTERIOR"},
        {"shelter from the wind", "TEMPERATE_FOREST|CAVE_MOUTH"},
        {"lower ground", "GRASSLAND|WETLAND|RIVER_BANK|COAST"}, {"the valley", "GRASSLAND|RIVER_BANK"},
    };

    /**
     * Which way a kind of ground lies (#37).
     *
     * <p>A player moves more often than they do anything else, and the movement axis swept at <b>26 of 42
     * sentences reaching nothing</b>. "which way is the water" was among them, though every piece of the answer
     * was in {@code world_chunk}: the neighbouring biomes, their elevations, and the grid offsets that say which
     * way each one lies. {@code planTravel} only knows places the Chronicle has NAMED, so a kind of ground could
     * be asked for and never found.
     *
     * <p><b>One ring, and only in the light</b>, which is the same bound {@link #skyReading} and the visual
     * context keep: the shape of the next ground over is plain from where anyone stands, and what lies two
     * chunks off is a thing to be scouted rather than known. Inside a cave, or after dark, nothing beyond this
     * ground can be seen and the answer says so rather than reading the map for them.
     *
     * <p>Bearings come from {@link com.devosphere.draugr.world.Compass}, the one place the convention lives.
     */
    private String whichWay(UUID location, String actionText, Instant at) {
        String v = actionText == null ? "" : actionText.toLowerCase(Locale.ROOT);
        String asked = null, biomes = null;
        for (String[] kind : GROUND_ASKED_FOR)
            if (groundNamed(v, kind[0]) && (asked == null || kind[0].length() > asked.length())) { asked = kind[0]; biomes = kind[1]; }
        if (asked == null) return null;

        java.util.Map<String,Object> here = jdbc.queryForMap(
            "SELECT world_id, grid_x, grid_y, elevation, biome FROM world_chunk WHERE id=?", location);
        String standingOn = (String) here.get("biome");
        boolean dark = isDark(at);
        boolean underground = "CAVE_INTERIOR".equals(standingOn);

        // Standing on it already is the first thing worth saying, and it is true in the dark and underground.
        for (String b : biomes.split("\\|"))
            if (b.equals(standingOn))
                return "You are standing on it — this ground is " + groundSpoken(standingOn) + ", and what you are "
                    + "looking for is underfoot.";

        if (underground) return "You are inside the rock. There is no telling which way anything lies from in here.";
        if (dark) return "It is too dark to make out the shape of the country beyond this ground. By daylight you "
            + "could see which way it lies; now you would be going on memory and guesswork.";

        java.util.List<String> seen = new java.util.ArrayList<>();
        for (java.util.Map<String,Object> n : jdbc.queryForList(
                "SELECT n.biome, n.elevation, n.grid_x - ? AS dx, n.grid_y - ? AS dy FROM world_chunk n " +
                "WHERE n.world_id=? AND abs(n.grid_x-?)+abs(n.grid_y-?)=1 ORDER BY dy, dx",
                here.get("grid_x"), here.get("grid_y"), here.get("world_id"), here.get("grid_x"), here.get("grid_y"))) {
            String b = (String) n.get("biome");
            boolean wanted = false;
            for (String k : biomes.split("\\|")) wanted |= k.equals(b);
            if (!wanted) continue;
            String bearing = com.devosphere.draugr.world.Compass.of(
                ((Number) n.get("dx")).intValue(), ((Number) n.get("dy")).intValue());
            if (bearing == null) continue;
            int rise = ((Number) n.get("elevation")).intValue() - ((Number) here.get("elevation")).intValue();
            seen.add(bearing + " lies " + groundSpoken(b)
                + (rise > 40 ? ", and the ground rises to it" : rise < -40 ? ", and the ground falls away to it" : ""));
        }

        if (seen.isEmpty())
            return "You look about for " + asked.replace("the ", "").replace("a ", "")
                + " and see nothing of it from here — not on this ground, and not on any ground you can see from "
                + "it. It may lie further off than the eye reaches, which is a thing to be scouted rather than "
                + "guessed at.";
        // Joined with semicolons rather than "and", because each bearing may carry a clause of its own about the
        // rise or the fall of the ground to it, and two kinds of "and" in one sentence read as one list.
        return "From here: " + String.join("; ", seen) + ". That is as far as the eye carries; past the next "
            + "ground over you would be scouting rather than looking.";
    }

    /**
     * Whether the sentence names a kind of ground somebody could ask the way to — with or without its article.
     *
     * <p>"any marsh nearby" is how a person asks, and the table says "the marsh"; rather than keep every kind
     * twice, the article is stripped and the bare noun matched <b>as a word</b>. That boundary is not optional:
     * "sea" sits inside "season" and "research", and this project has shipped the substring defect four times.
     */
    /**
     * Whether the sentence is an assembly's own name (#106/#77).
     *
     * <p>A buildable thing is reached by the assembly matcher, which only runs when no hard intent claims the
     * phrase first. So a question-intent broad enough to swallow an assembly's keyword makes that thing
     * unbuildable by its own name: {@code noJavaIntentShadowsAnAssemblysOwnKeywords} caught CHECK_STOCK doing it
     * to all three of the sick animal shelter's words, on "sick" plus "animal".
     *
     * <p>Asked of the matcher rather than held as a list of building verbs, so that every assembly added after
     * this one is protected too. Null-guarded because the routing regression test builds this service without an
     * AssemblyService: it has no world, and the guard that found this needs one.
     */
    private boolean namesAnAssembly(String value) {
        return assembly != null && assembly.match(value) != null;
    }

    private static boolean namesGroundAsked(String value) {
        for (String[] kind : GROUND_ASKED_FOR) if (groundNamed(value, kind[0])) return true;
        return false;
    }

    /** The kind as written, or its bare noun as a whole word. */
    private static boolean groundNamed(String value, String kind) {
        if (value.contains(kind)) return true;
        String bare = kind.replaceFirst("^(the|a|an) ", "");
        if (bare.equals(kind)) return false;
        return bare.indexOf(' ') >= 0 ? value.contains(bare) : word(value, bare);
    }

    /** What a biome is called by somebody standing on it, rather than by the generator. */
    private static String groundSpoken(String biome) {
        return switch (biome) {
            case "OCEAN" -> "open water";
            case "COAST" -> "the shore";
            case "RIVER_BANK" -> "a river bank";
            case "WETLAND" -> "marsh";
            case "GRASSLAND" -> "open grass";
            case "TEMPERATE_FOREST" -> "woodland";
            case "HIGHLAND" -> "high ground";
            case "MOUNTAIN" -> "mountain";
            case "CAVE_MOUTH" -> "a cave mouth";
            case "CAVE_INTERIOR" -> "cave";
            default -> biome.toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    /** The words a keeper has for an animal that is NOT in the catalogue — the kinds, ranks and ages of stock. */
    private static final String[] KEPT_STOCK_WORDS = {
        "animal", "animals", "beast", "beasts", "stock", "livestock", "cattle", "herd", "flock",
        "cow", "cows", "calf", "calves", "bullock", "heifer", "bull", "hen", "hens", "chick", "chicks",
        "ewe", "ram", "lamb", "kid", "foal", "mare", "colt", "mule", "sick one"};

    /**
     * Whether the sentence is about an animal somebody keeps (#106).
     *
     * <p><b>One list, for all three of them.</b> Tending, grooming and feeding each carried its OWN hand-kept
     * roll of species, and the three had drifted apart until they no longer agreed on what an animal was:
     *
     * <pre>
     *   TEND_ANIMAL    goat, horse, cow, ox, sheep, fowl, reindeer, donkey, buffalo
     *   GROOM_ANIMAL   (no species at all — only animal/beast/stock/herd/flock)
     *   FEED_ANIMAL    ox, aurochs, deer, elk, reindeer, cattle, livestock — no goat, sheep or horse
     * </pre>
     *
     * So the beast you could tend you could not groom, and the one you could groom you could not feed. Measured
     * on a booted world: "groom the goat" reached nothing, and "feed the goat" was answered by TAME — <i>"it lets
     * you come nearer than last time"</i> — which is the approach to a WILD animal offered to a keeper feeding
     * their own. Three lists meant three different animals; this is the one list, and the catalogue is the rest
     * of it. A species added to {@code draft_species} or {@code tamed_yield} becomes tendable, groomable and
     * feedable in the same breath, which is the whole point of asking the table.
     */
    private boolean namesABeast(String value) {
        for (String w : KEPT_STOCK_WORDS) if (w.indexOf(' ') < 0 ? word(value, w) : value.contains(w)) return true;
        return items.namesAKeptAnimal(value);
    }
    /**
     * The verbs that ask for something to be cooked — as WORDS, and never their past participles.
     *
     * <p>This rule asked {@code contains("cook")}, and "cooked" contains "cook": so "eat the cooked fish"
     * was answered by putting another fish on the fire. <b>A past participle names a FOOD; the verb asks for
     * work.</b> The same held for roasted, grilled, baked and stewed, and the defect was waiting in the rule
     * before V396 widened it — "eat the cooked meat" has always cooked more meat.
     */
    private static final java.util.regex.Pattern COOKING_VERB = java.util.regex.Pattern.compile(
            "(?<!\\w)(cook|cooks|cooking|roast|roasts|roasting|grill|grills|grilling|bake|bakes|baking"
          + "|broil|broils|broiling|simmer|simmers|simmering|stew|stews|stewing|braise|braises|braising)(?!\\w)");

    /**
     * The contested gathering verbs, as WORDS with their inflections.
     *
     * <p>Asked as a regex rather than with {@code contains}, because the first cut of this clause used
     * {@code contains("cut")} and <b>"scutch the flax" contains "cut"</b> — so scutching flax became a plant
     * gather and the linen road broke at its third step. "pick" sits inside "pickaxe" the same way. This is the
     * substring defect #37 exists to find, and it is no better for being in the fix.
     *
     * <p>"dig" is deliberately absent and spelled "dig up" at the call site: "dig a root cellar" names a real
     * root and a real assembly, so no boundary saves it, and "dig up" is how the act is said of a root.
     */
    /** A direction the world chose, or the reason it could not. Exactly one of the two is set. */
    private record Toward(Direction direction, String refusal) { }

    /**
     * Which way the next piece of a KIND of ground lies, for the acts whose object is the ground (#37).
     *
     * <p>One implementation for crossing, climbing and following, because all three ask the same question and
     * three copies of it would drift — and because the answer must agree with what {@link #whichWay} tells the
     * player. If the game says the marsh lies north, wading across had better go north.
     *
     * @param biomes pipe-separated biome keys, or null to go by height alone
     * @param upward with a null {@code biomes}: TRUE for the highest neighbour, FALSE for the lowest
     */
    private Toward toward(UUID location, String biomes, Boolean upward) {
        java.util.List<java.util.Map<String,Object>> around = jdbc.queryForList(
            "SELECT next.grid_x - here.grid_x AS dx, next.grid_y - here.grid_y AS dy, next.biome, " +
            "  next.elevation - here.elevation AS rise " +
            "FROM world_chunk here JOIN world_chunk next ON next.world_id=here.world_id " +
            "  AND abs(next.grid_x-here.grid_x) + abs(next.grid_y-here.grid_y) = 1 " +
            // North first and round, so a list of sides reads like a compass rather than like a table.
            "WHERE here.id=? ORDER BY dy, dx", location);

        java.util.List<java.util.Map<String,Object>> fit = new java.util.ArrayList<>();
        if (biomes != null) {
            for (java.util.Map<String,Object> n : around)
                for (String k : biomes.split("\\|")) if (k.equals(n.get("biome"))) { fit.add(n); break; }
            if (fit.isEmpty())
                return new Toward(null, "There is none of that within a step of this ground — not on any side of "
                    + "it. What you are after lies further off than one step, which is a thing to be scouted.");
        } else {
            // By height. A step that barely rises is not a climb: the same 40-unit threshold the aspect model and
            // the visual context's relief are built on, so "climb" means the same thing everywhere it is used.
            java.util.Map<String,Object> best = null;
            for (java.util.Map<String,Object> n : around) {
                int rise = ((Number) n.get("rise")).intValue();
                if (upward ? rise <= 40 : rise >= -40) continue;
                if (best == null || (upward ? rise > ((Number) best.get("rise")).intValue()
                                            : rise < ((Number) best.get("rise")).intValue())) best = n;
            }
            if (best == null)
                return new Toward(null, upward
                    ? "There is nothing to climb from here — the ground about you lies as level as this does."
                    : "There is nothing to climb down to — the ground about you lies as level as this does.");
            fit.add(best);
        }

        if (fit.size() > 1) {
            java.util.List<String> sides = new java.util.ArrayList<>();
            for (java.util.Map<String,Object> n : fit) {
                String bearing = com.devosphere.draugr.world.Compass.of(
                    ((Number) n.get("dx")).intValue(), ((Number) n.get("dy")).intValue());
                if (bearing != null) sides.add(bearing);
            }
            return new Toward(null, "That lies on more than one side of this ground — " + joinAnd(sides)
                + ". Say which way you mean to go.");
        }
        Direction chosen = Direction.of(((Number) fit.get(0).get("dx")).intValue(),
                                        ((Number) fit.get(0).get("dy")).intValue());
        return chosen == null ? new Toward(null, "You cannot work out which way that lies from here.")
                              : new Toward(chosen, null);
    }

    /**
     * The verbs whose object is a piece of ground rather than a bearing (#37).
     *
     * <p>As words: "follow" is harmless but "up" and "down" are not — "down" sits inside "downstream" and
     * "sundown", and a bare {@code contains} would make every sentence with a sundown in it a descent.
     */
    private static final java.util.regex.Pattern CLIMB_OR_FOLLOW = java.util.regex.Pattern.compile(
            "(?<!\\w)(climb|climbs|climbing|scramble|scrambles|scrambling|clamber|clambers|clambering"
            + "|follow|follows|following|ascend|ascends|descend|descends)(?!\\w)");

    /**
     * The climbing verbs alone, which need no object: a bare "climb" means UP in anybody's English, and so do
     * "scramble" and "ascend". "follow" is deliberately not among them — following with no object named is a
     * sentence with a hole in it, and the honest answer is to say so rather than pick a direction.
     */
    private static final java.util.regex.Pattern CLIMBING_VERB = java.util.regex.Pattern.compile(
            "(?<!\\w)(climb|climbs|climbing|scramble|scrambles|scrambling|clamber|clambers|clambering|ascend|ascends)(?!\\w)");

    /**
     * Whether the sentence asks for a step whose object is a piece of GROUND rather than a bearing (#37).
     *
     * <p>One condition, read by the classifier and by {@link #move} both, because the two must agree exactly:
     * a sentence the classifier sends to MOVE and the move cannot resolve is answered "you shift through the wet
     * ground, but do not commit to a direction", which is a true sentence about the wrong thing.
     *
     * <p>"go up the hill" and "go down into the valley" carry no climbing verb at all — just a preposition — and
     * are the commonest way of saying it, so they are here beside the climbing words.
     */
    private static boolean wantsGroundStep(String value) {
        // CLIMBING A TREE IS NOT WALKING UPHILL. A tree is a thing on the ground rather than a piece of it,
        // and the act of going up one has its own answer — which witnesses the ground it refuses on
        // ("nothing here that will take your weight"). This rule walked north instead, and found higher
        // ground to do it on, so a Chronicle on open grass climbed a tree that was not there.
        if (value.contains("tree") || value.contains("trunk") || value.contains("branches")) return false;
        boolean verb = CLIMB_OR_FOLLOW.matcher(value).find()
            || value.contains("go up") || value.contains("go down")
            || value.contains("head up") || value.contains("head down")
            || value.contains("up into") || value.contains("down into")
            || value.contains("make my way up") || value.contains("make my way down");
        if (!verb) return false;
        // A climbing verb needs no object — a bare "climb" means up — so it is enough on its own.
        if (CLIMBING_VERB.matcher(value).find()) return true;
        return namesGroundAsked(value) || value.contains("up the") || value.contains("down the")
            || value.contains("up into") || value.contains("down into")
            || value.contains("descend")
            || value.strip().endsWith(" up") || value.strip().endsWith(" down");
    }

    /**
     * How a person says they are going back the way they came (#37).
     *
     * <p>"my own tracks" is the deciding phrase against TRACK, which hunts animal sign: the tracks are the
     * player's and the act is retracing, not hunting. "back to camp" is deliberately absent — that is a journey
     * to a named place and TRAVEL's, and it works.
     */
    private static final java.util.regex.Pattern BACKTRACKING = java.util.regex.Pattern.compile(
            "retrace|back the way|way i came|way we came|my own tracks|my tracks|my own footprints|back on my tracks");

    /**
     * The verbs for getting over water (#37). As words, because "ford" is a name a person might give a place and
     * "cross" sits inside "crossbar", "crossing" and "crosswise" — and a sentence about a crossbar is not a
     * sentence about wading a fen.
     */
    private static final java.util.regex.Pattern CROSSING_VERB = java.util.regex.Pattern.compile(
            "(?<!\\w)(wade|wades|wading|ford|fords|fording|swim|swims|swimming|cross|crosses|traverse|traverses)(?!\\w)");

    private static final java.util.regex.Pattern GATHERING_VERB = java.util.regex.Pattern.compile(
            "(?<!\\w)(cut|cuts|cutting|pick|picks|picking|pull|pulls|pulling|snip|snips|snipping"
            // "mow" and "scythe" are what cutting grass is called (#106), and both reached nothing while "cut
            // the grass" worked. Bounded like everything else here: "mow" sits inside "mower" and "mown", and
            // a mown field is one that has already been cut rather than a request to cut it.
            + "|mow|mows|mowing|scythe|scythes|scything|reap|reaps|reaping)(?!\\w)");
    /**
     * The verbs that mean MAKING the thing, as words — so that asking a team to pull a cart is read as hauling
     * while asking for a cart is still read as building one. Held as words rather than substrings, because
     * "make" sits inside nothing useful but "carve" sits inside "carved" and a past participle names a thing
     * that already exists rather than asking for another.
     */
    private static final java.util.regex.Pattern MAKING_SOMETHING = java.util.regex.Pattern.compile(
            "(?<!\\w)(make|makes|making|build|builds|building|craft|crafts|crafting|carve|carves|carving"
          + "|assemble|assembles|assembling|weave|weaves|weaving|lash|lashes|lashing|haft|hafts|hafting"
          + "|repair|repairs|repairing|mend|mends|mending|dismantle|dismantles|dismantling)(?!\\w)");
    /**
     * Whether a phrase asks for a carcass's yield by naming the yield and nothing else — "take the hide",
     * "keep the antlers", "save the sinew", "take the pelt off".
     *
     * <p>The yield noun must END the phrase or be followed by {@code off} or {@code from}, so that the same
     * word standing as the MATERIAL of a made thing is left alone: "take my hide boots" is a take of boots,
     * "keep the skin bag" a keep of a bag. That is the whole guard — no held-back list of made nouns to drift
     * out of step with the catalogue.</p>
     */
    private static boolean namesTheYieldItself(String value) {
        return YIELD_ASKED_FOR_BY_NAME.matcher(value).find();
    }
    private static final java.util.regex.Pattern YIELD_ASKED_FOR_BY_NAME = java.util.regex.Pattern.compile(
            "\\b(take|keep|save)\\b[^.]*?\\b(hide|pelt|skin|antler|antlers|sinew)\\b(\\s+(off|from)\\b|\\s*[.!?]?\\s*$)");
    private Intent classifyLeanTo(String value) {
        // ASKING ABOUT A LEAN-TO IS NOT BUILDING ONE (#37). "is the lean-to still good" fell to the last branch
        // below and answered "You mark out a low shelter frame against the weather" — it STARTED BUILDING ONE.
        // A wrong answer that changes the world is the worst kind this project has found: a missing answer costs
        // a turn, and this cost a turn and left a frame standing that nobody asked for.
        //
        // The camp stocktake already reports every structure here with its integrity, which is exactly what the
        // question wants, so the question goes there.
        if(value.startsWith("is ")||value.startsWith("how ")||value.startsWith("does ")||value.startsWith("will ")
           ||value.startsWith("what ")||value.contains("still good")||value.contains("still sound")
           ||value.contains("still standing")||value.contains("still hold")||value.contains("any good")
           ||value.contains("sound enough")||value.contains("in good repair")) return Intent.TAKE_STOCK_OF_CAMP;
        if(value.contains("repair")) return Intent.REPAIR_LEAN_TO;
        if(value.contains("abandon")||value.contains("leave")) return Intent.ABANDON_LEAN_TO;
        if(value.contains("resume")||value.contains("return to")) return Intent.RESUME_LEAN_TO;
        return (value.contains("work") || value.contains("continue") || value.contains("build") || value.contains("weave") || value.contains("bind")) ? Intent.WORK_LEAN_TO : Intent.START_LEAN_TO;
    }
    private Intent classifyLegacy(String action) { String value = action.toLowerCase(Locale.ROOT); if (COOKING_VERB.matcher(value).find() && (value.contains("meat") || value.contains("game") || value.contains("flesh") || value.contains("carcass")
            // V396 gave the fire a table of what it turns into what, so the other raw foods it can now cook are
            // reachable by the sentence a person would use. A fish was the sharpest case: every preservation
            // process would take it and nothing would simply cook it.
            || value.contains("fish") || value.contains("fowl") || word(value, "bird"))) return Intent.COOK_MEAT; if ((value.contains("harvest") || value.contains("butcher") || value.contains("skin") || value.contains("gut") || value.contains("quarter") || value.contains("dress")
) && (value.contains("carcass") || value.contains("remains") || value.contains("animal") || value.contains("the kill") || value.contains("the game") || word(value, "deer") || word(value, "boar") || word(value, "elk") || word(value, "aurochs") || word(value, "hare") || word(value, "rabbit") || word(value, "goat") || word(value, "wolf") || word(value, "bear") || word(value, "fox"))
            // Act eleven: "take the hide" reached nothing standing over a deer this same rule would have skinned,
            // because every phrasing it knew named the animal. Naming the yield is naming the act; when nothing
            // here is dead, the harvest says so, which is a better answer than no answer.
            || namesTheYieldItself(value)) return Intent.HARVEST_CARCASS; if ((value.contains("bind") || value.contains("bandage") || value.contains("dress") || value.contains("clean") || value.contains("tend") || value.contains("treat") || value.contains("see to") || value.contains("wash")) && !value.contains("woundwort") && (value.contains("wound") || value.contains("injury") || value.contains("bleeding") || value.contains("the cut") || value.contains("my cut") || value.contains("gash"))) return Intent.TREAT_WOUND; if ((value.contains("feed") || value.contains("stoke") || value.contains("add wood")) && value.contains("fire")) return Intent.FEED_FIRE; if ((value.contains("light")||value.contains("ignite")) && value.contains("fire")) return Intent.LIGHT_FIRE; if (value.contains("fire pit") || value.contains("firepit")) return Intent.BUILD_FIRE_PIT; if ((value.contains("fight")||value.contains("attack")||value.contains("strike")||value.contains("spear ")||value.contains("shoot")||value.contains("hurl")||value.contains("throw the")||value.contains("throw my")||word(value,"kill")||word(value,"hunt")||word(value,"stalk")) && (value.contains("animal")||value.contains("wildlife")||value.contains("creature")||value.contains("beast")||word(value,"deer")||word(value,"boar")||word(value,"wolf")||word(value,"hare")||word(value,"rabbit")||word(value,"fox")||word(value,"elk")||word(value,"aurochs")||word(value,"bear")||word(value,"goat")||word(value,"bird"))) return Intent.CONFRONT_WILDLIFE; if ((value.contains("weave") || value.contains("craft") || value.contains("make")) && value.contains("basket") && !value.contains("burden") && !value.contains("pack") && !value.contains("large") && !value.contains("big") && !value.contains("pannier") && !value.contains("carrying") && !value.contains("back basket") && !value.contains("shoulder") && !value.contains("lidded") && !value.contains("covered") && !value.contains("with a lid")) return Intent.CRAFT_BASKET; if ((value.contains("gather")||value.contains("collect")) && value.contains("fiber")) return Intent.GATHER_FIBER; if (((value.contains("gather")||value.contains("collect")) && (value.contains("branch")||value.contains("stick")))
            // Act twelve, before a storm: "lay in more wood" is the same act as gathering it, and reached nothing.
            || ((value.contains("lay in")||value.contains("stock up")||value.contains("bring in")) && (value.contains("wood")||value.contains("fuel")||value.contains("firewood")))) return Intent.GATHER_BRANCHES; if ((value.contains("gather")||value.contains("collect")) && (value.contains("berry")||value.contains("berries"))) return Intent.GATHER_BERRIES; if ((value.contains("gather")||value.contains("collect")) && (value.contains("stone")||value.contains("rock"))) return Intent.GATHER_STONE; if (word(value,"eat")||value.contains("consume")) return Intent.EAT; if (value.contains("drink") && !MAKING_SOMETHING.matcher(value).find()) return Intent.DRINK; if (Direction.from(value) != null && (value.contains("walk") || value.contains("travel") || value.contains("go ") || value.contains("move"))) return Intent.MOVE; if (value.contains("observe") || value.contains("look") || value.contains("inspect") || value.contains("survey") || value.contains("scout") || value.contains("scan") || value.contains("explore") || value.contains("examine") || value.contains("study the") || value.contains("take in")) return Intent.OBSERVE; if ((value.contains("sleep") && !value.contains("platform") && !value.contains("sleeping bench") && !value.contains("sleeping mat") && !value.contains("sleeping pad")) || word(value,"nap") || value.contains("lie down to sleep") || value.contains("lie down") || value.contains("lay down to") || value.contains("bed down") || value.contains("go to sleep") || value.contains("go to bed")) return Intent.SLEEP; if (value.contains("rest") || value.contains("wait") || value.contains("sit down") || value.contains("sit for a") || value.contains("take the weight off")) return Intent.REST; if (value.contains("where am i") || value.contains("what is this place") || value.contains("what place is this") || value.contains("where do i stand")) return Intent.OBSERVE; if ((value.contains("write") || value.contains("inscribe") || value.contains("jot")) && !value.contains("map")) return Intent.WRITE; if (value.contains("urinate") || word(value, "pee")) return Intent.URINATE; if (value.contains("defecate") || value.contains("poop")) return Intent.DEFECATE;
        // "take off my boots" was an unequip and "take my boots off" reached nothing — the same sentence in the
        // word order English actually prefers for a separable particle. Deliberately the LAST rule in the chain,
        // so that everything with a better claim on a trailing "off" has already taken it: the lid comes off a
        // container, the bark off a tree, the hide off a carcass. Whatever is left is a person undressing, and
        // if they name something they are not wearing, the unequip says so by name.
        if (PARTICLE_AFTER_THE_GARMENT.matcher(value).find()) return Intent.UNEQUIP;
        return Intent.UNKNOWN; }
    private static final java.util.regex.Pattern PARTICLE_AFTER_THE_GARMENT = java.util.regex.Pattern.compile(
            "\\b(take|pull|get|slip|shrug|strip)\\b\\s+(my|the|them|it)\\b[^.]*?\\boff\\b\\s*[.!?]?\\s*$");
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
        // A forage marked poisonous (death cap, fly agaric) nourishes nothing — it sickens. The world
        // applies physics, not a warning: the player had to know which mushroom before they ate it.
        if (items.isPoisonousForage(itemKey)) {
            items.consumeOne(chronicle, itemKey, at);
            physiology.applyFoodborneIllness(chronicle, actionId, at);
            return new String[]{"SUCCEEDED", "You eat it. The taste turns sharp, then bitter, and a cold unease is spreading through your gut before you have finished."};
        }
        // Consume the food: a food that carries a spoilage state — a made dish, a preserved or dried food — goes
        // through the tracked consume so a spoiled one is caught (the same rule meat already follows); stateless
        // forage (berries, mushrooms, honey) is eaten directly, its grade read for nourishment (#271). Eating rotten
        // food is a real way to fall ill — before, only spoiled meat was caught, and a spoiled stew or dried fish ate
        // as clean as fresh.
        com.devosphere.draugr.quality.QualityGrade grade;
        boolean spoiled;
        FoodPreservationService.Consumption tracked = food.consume(chronicle, itemKey, at);
        if (tracked.consumed()) {
            grade = tracked.grade();
            spoiled = tracked.spoiled();
        } else {
            grade = items.gradeOfNextConsumed(chronicle, itemKey);
            items.consumeOne(chronicle, itemKey, at);
            spoiled = false;
        }
        physiology.eat(chronicle, grade);
        // A hot herbal infusion is barely a meal — its worth is the warmth it carries and the calm the steeped herb
        // brings; this applies however it was made, tracked or not.
        boolean infusion = itemKey.equals("herbal_infusion");
        if (infusion) physiology.drinkWarmInfusion(chronicle);
        if (spoiled) {
            physiology.applyFoodborneIllness(chronicle, actionId, at);
            return new String[]{"SUCCEEDED", infusion
                ? "You drink the infusion, but it has turned — sour and off, and it sits ill in you before the warmth ever comes."
                : "You eat it, but it has gone off — a sour, spoiled taste, and a cold unease follows it down."};
        }
        if (infusion)
            return new String[]{"SUCCEEDED", "You drink the hot infusion slowly; the warmth spreads through you and the herb's scent settles the edges of your mind."};
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
    // The convention — north is grid_y-1 — now lives in Compass (#224), because the visual context had to say
    // which way the next ground lies and a second copy of it would have let a place look one way and feel
    // another. CompassAgreesWithDirectionTest holds the enum below to the same convention.
    private String compass(int dx, int dy) { String c = com.devosphere.draugr.world.Compass.of(dx, dy); return c == null ? "at the centre" : c; }
    private String rotateCompass(String dir, boolean clockwise) { return com.devosphere.draugr.world.Compass.rotate(dir, clockwise); }
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
    /** Minutes per chunk crossed on horseback — better than twice walking pace, which is 18. */
    private static final int RIDDEN_MINUTES_PER_DISTANCE = 8;
    /** What a chunk cost to cross before the ground was asked (#77, V335): the fallback for country with no going. */
    private static final int FLAT_MINUTES_PER_DISTANCE = 18;

    /** A companion (#113) goes where the Chronicle goes, and the telling says so. */
    private String withCompanion(UUID chronicle, String perception, Instant at) {
        if (companions == null) return perception;
        UUID now = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        String line = companions.follow(chronicle, now, at);
        return line == null ? perception : perception + " " + line;
    }

    /** Whether this ground has taken enough refuse to be noticed standing on it (#218). Half the level at which
     *  a foul camp starts drawing hungry animals in — so it is said before it bites, not after. */
    private boolean campIsGettingFoul(UUID location) {
        Integer refuse = jdbc.queryForObject("SELECT COALESCE((SELECT refuse_level FROM chunk_refuse WHERE chunk_id=?),0)", Integer.class, location);
        return refuse != null && refuse >= 25;
    }

    private record ActiveChronicle(UUID id, UUID location) { } private record TravelPlan(UUID destination, int distance, String reason, int minutesPerChunk) { } private enum Intent { OBSERVE, MOVE, TRAVEL, MARK, REST, SLEEP, GATHER_FIBER, GATHER_STONE, GATHER_BERRIES, GATHER_BRANCHES, GATHER_CLAY, GATHER_STONE_SLAB, GATHER_PLANT, FELL_TREE, PLANT_TREE, COPPICE, TILL_GROUND, SOW, HARVEST_CROP, WEED_CROP, JUDGE_CROSSING, HIDE_TRAIL, TAKE_STOCK_OF_CAMP, READ_THE_SKY, WHICH_WAY, CHECK_FIRE, CHECK_STANDING, CHECK_CROP, TAKE_STOCK_OF_GEAR, TAKE_STOCK_OF_FOOD, JUDGE_WATER, WATER_CROP, SCARE_BIRDS, LINE_GARMENT, CLEAR_LAND, FEED_ANIMAL, RAID_HIVE, RAID_NEST, COLLECT_INSECTS, FISH, SNARE, TRACK, SCOUT, TAME, LURE, SET_TRAP, CHECK_TRAP, CRAFT_GARMENT, GATHER_MINERAL, CRAFT_FIRE_TOOL, PROCESS_MATERIAL, SKETCH_MAP, EAT, DRINK, COLLECT_WATER, BOIL_WATER, FILTER_WATER, WASH, WARM_BODY, DRY_BODY, COOL_BODY, SHELTER_BODY, STRETCH, TREAT_WOUND, EDIT_DOCUMENT, WRITE, STRIP_BARK, MAKE_CHARCOAL, LIGHT_FIRE, FEED_FIRE, EXTINGUISH_FIRE, BANK_FIRE, COOK_MEAT, CONFRONT_WILDLIFE, HARVEST_CARCASS, DISENGAGE, CRAFT_BASKET, CRAFT_SPEAR, CRAFT_KNIFE, CRAFT_HAMMER, CRAFT_PICKAXE, CRAFT_HATCHET, CRAFT_FIRE_KIT, CRAFT_TINDER, CRAFT_DESK, CRAFT_CHAIR, CRAFT_SHELF, CRAFT_WORKSTATION, CRAFT_NET, CRAFT_BELT, BUILD_FIRE_PIT, BUILD_ALARM, BUILD_FENCE, BUILD_PEN, BUILD_LOOKOUT, BUILD_FUEL_RACK, BUILD_LATRINE, BUILD_TOOL_SHED, BUILD_SMOKE_VENT, BUILD_STORAGE_AREA, RESTORE_HABITAT, START_LEAN_TO, WORK_LEAN_TO, ABANDON_LEAN_TO, RESUME_LEAN_TO, REPAIR_LEAN_TO, REPAIR_ITEM, REPAIR_STRUCTURE, DISMANTLE, EQUIP, UNEQUIP, DROP, PICK_UP, STORE, OPEN_CONTAINER, CLOSE_CONTAINER, DESIGNATE, REFINE, ADVANCE_ASSEMBLY, INSPECT, EXAMINE, ANALYZE, INVESTIGATE, SEARCH, LISTEN, SMELL, FEEL, READ, MEASURE, REWORK, URINATE, DEFECATE, PERSONAL_ACT, AGGRESSION_WILDLIFE, AGGRESSION_INANIMATE, MAKE_BED, MAINTAIN_CAMP, PLACE_WINDBREAK, PLACE_COVER, FORAGE_GROUND, TAKE_ANIMAL_YIELD, TEND_ANIMAL, GROOM_ANIMAL, CHECK_STOCK, SENSE_BODY, BREEDING_PROSPECTS, CONTACT_PEOPLE, TRADE_WITH_PEOPLE, CONDUCT_TOWARD_PEOPLE, AGREE_WITH_PEOPLE, WORK_FOR_PEOPLE, COMPANION_PEOPLE, ADDRESS_PEOPLE, JOIN_PEOPLE, SETTLE_CLAIM, EMPTY_CONTAINER, JUDGE_HAULAGE, UNKNOWN }
    private enum Direction { NORTH(0,-1,"north"), SOUTH(0,1,"south"), EAST(1,0,"east"), WEST(-1,0,"west"); final int dx; final int dy; final String description; Direction(int dx,int dy,String description){this.dx=dx;this.dy=dy;this.description=description;} static Direction from(String action){String value=action.toLowerCase(Locale.ROOT); for(Direction direction:values()) if(value.matches(".*\\b"+direction.description+"\\b.*")) return direction; return null;}
        /** The cardinal for a grid offset, for when the WORLD names the direction rather than the player (#37). */
        static Direction of(int dx,int dy){ for(Direction d:values()) if(d.dx==dx&&d.dy==dy) return d; return null; } }    /**     * The structured perception frame — the seam every future Simulation Agent reads
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
            // Aspect (#159): ground that falls away to the south takes the light square-on and sits out of the
            // north wind. Derived from the neighbour's stored elevation rather than a column of its own, so the
            // pinned world needs no migration and no regeneration to have slopes it always had.
            "EXISTS(SELECT 1 FROM world_chunk n WHERE n.world_id=wc.world_id AND n.grid_x=wc.grid_x " +
            "       AND n.grid_y=wc.grid_y-1 AND n.elevation > wc.elevation + 40) AS sun_warmed, " +
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
            globalKind, ((Number) env.get("t")).doubleValue(), ((Number) env.get("w")).intValue(),
            Boolean.TRUE.equals(env.get("sun_warmed")));
        boolean weatherChanged = beforeWeather != null && globalKind != null && !beforeWeather.equals(globalKind);
        return narrationEngine.ground(core, biome, timeOfDayLabel(at), local.kind(), attention, weatherChanged);
    }
    /**
     * The body, said in words (#37). Built from the SAME snapshot the Body HUD is built from, so the two cannot
     * disagree about whether a Chronicle is freezing.
     *
     * <p>It reports what PRESSES — the aspects that are not in their contented state — in the order a body
     * would force them on you, because a body does not announce that it is hydrated. When nothing presses it says
     * so, which is itself an answer and not a failure.
     */
    private String bodyReading(ChroniclePhysiologyService.BodyHudSnapshot b) {
        if (b == null) return "You take stock of yourself, and find nothing you can put a name to.";
        List<String> said = new java.util.ArrayList<>();
        switch (b.health()) {
            case "Critical" -> said.add("something in you is badly wrong and will not wait");
            case "Injured" -> said.add("there is a hurt in you that has not closed");
            default -> { }
        }
        switch (b.temperature()) {
            case "Hypothermic" -> said.add("the cold has got past shivering and into the middle of you");
            case "Cold" -> said.add("the chill has worked in under your skin");
            case "Warm" -> said.add("the heat sits on you and will not lift");
            case "Hot" -> said.add("the heat has you sweating even at rest");
            case "Hyperthermic" -> said.add("you are burning, and the sweat has stopped coming");
            default -> { }
        }
        switch (b.thirst()) {
            case "Critical Dehydration" -> said.add("your mouth is past dry and your head is splitting with it");
            case "Dehydrated" -> said.add("your tongue is thick and your lips have cracked");
            case "Thirsty" -> said.add("your mouth has gone dry");
            default -> { }
        }
        switch (b.hunger()) {
            case "Critical Starvation" -> said.add("there is nothing left on you and the body has started on itself");
            case "Starving" -> said.add("the hollow in you has stopped aching and gone quiet, which is worse");
            case "Very Hungry" -> said.add("hunger has settled into a steady ache");
            case "Hungry" -> said.add("your stomach is hollow");
            default -> { }
        }
        switch (b.energy()) {
            case "Collapsing" -> said.add("your legs are going out from under you");
            case "Exhausted" -> said.add("your limbs are heavy and slow to answer");
            case "Fatigued" -> said.add("the work costs more than it did this morning");
            default -> { }
        }
        switch (b.condition()) {
            case "In pain" -> said.add("the pain of it is hard to think past");
            case "Distressed" -> said.add("your nerves are drawn thin and will not settle");
            case "Sleep deprived" -> said.add("the want of sleep is behind your eyes");
            default -> { }
        }
        switch (b.wetness()) {
            case "Soaked" -> said.add("you are wet to the skin and the wind finds every inch of it");
            case "Wet" -> said.add("the wet has got through your clothes");
            default -> { }
        }
        switch (b.bladder()) {
            case "Critical" -> said.add("there is a hard pressure low in you that has become pain");
            case "Urgent" -> said.add("there is a pressure low in you");
            default -> { }
        }
        switch (b.bowel()) {
            case "Critical" -> said.add("your gut is cramping and will not hold much longer");
            case "Urgent" -> said.add("your gut is pressing to be emptied");
            default -> { }
        }
        switch (b.hygiene()) {
            case "Hazardous" -> said.add("you are filthy enough that the filth is itself a danger");
            case "Filthy" -> said.add("you are filthy enough to smell yourself");
            default -> { }
        }
        if (said.isEmpty())
            return "You take stock of yourself. Nothing presses: fed, watered, warm enough, dry enough, and whole. "
                 + "It will not stay so on its own, but it is so now.";
        StringBuilder sb = new StringBuilder("You take stock of yourself. ");
        for (int i = 0; i < said.size(); i++) {
            String part = said.get(i);
            sb.append(i == 0 ? Character.toUpperCase(part.charAt(0)) + part.substring(1) : part);
            sb.append(i == said.size() - 1 ? "." : i == said.size() - 2 ? ", and " : ", ");
        }
        return sb.toString();
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
     * The capability family an action exercises, from its impact card (#216/#26).
     *
     * <p>LOAD = strength and carrying, AIM = precision at a target, ATTENTION = perception, LOCOMOTION = travel,
     * RECOVERY = rest, KNOWLEDGE and INSIGHT = what reading and reasoning build, FINE_MOTOR = skilled hand-work.
     * One family per intent; the growth itself is slow, hidden and lifelong (CapabilityAdaptationService).
     *
     * <p>Was a switch over the enum, moved onto the card with the labour and the footprint so that all three can
     * be read as one statement about an act instead of three lists that could disagree. The families are exactly
     * the ones the switch gave — V309's guard asserts the count in each against what the switch produced, so a
     * slip in moving them could not have passed quietly.
     *
     * <p>FINE_MOTOR when a card is missing, which is what the switch's own default gave, so a catalogue gap
     * behaves as it always did rather than faulting mid-action.
     */
    private String capabilityDomainOf(Intent intent) {
        String domain = jdbc.query("SELECT capability_domain FROM activity_impact WHERE intent_key = ?",
            rs -> rs.next() ? rs.getString(1) : null, intent.name());
        return domain == null ? "FINE_MOTOR" : domain;
    }
    /**
     * Record what a successful act did to the ground it was done on, as its impact card says (#215/#216).
     *
     * <p>This is the whole of the footprint rule and the only implementation of it. Before the card table there
     * were four: a switch over five intents, and three recordings written inline into the dispatch lines for
     * felling, coppicing and clearing. Nothing anywhere could answer what the other hundred and twenty-one
     * intents did to the land, because their answer was "nothing" by omission — nobody had ever decided it.
     *
     * <p>A missing card is a failure, not a silence. The Intent enum and {@code activity_impact} are asserted to
     * be the same set by ActivityImpactCardIntegrationTest, so a new intent added without a card fails the suite
     * rather than quietly joining the hundred and twenty-one; and a card that marks nothing still has to carry
     * the sentence saying why, which the table's own CHECK enforces. That is #216's acceptance criterion — no
     * procedure activated without a complete card — expressed where it can actually be enforced.
     */
    private void markTheGround(Intent intent, String text, UUID location, Instant at) {
        Map<String, Object> card = jdbc.query(
            "SELECT footprint_kind, footprint_amount, drifts, only_with_fire FROM activity_impact WHERE intent_key = ?",
            rs -> rs.next() ? Map.of("kind", rs.getString(1) == null ? "" : rs.getString(1),
                                     "amount", rs.getInt(2), "drifts", rs.getBoolean(3), "fire", rs.getBoolean(4))
                            : null,
            intent.name());
        if (card == null) return;                                   // The test is the gate; play does not fault.
        String kind = (String) card.get("kind");
        int amount = (Integer) card.get("amount");
        if (kind.isEmpty() || amount <= 0) return;                  // A card that says the land is left as found.
        // PROCESS_MATERIAL is every material process there is, and only the ones that burn lay a plume.
        if ((Boolean) card.get("fire") && !items.actionIsFireProcess(text)) return;
        if ((Boolean) card.get("drifts")) wildlife.recordEmissionDrift(location, kind, amount, at);
        else wildlife.recordDisturbance(location, kind, amount, at);
    }

    /** The physical cost of an action beyond the passive tick (GitHub #27): energy spent, hygiene lost. */
    private record Labor(int energy, int hygiene) { }

    /**
     * What the act costs the body, from its impact card (#216).
     *
     * <p>This was a switch over the Intent enum, beside two others: one for the mark the act leaves on the land
     * and one for the mastery it builds. Three lists over the same 128 values, in the same file, each ending in
     * a {@code default} — so an intent added to one and forgotten by the others was silent, and an act that
     * costs nothing, builds nothing and marks nothing read exactly like an act somebody had decided was free.
     *
     * <p>Putting them on one row made a question askable that had never been asked: does anything mark the land
     * while costing the body nothing? Two did. One was {@code AGGRESSION_WILDLIFE}, which is right — a fight
     * runs its own physiology and charging it here would charge the body twice. The other was {@code COPPICE},
     * which was in none of the three labour tiers at all: cutting a stool back with an axe took the canopy down
     * and tired nobody. That is fixed in V309 rather than preserved.
     *
     * <p>Falls back to costing nothing when a card is missing, because play must not fault on a catalogue gap —
     * ActivityImpactCardIntegrationTest is the gate that makes a missing card impossible to ship.
     */
    private Labor laborOf(Intent intent) {
        Labor card = jdbc.query("SELECT labor_energy, labor_hygiene FROM activity_impact WHERE intent_key = ?",
            rs -> rs.next() ? new Labor(rs.getInt(1), rs.getInt(2)) : null, intent.name());
        return card == null ? new Labor(0, 0) : card;
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
            "SELECT COUNT(*) FROM ecology_site WHERE chunk_id=? AND (" + com.devosphere.draugr.ecology.FreshWater.sites() + ")",
            Integer.class, location);
        if (sites != null && sites > 0) return true;
        // A completed rainwater catchment (#77) is itself a source to fill from — the rain it has caught — so a camp
        // on dry ground with no stream can still draw water once one stands. It is not a safeWaterSource: caught
        // rainwater is raw and better boiled, exactly what COLLECT_WATER yields.
        return catchmentInReach(location);
    }
    /** Whether this ground has water of its OWN — a wet biome or a freshwater site — as against water that only
     *  stands here because something was built to hold it. The prose turns on the difference: a structure is what
     *  a stream is drawn THROUGH, but on dry ground the structure is the source itself. */
    private boolean naturalWaterHere(UUID location) {
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
        if ("WETLAND".equals(biome) || "RIVER_BANK".equals(biome)) return true;
        Integer sites = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ecology_site WHERE chunk_id=? AND (" + com.devosphere.draugr.ecology.FreshWater.sites() + ")",
            Integer.class, location);
        return sites != null && sites > 0;
    }

    /** A completed rainwater catchment OR well standing here, to fill a vessel from (#77). A well is the same
     *  thought as the catchment pointed downward: ground with no stream has its own water once one is sunk. Both
     *  give RAW water — a well is not a safeWaterSource either, and what it yields is better boiled. */
    private boolean catchmentInReach(UUID location) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind IN ('RAINWATER_CATCHMENT','WELL') AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND w.lifecycle_state='ACTIVE')", Boolean.class, location));
    }
    /**
     * What stands on this ground, by name, with a word for anything that is failing (#37).
     *
     * <p>Sound work is named and left at that. Work that is coming apart is named with its state, because that is
     * the thing a person standing in their own camp actually notices — and because the integrity that decides
     * whether a shelter still shelters, a pen still holds and a latrine still takes anything was invisible from
     * inside the game. A ruin (integrity 0) does not stand at all and is not listed; {@code campStocktake} names
     * those, since going round the camp deliberately is when you would find them.
     */
    private String standingHere(UUID location) {
        java.util.List<String> named = jdbc.query(
            "SELECT lower(ck.display_name), cp.integrity_percent FROM construction_project cp " +
            "JOIN world_object w ON w.id=cp.object_id JOIN construction_kind ck ON ck.project_kind=cp.project_kind " +
            "WHERE w.current_location_id=? AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND w.lifecycle_state='ACTIVE' " +
            "ORDER BY cp.integrity_percent, ck.display_name LIMIT 6",
            (rs, row) -> {
                int integrity = rs.getInt(2);
                String name = rs.getString(1);
                return integrity < 35 ? "a " + name + " that is coming apart"
                     : integrity < 70 ? "a weathered " + name
                     : "a " + name;
            }, location);
        if (named.isEmpty()) return "";
        return "Your own work stands here: " + joinAnd(named) + ".";
    }

    /**
     * Go round the camp and account for it (#37). "take stock of the camp" reached nothing at all, and the survey
     * would say only that structures stood here — so a Chronicle could not find out what they had built, what state
     * it was in, or what they had left half-finished, without reading the database.
     *
     * <p>Read-only, and deliberately fuller than the survey: it carries the state of each standing thing and the
     * work still under way with how far along it is. Nothing here is new information the world did not have; it
     * is the world's own record, said out loud.
     *
     * <p>It does NOT list ruins, and that is a deliberate omission rather than an oversight: the Auditor treats a
     * completed construction at zero integrity while still active as an inconsistency, and the tick takes such a
     * thing down in the same pass that wears it out. A "past mending" list would be prose for a state the world
     * is not allowed to be in.
     */
    /**
     * What the sky is doing, asked plainly (#37).
     *
     * <p>Act fifteen swept what a person says about the world over their head, and <b>22 of 35 phrasings reached
     * nothing</b> — while "what is the weather doing" and "what season is it" answered well. The hour, the month,
     * the wind and the frost were all simulated and none of them could be asked:
     *
     * <pre>
     *   what time is it        how long until dark     is it getting dark
     *   how high is the sun    what month is it        is it near winter
     *   what is the wind doing is the wind getting up  is it going to rain
     *   is there frost         will it freeze tonight  which way is north
     * </pre>
     *
     * <p><b>{@code world_weather.wind_speed_kph} is the sharpest of them</b>: a column the simulation maintains,
     * carried all the way into {@link com.devosphere.draugr.simulation.BiomeClimate.Local} as a FELT wind at this
     * elevation and aspect, and no sentence in the game could reach it.
     *
     * <p>Read-only, and all of it already known: the hour and the month from {@code simulation_clock}, the felt
     * wind and temperature from the same local climate the body is charged against, and the frost from whether
     * that temperature is near freezing. Day runs 06:00 to 20:00, the same hours {@link #isDark} uses, so the
     * answer about the light can never disagree with whether fine work is possible.
     */
    private String skyReading(UUID location, java.time.Instant at) {
        java.time.ZonedDateTime now = at.atZone(java.time.ZoneOffset.UTC);
        int hour = now.getHour();
        com.devosphere.draugr.simulation.BiomeClimate.Local local = localClimate(location);

        java.util.List<String> said = new java.util.ArrayList<>();
        // The hour, as a person tells it — by where the light is, not by a number nobody here could read.
        said.add(hour < 6 ? "It is still dark, the small hours before any light"
               : hour < 9 ? "The light is new and low, the morning not long up"
               : hour < 12 ? "The sun is climbing and the morning is well on"
               : hour < 14 ? "The sun stands at its highest, as near noon as makes no difference"
               : hour < 17 ? "The sun is past its height and going down the sky"
               : hour < 20 ? "The light is going amber and long, the day nearly done"
               : "The light has gone and the dark is full in");
        // How long until dark, which is the question behind most of the others.
        if (hour >= 6 && hour < 20) {
            int left = 20 - hour;
            said.add(left <= 1 ? "You have less than an hour of working light"
                   : "You have about " + left + " hours of light left");
        } else {
            int untilLight = hour < 6 ? 6 - hour : 30 - hour;
            said.add("First light is some " + untilLight + (untilLight == 1 ? " hour off" : " hours off"));
        }
        // The month and where it stands in the year, since the season decides what the ground will give.
        said.add("It is " + now.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
               + ", and the season is " + seasonNamed(now.getMonthValue()));

        // The wind, FELT here rather than the world's figure — the thing nothing could ask.
        int wind = local.windKph();
        said.add(wind < 5 ? "The air is almost still"
               : wind < 15 ? "There is a steady breeze, enough to feel and no more"
               : wind < 30 ? "The wind is up and working at everything loose"
               : "The wind is hard enough to lean on, and it will take heat out of you faster than the cold alone");

        double t = local.temperatureC();
        if (t <= 0) said.add("It is below freezing, and there is frost on everything that holds it");
        else if (t <= 3) said.add("It is near enough freezing that there will be frost before morning");
        String kind = local.kind() == null ? "CLEAR" : local.kind();
        if (kind.contains("RAIN") || kind.contains("STORM")) said.add("Rain is falling and shows no sign of easing");
        else if (kind.contains("SNOW")) said.add("Snow is coming down");
        else if (kind.contains("OVERCAST") || kind.contains("CLOUD")) said.add("The sky is closed over and could turn to rain");
        else said.add("The sky is open, with nothing in it that threatens rain");
        // Which way is north, which the sun answers at any hour of the day it is up.
        if (hour >= 6 && hour < 20) said.add(hour < 11 ? "The sun is in the east, so north is to your left as you face it"
                                    : hour < 14 ? "The sun is due south at this hour, so north is at your back as you face it"
                                    : "The sun is in the west, so north is to your right as you face it");
        else said.add("With the sun down there is nothing to take a bearing from but the stars");
        return String.join(". ", said) + ".";
    }

    /** The season a month falls in — the same mapping WeatherSimulationService drives the weather by, so the
     *  sky cannot name one season while the world is simulating another. */
    private static String seasonNamed(int month) {
        return switch (month) { case 12, 1, 2 -> "winter"; case 3, 4, 5 -> "spring"; case 6, 7, 8 -> "summer"; default -> "autumn"; };
    }

    /** The climate as FELT on this ground — the same reading the body is charged against (#28/#159). */
    private com.devosphere.draugr.simulation.BiomeClimate.Local localClimate(UUID location) {
        java.util.Map<String,Object> env = jdbc.queryForMap(
            "SELECT wc.biome, COALESCE(wc.elevation,0) AS elevation, COALESCE(wc.moisture,500) AS moisture, " +
            "COALESCE(wc.grid_y,0) AS grid_y, COALESCE(wg.height_chunks,1) AS height_chunks, " +
            "COALESCE(ww.weather_kind,'CLEAR') AS weather_kind, COALESCE(ww.ambient_temperature_c,18.0) AS t, " +
            "COALESCE(ww.wind_speed_kph,6) AS w, " +
            "COALESCE((SELECT TRUE FROM world_chunk n WHERE n.world_id=wc.world_id AND n.grid_x=wc.grid_x " +
            "          AND n.grid_y=wc.grid_y-1 AND n.elevation > wc.elevation + 40 LIMIT 1), FALSE) AS sun_warmed " +
            "FROM world_chunk wc LEFT JOIN world_genesis wg ON wg.world_id=wc.world_id " +
            "LEFT JOIN world_weather ww ON ww.world_id=wc.world_id WHERE wc.id=?", location);
        return com.devosphere.draugr.simulation.BiomeClimate.at(
            (String) env.get("biome"), ((Number) env.get("elevation")).intValue(),
            ((Number) env.get("moisture")).intValue(), ((Number) env.get("grid_y")).intValue(),
            ((Number) env.get("height_chunks")).intValue(), (String) env.get("weather_kind"),
            ((Number) env.get("t")).doubleValue(), ((Number) env.get("w")).intValue(),
            Boolean.TRUE.equals(env.get("sun_warmed")));
    }

    private String campStocktake(UUID location) {
        java.util.List<String> sound = jdbc.query(
            // Parenthesised, not comma'd: in a joined list "drying rack, coming apart, well, weathered, and the
            // latrine" reads as five things rather than three.
            "SELECT lower(ck.display_name) || CASE WHEN cp.integrity_percent < 35 THEN ' (coming apart)' " +
            "       WHEN cp.integrity_percent < 70 THEN ' (weathered)' ELSE '' END " +
            "FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "JOIN construction_kind ck ON ck.project_kind=cp.project_kind " +
            "WHERE w.current_location_id=? AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND w.lifecycle_state='ACTIVE' " +
            "ORDER BY cp.integrity_percent, ck.display_name", (rs, row) -> rs.getString(1), location);
        java.util.List<String> started = jdbc.query(
            "SELECT lower(ck.display_name) || ' (' || cp.progress_percent || ' in the hundred)' " +
            "FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "JOIN construction_kind ck ON ck.project_kind=cp.project_kind " +
            "WHERE w.current_location_id=? AND cp.state <> 'COMPLETED' AND w.lifecycle_state='ACTIVE' " +
            "ORDER BY cp.progress_percent DESC, ck.display_name", (rs, row) -> rs.getString(1), location);

        if (sound.isEmpty() && started.isEmpty())
            return "You walk the ground over and there is nothing of yours on it — no shelter, no pit, no pen, "
                 + "nothing begun. Whatever you make of this place, none of it is made yet.";
        StringBuilder s = new StringBuilder();
        if (!sound.isEmpty()) s.append("You go round what you have raised here: ").append(joinAnd(sound)).append(". ");
        if (!started.isEmpty()) s.append("Still unfinished: ").append(joinAnd(started)).append(". ");
        return s.toString().trim();
    }

    /** The best standing structure here that the water is drawn through (#77, V329): its name, how much of a raw draw's risk it clears, and whether it fills vessels filtered. */
    private record DrawTreatment(String name, int clarifies, boolean filtered) {}
    private DrawTreatment drawTreatment(UUID location) {
        return jdbc.query(
            "SELECT lower(ck.display_name), ck.clarifies_draw, ck.draws_filtered FROM construction_project cp " +
            "JOIN world_object w ON w.id=cp.object_id JOIN construction_kind ck ON ck.project_kind=cp.project_kind " +
            "WHERE w.current_location_id=? AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND w.lifecycle_state='ACTIVE' " +
            "AND (ck.clarifies_draw>0 OR ck.draws_filtered) ORDER BY ck.clarifies_draw DESC, ck.draws_filtered DESC LIMIT 1",
            rs -> rs.next() ? new DrawTreatment(rs.getString(1), rs.getInt(2), rs.getBoolean(3)) : null, location);
    }
    /** A spring on this ground, walled and covered by a standing structure that shields it (#77, V329). */
    private boolean springShielded(UUID location) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM ecology_site es WHERE es.chunk_id=? AND " + com.devosphere.draugr.ecology.FreshWater.springs("es") + ") " +
            "AND EXISTS(SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "JOIN construction_kind ck ON ck.project_kind=cp.project_kind WHERE w.current_location_id=? AND ck.shields_spring " +
            "AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND w.lifecycle_state='ACTIVE')", Boolean.class, location, location));
    }
    /** Whether raw water here is safe to drink untreated (#71): moving water — a river bank, spring, or stream —
     *  is clean; standing water (a wetland) is not, and drinking it raw carries a gut-illness risk. */
    /**
     * What bites you while you lie still on the wrong ground (#219).
     *
     * <p>{@code insect_colony_kind} carries a mosquito swarm: WETLAND, spring through autumn, crepuscular, with a
     * declared {@code hazard_kind} of ILLNESS. It has no {@code harvest_intent}, and the only code that ever read
     * a colony's hazard applied it <em>after harvesting that colony</em> — so the one thing in the catalogue you
     * could never harvest was also the one whose hazard could never reach you. A marsh at dusk in high summer was
     * as harmless as a dry hill at noon.
     *
     * <p>A biting swarm is a hazard of the ground, not of going looking for it. It finds a Chronicle who spends a
     * long stretch lying still on its ground, in its season, at its hours. Smoke turns it: an active fire here
     * keeps the air moving and bitter, which is the oldest answer there is to biting insects. So does sleeping
     * inside something built. Bites accumulate as illness the way untreated water does — one night is a misery,
     * a season of them is how a person sickens.
     */
    private String bittenWhileStill(ActiveChronicle chronicle, Instant at, int minutes) {
        if (minutes < 45) return "";                       // a moment's pause is not a night in the marsh
        String season = switch (at.atZone(java.time.ZoneOffset.UTC).getMonthValue()) {
            case 3, 4, 5 -> "SPRING"; case 6, 7, 8 -> "SUMMER"; case 9, 10, 11 -> "AUTUMN"; default -> "WINTER"; };
        // A long rest spans hours, and the clock here reads the END of it. A swarm that is out at dusk finds a
        // Chronicle who lay down at dusk, whatever hour they wake at — so the whole span is what matters, not the
        // moment it finished.
        String cycleAtEnd = cycleOf(at), cycleAtStart = cycleOf(at.minus(java.time.Duration.ofMinutes(minutes)));
        java.util.Map<String,Object> swarm = jdbc.query(
            "SELECT ck.colony_kind, ck.hazard_min, ck.hazard_max, ck.smoke_suppresses FROM insect_colony_kind ck " +
            "JOIN world_chunk c ON c.id = ? " +
            "WHERE ck.harvest_intent IS NULL AND ck.hazard_kind IS NOT NULL AND ck.hazard_max > 0 " +
            "  AND ck.biome_affinity ILIKE '%' || c.biome || '%' " +
            "  AND (ck.season_active='ALL' OR ck.season_active ILIKE ?) " +
            "  AND (ck.activity_cycle='ALL' OR ck.activity_cycle IN (?, ?)) " +
            "ORDER BY ck.hazard_max DESC LIMIT 1",
            rs -> rs.next() ? java.util.Map.of("kind", rs.getString(1), "min", rs.getInt(2), "max", rs.getInt(3)) : null,
            chronicle.location(), "%" + season + "%", cycleAtStart, cycleAtEnd);
        if (swarm == null) return "";

        // Smoke is the oldest answer to biting insects, and a roof is the other one.
        if (fireInReach(chronicle.location()) || shelterInReach(chronicle.location()))
            return " The air here is thick with biting insects, but the smoke keeps them off you and they settle on nothing.";

        int severity = Math.max(1, (int) swarm.get("min"));
        physiology.applyWaterborneRisk(chronicle.id(), severity);
        return " You are not left alone: the air over this ground is alive with biting insects, and they work at every "
             + "patch of skin you leave out. You come away marked, itching, and no more rested for the hours.";
    }

    /** Which part of the day an hour falls in, in the terms the ecology registry uses. */
    private static String cycleOf(Instant at) {
        int hour = at.atZone(java.time.ZoneOffset.UTC).getHour();
        if (hour >= 20 || hour < 4) return "NOCTURNAL";
        if (hour < 8 || hour >= 17) return "CREPUSCULAR";
        return "DIURNAL";
    }

    /** Deep night, when there is no working light to see fine detail by (#75): before dawn or after dusk. */
    private static boolean isDark(java.time.Instant at) {
        int h = at.atZone(java.time.ZoneOffset.UTC).getHour();
        return h < 6 || h >= 20;
    }

    /**
     * Hours of dark still to come, or 0 by day (#37) — what decides whether a fire will last the night.
     *
     * <p>Derived from {@link #isDark}'s own 06:00–20:00 so the fire reading can never disagree with the sky
     * reading about when the night ends. A keeper told their fuel will see the night out, by a reckoning of
     * nightfall the rest of the game does not share, has been given a number and no use for it.
     */
    private static int darkHoursLeft(java.time.Instant at) {
        if (!isDark(at)) return 0;
        int h = at.atZone(java.time.ZoneOffset.UTC).getHour();
        return h >= 20 ? (24 - h) + 6 : 6 - h;   // before midnight: round to dawn; after: straight to it
    }

    /**
     * Too dark for close work here and now (#75/#158): nightfall, or inside the rock at any hour of the day.
     *
     * <p>A cave mouth is dark whatever the sky is doing — the perception written for it says the daylight gets a
     * few paces in and no further — so fine work in one turns on the same light a midnight camp does. That is what
     * makes a lamp, a rushlight or a hooded flame worth carrying to a place rather than only to an hour.
     *
     * <p>Deliberately a SEPARATE question from {@link #isDark}, which stays purely about the hour. The other
     * caller of that is the night predator raid, and a cave being dark inside must not make wolves take your
     * stock at noon. Same flag, two questions, is the mistake this cycle has spent its time undoing.
     */
    private boolean tooDarkForFineWork(UUID location, java.time.Instant at) {
        if (isDark(at)) return true;
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM world_chunk WHERE id=? AND biome IN ('CAVE_MOUTH','CAVE_INTERIOR'))",
            Boolean.class, location));
    }
    /** Intents that are fine, close, sight-dependent work — impossible in the dark without a light (#75). */
    private static boolean isSightWork(Intent intent, String text) {
        // Reckoning up how long you have been somewhere is not sight work (#37). MEASURE covers weighing, counting
        // and sounding a depth, which all want light -- and, since act nine, the question "how long have I been
        // here", which wants only the count a person carries in their head. Gated on the intent alone, that
        // question was refused in the dark with "it is too dark to see the fine of it", which is nonsense: you do
        // not need a candle to know it has been about a week.
        if (intent == Intent.MEASURE && reckonsElapsedTime(text)) return false;
        return switch (intent) {
            case WRITE, EDIT_DOCUMENT, READ, EXAMINE, ANALYZE, INVESTIGATE, MEASURE, SKETCH_MAP -> true;
            default -> false;
        };
    }

    /** Whether these words ask how long, rather than how many or how heavy. Mirrors the branch in
     *  {@link ExaminationService#measure} that answers it, so the gate and the answer agree about which it is. */
    private static boolean reckonsElapsedTime(String text) {
        String v = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return (v.contains("day") || v.contains("long") || v.contains("week"))
            && (v.contains("woke") || v.contains("awoke") || v.contains("waking") || v.contains("arrived")
                || v.contains("been here") || v.contains("i have been") || v.contains("ive been")
                || v.contains("since i") || v.contains("so far"));
    }
    /** The refuse level at which a camp is visibly choked and its water is no longer worth calling clean. */
    private static final int FOULED_DRAW_REFUSE = 40;

    /**
     * Water here fit to drink untreated.
     *
     * <p>This used to call a river bank or any moving-freshwater site clean, unconditionally, so a keeper could
     * foul their camp to refuse 100 with their own leavings and their livestock's muck, stand on the bank, and
     * draw water that carried no risk at all, for ever. Refuse is wired to real consequences everywhere else —
     * it draws predators, costs the body condition, docks the shelf life of stored food — and the one place it
     * could not reach was the water, which is the first thing a fouled camp ruins.
     *
     * <p>Ground the Chronicle themselves designated for waste is never a clean draw, at any refuse level (V295).
     * They said what the place was for; a latrine upstream of the pot is not made safe by the stream moving.
     *
     * <p>Neither is a punishment invented for the occasion. MAINTAIN_CAMP clears refuse and a latrine contains
     * it, so a fouled camp is always recoverable — and the water comes back with it.
     */
    /**
     * What can be told about this water by looking at it (#37) — and nothing more than that.
     *
     * <p>"is the water safe to drink" reached DRINK and was answered by drinking it: <i>"You drink from the marsh
     * water. It eases the dryness, but it is not clean, and the gut will know it."</i> The Chronicle asked WHETHER,
     * and the world took the risk on their behalf. Every part of the honest answer already existed —
     * {@link #safeWaterSource} reads whether the water moves and whether the ground above it is fouled or was
     * designated for waste, {@link #drawTreatment} reads a structure that clears a raw draw, {@link #waterNamed}
     * says what the water is called — and none of it could be asked for without swallowing a mouthful first.
     *
     * <p>It judges what is <b>visible</b>: moving or standing, the state of the camp above it, and what is to hand
     * to treat it with. It never returns a verdict on what is not visible, because a person looking at a stream
     * cannot see what is in it — so the answer ends where certainty ends, which is at boiling.
     */
    private String[] judgeWater(UUID location) {
        if (!waterInReach(location))
            return new String[]{"FAILED", "There is no water here to judge — nothing running, nothing standing, "
                + "and nothing built to hold any."};

        String named = waterNamed(location);
        boolean clean = safeWaterSource(location);
        DrawTreatment through = drawTreatment(location);
        Integer refuse = jdbc.queryForObject("SELECT COALESCE((SELECT refuse_level FROM chunk_refuse WHERE chunk_id=?),0)", Integer.class, location);
        boolean foul = refuse != null && refuse >= FOULED_DRAW_REFUSE;

        StringBuilder said = new StringBuilder("You crouch at " + named + " and look at it properly. ");
        if (clean) said.append("It runs, and running water carries its own filth away — it is as good a draw as "
            + "this ground offers. ");
        else if (foul) said.append("The camp above it is in a state, and what is on that ground goes into this "
            + "water. You would not want it as it stands. ");
        else said.append("It lies still, with the look of water that has been sitting where it is: no current to "
            + "carry anything off, and whatever the ground has given it is still in it. ");

        if (through != null) said.append("The " + through.name() + " standing here takes the worst of it out on "
            + "the way to the pot. ");
        said.append(clean
            ? "Boiled it would be beyond question; raw, it is a risk worth taking if you must."
            : "Boiled, it would be safe enough. Raw, it is a bet against your own gut, and the odds are poor.");
        return new String[]{"SUCCEEDED", said.toString()};
    }

    private boolean safeWaterSource(UUID location) {
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
        boolean water = "RIVER_BANK".equals(biome);
        if (!water) {
            Integer moving = jdbc.queryForObject("SELECT COUNT(*) FROM ecology_site WHERE chunk_id=? AND (" + com.devosphere.draugr.ecology.FreshWater.sites() + ")", Integer.class, location);
            water = moving != null && moving > 0;
        }
        if (!water) return false;

        Integer refuse = jdbc.queryForObject("SELECT COALESCE((SELECT refuse_level FROM chunk_refuse WHERE chunk_id=?),0)", Integer.class, location);
        // A walled, covered spring head (#77) is fed from below: the refuse of a fouled camp does not run into it.
        if (refuse != null && refuse >= FOULED_DRAW_REFUSE && !springShielded(location)) return false;

        return !Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM chronicle_named_location nl JOIN district_purpose dp ON dp.purpose_tag=nl.purpose_tag " +
            "WHERE nl.chunk_id=? AND dp.fouls_water)", Boolean.class, location));
    }
    /** What the water on this ground is called, read from the same sites that make it water (#37, FreshWater). */
    /**
     * Why there is nothing here to drink, said as what the Chronicle is looking at (#30).
     *
     * <p>This used to be one line for everywhere: "no stream, no spring, only dry ground that gives nothing back". On
     * a shore that is false, because the whole horizon is water and the reason it cannot be drunk is that it is the
     * sea's. In rain it is false as well, and a player standing in a downpour notices. The weather is the one read at
     * the start of the action, so the line agrees with the sky the setting clause describes.
     */
    private String noWaterToDrink(UUID location, String weather) {
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
        if ("COAST".equals(biome) || "OCEAN".equals(biome))
            return "The only water here is the sea's. One mouthful is enough to know it would leave you thirstier than before.";
        if ("RAIN".equals(weather) || "STORM".equals(weather))
            return "Rain wets your face and runs off your hands, too little of it to drink, and there is no stream or spring here.";
        if ("SNOW".equals(weather))
            return "Snow lies about, but there is no running water here, and snow in the mouth numbs more than it quenches.";
        return "You look about, but there is no water here fit to drink — no stream, no spring, only dry ground that gives nothing back.";
    }

    private String waterNamed(UUID location) {
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
        return com.devosphere.draugr.ecology.FreshWater.name(
            jdbc.queryForList(com.devosphere.draugr.ecology.FreshWater.SITE_KINDS_ON_CHUNK, String.class, location), biome);
    }
    /** A fire burning within reach here — for warming and drying (#66). */
    private boolean fireInReach(UUID location) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM fire_state fs JOIN world_object w ON w.id=fs.construction_id WHERE w.current_location_id=? AND fs.active=true)", Boolean.class, location));
    }
    /** A completed, standing shelter here — for getting out of the weather (#66). Any roofed,
     * enclosing form counts (#61): a lean-to, or the huts whose walls and roof actually keep weather off. */
    private boolean shelterInReach(UUID location) {
        // Any completed build you can be inside counts, not a hardcoded four — and the four other places that ask
        // this same question now ask it through the same constant, so none of them can drift again. This check was
        // put onto is_shelter first, which reads too wide: thirty-four kinds carry that, fourteen of them doors,
        // walls, screens, a roofing frame, a smoke hood and furniture. A bark door on open grassland is not cover.
        //
        // A cave mouth shelters without being built (#158). It is the oldest roof there is, and the first reason
        // to walk into one: rock over your head keeps the rain off whether or not you ever raised anything. The
        // chamber behind it shelters more completely still — no weather reaches it at all — so both count.
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM world_chunk c WHERE c.id=? AND c.biome IN ('CAVE_MOUTH','CAVE_INTERIOR')) " +
            "    OR EXISTS(SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "WHERE w.current_location_id=? AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND w.lifecycle_state='ACTIVE' " +
            "AND " + com.devosphere.draugr.construction.Shelters.ENCLOSING + ")",
            Boolean.class, location, location));
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
    /**
     * The bench crafts and repairs whose setting-up a tool shed shortens (#207 heritage TOOL_SHED): fabrication and
     * mending, where having tools and made stock to hand saves the hunting-out at the start. Field labour (felling,
     * gathering, raising structures) and body/fire/writing acts are unaffected — the shed speeds the workbench, not
     * the whole camp. Package-private so the regression can assert the exact set deterministically.
     */
    boolean speededByToolShed(Intent intent) {
        return switch (intent) {
            case CRAFT_GARMENT, CRAFT_FIRE_TOOL, CRAFT_BASKET, CRAFT_SPEAR, CRAFT_KNIFE, CRAFT_HAMMER, CRAFT_PICKAXE,
                 CRAFT_HATCHET, CRAFT_FIRE_KIT, CRAFT_TINDER, CRAFT_DESK, CRAFT_CHAIR, CRAFT_SHELF, CRAFT_WORKSTATION,
                 CRAFT_NET, CRAFT_BELT, PROCESS_MATERIAL, REFINE, REWORK, REPAIR_ITEM, ADVANCE_ASSEMBLY -> true;
            default -> false;
        };
    }
    /** Whether a completed, standing tool shed keeps this ground — the read behind the prep-time saving. */
    boolean hasToolShed(UUID location) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "WHERE w.current_location_id=? AND cp.project_kind='TOOL_SHED' AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND w.lifecycle_state='ACTIVE')",
            Boolean.class, location));
    }
    /** Whether a utility belt is worn — tools kept in its loops are to hand, the read behind the belt's prep cut (#57). */
    boolean wearsUtilityBelt(UUID chronicle) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM equipment_attachment e JOIN item_instance i ON i.object_id=e.item_id " +
            // The primitive tool carriers count too. A cordage tool belt and a reed tool loop are the same idea as a
            // utility belt — tools kept to hand instead of set down and hunted for — but only 'utility_belt' was read
            // here, so both were craftable, wearable and completely inert. The fix named them in a second list, and
            // the leather tool girdle (V248) then went unread the same way; what counts is now tool_carrier (V315).
            "JOIN tool_carrier tc ON tc.item_key=i.item_key " +
            "JOIN world_object w ON w.id=e.item_id WHERE e.chronicle_id=? AND w.lifecycle_state='ACTIVE')",
            Boolean.class, chronicle));
    }
    /**
     * The documentation work a standing desk shortens (#207 heritage KNOWLEDGE_STATION): putting marks to a page —
     * writing a record, revising one, sketching a map. Package-private so the regression can assert the set.
     */
    boolean easedByDesk(Intent intent) {
        return switch (intent) { case WRITE, EDIT_DOCUMENT, SKETCH_MAP -> true; default -> false; };
    }
    /** Whether a steady work surface (a built wooden desk) stands on this ground — the read behind the writing ease. */
    boolean hasWritingSurface(UUID location) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "WHERE w.current_location_id=? AND i.item_key='wooden_desk' AND w.lifecycle_state='ACTIVE')",
            Boolean.class, location));
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
