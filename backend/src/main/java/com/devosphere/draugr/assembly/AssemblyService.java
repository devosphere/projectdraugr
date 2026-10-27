package com.devosphere.draugr.assembly;

import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.quality.QualityGrade;
import com.devosphere.draugr.routing.ActivityClassifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runs staged assemblies (V58): multi-stage structures and crafts advanced one stage
 * at a time. Unlike a material process, which turns inputs into an output in a single
 * act, an assembly holds an ordered list of stages -- each consuming inputs, or, for a
 * cure stage, waiting out a span of world time before it can complete.
 *
 * <p>State of a build lives in {@code assembly_instance} and {@code
 * assembly_stage_completion}, not in the definition, so the same blueprint serves
 * every chronicle. A cure measures elapsed simulated time against the prerequisite
 * stage's completion, which is the same tick the body and food already run on -- the
 * player passes it by doing other things and coming back.
 */
@Service
public class AssemblyService {

    private final JdbcTemplate jdbc;
    private final PhysicalItemService items;
    private final com.devosphere.draugr.simulation.LocalWeatherService weather;

    public AssemblyService(JdbcTemplate jdbc, PhysicalItemService items,
                           com.devosphere.draugr.simulation.LocalWeatherService weather) {
        this.jdbc = jdbc; this.items = items; this.weather = weather;
    }

    /**
     * The verified assembly whose keyword best matches the text, or null when the text
     * names no assembly. Longest matching keyword wins, so "build a hunting bow" beats a
     * bare "build a bow" elsewhere and the same text always resolves the same way.
     */
    @Transactional(readOnly = true)
    public String match(String text) {
        String[] hit = matchWithKeyword(text);
        return hit == null ? null : hit[0];
    }

    /**
     * The assembly the text names and the keyword that named it — longest wins, as it always has.
     *
     * <p>And every assembly TIED at that length, because "longest wins" says nothing about what to do when two
     * win. The old form took the strictly-longer keyword only, so a tie kept whichever row the database handed
     * back first, from a query with no ORDER BY — an arbitrary pick presented to the player as a decision. No two
     * verified assemblies shared a keyword when this was written, so nothing was being silently mis-built: the
     * flaw was latent, and giving four huts the word "hut" is what would have woken it. Same defect the material
     * processes had before #593, and it gets the same answer: ask.
     */
    private String[] matchWithKeyword(String text) {
        List<String> tied = new ArrayList<>();
        String v = ActivityClassifier.normalise(asBuildingWords(text));
        String bestKeyword = null; int bestLen = -1;
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT assembly_key, keywords FROM assembly_definition WHERE review_state='VERIFIED' ORDER BY assembly_key")) {
            String key = (String) row.get("assembly_key");
            for (String kw : ((String) row.get("keywords")).split(",")) {
                String k = kw.trim();
                if (k.isEmpty() || !ActivityClassifier.containsTerm(v, k)) continue;
                if (k.length() > bestLen) { bestLen = k.length(); bestKeyword = k; tied.clear(); tied.add(key); }
                else if (k.length() == bestLen && !tied.contains(key)) tied.add(key);
            }
        }
        return tied.isEmpty() ? null : new String[]{tied.get(0), bestKeyword, String.join(",", tied)};
    }

    /**
     * The verbs for beginning a thing, said as the catalogue says them (#37/#99).
     *
     * <p>Every assembly's keywords are written with "build", "make", "raise" or "put up", so {@code build a
     * shelter} reaches the lean-to and {@code start a shelter} and {@code begin a shelter} reached nothing at
     * all — while {@code start a lean-to} worked, because the lean-to has a Java branch of its own and the
     * other ninety-nine builds do not. Measured live.
     *
     * <p>One rewrite here rather than a hundred keywords in a migration: a beginning verb in front of a thing
     * is a building verb. The fire keeps its own words — {@code start a fire} is LIGHT_FIRE and the classifier
     * answers it long before anything reaches this matcher.
     */
    private static String asBuildingWords(String text) {
        if (text == null) return null;
        String v = text;
        for (String begin : new String[]{"start building ", "begin building ", "start to build ",
                                         "get started on ", "set about building ", "set about ",
                                         "start ", "begin "}) {
            int at = v.toLowerCase(java.util.Locale.ROOT).indexOf(begin);
            if (at >= 0) return v.substring(0, at) + "build " + v.substring(at + begin.length());
        }
        return v;
    }

    /** Every assembly the words fit equally, in catalogue order. One entry means the words named one thing. */
    private List<String> tiedIn(String[] hit) {
        return hit == null || hit.length < 3 ? List.of() : new ArrayList<>(List.of(hit[2].split(",")));
    }

    /**
     * Narrow a tie the way the world already has. One already under way settles it — carrying on with the
     * half-built hut in front of you is not ambiguous — and otherwise the ones whose first stage could actually
     * be begun here, because a choice between four huts you have no materials for is not a choice.
     *
     * <p>But only what is IN HAND may narrow it (V427). A first stage that asks for nothing — the earth oven's
     * is digging its pit — is "within reach" whatever is carried, so it used to survive every narrowing alone:
     * with empty hands, {@code dig a pit} would have quietly begun an earth oven out of seven pits, a pick
     * presented as a decision. Such an assembly stays among the choices, and never settles one by itself.
     */
    private List<String> narrow(UUID chronicle, UUID location, List<String> tied) {
        List<String> started = new ArrayList<>();
        for (String k : tied) if (underWay(chronicle, k)) started.add(k);
        if (!started.isEmpty()) return started;
        List<String> canBegin = new ArrayList<>();
        for (String k : tied) if (firstStageIsWithinReach(chronicle, k)) canBegin.add(k);
        return somethingInHandPointsAt(chronicle, tied) ? canBegin : tied;
    }

    /** Whether the Chronicle carries the makings of at least one of these whose first stage asks for anything. */
    private boolean somethingInHandPointsAt(UUID chronicle, List<String> keys) {
        for (String k : keys) if (firstStageAsksForSomething(k) && firstStageIsWithinReach(chronicle, k)) return true;
        return false;
    }

    private boolean firstStageAsksForSomething(String assemblyKey) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM assembly_stage_requirement r WHERE r.stage_key = " +
            "(SELECT stage_key FROM assembly_stage WHERE assembly_key=? ORDER BY stage_order LIMIT 1))",
            Boolean.class, assemblyKey));
    }

    /**
     * The question put to a Chronicle whose words named more than one thing to build (#37), mirroring the one
     * the material processes ask. Changes nothing while it asks.
     */
    private String[] whichOfThese(List<String> offer, boolean noneInReach) {
        List<String> names = new ArrayList<>();
        for (String k : offer)
            names.add(jdbc.queryForObject("SELECT lower(display_name) FROM assembly_definition WHERE assembly_key=?", String.class, k));
        String choices = names.size() == 2 ? names.get(0) + ", or " + names.get(1)
            : String.join(", ", names.subList(0, names.size() - 1)) + ", or " + names.get(names.size() - 1);
        return new String[]{"FAILED", noneInReach
            ? "Those words fit more than one thing you could build — " + choices + " — and nothing you carry settles which. Say which you mean."
            : "Those words fit more than one thing you could build here — " + choices + ". Say which you mean, and your hands will know where to begin."};
    }

    /**
     * Why a shaft here would find nothing, or null when it would find water.
     *
     * <p>Two things decide it, and both are already on the chunk: how damp the ground is, and how high it stands
     * above the land that drains into it. High ground drains and dry ground has nothing to give, so a well is
     * possible in the lowlands and the woods, occasional in the highlands, and never on the mountain — which is
     * the geography making the decision rather than a rule invented for it. Across the canonical world: all 141
     * temperate-forest chunks, 34 of 85 grassland, 12 of 100 highland, none of the mountain or cave ground.
     */
    private String groundTooDryToSink(UUID location) {
        Map<String, Object> g = jdbc.queryForMap(
            "SELECT COALESCE(moisture, 500) AS moisture, COALESCE(elevation, 0) AS elevation, biome " +
            "FROM world_chunk WHERE id=?", location);
        int moisture = ((Number) g.get("moisture")).intValue();
        int elevation = ((Number) g.get("elevation")).intValue();
        boolean dry = moisture < DAMP_ENOUGH_TO_SINK_A_WELL;
        boolean high = elevation > LOW_ENOUGH_TO_SINK_A_WELL;
        if (!dry && !high) return null;
        if (dry && high) return "You break the ground and it comes up dry and loose, and the land falls away on "
            + "every side. Water does not stand this high, and there is none in this ground to find. You fill the "
            + "hole back in.";
        if (high) return "The ground is damp enough, but it stands too high: whatever water it holds runs off to "
            + "the low country instead of gathering under your feet. A shaft here would follow it down and never "
            + "catch it.";
        return "A spade's depth down it is still dry gravel, and drier below that. There is no water under this "
            + "ground to reach.";
    }

    /** Damp enough that a hand-dug shaft reaches the water table. Moisture runs 0..1000, averaging 500. */
    private static final int DAMP_ENOUGH_TO_SINK_A_WELL = 450;
    /** And low enough that the water gathers rather than draining away. */
    private static final int LOW_ENOUGH_TO_SINK_A_WELL = 700;

    /** Whether the first stage of this assembly could be begun — its inputs, not its tools. */
    private boolean firstStageIsWithinReach(UUID chronicle, String assemblyKey) {
        String firstStage = jdbc.query(
            "SELECT stage_key FROM assembly_stage WHERE assembly_key=? ORDER BY stage_order LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : null, assemblyKey);
        if (firstStage == null) return false;
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT item_key, quantity FROM assembly_stage_requirement WHERE stage_key=?", firstStage))
            if (!items.hasAtLeast(chronicle, (String) r.get("item_key"), ((Number) r.get("quantity")).intValue())) return false;
        return true;
    }

    /**
     * Advance the named assembly by one stage. Returns [outcome, narration], or null
     * when the text names no assembly at all (the caller then tries a material process).
     *
     * <p>Resolves the next stage whose prerequisite is complete; a cure stage completes
     * only once enough world time has passed, a work stage only once its tool, fire and
     * inputs are present. The final stage produces the craft item or raises the
     * construction.
     */
    @Transactional
    public String[] advance(UUID chronicle, UUID location, String text, Instant at) {
        String[] hit = matchWithKeyword(text);
        if (hit == null) return null;
        // More than one thing answers to these words equally well (#37). Narrow it by what is already under way
        // and what could be begun here; ask only if a real choice is left, and build nothing while asking.
        List<String> tied = tiedIn(hit);
        if (tied.size() > 1) {
            List<String> offer = narrow(chronicle, location, tied);
            if (offer.size() > 1) return whichOfThese(offer, !somethingInHandPointsAt(chronicle, tied));
            tied = offer;
        }
        String key = tied.isEmpty() ? hit[0] : tied.get(0);
        String matchedKeyword = hit[1];

        Map<String, Object> def = jdbc.queryForMap(
            "SELECT subject_kind, produces_item_key, construction_kind, display_name, narration, site_biomes " +
            "FROM assembly_definition WHERE assembly_key=?", key);
        String subjectKind = (String) def.get("subject_kind");

        // Naming a thing that already stands is USING it, not building another (#77).
        //
        // An assembly's keywords include its bare name — "drying rack", "smoke rack", "causeway" — so that "work on
        // the drying rack" advances one being built. But this matcher runs before the material processes, and once
        // a structure is finished no stage is left in progress, so the next mention of it started a NEW one: "dry
        // the fish on the drying rack", with a rack standing right there, began raising a second rack and the fish
        // never dried. Every station named while it was being used had the same trap, and so did every way named
        // while it was being walked.
        //
        // So a structure that already stands on this ground, named only by its bare name, is a reference and not a
        // request: this returns null and the caller tries the material process the text names. What counts as a
        // request is the assembly's OWN data — its keywords include the verb forms that build it ("make a drying
        // rack", "raise a smoke rack"), and the longest keyword wins, so a text that says one of those matched one
        // of those. A word list on the text would have misfired both ways: "make" and "lay" are how half the
        // processes are phrased ("make cordage at the post", "lay out the hide"). Asking for "another", a
        // "second" or a "new" one still builds, and one already under way HERE is always advanced — "work on the
        // drying rack" must reach a half-built second rack.
        //
        // It runs BEFORE the place conditions, because a reference builds nothing and so has no ground to be
        // refused on. After them, the footprint rule answered "feed the hens in the poultry run", with the run
        // standing right there, by saying a poultry run already takes this ground — true, and the reason the
        // hens were in it. And it asks about this ground only: a second rack half-built a day's walk off does
        // not make the one in front of you anything other than a rack.
        if ("STRUCTURE".equals(subjectKind) && !isBuildPhrase(matchedKeyword) && !asksForAnother(text)
                && !underWayHere(chronicle, key, location) && standsHere(location, (String) def.get("construction_kind")))
            return null;

        // A PART-BUILT STRUCTURE STANDS WHERE IT WAS BEGUN (#77).
        //
        // One of a kind may be under way per Chronicle (assembly_one_active_per_chronicle), and the instance was
        // looked up by Chronicle and kind alone — so posts set on one piece of ground took their bars on another,
        // and the finished rack went up wherever the last stage happened to be worked, built of work done
        // somewhere else entirely. The place conditions were already asked per location (underWayHere); the
        // work itself was not. A thing raised on the ground cannot follow its maker, so it is refused here and
        // the refusal says which way it lies. A craft carries no location and travels in the hand, as it should.
        if ("STRUCTURE".equals(subjectKind)) {
            String elsewhere = standsElsewhere(chronicle, key, location);
            if (elsewhere != null) return new String[]{"FAILED", elsewhere};
        }

        // SOME GROUND WILL NOT TAKE WHAT IS ASKED OF IT (#77), and now it is data rather than a switch.
        //
        // The comment on the well below used to end "the second such assembly is the one that should promote
        // it to data". There are eight — a water draw platform, a washing place, a settling basin, a fishing
        // landing, a weir, a duck house, riverbank steps and a footbridge — every one of which says in its own
        // narration that it stands at the water. So assembly_definition.site_biomes carries the eligible
        // ground and this reads it.
        //
        // Checked only when nothing is under way, following the well's own precedent: a structure cannot move
        // once begun, so the first stage is where the ground has to be right, and asking again on every stage
        // would refuse work on a thing already standing in the correct place.
        // FOOTPRINT (#77): one thing may take a whole chunk of ground, and then nothing else may ring it.
        //
        // BUILD_PEN already refused a second pen on one chunk — "there is no room to ring another around this
        // ground" — and was the only rule of its kind, hard-coded against ANIMAL_PEN. V432 promotes it to
        // construction_kind.takes_the_whole_ground, and ConstructionService.buildPen now reads the same flag,
        // so there is one rule rather than two.
        //
        // Deliberately NOT "nothing else may stand here": a pen ringing a byre is ordinary husbandry and a
        // shelter inside a fold is the point of a fold. Two rings around one piece of ground is the incoherent
        // thing, and that is all this refuses.
        if ("STRUCTURE".equals(subjectKind) && location != null && !underWayHere(chronicle, key, location)
                && groundIsTakenWhole(location, (String) def.get("construction_kind"))) {
            String standing = jdbc.queryForObject(
                "SELECT lower(ck.display_name) FROM construction_project cp JOIN world_object w ON w.id=cp.object_id "
              + "JOIN construction_kind ck ON ck.project_kind=cp.project_kind "
              + "WHERE w.current_location_id=? AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE' "
              + "AND ck.takes_the_whole_ground ORDER BY w.created_at LIMIT 1", String.class, location);
            return new String[]{"FAILED", "A " + standing + " already takes this ground; there is no room to "
                + "ring another around it. Somewhere else, or take that one apart first."};
        }

        String siteBiomes = (String) def.get("site_biomes");
        if (siteBiomes != null && !siteBiomes.isBlank() && location != null && !underWayHere(chronicle, key, location)) {
            String here = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
            java.util.List<String> wanted = java.util.Arrays.stream(siteBiomes.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
            if (here != null && !wanted.contains(here)) {
                // Name the ground it wants and the ground it is standing on, spelled from the catalogue's own
                // words rather than a phrase table, so a biome added later needs no second list to be legible.
                java.util.List<String> spoken = wanted.stream()
                    .map(b -> b.toLowerCase(java.util.Locale.ROOT).replace('_', ' ')).toList();
                String options = spoken.size() == 1 ? spoken.get(0)
                    : String.join(", ", spoken.subList(0, spoken.size() - 1)) + " or " + spoken.get(spoken.size() - 1);
                return new String[]{"FAILED", "This wants water at hand — " + options + " — and this is "
                    + here.toLowerCase(java.util.Locale.ROOT).replace('_', ' ')
                    + ". It would stand here and serve nothing."};
            }
        }

        // Some ground will not take what is asked of it (#37/#77). A well is the first assembly with a condition
        // on the PLACE rather than on what is carried, so this is a switch on the construction_kind rather than a
        // new column: the second such assembly is the one that should promote it to data.
        //
        // A hand-dug shaft reaches the water table or it reaches dry gravel, and which of those it is depends on
        // the ground. Refused at the first stage rather than allowed to finish dry, because a well that yields
        // nothing is the catalogue token this project exists to avoid — and because a refusal that names the
        // reason is how a Chronicle learns where to dig.
        if ("WELL".equals(def.get("construction_kind")) && !underWayHere(chronicle, key, location)) {
            String dry = groundTooDryToSink(location);
            if (dry != null) return new String[]{"FAILED", dry};
        }

        UUID instanceId = jdbc.query(
            "SELECT id FROM assembly_instance WHERE chronicle_id=? AND assembly_key=? AND state='IN_PROGRESS' LIMIT 1 FOR UPDATE",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, chronicle, key);

        Set<String> done = new HashSet<>();
        Map<String, Timestamp> doneAt = new HashMap<>();
        Map<String, QualityGrade> doneGrade = new HashMap<>();
        if (instanceId != null)
            for (Map<String, Object> c : jdbc.queryForList(
                    "SELECT stage_key, completed_at, quality_grade FROM assembly_stage_completion WHERE instance_id=?", instanceId)) {
                done.add((String) c.get("stage_key"));
                doneAt.put((String) c.get("stage_key"), (Timestamp) c.get("completed_at"));
                doneGrade.put((String) c.get("stage_key"), QualityGrade.of((String) c.get("quality_grade")));
            }

        List<Map<String, Object>> stages = jdbc.queryForList(
            "SELECT stage_key, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, weather_limit, narration " +
            "FROM assembly_stage WHERE assembly_key=? ORDER BY stage_order", key);

        Map<String, Object> next = null;
        for (Map<String, Object> s : stages) {
            if (done.contains((String) s.get("stage_key"))) continue;
            String prereq = (String) s.get("prerequisite_stage_key");
            if (prereq == null || done.contains(prereq)) { next = s; break; }
        }
        if (next == null) return new String[]{"FAILED", "It is already finished."};

        String stageKey = (String) next.get("stage_key");
        int cure = ((Number) next.get("cure_minutes")).intValue();
        boolean isLast = stages.get(stages.size() - 1).get("stage_key").equals(stageKey);
        String prereqKey = (String) next.get("prerequisite_stage_key");

        // A flawed prior step gates everything after it (M3b): the build cannot go on
        // until the defective stage is reworked, so a defect can never be carried into a
        // finished piece.
        if (prereqKey != null && doneGrade.get(prereqKey) == QualityGrade.DEFECTIVE)
            return new String[]{"FAILED", "The previous work is flawed — it has to be reworked before you can go on."};

        if (cure > 0) {
            Timestamp since = prereqKey != null ? doneAt.get(prereqKey) : null;
            long elapsedMin = since == null ? Long.MAX_VALUE : (at.toEpochMilli() - since.getTime()) / 60000L;
            if (elapsedMin < cure) {
                long remainingHours = Math.max(1, (cure - elapsedMin + 59) / 60);
                return new String[]{"FAILED",
                    "It is still curing — it needs about " + remainingHours + " more hour" +
                    (remainingHours == 1 ? "" : "s") + " before it can be worked."};
            }
            // A cure is only waiting; it carries the grade of the work it set.
            recordStage(instanceId, stageKey, at, prereqKey != null ? doneGrade.getOrDefault(prereqKey, QualityGrade.SOUND) : QualityGrade.SOUND);
        } else {
            // Name the tool, not "a tool" (#37). The stage knows which class it wants, and a Chronicle told
            // only that something is missing has to guess which of their tools it meant — or which to go and make.
            String tool = (String) next.get("tool_class");
            if (tool != null && !hasTool(chronicle, tool))
                return new String[]{"FAILED", "The work wants " + toolPhrase(tool) + ", and you are not carrying one."};
            if (Boolean.TRUE.equals(next.get("requires_fire")) && !fireHere(location))
                return new String[]{"FAILED", "It needs heat, and there is no fire burning here."};

            // WEATHER LIMITS DURING CONSTRUCTION (#77). Until now a stage read its tool, its fire and its
            // materials, and NOTHING consulted the sky — so a roof could be thatched in a storm and daub
            // applied in driving rain, which is work that would be ruined before it set. #77 asks for weather
            // limits and this is them: the stage declares what it cannot be done in, and this reads it.
            //
            // Declared AND read, in the same change. A property the data states and the code ignores is the
            // defect this project keeps finding — V429 was exactly that, a stage declaring materials on a
            // branch that never consumes them — so the column and its reader ship together, with a standing
            // guard that every stage carrying a limit is one this code can actually refuse.
            //
            // Location, not the global front: a sheltered hollow and an open ridge do not share a sky (#778).
            String limit = (String) next.get("weather_limit");
            if (limit != null && location != null) {
                if ("DRY".equals(limit) && weather.wetHere(location))
                    return new String[]{"FAILED", "This is work that has to go on dry, and it is coming down too "
                        + "hard — laid wet now it would be spoiled before it set. It will have to wait for the weather."};
                if ("CALM".equals(limit) && weather.galeHere(location))
                    return new String[]{"FAILED", "The wind is taking everything you lift. Long timbers overhead "
                        + "in this is how people are hurt, and it will have to wait for the gale to pass."};
            }

            List<Map<String, Object>> reqs = jdbc.queryForList(
                "SELECT item_key, quantity FROM assembly_stage_requirement WHERE stage_key=?", stageKey);
            // Say what, and how much (#37). "You have not got enough to hand for that step" told a Chronicle
            // standing in the rain that something was missing and left them to work out what — which is the
            // same defect as the cordage refusal that blamed misplacement for a shortfall.
            for (Map<String, Object> r : reqs) {
                String itemKey = (String) r.get("item_key");
                int wants = ((Number) r.get("quantity")).intValue();
                if (!items.hasAtLeast(chronicle, itemKey, wants)) {
                    String what = itemKey.replace('_', ' ');
                    int have = items.reachCountAt(chronicle, location, itemKey);
                    return new String[]{"FAILED", have > 0
                        ? "That step wants " + wants + " " + what + ", and you have " + have + " to hand."
                        : "That step wants " + wants + " " + what + ", and you have none to hand."};
                }
            }

            // A defective material is gated, not silently built in: it must be replaced
            // or reworked before it can be consumed.
            List<String> reqKeys = new java.util.ArrayList<>();
            for (Map<String, Object> r : reqs) reqKeys.add((String) r.get("item_key"));
            QualityGrade inputGrade = items.worstGradeAmong(chronicle, reqKeys);
            if (inputGrade == QualityGrade.DEFECTIVE)
                return new String[]{"FAILED", "One of the materials for this step is defective — replace or rework it before you use it."};

            if (isLast && "CRAFT".equals(subjectKind) && !items.hasCarryRoomFor(chronicle, (String) def.get("produces_item_key")))
                return new String[]{"FAILED", "You could finish it, but you could not carry what it would make."};

            if (instanceId == null) {
                instanceId = UUID.randomUUID();
                jdbc.update("INSERT INTO assembly_instance (id, assembly_key, chronicle_id, location_id) VALUES (?,?,?,?)",
                    instanceId, key, chronicle, "STRUCTURE".equals(subjectKind) ? location : null);
            }
            for (Map<String, Object> r : reqs) {
                int q = ((Number) r.get("quantity")).intValue();
                for (int i = 0; i < q; i++) items.consumeOne(chronicle, (String) r.get("item_key"), at);
            }
            // The stage is only as good as its materials and the care of the attempt.
            recordStage(instanceId, stageKey, at, QualityGrade.worst(inputGrade, QualityGrade.attempt(text)));
        }

        if (isLast) {
            Timestamp ts = Timestamp.from(at);
            jdbc.update("UPDATE assembly_instance SET state='COMPLETE', updated_at=? WHERE id=?", ts, instanceId);
            if ("CRAFT".equals(subjectKind)) {
                // The finished piece is only as good as its worst stage (M3b).
                QualityGrade outGrade = worstStageGrade(instanceId);
                String outKey = (String) def.get("produces_item_key");
                String outName = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, outKey);
                UUID made = items.createCarriedItem(chronicle, outKey, outName, at, "ASSEMBLED", outGrade);
                jdbc.update("UPDATE assembly_instance SET object_id=? WHERE id=?", made, instanceId);
            } else {
                UUID obj = UUID.randomUUID();
                jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION',?,?)",
                    obj, def.get("display_name"), location);
                jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,?,'COMPLETED',100,?)",
                    obj, def.get("construction_kind"), ts);
                jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTED',jsonb_build_object('projectKind',?::text,'viaAssembly',?::text))",
                    obj, ts, def.get("construction_kind"), key);
                jdbc.update("UPDATE assembly_instance SET object_id=? WHERE id=?", obj, instanceId);
            }
            // THE FINISHED PIECE IS A THING LEARNED (#37/#99), carried back as a third element the same way a
            // process carries its key. Only on the LAST stage: a keeper has not learned to make a hut by
            // digging its post holes.
            return new String[]{"SUCCEEDED", (String) def.get("narration"), "ASSEMBLY:" + key};

        }
        jdbc.update("UPDATE assembly_instance SET updated_at=? WHERE id=?", Timestamp.from(at), instanceId);
        return new String[]{"SUCCEEDED", (String) next.get("narration")};
    }

    private void recordStage(UUID instanceId, String stageKey, Instant at, QualityGrade grade) {
        jdbc.update("INSERT INTO assembly_stage_completion (instance_id, stage_key, completed_at, quality_grade) VALUES (?,?,?,?)",
            instanceId, stageKey, Timestamp.from(at), grade.name());
    }

    /** The worst grade across an instance's completed stages — what its finished piece inherits. */
    private QualityGrade worstStageGrade(UUID instanceId) {
        QualityGrade worst = null;
        for (String g : jdbc.query("SELECT quality_grade FROM assembly_stage_completion WHERE instance_id=?",
                (rs, n) -> rs.getString(1), instanceId)) {
            QualityGrade q = QualityGrade.of(g);
            worst = worst == null ? q : QualityGrade.worst(worst, q);
        }
        return worst == null ? QualityGrade.SOUND : worst;
    }

    /**
     * Report the grade of an in-progress assembly stage by stage, or of the materials
     * carried — witness-stance, no advice (M3b). Returns [outcome, narration].
     */
    @Transactional(readOnly = true)
    public String[] inspect(UUID chronicle, UUID location, String text) {
        // A part-built structure is looked over where it stands (#77): from a day's walk off, "you look over the
        // poultry run" reported the grade of work nobody could see.
        String key = match(text);
        String elsewhere = key == null ? null : standsElsewhere(chronicle, key, location);
        if (elsewhere != null) return new String[]{"FAILED", elsewhere};
        UUID instanceId = instanceWithinReach(chronicle, key, location, false);

        if (instanceId != null) {
            String name = jdbc.queryForObject(
                "SELECT d.display_name FROM assembly_instance ai JOIN assembly_definition d ON d.assembly_key=ai.assembly_key WHERE ai.id=?",
                String.class, instanceId);
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT s.name, c.quality_grade FROM assembly_stage_completion c JOIN assembly_stage s ON s.stage_key=c.stage_key " +
                "WHERE c.instance_id=? ORDER BY s.stage_order", instanceId);
            if (rows.isEmpty())
                return new String[]{"SUCCEEDED", "You look over the " + name + ", but no work has been set into it yet."};
            List<String> flawed = new java.util.ArrayList<>();
            for (Map<String, Object> r : rows)
                if (QualityGrade.of((String) r.get("quality_grade")) == QualityGrade.DEFECTIVE)
                    flawed.add((String) r.get("name"));
            if (!flawed.isEmpty())
                return new String[]{"SUCCEEDED", "You look over the " + name + ". The " + String.join(" and the ", flawed).toLowerCase(java.util.Locale.ROOT) + " has come out defective; it will spoil the finished piece if it stands."};
            QualityGrade w = worstStageGrade(instanceId);
            return new String[]{"SUCCEEDED", "You look over the " + name + ". The work so far is " + w.label() + ", with nothing flawed in it."};
        }

        // No assembly in hand: report the materials carried.
        Integer defective = jdbc.queryForObject(
            "WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' " +
            "UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') " +
            "SELECT COUNT(*) FROM reachable r JOIN item_instance i ON i.object_id=r.id WHERE i.quality_grade='DEFECTIVE'", Integer.class, chronicle);
        Integer poor = jdbc.queryForObject(
            "WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' " +
            "UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') " +
            "SELECT COUNT(*) FROM reachable r JOIN item_instance i ON i.object_id=r.id WHERE i.quality_grade='POOR'", Integer.class, chronicle);
        int d = defective == null ? 0 : defective, p = poor == null ? 0 : poor;
        if (d == 0 && p == 0)
            return new String[]{"SUCCEEDED", "You turn your materials over in your hands. What you carry is sound."};
        StringBuilder sb = new StringBuilder("You turn your materials over in your hands. ");
        if (d > 0) sb.append(d).append(d == 1 ? " piece is defective" : " pieces are defective");
        if (d > 0 && p > 0) sb.append(", and ");
        if (p > 0) sb.append(p).append(p == 1 ? " is only poor" : " are only poor");
        sb.append(".");
        return new String[]{"SUCCEEDED", sb.toString()};
    }

    /**
     * What you have begun and not finished, and what the next step of it wants (#37).
     *
     * <p>{@code assembly_instance} keeps every piece a Chronicle has started, {@code assembly_stage_completion}
     * keeps which stages are set into it, and {@code assembly_stage} keeps what the next one wants — its tool,
     * its fire, the span it must cure, and the weather it cannot be worked in. {@code construction_project}
     * keeps a raising at its percentage. None of it could be asked. Swept on a booted world:
     *
     * <pre>
     *   what am I working on / what is unfinished / what jobs are half done -&gt; nothing at all
     *   is the shelter finished / what is left to do on the shelter         -&gt; nothing at all
     *   how far along is the lean-to  -&gt; TAKE_STOCK_OF_CAMP, a list of what is already raised
     *   how far along is the hide     -&gt; MEASURE, "you pace it out and reckon by eye"
     *   how is my shelter holding up  -&gt; TAKE_STOCK_OF_GEAR, the clothes on your back
     * </pre>
     *
     * <p>The last three are the worse half: a question about a work in progress answered in the same even voice
     * with something else entirely.
     *
     * <p><b>Every clause here is a clause {@link #advance} refuses on</b> — the flawed prior stage, the cure not
     * yet run, the named tool, the fire, the weather limit, the stage's own bill of materials — read through the
     * same helpers, so what a keeper is told is waiting is what would actually stop them. {@link #inspect}
     * reports the QUALITY of the work already set in; this reports what remains.
     *
     * <p>Returns null when the text names no assembly and nothing is under way, so the caller can answer about
     * a material instead of inventing an answer about nothing.
     */
    @Transactional(readOnly = true)
    public String[] whatRemains(UUID chronicle, UUID location, String text, Instant at,
                                boolean mayTakeAnyWorkInHand) {
        String[] hit = matchWithKeyword(text);
        String key = hit == null ? null : hit[0];
        UUID instanceId = key != null ? jdbc.query(
            "SELECT id FROM assembly_instance WHERE chronicle_id=? AND assembly_key=? AND state='IN_PROGRESS' LIMIT 1",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, chronicle, key) : null;
        // ...AND THE LAST THING WORKED, but only when the sentence named nothing of its own. "what am I working
        // on" means whatever is nearest finishing; "is the hide ready" names a material, and answering it with
        // the half-built oven two valleys back would be the same even-voiced wrong answer this reading exists to
        // end. The caller settles which kind of sentence it is, because the caller holds the catalogue.
        if (instanceId == null && key == null && mayTakeAnyWorkInHand) instanceId = jdbc.query(
            "SELECT id FROM assembly_instance WHERE chronicle_id=? AND state='IN_PROGRESS' ORDER BY updated_at DESC LIMIT 1",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, chronicle);

        String raising = mayTakeAnyWorkInHand || key != null ? raisingsHere(location) : "";
        if (instanceId != null) {
            String said = stageByStage(chronicle, location, instanceId, at);
            return new String[]{"SUCCEEDED", raising.isEmpty() ? said : said + " " + raising};
        }
        if (key != null) {
            String name = jdbc.queryForObject(
                "SELECT display_name FROM assembly_definition WHERE assembly_key=?", String.class, key);
            String said = "You have begun no " + name.toLowerCase(java.util.Locale.ROOT)
                + ". Nothing of it stands, so nothing of it is waiting on you.";
            return new String[]{"SUCCEEDED", raising.isEmpty() ? said : said + " " + raising};
        }
        if (!raising.isEmpty()) return new String[]{"SUCCEEDED", raising};
        return null;
    }

    /** A raising here that is not finished, read from its own record rather than from a list of what stands. */
    private String raisingsHere(UUID location) {
        if (location == null) return "";
        List<String> said = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT ck.display_name AS name, cp.progress_percent AS pct, cp.state " +
                "FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
                "  JOIN construction_kind ck ON ck.project_kind=cp.project_kind " +
                "WHERE w.current_location_id=? AND w.lifecycle_state='ACTIVE' " +
                "  AND cp.state IN ('PLANNED','IN_PROGRESS') ORDER BY cp.progress_percent DESC, ck.display_name",
                location)) {
            String name = ((String) row.get("name")).toLowerCase(java.util.Locale.ROOT);
            int pct = ((Number) row.get("pct")).intValue();
            said.add("PLANNED".equals(row.get("state"))
                ? "the " + name + " is planned and not begun"
                : "the " + name + " stands at " + pct + " in the hundred");
        }
        if (said.isEmpty()) return "";
        return "On this ground: " + String.join("; ", said) + ".";
    }

    /** How far a begun piece has come, and what the next stage wants of you, in advance's own terms. */
    private String stageByStage(UUID chronicle, UUID location, UUID instanceId, Instant at) {
        Map<String, Object> head = jdbc.queryForMap(
            "SELECT ai.assembly_key, d.display_name FROM assembly_instance ai " +
            "JOIN assembly_definition d ON d.assembly_key=ai.assembly_key WHERE ai.id=?", instanceId);
        String key = (String) head.get("assembly_key");
        String name = ((String) head.get("display_name")).toLowerCase(java.util.Locale.ROOT);

        List<String> done = new ArrayList<>();
        Map<String, Timestamp> doneAt = new java.util.HashMap<>();
        Map<String, QualityGrade> doneGrade = new java.util.HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT c.stage_key, c.completed_at, c.quality_grade FROM assembly_stage_completion c " +
                "JOIN assembly_stage s ON s.stage_key=c.stage_key WHERE c.instance_id=? ORDER BY s.stage_order",
                instanceId)) {
            String stage = (String) row.get("stage_key");
            done.add(stage);
            doneAt.put(stage, (Timestamp) row.get("completed_at"));
            doneGrade.put(stage, QualityGrade.of((String) row.get("quality_grade")));
        }
        List<Map<String, Object>> stages = jdbc.queryForList(
            "SELECT stage_key, name, prerequisite_stage_key, cure_minutes, COALESCE(tool_class,'') AS tool_class, " +
            "       requires_fire, weather_limit FROM assembly_stage WHERE assembly_key=? ORDER BY stage_order", key);

        StringBuilder b = new StringBuilder("The " + name + " has " + done.size() + " of " + stages.size()
            + (stages.size() == 1 ? " stage" : " stages") + " set into it.");

        Map<String, Object> next = null;
        for (Map<String, Object> s : stages) {
            if (done.contains((String) s.get("stage_key"))) continue;
            String prereq = (String) s.get("prerequisite_stage_key");
            if (prereq == null || done.contains(prereq)) { next = s; break; }
        }
        if (next == null) return b + " Nothing of it is left to do.";

        String prereqKey = (String) next.get("prerequisite_stage_key");
        if (prereqKey != null && doneGrade.get(prereqKey) == QualityGrade.DEFECTIVE)
            return b + " The work before it came out defective, and nothing more can go in until that is"
                 + " reworked.";

        String stageName = ((String) next.get("name")).toLowerCase(java.util.Locale.ROOT);
        int cure = ((Number) next.get("cure_minutes")).intValue();
        if (cure > 0) {
            Timestamp since = prereqKey == null ? null : doneAt.get(prereqKey);
            long elapsed = since == null ? Long.MAX_VALUE : (at.toEpochMilli() - since.getTime()) / 60000L;
            if (elapsed < cure) {
                long hours = Math.max(1, (cure - elapsed + 59) / 60);
                return b + " What it wants next is nothing of your hands: the " + stageName + " is still running,"
                     + " about " + hours + " more hour" + (hours == 1 ? "" : "s") + " before it can be worked.";
            }
            return b + " The " + stageName + " has had its time and the work can go on.";
        }

        b.append(" Next: ").append(stageName).append('.');
        List<String> wants = new ArrayList<>();
        String tool = (String) next.get("tool_class");
        if (!tool.isBlank()) wants.add(toolPhrase(tool)
            + (hasTool(chronicle, tool) ? " — which you are carrying" : " — which you have not got"));
        if (Boolean.TRUE.equals(next.get("requires_fire")))
            wants.add(fireHere(location) ? "heat — and there is a fire burning here" : "heat — and there is no fire here");
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT item_key, quantity FROM assembly_stage_requirement WHERE stage_key=? ORDER BY item_key",
                next.get("stage_key"))) {
            String itemKey = (String) r.get("item_key");
            int quantity = ((Number) r.get("quantity")).intValue();
            int have = items.reachCountAt(chronicle, location, itemKey);
            wants.add(quantity + " " + itemKey.replace('_', ' ')
                + (have >= quantity ? " (you have them)" : have > 0 ? " (you have " + have + ")" : " (you have none)"));
        }
        if (!wants.isEmpty()) b.append(" It wants ").append(String.join(", and ", wants)).append('.');

        // AND THE SKY, which advance refuses on and nothing could be asked about beforehand. A keeper who is
        // told what a stage wants and not that it cannot be laid in this rain learns it by losing the work.
        String limit = (String) next.get("weather_limit");
        if (limit != null && location != null) {
            if ("DRY".equals(limit)) b.append(weather.wetHere(location)
                ? " It has to go on dry, and it is coming down too hard for it now."
                : " It has to go on dry, and the weather is holding.");
            if ("CALM".equals(limit)) b.append(weather.galeHere(location)
                ? " It cannot be worked in this wind."
                : " It wants calm air, and the wind is down.");
        }
        return b.toString();
    }

    /**
     * Return an in-progress assembly to its earliest defective stage, stripping that
     * stage and everything after it so it can be done again (M3b). The flawed work is
     * undone, not discarded.
     */
    @Transactional
    public String[] rework(UUID chronicle, UUID location, String text, Instant at) {
        // Stripping work back out is done with the hands, on the thing (#77) — never to a structure out of sight.
        String key = match(text);
        String elsewhere = key == null ? null : standsElsewhere(chronicle, key, location);
        if (elsewhere != null) return new String[]{"FAILED", elsewhere};
        UUID instanceId = instanceWithinReach(chronicle, key, location, true);
        if (instanceId == null) return new String[]{"FAILED", "You have nothing half-made to rework."};

        Map<String, Object> flaw = jdbc.query(
            "SELECT s.stage_key, s.stage_order, s.name FROM assembly_stage_completion c JOIN assembly_stage s ON s.stage_key=c.stage_key " +
            "WHERE c.instance_id=? AND c.quality_grade='DEFECTIVE' ORDER BY s.stage_order LIMIT 1",
            rs -> rs.next() ? Map.of("order", rs.getInt("stage_order"), "name", rs.getString("name")) : null, instanceId);
        if (flaw == null) return new String[]{"FAILED", "You look the piece over, but there is nothing flawed to redo."};

        int fromOrder = (int) flaw.get("order");
        final UUID inst = instanceId;
        jdbc.update("DELETE FROM assembly_stage_completion WHERE instance_id=? AND stage_key IN (" +
            "SELECT stage_key FROM assembly_stage WHERE assembly_key=(SELECT assembly_key FROM assembly_instance WHERE id=?) AND stage_order >= ?)",
            inst, inst, fromOrder);
        jdbc.update("UPDATE assembly_instance SET updated_at=? WHERE id=?", Timestamp.from(at), inst);
        return new String[]{"SUCCEEDED", "You strip the " + ((String) flaw.get("name")).toLowerCase(java.util.Locale.ROOT) + " back out and ready the piece to be done again."};
    }

    /**
     * Read the tool registry rather than a hand-written list. These three cases each named a handful of item keys and
     * had fallen behind tool_profile, which the material processes already consult: ten registered CUTTING tools were
     * invisible here, four STRIKING ones, and every metal AXE — so a Chronicle could smelt an iron axe and still be
     * refused a stage a stone axe could do. Every key the old lists named is in the registry, so this only widens.
     */
    /** What to call a missing tool class, so a refusal names a thing rather than a class name (#37). Deferred to
     *  the one list, so a stage and a material process cannot drift into different words for the same tool. */
    private static String toolPhrase(String toolClass) {
        return com.devosphere.draugr.item.PhysicalItemService.toolPhrase(toolClass);
    }

    private boolean hasTool(UUID chronicle, String toolClass) {
        return switch (toolClass) {
            case "CUTTING", "STRIKING", "AXE" -> items.hasToolOfClass(chronicle, toolClass);
            default -> true;
        };
    }

    private boolean fireHere(UUID location) {
        Boolean f = jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM fire_state fs JOIN world_object w ON w.id=fs.construction_id WHERE w.current_location_id=? AND fs.active=true)",
            Boolean.class, location);
        return Boolean.TRUE.equals(f);
    }

    /**
     * The verbs an assembly's own keywords use to ask for it to be made. A keyword containing one — "build a
     * drying rack", "work on the causeway" — is a build phrase; a keyword without one — "drying rack", "causeway" —
     * is the thing's bare name, which a person says as readily while using it as while building it.
     */
    private static final java.util.List<String> BUILD_VERBS = java.util.List.of(
        "build", "make", "raise", "erect", "construct", "set up", "put up", "lay", "dig", "lash", "work on",
        "start", "begin", "wall", "cover", "set out");

    /** Is this keyword a request to make the thing, rather than its bare name (#77)? */
    static boolean isBuildPhrase(String keyword) {
        if (keyword == null) return false;
        String v = ActivityClassifier.normalise(keyword);
        for (String w : BUILD_VERBS) if (ActivityClassifier.containsTerm(v, w)) return true;
        return false;
    }

    /** "Another", "a second", "a new one": how a person asks for one more of a thing they already have. */
    static boolean asksForAnother(String text) {
        String v = ActivityClassifier.normalise(text);
        return ActivityClassifier.containsTerm(v, "another") || ActivityClassifier.containsTerm(v, "second")
            || ActivityClassifier.containsTerm(v, "new");
    }

    /** A finished, sound structure of this kind already standing on this ground. */
    private boolean standsHere(UUID location, String constructionKind) {
        if (location == null || constructionKind == null) return false;
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "WHERE w.current_location_id=? AND cp.project_kind=? AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE')",
            Boolean.class, location, constructionKind));
    }

    /**
     * Would this kind be the second thing to ring this ground? (#77/V432)
     *
     * <p>True when the kind about to be raised takes a whole chunk and something that already takes this chunk
     * stands on it. False when the kind takes no ground of its own — a rack or a hearth may go up beside a fold
     * — and false when the ground is clear.
     *
     * <p>Shared with {@link com.devosphere.draugr.construction.ConstructionService#buildPen}, whose hard-coded
     * one-pen-per-chunk refusal is what this generalises, so the two cannot drift apart.
     */
    private boolean groundIsTakenWhole(UUID location, String constructionKind) {
        if (location == null || constructionKind == null) return false;
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM construction_kind mine WHERE mine.project_kind=? AND mine.takes_the_whole_ground) " +
            "   AND EXISTS(SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "              JOIN construction_kind ck ON ck.project_kind=cp.project_kind " +
            "              WHERE w.current_location_id=? AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE' " +
            "                AND ck.takes_the_whole_ground)",
            Boolean.class, constructionKind, location));
    }

    /** This Chronicle has one of these already under way — whatever else stands here, that one is advanced. */
    private boolean underWay(UUID chronicle, String assemblyKey) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM assembly_instance WHERE chronicle_id=? AND assembly_key=? AND state='IN_PROGRESS')",
            Boolean.class, chronicle, assemblyKey));
    }

    /**
     * Is one of these part-built <b>on this ground</b>? (#77/#108)
     *
     * <p>Every condition on the PLACE — the well's water table, the site a structure must stand on, and the
     * ground a thing takes whole — is checked only when the work is not already begun, because a structure
     * cannot move once started and re-asking on each stage would refuse work on a thing already standing
     * correctly. {@link #underWay} answers the wrong question for that: it asks whether the Chronicle is
     * part-way through one of these <em>anywhere</em>.
     *
     * <p><b>That was a real hole, and CI found it through a test of the footprint rule.</b> With a poultry run
     * half-built on one chunk, the place conditions were skipped everywhere — so a second run could be ringed
     * around ground already holding a goat fold, a washing place could be raised inland, and a well sunk on dry
     * gravel, purely because an unfinished one of the same kind existed somewhere else.
     *
     * <p>Asking per location preserves the original intent exactly and closes the hole: the checks are skipped
     * only for the one you are actually building here.
     */
    private boolean underWayHere(UUID chronicle, String assemblyKey, UUID location) {
        if (location == null) return underWay(chronicle, assemblyKey);
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM assembly_instance WHERE chronicle_id=? AND assembly_key=? " +
            "AND state='IN_PROGRESS' AND location_id=?)",
            Boolean.class, chronicle, assemblyKey, location));
    }

    /**
     * Why this part-built structure cannot be worked from here, or null when it can (#77).
     *
     * <p>Null when nothing of this kind is under way, when it is under way on this ground, and when it carries no
     * location at all (a craft, which travels in the hand). Otherwise the sentence names the thing and which way
     * it lies, from {@link com.devosphere.draugr.world.Compass} so the bearing agrees with every other one the
     * world gives.
     */
    private String standsElsewhere(UUID chronicle, String assemblyKey, UUID location) {
        // Nowhere to stand is nowhere to measure from; the old unscoped behaviour holds rather than a guess.
        if (location == null) return null;
        Map<String, Object> there = jdbc.query(
            "SELECT lower(d.display_name) AS name, c.grid_x AS x, c.grid_y AS y FROM assembly_instance ai " +
            "JOIN assembly_definition d ON d.assembly_key=ai.assembly_key JOIN world_chunk c ON c.id=ai.location_id " +
            "WHERE ai.chronicle_id=? AND ai.assembly_key=? AND ai.state='IN_PROGRESS' " +
            "AND ai.location_id IS DISTINCT FROM ?::uuid",
            rs -> rs.next() ? Map.of("name", rs.getString("name"), "x", rs.getInt("x"), "y", rs.getInt("y")) : null,
            chronicle, assemblyKey, location);
        return there == null ? null : liesElsewhere((String) there.get("name"), (int) there.get("x"), (int) there.get("y"), location);
    }

    /** The sentence for a part-built structure on other ground: what it is, and which way it lies from here. */
    private String liesElsewhere(String name, int x, int y, UUID location) {
        String way = null;
        int far = 0;
        if (location != null) {
            Map<String, Object> here = jdbc.query("SELECT grid_x, grid_y FROM world_chunk WHERE id=?",
                rs -> rs.next() ? Map.of("x", rs.getInt(1), "y", rs.getInt(2)) : null, location);
            if (here != null) {
                int dx = x - (int) here.get("x"), dy = y - (int) here.get("y");
                way = com.devosphere.draugr.world.Compass.of(dx, dy);
                far = Math.max(Math.abs(dx), Math.abs(dy));
            }
        }
        String where = way == null ? "on other ground"
            : far <= 1 ? "on the next ground to the " + way : "some way off to the " + way;
        return "Your " + name + " stands half-raised where you began it, " + where + ". A thing set into the ground "
            + "stays where it was begun; the work goes on there.";
    }

    /**
     * The in-progress instance a Chronicle means, among those within reach of where they stand: a craft (no
     * location) wherever they are, a structure only on its own ground. By kind when the words name one, else
     * the most recently worked.
     */
    private UUID instanceWithinReach(UUID chronicle, String key, UUID location, boolean lock) {
        String reach = " AND (location_id IS NULL OR location_id = ?::uuid)";
        String forUpdate = lock ? " FOR UPDATE" : "";
        UUID id = key == null ? null : jdbc.query(
            "SELECT id FROM assembly_instance WHERE chronicle_id=? AND assembly_key=? AND state='IN_PROGRESS'" + reach + " LIMIT 1" + forUpdate,
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, chronicle, key, location);
        if (id == null) id = jdbc.query(
            "SELECT id FROM assembly_instance WHERE chronicle_id=? AND state='IN_PROGRESS'" + reach +
            " ORDER BY updated_at DESC LIMIT 1" + forUpdate,
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, chronicle, location);
        return id;
    }
}
