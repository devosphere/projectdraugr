package com.devosphere.draugr.item;

import com.devosphere.draugr.ecology.ResourceEcologyService;
import com.devosphere.draugr.routing.ProcessMatcher;
import com.devosphere.draugr.quality.QualityGrade;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PhysicalItemService {
    private final JdbcTemplate jdbc; private final ResourceEcologyService resources;
    private final ProcessMatcher matcher;
    public PhysicalItemService(JdbcTemplate jdbc, ResourceEcologyService resources, ProcessMatcher matcher) {
        this.jdbc = jdbc; this.resources = resources; this.matcher = matcher;
    }

    @Transactional(readOnly = true)
    public List<ItemView> carried() {
        UUID chronicle = activeChronicle();
        return jdbc.query("WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') SELECT w.id, w.display_name, i.item_key, w.current_owner_id, ic.container_id FROM reachable r JOIN world_object w ON w.id=r.id JOIN item_instance i ON i.object_id=w.id LEFT JOIN item_containment ic ON ic.item_id=w.id ORDER BY w.display_name", (rs,row) -> new ItemView(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getObject(4,UUID.class),rs.getObject(5,UUID.class)), chronicle);
    }
    @Transactional(readOnly = true)
    public ItemState state() {
        UUID chronicle=activeChronicle();
        List<ItemView> carried=carried();
        List<EquippedView> equipped=jdbc.query("SELECT w.id,w.display_name,i.item_key,e.body_position,e.layer FROM equipment_attachment e JOIN world_object w ON w.id=e.item_id JOIN item_instance i ON i.object_id=w.id WHERE e.chronicle_id=? ORDER BY e.body_position,e.layer",(rs,row)->new EquippedView(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5)),chronicle);
        return new ItemState(chronicle,carried,equipped,loadState(chronicle),containers(chronicle));
    }
    @Transactional
    public int gatherPlantFiber(UUID chronicle, UUID location, Instant occurredAt) { return gatherPlantFiber(chronicle, location, occurredAt, 0); }
    /** {@code extra} is the effort/skill yield bonus (#68): a careful, practised gather wins more where the source holds it. */
    @Transactional
    public int gatherPlantFiber(UUID chronicle, UUID location, Instant occurredAt, int extra) {
        String biome=jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?",String.class,location);
        if ("OCEAN".equals(biome) || "MOUNTAIN".equals(biome)) return 0; // No suitable fiber here; resolves as a graceful empty-handed attempt.
        int desired=Math.min(("WETLAND".equals(biome)?3:2)+extra, capacityHeadroomUnits(chronicle,"plant_fiber"));
        if(desired<=0) return 0; // The Chronicle cannot carry any more; a graceful empty-handed attempt.
        int count=resources.take(location,"plant_fiber",desired,occurredAt);
        for(int i=0;i<count;i++){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM','Plant fiber bundle',?)",id,chronicle);jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,'plant_fiber','SOUND')",id);jdbc.update("INSERT INTO object_transition (object_id,transition_type,payload) VALUES (?,'GATHERED',jsonb_build_object('biome',?))",id,biome);}
        assertCarryCapacity(chronicle);
        return count;
    }
    @Transactional
    public int gatherFieldStones(UUID chronicle, UUID location, Instant occurredAt) { return gatherFieldStones(chronicle, location, occurredAt, 0); }
    @Transactional
    public int gatherFieldStones(UUID chronicle, UUID location, Instant occurredAt, int extra) {
        String biome=jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?",String.class,location);
        if ("OCEAN".equals(biome)) return 0; // No loose stone here; resolves as a graceful empty-handed attempt.
        int desired=Math.min(("MOUNTAIN".equals(biome) || "HIGHLAND".equals(biome) ? 3 : 2)+extra, capacityHeadroomUnits(chronicle,"field_stone"));
        if(desired<=0) return 0; // The Chronicle cannot carry any more; a graceful empty-handed attempt.
        int count=resources.take(location,"field_stone",desired,occurredAt);
        for(int i=0;i<count;i++){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM','Field stone',?)",id,chronicle);jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,'field_stone','SOUND')",id);jdbc.update("INSERT INTO object_transition (object_id,transition_type,payload) VALUES (?,'GATHERED',jsonb_build_object('biome',?))",id,biome);}
        assertCarryCapacity(chronicle);
        return count;
    }
    @Transactional
    public int gatherWildBerries(UUID chronicle, UUID location, Instant occurredAt) { return gatherWildBerries(chronicle, location, occurredAt, 0); }
    @Transactional
    public int gatherWildBerries(UUID chronicle, UUID location, Instant occurredAt, int extra) {
        String biome=jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?",String.class,location);
        if ("MOUNTAIN".equals(biome) || "OCEAN".equals(biome)) return 0; // No edible growth here; resolves as a graceful empty-handed attempt.
        int desired=Math.min(("WETLAND".equals(biome)?3:2)+extra, capacityHeadroomUnits(chronicle,"wild_berries"));
        if(desired<=0) return 0; // The Chronicle cannot carry any more; a graceful empty-handed attempt.
        int count=resources.take(location,"wild_berries",desired,occurredAt);
        for(int i=0;i<count;i++){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM','Wild berries',?)",id,chronicle);jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,'wild_berries','SOUND')",id);jdbc.update("INSERT INTO object_transition (object_id,transition_type,payload) VALUES (?,'GATHERED',jsonb_build_object('biome',?))",id,biome);} assertCarryCapacity(chronicle); return count;
    }
    @Transactional
    public int gatherDryBranches(UUID chronicle, UUID location, Instant occurredAt) { return gatherDryBranches(chronicle, location, occurredAt, 0); }
    @Transactional
    public int gatherDryBranches(UUID chronicle, UUID location, Instant occurredAt, int extra) {
        String biome=jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?",String.class,location);
        if ("OCEAN".equals(biome)) return 0; // No branches here; resolves as a graceful empty-handed attempt.
        int desired=Math.min(("MOUNTAIN".equals(biome)?1:2)+extra, capacityHeadroomUnits(chronicle,"dry_branch"));
        if(desired<=0) return 0; // The Chronicle cannot carry any more; a graceful empty-handed attempt.
        int count=resources.take(location,"dry_branch",desired,occurredAt);
        for(int i=0;i<count;i++){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM','Dry branch',?)",id,chronicle);jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,'dry_branch','SOUND')",id);jdbc.update("INSERT INTO object_transition (object_id,transition_type,payload) VALUES (?,'GATHERED','{}'::jsonb)",id);} assertCarryCapacity(chronicle); return count;
    }

    /**
     * Search the ground and natural debris for small ambient survival materials (#133): the forest-floor scavenge
     * that #192 made gatherable. The action text names what is looked for (twigs, tinder/leaf litter, loose bark,
     * shed feather/fur, driftwood, reeds); with no hint it yields the biome's default litter. A low, opportunistic
     * yield — this is combing the ground, not harvesting a stand. Reeds want a wet margin; OCEAN has no ground.
     */
    @Transactional
    public String[] forageGround(UUID chronicle, UUID location, String text, Instant at) {
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
        if (biome == null || "OCEAN".equals(biome)) return new String[]{"FAILED", "There is no ground to comb here — only open water."};
        String v = text.toLowerCase(java.util.Locale.ROOT);
        String key, name;
        if (v.contains("leaf litter") || v.contains("litter") || v.contains("fallen leaves") || v.contains("tinder")) { key = "fallen_leaf_litter"; name = "Fallen leaf litter"; }
        else if (v.contains("bark")) { key = "loose_bark_strip"; name = "Loose bark strip"; }
        else if (v.contains("feather")) { key = "shed_feather"; name = "Shed feather"; }
        else if (v.contains("fur") || v.contains("hair")) { key = "shed_fur_tuft"; name = "Shed fur tuft"; }
        else if (v.contains("reed")) { key = "straight_reed"; name = "Straight reed"; }
        else if (v.contains("driftwood") || v.contains("deadwood") || v.contains("firewood") || v.contains("dry wood") || v.contains("windfall") || v.contains("branch")) { key = "dry_branch"; name = "Dry branch"; }
        else if (v.contains("twig") || v.contains("kindling")) { key = "dry_twig"; name = "Dry twig"; }
        // Scavenged animal remains (#133): a gnawed bone or a shed antler off the ground — bone/antler for tools
        // WITHOUT a kill, the whole point of the story. Uncommon, so a single piece per search.
        else if (v.contains("antler")) { key = "deer_antler"; name = "Deer antler"; }
        else if (v.contains("bone")) { key = "animal_bone"; name = "Animal bone"; }
        else { key = switch (biome) { case "WETLAND" -> "straight_reed"; case "GRASSLAND" -> "dry_grass_bundle"; default -> "dry_twig"; };
               name = switch (key) { case "straight_reed" -> "Straight reed"; case "dry_grass_bundle" -> "Dry grass bundle"; default -> "Dry twig"; }; }
        if ("straight_reed".equals(key) && !"WETLAND".equals(biome)) return new String[]{"FAILED", "You cast about for reeds, but there is no wet margin here where they grow."};
        // Bone and antler are found singly and rarely; litter comes by the armful.
        boolean scarce = "deer_antler".equals(key) || "animal_bone".equals(key);
        int base = scarce ? 1 : 2 + ("TEMPERATE_FOREST".equals(biome) || "WETLAND".equals(biome) ? 1 : 0);
        // A rake or a hoe drags loose ground litter — leaves, grass, twigs, reeds — up by the armful (#257):
        // both were craftable but read by nothing, so raking the ground gathered no more than bare hands. It is
        // no help finding a bone or antler, which are scavenged singly however you comb the ground.
        int rake = (!scarce && (hasAtLeast(chronicle, "wooden_rake", 1) || hasAtLeast(chronicle, "wooden_hoe", 1))) ? 2 : 0;
        int desired = Math.min(base + rake, capacityHeadroomUnits(chronicle, key));
        if (desired <= 0) return new String[]{"FAILED", "Your hands and packs are full; there is no room to carry more."};
        for (int i = 0; i < desired; i++) createCarriedItem(chronicle, key, name, at, "FORAGED_FROM_GROUND");
        assertCarryCapacity(chronicle);
        return new String[]{"SUCCEEDED", "You comb the ground and gather up " + name.toLowerCase() + " — " + desired + " to hand."};
    }
    /**
     * The ONE reachability model (DR-0022 Layer 1): everything the Chronicle can physically reach from where
     * it stands — carried, nested in a carried container, loose on the ground here, AND inside any container or
     * storage sited at its location (a bin, a shelf, a tool rack). Roots at carried ∪ location-sited, then
     * descends {@code item_containment} from both, so the contents of an on-site store are in reach without
     * being owned. Bind order is always {@code (chronicle, location)}. Every input-sourcing, tool, and grade
     * check routes through this so no code path can disagree about what is "in reach" — unlike the historical
     * carried-only CTEs, which left materials in a bin and tools on a rack unreachable (the stone-shelf gap).
     * The inventory/HUD view (carried load, capacity) deliberately stays carried-only elsewhere.
     */
    static final String REACHABLE_CTE =
        "WITH RECURSIVE reachable(id) AS (" +
        "SELECT id FROM world_object WHERE lifecycle_state='ACTIVE' AND (current_owner_id=? OR (current_owner_id IS NULL AND current_location_id=?)) " +
        "UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') ";

    /** How many of an item the Chronicle can reach from {@code location} (carried + ground + on-site storage). */
    private int reachCount(UUID chronicle, UUID location, String itemKey) {
        Integer c = jdbc.queryForObject(REACHABLE_CTE +
            "SELECT COUNT(*) FROM reachable r JOIN item_instance i ON i.object_id=r.id WHERE i.item_key=?",
            Integer.class, chronicle, location, itemKey);
        return c == null ? 0 : c;
    }

    /** Consume one reachable such item — what is carried first, then the nearest in reach — or false if none. */
    /**
     * The order a kind is spent in: <b>the poorest first</b>, then what is in hand before what is in a store,
     * then by id so it is never a coin toss.
     *
     * <p>This used to be carried-first and then id — an arbitrary instance — while {@link #worstGradeAmong},
     * which caps what the work can come out as, read the <b>worst</b> reachable one. The two disagreed, and a
     * Chronicle carrying a fine fibre and a poor one paid for it twice: the cap was taken from the poor fibre and
     * the fine one was what got consumed. They lost the good stock <em>and</em> got the poor result, and the poor
     * stock was still sitting there afterwards.
     *
     * <p>Spending the poorest first makes the two agree by construction — the thing whose grade decided the
     * outcome is the thing that was used up — and it is what a person does anyway: work off the rough stock, keep
     * the good for when it matters. It reads the same for food and fuel, where eating or burning the worst first
     * is the ordinary thrift.
     */
    static final String POOREST_FIRST =
        "ORDER BY CASE i.quality_grade WHEN 'DEFECTIVE' THEN 0 WHEN 'POOR' THEN 1 WHEN 'SOUND' THEN 2 ELSE 3 END, " +
        "CASE WHEN w.current_owner_id=? THEN 0 ELSE 1 END, r.id ";

    private boolean consumeFromReach(UUID chronicle, UUID location, String itemKey, Instant occurredAt) {
        UUID item = jdbc.query(REACHABLE_CTE +
            "SELECT r.id FROM reachable r JOIN item_instance i ON i.object_id=r.id JOIN world_object w ON w.id=r.id " +
            "WHERE i.item_key=? " + POOREST_FIRST + "FOR UPDATE OF i LIMIT 1",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, chronicle, location, itemKey, chronicle);
        if (item == null) return false;
        retire(item, occurredAt, "CONSUMED", itemKey);
        return true;
    }

    @Transactional
    public boolean consumeOne(UUID chronicle, String itemKey, Instant occurredAt) {
        return consumeFromReach(chronicle, chronicleLocation(chronicle), itemKey, occurredAt);
    }

    /** The workmanship grade of the reachable item {@code consumeOne} would take next (same carried-first order),
     *  or SOUND if none — so a caller can let a food's grade scale its effect before consuming it (#271). */
    @Transactional(readOnly = true)
    public QualityGrade gradeOfNextConsumed(UUID chronicle, String itemKey) {
        UUID location = chronicleLocation(chronicle);
        // The same order consumeFromReach spends in, or this describes an object other than the one taken.
        String g = jdbc.query(REACHABLE_CTE +
            "SELECT i.quality_grade FROM reachable r JOIN item_instance i ON i.object_id=r.id JOIN world_object w ON w.id=r.id " +
            "WHERE i.item_key=? " + POOREST_FIRST + "LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : null, chronicle, location, itemKey, chronicle);
        return QualityGrade.of(g);
    }

    /**
     * The item_key of a reachable, edible item (category FOOD) the action text names — by the
     * item's own key or its display name — or null. This is what lets "eat the oyster mushroom"
     * find the mushroom the chronicle actually foraged, rather than the EAT handler only knowing a
     * hardcoded few (GitHub #24). Meat is included, but the caller routes it through the
     * spoilage-tracked food service.
     */
    @Transactional(readOnly = true)
    public String namedFoodInReach(UUID chronicle, String lowerText) {
        return reachableFoods(chronicle).stream()
            .filter(f -> lowerText.contains(f[0].replace("_", " ")) || lowerText.contains(f[1].toLowerCase(java.util.Locale.ROOT)))
            .map(f -> f[0]).findFirst().orElse(null);
    }

    /** The item_key of any reachable edible (category FOOD) item, or null — the fallback when the player just says "eat". */
    @Transactional(readOnly = true)
    public String anyFoodInReach(UUID chronicle) {
        java.util.List<String[]> foods = reachableFoods(chronicle);
        return foods.isEmpty() ? null : foods.get(0)[0];
    }

    /** True if eating this item is dangerous — it drops from a flora marked poisonous (e.g. death cap, fly agaric). */
    @Transactional(readOnly = true)
    public boolean isPoisonousForage(String itemKey) {
        Boolean poisonous = jdbc.query(
            "SELECT bool_or(fd.is_poisonous) FROM flora_drop d JOIN flora_definition fd ON fd.flora_key=d.flora_key WHERE d.item_key=?",
            rs -> rs.next() ? (Boolean) rs.getObject(1) : null, itemKey);
        return Boolean.TRUE.equals(poisonous);
    }

    /**
     * What food you have and how long it will keep (#37).
     *
     * <p>Spoilage is a complete subsystem: five tiers with their own spans (raw 18h, cooked 72h, smoked 30d,
     * dried 45d, salted 60d), a clock each object keeps, and an illness for eating what has gone over. All of it
     * was invisible from inside the game. A Chronicle could carry a fortnight of salted meat and a fish that was
     * finished by evening and had no way to tell them apart — no phrasing of the question reached anything, and
     * the survey that does walk your camp counts only structures.
     *
     * <p>Read-only, and nothing here is new information: it is the world's own record said out loud, on the same
     * reach rule that decides what you can eat, so what is counted is exactly what is within your hands.
     * Soonest to spoil is named first, because that is the one the answer is really about.
     *
     * <p>Food with no preservation state is named as keeping, without a span — that is the honest reading of the
     * record, and it is also the only place in the game where an untracked food becomes visible at all.
     */
    @Transactional(readOnly = true)
    public String foodStocktake(UUID chronicle, UUID location, Instant at) {
        record Lot(String name, int count, String tier, Long hoursLeft) { }
        java.util.List<Lot> lots = jdbc.query(REACHABLE_CTE +
            "SELECT d.display_name, COUNT(*)::int, MIN(f.preparation_kind), " +
            "       MIN(EXTRACT(EPOCH FROM (f.safe_until - ?))/3600.0) " +
            "FROM reachable r JOIN item_instance i ON i.object_id=r.id JOIN item_definition d ON d.item_key=i.item_key " +
            "LEFT JOIN food_preservation_state f ON f.object_id=r.id " +
            "WHERE d.category='FOOD' GROUP BY d.display_name " +
            // Nulls last: what has no clock on it is not what you want told first.
            "ORDER BY 4 NULLS LAST, d.display_name",
            (rs, row) -> {
                double h = rs.getDouble(4);
                return new Lot(rs.getString(1).toLowerCase(java.util.Locale.ROOT), rs.getInt(2),
                               rs.getString(3), rs.wasNull() ? null : Math.round(h));
            }, chronicle, location, Timestamp.from(at));

        if (lots.isEmpty())
            return "You go through what you are carrying and what is put by here, and there is no food in any of "
                 + "it. Nothing to eat now and nothing kept against later.";

        java.util.List<String> said = new java.util.ArrayList<>();
        int gone = 0;
        for (Lot lot : lots) {
            // "6 beech mast", not "6 beech masts" — the bare count is how this game already says a quantity of an
            // item everywhere else, and half the food names are mass nouns that no -s fits.
            String head = lot.count() > 1 ? lot.count() + " " + lot.name() : lot.name();
            if (lot.hoursLeft() == null) { said.add(head + ", which keeps"); continue; }
            if (lot.hoursLeft() <= 0) { said.add(head + ", gone over and not safe"); gone++; continue; }
            said.add(head + " (" + tierWord(lot.tier()) + ") with " + spanLeft(lot.hoursLeft()) + " left in it");
        }
        StringBuilder s = new StringBuilder("You go through the food: ").append(joinAnd(said)).append(".");
        if (gone > 0) s.append(gone == 1
            ? " One of them is past eating; it will sicken you if you try."
            : " " + gone + " of them are past eating; they will sicken you if you try.");
        return s.toString();
    }

    /** The tier a food keeps on, in the word a person would use for it. */
    private static String tierWord(String kind) {
        if (kind == null) return "untreated";
        return switch (kind) {
            case "SALTED" -> "salted"; case "SMOKED" -> "smoked"; case "DRIED" -> "dried";
            case "COOKED" -> "cooked"; case "FRESH" -> "fresh"; case "RAW" -> "raw";
            default -> kind.toLowerCase(java.util.Locale.ROOT);
        };
    }

    /** How long is left, in the coarsest unit that is still honest — nobody counts a month of salt beef in hours. */
    private static String spanLeft(long hours) {
        if (hours < 24) return hours <= 1 ? "under an hour" : hours + " hours";
        long days = hours / 24;
        return days == 1 ? "a day" : days + " days";
    }

    /** Reachable FOOD-category items as [item_key, display_name] pairs. */
    private java.util.List<String[]> reachableFoods(UUID chronicle) {
        return jdbc.query(REACHABLE_CTE +
            "SELECT DISTINCT i.item_key, d.display_name FROM reachable r JOIN item_instance i ON i.object_id=r.id JOIN item_definition d ON d.item_key=i.item_key " +
            "WHERE d.category='FOOD' ORDER BY i.item_key",
            (rs, row) -> new String[]{rs.getString(1), rs.getString(2)}, chronicle, chronicleLocation(chronicle));
    }

    /**
     * True when the chronicle can reach at least {@code required} of an item — carried,
     * in a carried container, OR lying on the ground at their current location. A felled
     * log is worked where it fell; you do not have to shoulder a whole trunk to split it,
     * which is exactly the deadlock that made felling impossible for anything too heavy
     * to carry (GitHub #17).
     */
    @Transactional(readOnly = true)
    public boolean hasAtLeastHere(UUID chronicle, UUID location, String itemKey, int required) {
        return reachCount(chronicle, location, itemKey) >= required;
    }

    /** Consume one such item, taking what is carried first and then the nearest in reach (ground or on-site store). */
    @Transactional
    public boolean consumeOneHere(UUID chronicle, UUID location, String itemKey, Instant occurredAt) {
        return consumeFromReach(chronicle, location, itemKey, occurredAt);
    }
    @Transactional
    public UUID createCarriedItem(UUID chronicle, String itemKey, String displayName, Instant occurredAt, String transitionType) {
        return createCarriedItem(chronicle, itemKey, displayName, occurredAt, transitionType, QualityGrade.SOUND);
    }
    /**
     * An item held by some other world object than the Chronicle — a native community's store (#111), for one. It is
     * a real item with a transition on the ledger, and if it is food it keeps on the same clock food keeps on
     * anywhere else, so a store's stock can spoil and run out rather than being a number that never changes.
     */
    @Transactional
    public UUID createHeldItem(UUID holder, String itemKey, String displayName, Instant occurredAt, String transitionType) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM',?,?)", id, displayName, holder);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state,quality_grade) VALUES (?,?,'SOUND','SOUND')", id, itemKey);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,?,jsonb_build_object('itemKey',?))", id, Timestamp.from(occurredAt), transitionType, itemKey);
        String keep = keepKindFor(itemKey);
        if (keep != null) registerPreserved(id, keep, occurredAt);
        return id;
    }

    /**
     * The tier an item keeps on, or null for anything that is not food (#37, #60).
     *
     * <p>The named map is the authority where a preparation decides the span — salting, smoking, drying, a wet
     * cooked dish. <b>Everything else that is FOOD falls back to the foraged reading rather than to nothing</b>,
     * and that fallback is the point of this method: the map was a hand-written list standing in for a table, and
     * a made food missing from it never spoiled at all.
     *
     * <p>Eight made foods were in exactly that state — acorn flour, grain flour, wild grain, hazelnut and walnut
     * kernels, a peeled root, a washed root and a bait pouch — because nothing had added them. A washed root is
     * the #60 defect again: <i>processing a perishable food laundered it into food that never spoiled</i>, the
     * same way gutting a fish once did. The dry keepers genuinely do keep for weeks and take DRIED; a prepared
     * root is produce and takes FRESH; worms and crushed berries in a pouch are produce too.
     *
     * <p>Fixing it by adding eight cases would have left the next made food to be found the same way. Both paths
     * already agreed on all eight, so the honest fix is to stop asking the list and let the fallback answer.
     */
    private String keepKindFor(String itemKey) {
        String named = preservationKind(itemKey);
        if (named != null) return named;
        return "FOOD".equals(jdbc.query("SELECT category FROM item_definition WHERE item_key=?",
                                        rs -> rs.next() ? rs.getString(1) : null, itemKey))
             ? foragedKeepKind(itemKey) : null;
    }

    /** As above, but with an explicit quality grade — used by processes and assemblies whose output grade flows from their inputs (M3b). */
    public UUID createCarriedItem(UUID chronicle, String itemKey, String displayName, Instant occurredAt, String transitionType, QualityGrade grade) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM',?,?)", id, displayName, chronicle);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state,quality_grade) VALUES (?,?,'SOUND',?)", id, itemKey, grade.name());
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,?,jsonb_build_object('itemKey',?))", id, Timestamp.from(occurredAt), transitionType, itemKey);
        assertCarryCapacity(chronicle);
        return id;
    }

    /** The Chronicle's current sustained mass-carrying capacity in grams, including any equipped carrying aid (#57). */
    @Transactional(readOnly = true)
    public int sustainedMassCapacity(UUID chronicle) { return loadState(chronicle).sustainedMassCapacityGrams(); }

    /** How much a draft beast tires from one bout of work (moving/travelling with a vehicle hitched), and how much a
     *  spell of rest gives back. Fatigue scales its haul in loadState (#100/#101). */
    private static final int DRAFT_FATIGUE_PER_WORK = 20;
    // What geared work costs is now draft_gear.eases_fatigue_to, per piece of gear (#106, V371). The constant is
    // gone rather than kept beside the table: two answers to one question is how the reed-goods list drifted.
    private static final int DRAFT_REST_RECOVERY = 40;

    /** Terrain the draft team crosses easily — open ground a vehicle rolls or drags over without a fight. Everything
     *  else (forest, mountain, highland, wetland, bog) is rough going that strains a hauling beast half again as hard,
     *  so a keeper routes a loaded team over open country to spare it (#103 terrain/route). */
    private boolean isEasyDraftGround(String biome) {
        return "GRASSLAND".equals(biome) || "PLAINS".equals(biome) || "COAST".equals(biome);
    }

    /** Work the Chronicle's hitched draft beasts (#101): moving or travelling with a draft vehicle to hand tires every
     *  tamed draft-capable beast bonded to them, up to spent. A beast with no vehicle to pull is not worked. Proper
     *  draft gear — a harness or a yoke (#102) — spreads the load, so a harnessed beast tires slower for the same work.
     *  Rough ground (#103) tires it half again as hard — the reason a loaded team is routed over open country. */
    @Transactional
    public String workDraftBeasts(UUID chronicle) {
        String biome = jdbc.query("SELECT ch.biome FROM world_object cw JOIN world_chunk ch ON ch.id=cw.current_location_id WHERE cw.id=?",
            rs -> rs.next() ? rs.getString(1) : null, chronicle);
        // Rough going strains the team half again as hard, geared or not. The numerator is the ungeared cost;
        // what gear takes off it is `draft_gear.eases_fatigue_to` (#106, V371), read per beast in the statement
        // below rather than baked into a constant here, so a heavier yoke can be worth more than a light one.
        boolean easy = isEasyDraftGround(biome);
        int hard = easy ? DRAFT_FATIGUE_PER_WORK : DRAFT_FATIGUE_PER_WORK * 3 / 2;
        jdbc.update(
            // A harness is worn by ONE beast (#106). This was a single EXISTS over the keeper's goods, so one
            // harness spread the load across a team of any size — buy one strap, and eight oxen pull easy for
            // ever. The winter blanket three lines of this class away already had the honest rule, counting
            // covers against beasts by bond, and this is the same rule in the same shape.
            //
            // And a BROKEN harness spread nothing, because a broken strap is a strap that parted: the draft
            // VEHICLE in the same statement was already checked for that, and the gear that hitches the beast
            // to it was not.
            "UPDATE wildlife_bond wb SET draft_fatigue = LEAST(100, draft_fatigue + " +
            "  COALESCE(" + gearOnBeast(easy) + ", ?)), draft_conditioning = LEAST(100, draft_conditioning + 3) " +
            beastsAtWork(),
            hard, chronicle, chronicle);
        return haulageReport(chronicle, easy);
    }

    /**
     * Which of a keeper's bonded beasts are actually at work when they travel: tamed, of a draft species, and with
     * a sound draft vehicle of the keeper's to pull. A beast with nothing to pull is not worked.
     *
     * <p>Shared by the two statements that must never disagree about it — the one that tires the team, and the one
     * that tells the keeper what the pull cost them. Takes the chronicle twice.
     */
    private static String beastsAtWork() {
        return "WHERE wb.chronicle_id=? AND wb.bond_stage='TAMED' " +
            "AND EXISTS (SELECT 1 FROM wildlife_population wp JOIN draft_species ds ON ds.species_key=wp.species_key WHERE wp.id=wb.population_id) " +
            "AND EXISTS (SELECT 1 FROM item_instance ti JOIN world_object tw ON tw.id=ti.object_id " +
            "  WHERE ti.item_key IN (SELECT item_key FROM draft_vehicle) AND ti.condition_state <> 'BROKEN' AND tw.current_owner_id=? AND tw.lifecycle_state='ACTIVE')";
    }

    /**
     * What your team can pull, asked standing still (#37).
     *
     * <p>The draft subsystem is finished and almost entirely invisible. Gear is sized to the body, so a collar
     * harness eases a goat and does nothing whatever for an ox; four vehicles have four different beds; rough
     * ground tires a team half again as hard; fatigue, hunger, thirst and conditioning all scale what a beast can
     * draw. <b>All of it is computed inside an UPDATE that runs only when you walk</b>, and the one line of prose
     * about it comes back as part of a journey. A keeper standing in their own camp could not ask what their
     * oxen would pull, whether the strap they own fits them, or which of them was blown.
     *
     * <p>Worse than silent: every sentence that asked was answered by a recipe for a cart. "pull the cart",
     * "load the cart", "hitch the ox to the cart" all reached the cart's own assembly and were told what timber
     * they lacked.
     *
     * <p>Read-only, and nothing here is new: it is the same {@code gearOnBeast} expression the haul charges by
     * and the same {@code bestBed} cap the load uses, said out loud. Deliberately a LOOSER clause than
     * {@link #beastsAtWork()}, which requires a vehicle to exist — a keeper with two tamed oxen and no cart has
     * the most to be told, and that clause would tell them nothing.
     */
    @Transactional(readOnly = true)
    public String judgeHaulage(UUID chronicle) {
        String biome = jdbc.query("SELECT ch.biome FROM world_object cw JOIN world_chunk ch ON ch.id=cw.current_location_id WHERE cw.id=?",
            rs -> rs.next() ? rs.getString(1) : null, chronicle);
        boolean easy = isEasyDraftGround(biome);

        java.util.List<java.util.Map<String,Object>> team = jdbc.queryForList(
            "SELECT (SELECT wp2.species_key FROM wildlife_population wp2 WHERE wp2.id=wb.population_id) AS species, " +
            "  (" + gearOnBeast(easy) + ") IS NOT NULL AS geared, wb.draft_fatigue AS spentness, " +
            "  GREATEST(wb.draft_hunger, wb.draft_thirst) AS want, wb.draft_conditioning AS seasoned " +
            "FROM wildlife_bond wb WHERE wb.chronicle_id=? AND wb.bond_stage='TAMED' " +
            "AND EXISTS (SELECT 1 FROM wildlife_population wp JOIN draft_species ds ON ds.species_key=wp.species_key " +
            "            WHERE wp.id=wb.population_id) ORDER BY wb.draft_fatigue DESC",
            // One parameter, not two: gearOnBeast carries no placeholder of its own (it reaches the keeper
            // through wb.chronicle_id), and this clause is the looser one rather than beastsAtWork's pair.
            chronicle);

        java.util.List<String> vehicles = jdbc.queryForList(
            "SELECT DISTINCT w.display_name FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "JOIN draft_vehicle dv ON dv.item_key=i.item_key " +
            "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.condition_state <> 'BROKEN' " +
            "ORDER BY w.display_name", String.class, chronicle);
        java.util.List<String> gear = jdbc.queryForList(
            "SELECT DISTINCT w.display_name FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "JOIN draft_gear dg ON dg.item_key=i.item_key " +
            "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.condition_state <> 'BROKEN' " +
            "ORDER BY w.display_name", String.class, chronicle);

        if (team.isEmpty())
            return vehicles.isEmpty()
                ? "You have nothing tamed that pulls, and nothing for it to pull. A beast has to be tamed to the "
                + "hand before it will draw anything, and then it needs something to draw."
                : "You have " + joinAnd(withArticle(vehicles)) + " and nothing tamed to put in front of it. It will "
                + "sit where you left it until there is a beast that answers to you.";

        java.util.List<String> said = new java.util.ArrayList<>();
        int geared = 0, blown = 0, wanting = 0;
        java.util.List<String> bare = new java.util.ArrayList<>();
        for (java.util.Map<String,Object> beast : team) {
            String name = String.valueOf(beast.get("species")).replace('_', ' ');
            if (Boolean.TRUE.equals(beast.get("geared"))) geared++; else bare.add(name);
            if (((Number) beast.get("spentness")).intValue() >= UNFIT_TO_CARRY) blown++;
            if (((Number) beast.get("want")).intValue() >= UNFIT_TO_CARRY) wanting++;
        }
        // The team and what it has to draw are ONE sentence, joined by a comma: said as two they read as
        // "You have 1 tamed to the draught. and a cart for them to draw", which is how the first cut read.
        said.add("You have " + team.size() + (team.size() == 1 ? " beast" : " beasts") + " tamed to the draught"
               + (vehicles.isEmpty()
                  ? ", and nothing for them to pull — a beast with no load behind it is a beast standing still"
                  : ", and " + joinAnd(withArticle(vehicles)) + " for them to draw"));

        if (gear.isEmpty())
            said.add("You keep no gear for them at all, so they would haul against bare rope and pay for every mile of it");
        else if (geared == team.size())
            said.add("The " + joinAnd(lower(gear)) + " you keep fits them, and the weight would ride spread rather than on one strap");
        else if (geared == 0)
            said.add("The " + joinAnd(lower(gear)) + " you keep goes on none of them — nothing you have will fit an "
                   + "animal that size, so the whole load would hang off bare rope");
        else
            said.add("Your gear fits some of them; the " + joinAnd(bare) + " would pull bare alongside");

        if (blown > 0) said.add(blown == 1 ? "One of them is blown and will haul nothing until it has stood a long while"
                                           : blown + " of them are blown and will haul nothing until they have stood a long while");
        if (wanting > 0) said.add(wanting == 1 ? "One of them wants feeding or watering before it draws well"
                                               : wanting + " of them want feeding or watering before they draw well");
        if (!vehicles.isEmpty())
            said.add(easy ? "The ground here is open going, which is the easiest work they will get"
                          : "The ground here is broken going, and they would labour half again as hard over it as over open country");
        return String.join(". ", said) + ".";
    }

    /** Lower-cased for prose. */
    private static java.util.List<String> lower(java.util.List<String> names) {
        return names.stream().map(n -> n.toLowerCase(java.util.Locale.ROOT)).toList();
    }

    /** Lower-cased and given its article, so a list reads "a cart and an ox-yoke" rather than "cart, ox-yoke".
     *  Used where the sentence does not supply a determiner of its own — "Your an ox-yoke" was the first cut. */
    private static java.util.List<String> withArticle(java.util.List<String> names) {
        return lower(names).stream()
            .map(s -> s.isEmpty() ? s : ("aeiou".indexOf(s.charAt(0)) >= 0 ? "an " + s : "a " + s))
            .toList();
    }

    private static String joinAnd(java.util.List<String> parts) {
        if (parts.isEmpty()) return "";
        if (parts.size() == 1) return parts.get(0);
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
    }

    /**
     * What a keeper sees of their own team after a haul (#106), or "" when nothing of theirs pulled.
     *
     * <p><b>The gear was silent.</b> V371 sized draft gear to the body, so a collar harness eases a goat and does
     * nothing whatever for an ox, and {@link #gearThatFitsNothingKept} says so at the moment such gear is MADE.
     * But a keeper who already owns the wrong strap hauls with it for ever and is told nothing, because the tiring
     * happens inside an UPDATE with no prose attached to it: the gear is exactly as invisible as having no gear.
     * This is the hauling half of that same sentence, and it names the one thing the keeper cannot otherwise see —
     * how many of the team were in gear that fitted them, and how many pulled bare.
     *
     * <p>Read with the SAME clauses as the statement above and the same {@link #gearOnBeast} expression, so the
     * report can never claim a beast worked that the update did not tire, nor call a beast geared that the update
     * charged the full ungeared price. That is why the clause is a method rather than a second copy.
     */
    private String haulageReport(UUID chronicle, boolean easy) {
        java.util.List<java.util.Map<String, Object>> team = jdbc.queryForList(
            "SELECT (SELECT wp2.species_key FROM wildlife_population wp2 WHERE wp2.id=wb.population_id) AS species, " +
            "  (" + gearOnBeast(easy) + ") IS NOT NULL AS geared, wb.draft_fatigue AS spentness " +
            "FROM wildlife_bond wb " + beastsAtWork() + " ORDER BY geared, species", chronicle, chronicle);
        if (team.isEmpty()) return "";

        int geared = 0;
        String bare = null, blown = null;
        for (java.util.Map<String, Object> beast : team) {
            String name = String.valueOf(beast.get("species")).replace('_', ' ');
            if (Boolean.TRUE.equals(beast.get("geared"))) geared++;
            else if (bare == null) bare = name;
            if (((Number) beast.get("spentness")).intValue() >= UNFIT_TO_CARRY && blown == null) blown = name;
        }

        StringBuilder said = new StringBuilder();
        if (geared == team.size()) {
            said.append(" The load comes on behind you steadily, the weight of it spread over gear that fits.");
        } else if (geared == 0) {
            said.append(team.size() == 1
                ? " The " + bare + " hauls against bare rope: you have nothing that will go on an animal that size, "
                  + "and the whole of the load hangs off the one strap and its shoulders."
                : " The team hauls against bare rope. Nothing you keep fits any of them, and each mile takes more "
                  + "out of them than it needs to.");
        } else {
            said.append(" Some of the team are in gear that fits them and lean into it easily. The " + bare
                + " is not: you keep nothing that will go on it, so it pulls bare alongside and pays for the "
                + "difference.");
        }
        if (!easy) said.append(" The ground is broken going, and they labour harder for it than they would on open country.");
        if (blown != null) said.append(" The " + blown + " is blown, head down and blowing hard. It will haul nothing "
            + "more until it has stood a long while.");
        return said.toString();
    }

    /**
     * What a bout costs THIS beast in the best gear it has that actually fits it (#106, V371), or NULL when it is
     * working bare — the caller supplies the ungeared cost for that case.
     *
     * <p>Three rules, and each of them was missing something:
     * <ul>
     *   <li><b>One piece of gear to one animal</b>, counted the way {@link #coveredBy} counts winter blankets: a
     *       keeper with two harnesses and three oxen harnesses two of them, decided by bond so the answer never
     *       depends on the order rows arrive in. This was a single EXISTS, so one strap geared a team of any size.</li>
     *   <li><b>Sound gear only.</b> A parted strap pulls nothing. The draft <i>vehicle</i> in the same statement
     *       was always checked for this; the gear hitching the beast to it was not.</li>
     *   <li><b>Gear that fits the body.</b> V369's ceiling, read with the same {@code body_size_rank()}: a collar
     *       harness is not a thing you put on the neck of an ox.</li>
     * </ul>
     *
     * @param easy whether this is easy draft ground; rough going strains a geared beast half again as hard too
     */
    private static String gearOnBeast(boolean easy) {
        String cost = easy ? "g.eases_fatigue_to" : "((g.eases_fatigue_to * 3) / 2)";
        return "(SELECT MIN(" + cost + ") FROM draft_gear g " +
               " WHERE body_size_rank(g.fits_up_to_size) >= body_size_rank(" +
               "         (SELECT ws5.size_tier FROM wildlife_population wp5 JOIN wildlife_species ws5 ON ws5.species_key=wp5.species_key " +
               "           WHERE wp5.id=wb.population_id)) " +
               "   AND (SELECT count(*) FROM item_instance hi JOIN world_object hw ON hw.id=hi.object_id " +
               "         WHERE hi.item_key=g.item_key AND hw.current_owner_id=wb.chronicle_id " +
               "           AND hw.lifecycle_state='ACTIVE' AND hi.condition_state <> 'BROKEN') " +
               "     >= (SELECT count(*) FROM wildlife_bond h3 JOIN wildlife_population p3 ON p3.id=h3.population_id " +
               "          JOIN draft_species d3 ON d3.species_key=p3.species_key " +
               "          WHERE h3.chronicle_id=wb.chronicle_id AND h3.bond_stage='TAMED' AND h3.id <= wb.id " +
               "            AND body_size_rank(g.fits_up_to_size) >= body_size_rank(" +
               "                  (SELECT ws6.size_tier FROM wildlife_species ws6 WHERE ws6.species_key=p3.species_key))))";
    }

    /** Rest the Chronicle's draft beasts (#101): a spell of rest or sleep lets every bonded beast recover some fatigue,
     *  down to fresh. The beast rests where its handler rests. */
    @Transactional
    public void restDraftBeasts(UUID chronicle) {
        jdbc.update("UPDATE wildlife_bond SET draft_fatigue = GREATEST(0, draft_fatigue - ?) WHERE chronicle_id=? AND draft_fatigue > 0",
            DRAFT_REST_RECOVERY, chronicle);
    }

    /** How much a penned beast recovers each turn of the world (#100/#108). */
    private static final int PEN_REST_RECOVERY = 25;

    /** Rest the draft beasts of any keeper who has somewhere at hand to keep them (#108): each turn of the world, a
     *  beast whose keeper stands where a stock shelter stands recovers draft-fatigue even as the keeper works — so a
     *  pen keeps the draft team fresh.
     *
     *  <p>Which structures those are is <b>data</b>, not a list written here (V293). This named three literals —
     *  ANIMAL_PEN, HITCHING_POST, TETHER_LINE — while the catalogue grew the animal houses a keeper would actually
     *  raise: a cattle byre, a goat fold, a pig sty, a poultry coop, a timber barn, every one of them buildable
     *  today from a verified assembly. None of them rested anything. A byre exists to be the place an ox stands
     *  overnight, and it was scenery. {@code construction_kind.shelters_stock} is now the question, so raising one
     *  more animal house is a data row rather than another literal to remember.
     *
     *  <p>A ruined shelter holds nothing, so integrity is required here as it is everywhere else stock are kept.
     *  Set-based over the whole world; runs in the tick. */
    @Transactional
    public void restPennedDraftBeasts(Instant now) {
        jdbc.update(
            "UPDATE wildlife_bond wb SET draft_fatigue = GREATEST(0, draft_fatigue - ?) " +
            "WHERE wb.draft_fatigue > 0 AND EXISTS (" +
            "  SELECT 1 FROM world_object cw JOIN construction_project cp ON cp.state='COMPLETED' AND cp.integrity_percent > 0 " +
            "    AND EXISTS(SELECT 1 FROM construction_kind ck WHERE ck.project_kind=cp.project_kind AND ck.shelters_stock) " +
            "  JOIN world_object pw ON pw.id=cp.object_id AND pw.lifecycle_state='ACTIVE' AND pw.current_location_id=cw.current_location_id " +
            "  WHERE cw.id=wb.chronicle_id)", PEN_REST_RECOVERY);
    }

    private static final int DRAFT_THIRST_PER_TURN = 4;   // a beast dries out faster than it grows hungry
    private static final int DRAFT_WATER_RELIEF    = 40;  // reaching water settles it quickly

    /**
     * Turn of the world for draft thirst (V267). wildlife_bond tracked fatigue, conditioning and hunger but nothing
     * for water, so an animal never needed a drink and every watering structure was inert by construction. Thirst
     * mirrors hunger: it rises as the world turns, and falls where the keeper's ground actually holds water — wet
     * ground, a freshwater site, or a built trough or catchment. Stock kept on dry ground must have water brought to
     * them, and a thirsty beast hauls less (the haul formula weighs thirst alongside fatigue and hunger).
     */
    @Transactional
    public void advanceDraftThirst(Instant now) {
        jdbc.update(
            "UPDATE wildlife_bond wb SET draft_thirst = CASE WHEN EXISTS (" +
            "  SELECT 1 FROM world_object cw JOIN world_chunk ch ON ch.id=cw.current_location_id WHERE cw.id=wb.chronicle_id AND (" +
            "     ch.biome IN ('WETLAND','RIVER_BANK') " +
            "     OR EXISTS(SELECT 1 FROM ecology_site es WHERE es.chunk_id=ch.id AND (" + com.devosphere.draugr.ecology.FreshWater.sites("es") + ")) " +
            "     OR EXISTS(SELECT 1 FROM construction_project cp JOIN world_object tw ON tw.id=cp.object_id " +
            "               WHERE cp.project_kind IN ('WATERING_STATION','RAINWATER_CATCHMENT') AND cp.state='COMPLETED' AND cp.integrity_percent>0 " +
            "                 AND tw.lifecycle_state='ACTIVE' AND tw.current_location_id=ch.id))) " +
            "  THEN GREATEST(0, draft_thirst - ?) ELSE LEAST(100, draft_thirst + ? + " + heatOnStock() + ") END " +
            // Every kept animal (#122), for the same reason as hunger above: a beast that cannot be thirsty makes
            // every trough, catchment and watering station beside it decoration for all but eleven species.
            "WHERE wb.bond_stage='TAMED'",
            DRAFT_WATER_RELIEF, DRAFT_THIRST_PER_TURN);
    }

    /** Above this, the day is hot enough that stock are working to shed heat rather than just standing in it. */
    private static final int HOT_ENOUGH_TO_TELL_C = 26;
    /** What a hot day adds to a beast's thirst, on top of the ordinary turn. */
    private static final int HEAT_THIRST = 5;

    /**
     * What the heat adds to a beast's thirst (#108/V303) — a SQL fragment, because it belongs inside the single
     * set-based thirst statement rather than in a second pass over the same rows.
     *
     * <p>Thirst has risen by a flat amount in every weather there is since V267: a buffalo in a July heatwave
     * dried out at exactly the same rate as a reindeer in a cold drizzle. Weather is the one thing that decides
     * how much water an animal needs, and it was the one thing the rule did not look at. That is also why #108's
     * shade shelter and wallows were blocked — both are answers to heat, and there was no heat.
     *
     * <p><b>Two answers, and they are not interchangeable.</b> Most stock shed heat by sweating and panting and
     * want <em>shade</em>; a keeper with a byre has already solved it. <b>Pigs and buffalo cannot sweat</b> —
     * that is not a flourish, it is why both species wallow — so shade does nothing for them and only a
     * <em>wallow</em> will do. {@code needs_a_wallow} says which is which.
     */
    private static String heatOnStock() {
        return "(CASE WHEN EXISTS (" +
            "  SELECT 1 FROM world_object cw2 JOIN world_chunk ch2 ON ch2.id=cw2.current_location_id " +
            "  JOIN world_weather ww ON ww.world_id=ch2.world_id " +
            "  WHERE cw2.id=wb.chronicle_id AND ww.ambient_temperature_c >= " + HOT_ENOUGH_TO_TELL_C +
            // Relief from the heat, and which relief depends on the animal rather than on what is cheapest to build.
            "    AND NOT EXISTS (SELECT 1 FROM construction_project cp2 JOIN world_object sw2 ON sw2.id=cp2.object_id " +
            "                    JOIN construction_kind ck2 ON ck2.project_kind=cp2.project_kind " +
            "                    JOIN wildlife_population wp2 ON wp2.id=wb.population_id " +
            "                    JOIN wildlife_species ws2 ON ws2.species_key=wp2.species_key " +
            "                    WHERE cp2.state='COMPLETED' AND cp2.integrity_percent>0 AND sw2.lifecycle_state='ACTIVE' " +
            "                      AND sw2.current_location_id=ch2.id " +
            "                      AND (CASE WHEN ws2.needs_a_wallow THEN ck2.is_wallow ELSE ck2.gives_shade END))" +
            ") THEN " + HEAT_THIRST + " ELSE 0 END)";
    }

    private static final int LIVESTOCK_FOULING_PER_TURN = 4; // kept stock foul the ground they stand on

    /**
     * Foul the ground where tamed draft stock are kept (#106). Beasts stood at a camp produced nothing at all before
     * this: the ground never grew foul however long stock were kept on it, which is the one certainty of keeping
     * animals. A completed <b>manure pit</b> or <b>compost bay</b> at that ground CONTAINS the muck, so it is gathered
     * rather than trodden through camp, and the ground stays clean.
     *
     * <p>Refuse is already wired to real consequences — it draws wildlife, costs the body condition, and lets pests
     * dock the shelf life of stored food — so keeping stock without mucking out now carries those costs. MAINTAIN_CAMP
     * still clears refuse, so a fouled camp is always recoverable. Grouped by chunk so a keeper with several beasts
     * fouls the ground once per turn, not once per animal. Run in the world tick.
     */
    @Transactional
    public void foulGroundWithLivestock(Instant now) {
        java.sql.Timestamp ts = java.sql.Timestamp.from(now);
        jdbc.update(
            "INSERT INTO chunk_refuse (chunk_id, refuse_level, last_updated_at) " +
            "SELECT cw.current_location_id, LEAST(100, ?), ? FROM wildlife_bond wb " +
            "JOIN world_object cw ON cw.id = wb.chronicle_id " +
            "WHERE wb.bond_stage='TAMED' AND cw.current_location_id IS NOT NULL " +
            "AND EXISTS (SELECT 1 FROM wildlife_population wp JOIN draft_species ds ON ds.species_key=wp.species_key WHERE wp.id=wb.population_id) " +
            "AND NOT EXISTS (SELECT 1 FROM construction_project cp JOIN world_object mw ON mw.id=cp.object_id " +
            "                WHERE cp.project_kind IN ('MANURE_PIT','COMPOST_BAY') AND cp.state='COMPLETED' AND cp.integrity_percent>0 " +
            "                  AND mw.lifecycle_state='ACTIVE' AND mw.current_location_id=cw.current_location_id) " +
            "GROUP BY cw.current_location_id " +
            "ON CONFLICT (chunk_id) DO UPDATE SET refuse_level = LEAST(100, chunk_refuse.refuse_level + ?), last_updated_at = ?",
            LIVESTOCK_FOULING_PER_TURN, ts, LIVESTOCK_FOULING_PER_TURN, ts);
    }

    /** A beast in this state is not thriving, and stock that are not thriving do not breed. */
    /** Above this in hunger, thirst or fatigue, a beast is not in condition — to conceive (#100) or to give (#122). */
    public static final int NOT_IN_CONDITION = 60;

    /**
     * Turn of the world for breeding (#52/#79/#108): kept stock in good condition get in calf, carry, give birth,
     * and their young grow up into stock of their own.
     *
     * <p>Everything about keeping animals existed except the one thing that makes it husbandry rather than
     * ownership. A keeper could tame a goat, feed it, water it, rest it in a byre, muck out after it, milk it and
     * shear it — and the number of goats in the world would never change except downward. A herd was a fixed
     * count to draw down and could never be built.
     *
     * <p>Three conditions, and each is something a keeper does rather than a die roll:
     * <ul>
     *   <li><b>Two of a kind.</b> Breeding needs two tamed animals of the species. Sex is deliberately not
     *       modelled (see V296), and two is the honest floor of what a herd requires without claiming to know
     *       which is which.</li>
     *   <li><b>Condition.</b> A beast that is hungry, thirsty or worked to exhaustion does not conceive. This is
     *       the first thing in the simulation that makes feeding and watering matter beyond haulage.</li>
     *   <li><b>Somewhere to be kept.</b> A completed, intact stock shelter on the ground (V293's
     *       {@code shelters_stock}) — the byre, fold, sty, coop or barn a keeper raised. Stock scattered on open
     *       ground do not settle to breed, which is the oldest reason to build a pen.</li>
     * </ul>
     *
     * <p>And a recovery period after birth, because a dam worked straight back into calf is how a herd is ruined.
     * Set-based over the whole world; runs in the tick.
     */
    @Transactional
    public void advanceBreeding(Instant now) {
        java.sql.Timestamp ts = java.sql.Timestamp.from(now);

        // 1. Conceive. One pregnancy per bond, so the primary key is the guard against a beast carrying twice.
        jdbc.update(
            "INSERT INTO tamed_gestation (bond_id, species_key, conceived_at, due_at) " +
            // Every timestamp parameter in interval arithmetic is cast explicitly. A bare `?` beside
            // make_interval gives the driver nothing to infer from, and Postgres resolves it as an interval —
            // "operator does not exist: timestamp with time zone <= interval", at runtime, in every test that
            // ticks the world. A PREPARE with a declared parameter type will not reproduce it, because the
            // declaration is exactly the thing JDBC does not supply.
            "SELECT wb.id, wp.species_key, ?::timestamptz, ?::timestamptz + make_interval(hours => bp.gestation_hours) " +
            "FROM wildlife_bond wb " +
            "JOIN wildlife_population wp ON wp.id = wb.population_id " +
            "JOIN breeding_profile bp ON bp.species_key = wp.species_key " +
            "JOIN wildlife_species ws ON ws.species_key = wp.species_key " +
            "JOIN world_object cw ON cw.id = wb.chronicle_id " +
            "WHERE wb.bond_stage = 'TAMED' " +
            // Condition, and health (V299). A sick animal does not get in calf — the same rule as hunger and
            // thirst, and the second thing that makes looking after stock matter rather than merely owning them.
            "  AND wb.draft_hunger < ? AND wb.draft_thirst < ? AND wb.draft_fatigue < ? AND wb.sickness < ? " +
            "  AND (wb.last_birth_at IS NULL OR wb.last_birth_at <= ?::timestamptz - make_interval(hours => bp.recovery_hours)) " +
            // Two of a kind, counted among this keeper's own tamed stock.
            "  AND (SELECT COUNT(*) FROM wildlife_bond o JOIN wildlife_population op ON op.id = o.population_id " +
            "        WHERE o.chronicle_id = wb.chronicle_id AND o.bond_stage = 'TAMED' AND op.species_key = wp.species_key) >= 2 " +
            // Somewhere to be kept: a completed, intact stock shelter on the keeper's ground.
            "  AND EXISTS (SELECT 1 FROM construction_project cp JOIN world_object sw ON sw.id = cp.object_id " +
            "              JOIN construction_kind ck ON ck.project_kind = cp.project_kind AND ck.shelters_stock " +
            "              WHERE cp.state = 'COMPLETED' AND cp.integrity_percent > 0 " +
            "                AND sw.lifecycle_state = 'ACTIVE' AND sw.current_location_id = cw.current_location_id " +
            // And it is a shelter these bodies fit in (#108, V369). A hen house standing on the ground was what
            // let a keeper's aurochs settle to breed, because shelters_stock meant "animals, any of them".
            "                AND body_size_rank(ck.shelters_up_to_size) >= body_size_rank(ws.size_tier)) " +
            "ON CONFLICT (bond_id) DO NOTHING",
            ts, ts, NOT_IN_CONDITION, NOT_IN_CONDITION, NOT_IN_CONDITION, TOO_SICK_TO_GIVE, ts);

        // 2. Give birth. The litter size is deterministic per pregnancy rather than random, so a save resumed
        //    twice does not produce two different herds — the bond id and the hour it was conceived decide it.
        //
        //    Not all of them live (V298). Perinatal loss is the largest single loss in keeping stock, far larger
        //    than winter, and it is the one thing a purpose-built birthing house is for — which is what stops
        //    #108's farrowing shelter being a fourth name for a byre. Three tiers, each of them something the
        //    keeper built:
        //
        //        open ground             -> the full loss the species carries
        //        a roofed stock shelter  -> half of it
        //        a birthing house        -> none
        //
        //    Rolled per ANIMAL, from the pregnancy and its index in the litter, so a resumed save loses the same
        //    young. Per animal rather than per litter because a cow carries one, and a percentage of a litter of
        //    one rounds to nothing — a foaling stall that helped every species except horses would be absurd.
        //    Each tier is sized to the animal being born (#108, V369). A brooder shelter is a warmed box for
        //    day-old chicks, and it was reading as a birthing house for a water buffalo — perinatal loss zero,
        //    from a structure the dam could not have got her head into.
        String bodyBeingBorn =
            "body_size_rank((SELECT ws2.size_tier FROM wildlife_species ws2 WHERE ws2.species_key = tg.species_key))";
        String birthingHouseHere =
            "EXISTS (SELECT 1 FROM construction_project cp JOIN world_object sw ON sw.id = cp.object_id " +
            "        JOIN construction_kind ck ON ck.project_kind = cp.project_kind " +
            "        WHERE ck.shelters_birth AND cp.state='COMPLETED' AND cp.integrity_percent > 0 " +
            "          AND sw.lifecycle_state='ACTIVE' AND sw.current_location_id = cw.current_location_id " +
            "          AND body_size_rank(ck.shelters_up_to_size) >= " + bodyBeingBorn + ")";
        String roofOverStockHere =
            "EXISTS (SELECT 1 FROM construction_project cp JOIN world_object sw ON sw.id = cp.object_id " +
            "        JOIN construction_kind ck ON ck.project_kind = cp.project_kind " +
            "        WHERE ck.shelters_stock AND ck.encloses AND cp.state='COMPLETED' AND cp.integrity_percent > 0 " +
            "          AND sw.lifecycle_state='ACTIVE' AND sw.current_location_id = cw.current_location_id " +
            "          AND body_size_rank(ck.shelters_up_to_size) >= " + bodyBeingBorn + ")";
        jdbc.update(
            "INSERT INTO tamed_young (bond_id, species_key, born_at, matures_at) " +
            "SELECT tg.bond_id, tg.species_key, tg.due_at, tg.due_at + make_interval(hours => bp.maturity_hours) " +
            "FROM tamed_gestation tg " +
            "JOIN breeding_profile bp ON bp.species_key = tg.species_key " +
            "JOIN wildlife_bond wb ON wb.id = tg.bond_id " +
            "JOIN world_object cw ON cw.id = wb.chronicle_id " +
            "CROSS JOIN LATERAL generate_series(1, bp.litter_min + " +
            "  (('x' || substr(md5(tg.bond_id::text || tg.conceived_at::text), 1, 8))::bit(32)::bigint " +
            "   % (bp.litter_max - bp.litter_min + 1))::int) AS n " +
            "WHERE tg.due_at <= ?::timestamptz " +
            "  AND (('x' || substr(md5(tg.bond_id::text || tg.conceived_at::text || n::text), 1, 8))::bit(32)::bigint % 100) >= " +
            "      CASE WHEN " + birthingHouseHere + " THEN 0 " +
            "           WHEN " + roofOverStockHere + " THEN bp.birth_loss_percent / 2 " +
            "           ELSE bp.birth_loss_percent END", ts);
        jdbc.update("UPDATE wildlife_bond SET last_birth_at = tg.due_at FROM tamed_gestation tg " +
                    "WHERE tg.bond_id = wildlife_bond.id AND tg.due_at <= ?", ts);
        jdbc.update("DELETE FROM tamed_gestation WHERE due_at <= ?", ts);

        // 3. Grow up. A matured animal JOINS THE HERD — its parent's population, which is what a herd is in this
        //    model: one bond is a Chronicle's relationship with a population, not with an individual beast.
        //
        //    An earlier draft gave each matured animal a bond of its own and could not have worked: wildlife_bond
        //    is UNIQUE (chronicle_id, population_id), so a keeper holds exactly one bond per population, and
        //    wildlife_population is UNIQUE (site_id), so a second population would have needed a second ecology
        //    site — a new "site" in the world for every kid born, which perception reads and would have littered
        //    the ground with phantom habitats. The herd is the count. That is what the schema already says.
        jdbc.update(
            "UPDATE wildlife_population wp SET population_count = wp.population_count + grown.n " +
            "FROM (SELECT parent.population_id AS pid, COUNT(*) AS n FROM tamed_young ty " +
            "      JOIN wildlife_bond parent ON parent.id = ty.bond_id " +
            "      WHERE ty.matures_at <= ? GROUP BY parent.population_id) grown " +
            "WHERE wp.id = grown.pid", ts);
        // Carrying capacity is what the ground will support. A herd grown past it is a herd outgrowing its
        // pasture, and the ecology simulation already knows what to do about that — this only makes sure the
        // ceiling rises with a herd a keeper has deliberately built, rather than capping it at the wild number.
        jdbc.update(
            "UPDATE wildlife_population SET carrying_capacity = GREATEST(carrying_capacity, population_count) " +
            "WHERE population_count > carrying_capacity");
        jdbc.update("DELETE FROM tamed_young WHERE matures_at <= ?", ts);
    }

    /** What a day does to a coat nobody touches, and what it does to one standing in its own filth (#106). */
    public static final int COAT_WEARS = 2, COAT_WEARS_IN_FILTH = 7;
    /** Below this a coat is matted and verminous: it makes an animal ill, and its fleece is not worth taking. */
    public static final int COAT_IS_MATTED = 40;
    /** What one working-over with a comb puts back, and what it takes out of an animal's sickness. */
    public static final int GROOMING_PUTS_BACK = 30, GROOMING_RELIEF = 2;
    /** A coat already this good has nothing in it worth a Chronicle's half-hour. */
    private static final int COAT_IS_CLEAN = 95;
    /** What a comb is: anything in the catalogue that is made for combing out a coat. */
    private static final String[] COMBS = {"bone_comb", "wooden_comb", "antler_comb"};

    /** What tending a beast with a herbal remedy takes out of its sickness. */
    private static final int TENDING_RELIEF = 35;

    /**
     * Tend a sick animal (#106/#108).
     *
     * <p>V299 gave stock sickness and two ways out of it: clean ground, and time. Both are things a keeper does
     * to the GROUND. There was nothing a keeper could do to the ANIMAL — no tending, no dosing, no sitting up
     * with it — which is why #106's grooming-and-health group (`animal_first_aid_roll`, `animal_bandage`,
     * `hoof_wrap`, `curry_comb` and the rest) had nothing to hang on.
     *
     * <p>This closes that loop with the medicine the catalogue already carries: a herbal poultice, an infusion,
     * or a bundle of dried herbs. No new item — those exist, they are already made by a Chronicle who has learned
     * to, and until now the only patient they had was the Chronicle themselves.
     *
     * <p>Takes the worst-off animal first, which is what a keeper walking into a byre actually does. The remedy
     * is consumed, so tending a herd through a bad spell costs what it should.
     */
    @Transactional
    public String[] tendSickAnimal(UUID chronicle, Instant at) {
        java.util.Map<String,Object> patient = jdbc.query(
            "SELECT wb.id, wp.species_key, wb.sickness FROM wildlife_bond wb " +
            "JOIN wildlife_population wp ON wp.id = wb.population_id " +
            "WHERE wb.chronicle_id = ? AND wb.bond_stage = 'TAMED' AND wb.sickness > 0 " +
            "ORDER BY wb.sickness DESC LIMIT 1 FOR UPDATE OF wb",
            rs -> rs.next() ? java.util.Map.of("id", rs.getObject(1, UUID.class),
                    "species", rs.getString(2), "sickness", rs.getInt(3)) : null, chronicle);
        if (patient == null)
            return new String[]{"FAILED", "You go among the stock looking for one that needs tending, and find none that is ailing."};

        String remedy = hasAtLeast(chronicle, "herbal_poultice", 1) ? "herbal_poultice"
                      : hasAtLeast(chronicle, "herbal_infusion", 1) ? "herbal_infusion"
                      : hasAtLeast(chronicle, "dried_herb_bundle", 1) ? "dried_herb_bundle" : null;
        if (remedy == null)
            return new String[]{"FAILED", "You have nothing to treat it with — no poultice, no infusion, nothing dried and put by."};

        consumeOne(chronicle, remedy, at);
        jdbc.update("UPDATE wildlife_bond SET sickness = GREATEST(0, sickness - ?) WHERE id = ?", TENDING_RELIEF, patient.get("id"));
        int now = Math.max(0, (Integer) patient.get("sickness") - TENDING_RELIEF);
        String beast = ((String) patient.get("species")).replace('_', ' ');
        return new String[]{"SUCCEEDED", now == 0
            ? "You work the " + remedy.replace('_', ' ') + " into the " + beast + " and stay with it a while. By the end of it the animal is standing easy again."
            : "You dose the " + beast + " with the " + remedy.replace('_', ' ') + " and stay with it a while. It is not well yet, but it is better than it was."};
    }

    /** A beast this tired, hungry, thirsty or ill will not carry anyone. */
    private static final int UNFIT_TO_CARRY = 70;
    /** Carrying this share of what you can bear makes getting up onto a tall animal the hard part. */
    private static final double LADEN_ENOUGH_TO_NEED_A_LEG_UP = 0.55;
    /** What a journey on horseback takes out of the animal, per chunk crossed. */
    private static final int RIDDEN_FATIGUE_PER_DISTANCE = 6;

    /**
     * The beast a Chronicle would ride if they set out now, or null if they would walk (#108/#106/#100).
     *
     * <p>Eight species pull, and <b>nothing in the world could be ridden</b> — a horse and an ox were the same
     * animal to this simulation, a number of grams. Riding is not a convenience; it is the reason a horse was
     * worth more than the meat on it, and its absence is what left #108's mounting block and the tack half of
     * #106 blocked.
     *
     * <p><b>Riding is not a state here.</b> There is no mount action, no dismount, no saddle to lose track of.
     * It is how you travel: set out with a beast that can be ridden and something to guide it by, and you ride.
     * A Chronicle who does not qualify walks, and is told nothing about it, because walking is not a failure.
     *
     * <p>Four things decide it, and each is something the keeper did:
     * <ul>
     *   <li>a TAMED beast of a species people actually rode ({@code draft_species.rideable});</li>
     *   <li>that beast fit to carry — not worked out, starved, parched or ill;</li>
     *   <li>a harness to guide it by, which the catalogue already has;</li>
     *   <li>and a way up, if the handler is laden: a mounting block, or a light enough load.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public UUID beastToRide(UUID chronicle, UUID at) {
        if (!hasAtLeast(chronicle, "draft_harness", 1) && !hasAtLeast(chronicle, "rope_harness", 1)) return null;

        UUID beast = jdbc.query(
            "SELECT wb.id FROM wildlife_bond wb " +
            "JOIN wildlife_population wp ON wp.id = wb.population_id " +
            "JOIN draft_species ds ON ds.species_key = wp.species_key AND ds.rideable " +
            "WHERE wb.chronicle_id = ? AND wb.bond_stage = 'TAMED' AND wp.population_count > 0 " +
            "  AND wb.draft_fatigue < ? AND wb.draft_hunger < ? AND wb.draft_thirst < ? AND wb.sickness < ? " +
            "ORDER BY wb.draft_fatigue LIMIT 1",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
            chronicle, UNFIT_TO_CARRY, UNFIT_TO_CARRY, UNFIT_TO_CARRY, TOO_SICK_TO_GIVE);
        if (beast == null) return null;

        // Getting up onto a tall animal while carrying a load is the part that actually stops people, and a
        // block is the oldest answer to it. Under that load it does not arise.
        LoadState load = currentLoad(chronicle);
        boolean laden = load.sustainedMassCapacityGrams() > 0
            && (double) load.massGrams() / load.sustainedMassCapacityGrams() >= LADEN_ENOUGH_TO_NEED_A_LEG_UP;
        if (!laden) return beast;

        boolean somethingToClimbFrom = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object w ON w.id = cp.object_id " +
            "JOIN construction_kind ck ON ck.project_kind = cp.project_kind " +
            "WHERE ck.aids_mounting AND cp.state='COMPLETED' AND cp.integrity_percent > 0 " +
            "  AND w.lifecycle_state='ACTIVE' AND w.current_location_id = ?)", Boolean.class, at));
        return somethingToClimbFrom ? beast : null;
    }

    /**
     * The journey's cost, moved rather than removed: it tires the ANIMAL.
     *
     * <p>Draft fatigue already gates haulage, so a horse ridden hard all week is a horse that cannot pull — and
     * a keeper who rides everywhere finds their draft team useless exactly when they need it. That is the trade
     * real keepers made, and it costs no new state to express.
     */
    @Transactional
    public void tireRiddenBeast(UUID bond, int distance) {
        jdbc.update("UPDATE wildlife_bond SET draft_fatigue = LEAST(100, draft_fatigue + ?), " +
                    "  draft_conditioning = LEAST(100, draft_conditioning + 2) WHERE id = ?",
            Math.max(1, distance) * RIDDEN_FATIGUE_PER_DISTANCE, bond);
    }

    /** How long a vehicle stands out in the open before the weather takes a step out of it. */
    private static final int GEAR_WEATHER_HOURS = 240;   // ten days of rain on unprotected timber

    /**
     * A cart left in the rain (#108/#100).
     *
     * <p>Metal rusts, hides rot, unfired pottery slakes back to mud — and a wooden cart left standing in the open
     * through a winter was exactly as good as the day it was built, for ever. It is the largest wooden thing a
     * Chronicle owns and the only one the weather could not touch.
     *
     * <p>What protects it is a roofed store the keeper already builds ({@code shelters_gear}, V300) — a tool
     * shed, a barn, a covered wood store. There is deliberately no cart shed: nothing in this model distinguishes
     * what will fit inside a building, so a cart shed would keep a cart dry exactly as a tool shed does under
     * another name.
     *
     * <p>Graded rather than fatal, on the {@code condition_state} ladder the catalogue already carries: SOUND to
     * WORN to BROKEN, and no further. REPAIR_ITEM takes it back up again with cordage, so a neglected cart is a
     * job to do rather than a thing lost. Only gear left ON THE GROUND weathers — what a Chronicle carries is
     * with them, not standing out in it.
     */
    @Transactional
    public void weatherExposedGear(Instant now) {
        java.sql.Timestamp ts = java.sql.Timestamp.from(now);
        java.util.List<java.util.Map<String,Object>> exposed = jdbc.queryForList(
            "SELECT i.object_id, i.weathered_at, i.condition_state FROM item_instance i " +
            "JOIN world_object w ON w.id = i.object_id " +
            "WHERE w.lifecycle_state='ACTIVE' AND w.current_owner_id IS NULL AND w.current_location_id IS NOT NULL " +
            "  AND i.item_key IN (SELECT item_key FROM draft_vehicle) " +
            "  AND i.condition_state IN ('SOUND','WORN') " +
            "  AND NOT EXISTS (SELECT 1 FROM construction_project cp JOIN world_object sw ON sw.id = cp.object_id " +
            "                  JOIN construction_kind ck ON ck.project_kind = cp.project_kind " +
            "                  WHERE ck.shelters_gear AND cp.state='COMPLETED' AND cp.integrity_percent > 0 " +
            "                    AND sw.lifecycle_state='ACTIVE' AND sw.current_location_id = w.current_location_id)");
        for (java.util.Map<String,Object> r : exposed) {
            UUID id = (UUID) r.get("object_id");
            java.sql.Timestamp since = (java.sql.Timestamp) r.get("weathered_at");
            if (since == null) { // first spell in the open: the clock starts, it does not bite yet
                jdbc.update("UPDATE item_instance SET weathered_at=? WHERE object_id=?", ts, id);
                continue;
            }
            if (java.time.Duration.between(since.toInstant(), now).toHours() < GEAR_WEATHER_HOURS) continue;
            String next = "SOUND".equals(r.get("condition_state")) ? "WORN" : "BROKEN";
            jdbc.update("UPDATE item_instance SET condition_state=?, weathered_at=? WHERE object_id=?", next, ts, id);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) " +
                        "VALUES (?,?,'WEATHERED',jsonb_build_object('to',?))", id, ts, next);
        }
        // Put away, or the rain stopped mattering: the spell breaks and the clock is cleared, so ten days under
        // cover and ten days out is not twenty days of weather.
        jdbc.update(
            "UPDATE item_instance i SET weathered_at = NULL FROM world_object w " +
            "WHERE w.id = i.object_id AND i.weathered_at IS NOT NULL " +
            "  AND i.item_key IN (SELECT item_key FROM draft_vehicle) " +
            "  AND (w.current_owner_id IS NOT NULL OR EXISTS (" +
            "        SELECT 1 FROM construction_project cp JOIN world_object sw ON sw.id = cp.object_id " +
            "        JOIN construction_kind ck ON ck.project_kind = cp.project_kind " +
            "        WHERE ck.shelters_gear AND cp.state='COMPLETED' AND cp.integrity_percent > 0 " +
            "          AND sw.lifecycle_state='ACTIVE' AND sw.current_location_id = w.current_location_id))");
    }

    /** The refuse level at which the ground a beast stands on starts to make it ill. */
    private static final int FOUL_GROUND_SICKENS = 40;
    private static final int SICKNESS_PER_TURN   = 5;   // standing in filth
    private static final int SICKNESS_SPREAD     = 4;   // caught from the rest of the keeper's stock
    private static final int SICKNESS_RECOVERY   = 7;   // clean ground, and the animal mends
    /** At or past this, an animal is too ill to give anything or to get in calf. */
    public  static final int TOO_SICK_TO_GIVE    = 50;

    /**
     * What spreads through a herd (#108/#52/#79).
     *
     * <p>Stock could be hungry, thirsty and worked to exhaustion, and their newborns could die of cold. They
     * could not get sick — which is why #108's quarantine pen and sick-animal shelter sat recorded as blocked for
     * three cycles: there was nothing to isolate an animal <em>from</em>.
     *
     * <p>It also left a hole in the middle of a loop that already runs. Kept stock have fouled the ground they
     * stand on since {@link #foulGroundWithLivestock}, and a manure pit or compost bay contains that muck. But the
     * cost was only ever paid by the Chronicle and their larder — refuse draws predators, costs body condition,
     * docks the shelf life of stored food — and <b>the animals standing in it were unaffected</b>, which is
     * exactly backwards. Filth is the oldest reason stock sicken and the oldest reason to muck out.
     *
     * <p>So this is not a new system bolted on; it is the missing consequence of one that already ran:
     * <em>stock foul the ground, the ground sickens the stock, the keeper mucks out or does not.</em>
     *
     * <p><b>It spreads</b> through a keeper's animals of the same species, and an isolation shelter is what stops
     * it — the whole of that structure's job, and why it is not a fourth pen. Set-based; runs in the tick.
     */
    @Transactional
    public void advanceHerdSickness(Instant now) {
        // Fouled ground underfoot, and nowhere to put the sick apart from the rest.
        String groundIsFoul =
            "EXISTS (SELECT 1 FROM chunk_refuse cr WHERE cr.chunk_id = cw.current_location_id AND cr.refuse_level >= ?)";
        // The isolation shelter is sized too (#108, V369): you cannot shut an ox in a brooder box and call the
        // rest of the herd safe. Correlated on wp, which is in scope in the statement that spreads it.
        String isolationHere =
            "EXISTS (SELECT 1 FROM construction_project cp JOIN world_object sw ON sw.id = cp.object_id " +
            "        JOIN construction_kind ck ON ck.project_kind = cp.project_kind " +
            "        WHERE ck.isolates_sick AND cp.state='COMPLETED' AND cp.integrity_percent > 0 " +
            "          AND sw.lifecycle_state='ACTIVE' AND sw.current_location_id = cw.current_location_id " +
            "          AND body_size_rank(ck.shelters_up_to_size) >= " +
            "              body_size_rank((SELECT ws4.size_tier FROM wildlife_species ws4 WHERE ws4.species_key = wp.species_key)))";

        // Standing in filth makes an animal ill; clean ground lets it mend. One statement so a beast cannot both
        // sicken and recover in the same turn. A matted coat is its own small illness and adds to it (#106).
        jdbc.update(
            "UPDATE wildlife_bond wb SET sickness = CASE WHEN " + groundIsFoul +
            "    THEN LEAST(100, wb.sickness + ? + CASE WHEN wb.coat_condition < ? THEN ? ELSE 0 END) " +
            "    ELSE GREATEST(0, wb.sickness - ? + CASE WHEN wb.coat_condition < ? THEN ? ELSE 0 END) END, " +
            "  coat_condition = GREATEST(0, wb.coat_condition - CASE WHEN " + groundIsFoul + " THEN ? ELSE ? END) " +
            "FROM world_object cw WHERE cw.id = wb.chronicle_id AND wb.bond_stage = 'TAMED'",
            FOUL_GROUND_SICKENS, SICKNESS_PER_TURN, COAT_IS_MATTED, MATTED_COAT_SICKENS,
            SICKNESS_RECOVERY, COAT_IS_MATTED, MATTED_COAT_SICKENS,
            FOUL_GROUND_SICKENS, COAT_WEARS_IN_FILTH, COAT_WEARS);

        // And it runs through the rest of the keeper's stock of the same kind — unless there is somewhere to put
        // the sick one. The shelter does not cure what is already in it; it stops the next animal catching it.
        jdbc.update(
            "UPDATE wildlife_bond wb SET sickness = LEAST(100, wb.sickness + ?) " +
            "FROM world_object cw, wildlife_population wp " +
            "WHERE cw.id = wb.chronicle_id AND wp.id = wb.population_id AND wb.bond_stage = 'TAMED' " +
            "  AND wb.sickness < ? " +
            "  AND NOT " + isolationHere +
            "  AND EXISTS (SELECT 1 FROM wildlife_bond o JOIN wildlife_population op ON op.id = o.population_id " +
            "              WHERE o.chronicle_id = wb.chronicle_id AND o.id <> wb.id AND o.bond_stage = 'TAMED' " +
            "                AND op.species_key = wp.species_key AND o.sickness >= ?)",
            SICKNESS_SPREAD, TOO_SICK_TO_GIVE, TOO_SICK_TO_GIVE);
    }

    /** What a verminous coat adds to an animal's illness each turn, or takes off its mending. */
    private static final int MATTED_COAT_SICKENS = 2;

    /** Hard cold: at or below freezing, where a young animal without a roof cannot keep its own heat. */
    private static final double HARD_COLD_C = 0.0;
    /** Three days of unbroken hard cold with nothing over them takes the young (#52/#108). */
    private static final int COLD_HOURS_THAT_KILL = 72;

    /**
     * Combing out a coat (#106): the one thing the grooming tools were named for and could not do.
     *
     * <p>A kept animal's coat is a real state that a keeper keeps. Left alone it mats, and faster where the ground
     * is foul; matted and verminous, the animal sickens more readily and its fleece is not worth the shearing.
     * Working it over with a comb puts most of that back and takes a little of the sickness with it — which is why
     * a comb in a keeper's pack is not an ornament.
     *
     * @return {outcome, narration}
     */
    @Transactional
    public String[] groomAnimal(UUID chronicle, Instant at) {
        UUID comb = null;
        for (String key : COMBS) { comb = findReachable(chronicle, key); if (comb != null) break; }
        if (comb == null)
            return new String[]{"FAILED", "You run a hand down the animal's flank and feel the matted hair under it. Fingers will not do this; it wants a comb."};
        Map<String, Object> worst = jdbc.query(
            "SELECT wb.id, wp.species_key, wb.coat_condition FROM wildlife_bond wb " +
            "JOIN wildlife_population wp ON wp.id = wb.population_id " +
            "JOIN wildlife_species ws ON ws.species_key = wp.species_key " +
            "WHERE wb.chronicle_id = ? AND wb.bond_stage = 'TAMED' AND ws.kingdom_class = 'MAMMALIA' " +
            "ORDER BY wb.coat_condition LIMIT 1 FOR UPDATE OF wb",
            rs -> rs.next() ? Map.of("id", rs.getObject(1, UUID.class), "species", rs.getString(2), "coat", rs.getInt(3)) : null, chronicle);
        if (worst == null)
            return new String[]{"FAILED", "You have nothing tamed here with a coat to comb out."};
        String beast = ((String) worst.get("species")).replace('_', ' ');
        int coat = (Integer) worst.get("coat");
        if (coat >= COAT_IS_CLEAN)
            return new String[]{"PARTIAL", "The " + beast + "'s coat is already clean and lying flat. You take a few passes down it anyway, and the comb comes away with almost nothing."};
        jdbc.update("UPDATE wildlife_bond SET coat_condition = LEAST(100, coat_condition + ?), sickness = GREATEST(0, sickness - ?) WHERE id = ?",
            GROOMING_PUTS_BACK, GROOMING_RELIEF, worst.get("id"));
        // The comb wears like any other tool in a hand doing work with it.
        Map<String, Object> tool = jdbc.queryForMap("SELECT i.item_key, i.use_count, i.condition_state FROM item_instance i WHERE i.object_id=?", comb);
        int uses = ((Number) tool.get("use_count")).intValue() + 1;
        int[] wears = toolWearThresholds((String) tool.get("item_key"));
        String was = (String) tool.get("condition_state");
        String now = uses >= wears[1] ? "BROKEN" : uses >= wears[0] ? "WORN" : was;
        jdbc.update("UPDATE item_instance SET use_count=?, condition_state=? WHERE object_id=?", uses, now, comb);
        if (!now.equals(was))
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'TOOL_WORN',jsonb_build_object('from',?,'to',?))",
                comb, Timestamp.from(at), was, now);
        return new String[]{"SUCCEEDED", coat < COAT_IS_MATTED
            ? "You work the comb down through the matted hair a handful at a time, and what comes out of it is dirt, old shed coat and things that move. "
              + "By the end the " + beast + " is leaning into it, and the skin underneath is no longer crawling."
            : "You comb the " + beast + " over from neck to flank. The coat comes up clean and lies flat, and the animal stands for all of it."};
    }

    /**
     * A hard winter takes the young (#52/#108).
     *
     * <p>V296 gave the world young animals and nothing could interrupt them: a kid born into a January night on
     * open ground reached maturity as reliably as one born in a byre in May. The young were a timer rather than
     * something a keeper keeps alive — which is also why #108's farrowing and brooder shelters could not honestly
     * be built, since a structure that reduces losses is meaningless while there are no losses.
     *
     * <p><b>What decides it was already in the catalogue.</b> {@code construction_kind.encloses} has meant "can a
     * Chronicle be inside this, out of the weather" since V280, and the stock shelters split cleanly along it: a
     * byre, a coop and a barn are buildings; a pen, a fold and a sty are fences. Until now those two kinds were
     * identical to a keeper — both rested a beast, both stood in a wolf's way — and the difference between walls
     * and hurdles meant nothing. It means this.
     *
     * <p><b>The shape of the loss is a deadline, not a dice game.</b> {@code cold_since} marks when an unbroken
     * hard spell began and is cleared the moment the animal is sheltered or the weather turns, so a keeper who
     * sees the frost coming and raises a byre saves them, and a keeper away for a week does not. Set-based over
     * the whole world; runs in the tick, after birth so a newborn is not judged before it exists.
     */
    @Transactional
    public void exposeYoungToTheCold(Instant now) {
        java.sql.Timestamp ts = java.sql.Timestamp.from(now);

        // Exposed: hard cold on the keeper's ground, and nothing roofed standing over the stock. The clock starts
        // once and is not restarted while the spell holds, which is what makes it a spell.
        String exposed =
            "SELECT ty.id FROM tamed_young ty " +
            "JOIN wildlife_bond wb ON wb.id = ty.bond_id " +
            "JOIN world_object cw ON cw.id = wb.chronicle_id " +
            "JOIN world_chunk ch ON ch.id = cw.current_location_id " +
            "JOIN world_weather ww ON ww.world_id = ch.world_id " +
            "WHERE ww.ambient_temperature_c <= ? " +
            "  AND NOT EXISTS (SELECT 1 FROM construction_project cp JOIN world_object sw ON sw.id = cp.object_id " +
            "                  JOIN construction_kind ck ON ck.project_kind = cp.project_kind " +
            "                  WHERE ck.shelters_stock AND ck.encloses AND cp.state = 'COMPLETED' " +
            "                    AND cp.integrity_percent > 0 AND sw.lifecycle_state = 'ACTIVE' " +
            "                    AND sw.current_location_id = ch.id " +
            // Sized to the kind it will grow into (#108, V369): a calf is small for an aurochs and is still not
            // going to spend a frost inside a hen house.
            "                    AND body_size_rank(ck.shelters_up_to_size) >= " +
            "                        body_size_rank((SELECT ws.size_tier FROM wildlife_species ws WHERE ws.species_key = ty.species_key)))";

        jdbc.update("UPDATE tamed_young SET cold_since = ?::timestamptz " +
                    "WHERE cold_since IS NULL AND id IN (" + exposed + ")", ts, HARD_COLD_C);

        // Sheltered again, or the thaw came. Either breaks the spell outright — three days of cold either side of
        // a warm week is not three days of cold.
        jdbc.update("UPDATE tamed_young SET cold_since = NULL " +
                    "WHERE cold_since IS NOT NULL AND id NOT IN (" + exposed + ")", HARD_COLD_C);

        jdbc.update("DELETE FROM tamed_young WHERE cold_since IS NOT NULL " +
                    "AND cold_since <= ?::timestamptz - make_interval(hours => ?)", ts, COLD_HOURS_THAT_KILL);
    }

    /**
     * A bull among the young (#108/V304).
     *
     * <p>V302 gave species a temperament, and a DANGEROUS animal hurts the keeper who works on it unrestrained.
     * It did not yet hurt anything else: a bull, a boar or a buffalo stood in the same fold as the kids and lambs
     * and was no more trouble to them than a goose. That is the last thing #108's boar pen was waiting on, and
     * why V302 deliberately did not build it — restraining an animal while you work and keeping it away from the
     * herd are two different jobs, and the second needs the herd to be able to come to harm.
     *
     * <p>Trampling and goring by breeding males is a real and ordinary loss in stock-keeping, and separating the
     * boar is the ordinary answer. The young are what is at risk, being the small and slow thing in a herd —
     * and already the fragile thing in this simulation, lost to cold (V297) and to a hard birth (V298).
     *
     * <p>Takes the <b>youngest</b> first, which is what actually happens, and takes one per turn rather than a
     * herd at a stroke: a keeper who notices has time to build the yard. Runs in the tick after the cold.
     */
    @Transactional
    public void dangerousStockAmongTheYoung(Instant now) {
        jdbc.update(
            "DELETE FROM tamed_young WHERE id IN (" +
            "  SELECT ty.id FROM tamed_young ty " +
            "  JOIN wildlife_bond wb ON wb.id = ty.bond_id " +
            "  JOIN world_object cw ON cw.id = wb.chronicle_id " +
            // A dangerous animal the same keeper holds, standing on the same ground as the young.
            "  WHERE EXISTS (SELECT 1 FROM wildlife_bond d " +
            "                JOIN wildlife_population dp ON dp.id = d.population_id " +
            "                JOIN wildlife_species ds ON ds.species_key = dp.species_key " +
            "                WHERE d.chronicle_id = wb.chronicle_id AND d.bond_stage = 'TAMED' " +
            "                  AND ds.temperament = 'DANGEROUS') " +
            // Unless there is somewhere to keep it apart.
            "    AND NOT EXISTS (SELECT 1 FROM construction_project cp JOIN world_object sw ON sw.id = cp.object_id " +
            "                    JOIN construction_kind ck ON ck.project_kind = cp.project_kind " +
            "                    WHERE ck.separates_dangerous AND cp.state='COMPLETED' AND cp.integrity_percent > 0 " +
            "                      AND sw.lifecycle_state='ACTIVE' AND sw.current_location_id = cw.current_location_id) " +
            "  ORDER BY ty.born_at DESC LIMIT 1)");
    }

    /** How many young this Chronicle is raising, and of what — for perception, not for working with. */
    @Transactional(readOnly = true)
    public int youngInCare(UUID chronicle) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM tamed_young ty JOIN wildlife_bond wb ON wb.id = ty.bond_id WHERE wb.chronicle_id = ?",
            Integer.class, chronicle);
        return n == null ? 0 : n;
    }

    private static final int DRAFT_HUNGER_PER_TURN = 3;   // a beast grows hungry as the world turns
    private static final int DRAFT_GRAZE_RELIEF   = 10;   // pasture feeds it faster than it hungers
    private static final int DRAFT_FEED_RELIEF     = 60;   // a bundle of fodder is a good feed
    private static final int DRAFT_FEED_RELIEF_RACKED = 90; // fed from a rack or dry store, far less is trampled and wasted

    /** Turn of the world for draft hunger (#104): every tamed draft beast grows a little hungrier, UNLESS its keeper is
     *  on grassland — there it grazes the pasture and stays fed. A beast on bare or wooded ground must be brought fodder.
     *  Set-based; runs in the tick. */
    @Transactional
    public void advanceDraftHunger(Instant now) {
        // The weather's cost is added in both branches (#106, V325): a beast on good pasture still burns feed to keep
        // warm in a frost, it just has more to burn.
        String cold = coldOnStock();
        jdbc.update(
            "UPDATE wildlife_bond wb SET draft_hunger = CASE " +
            "  WHEN EXISTS (SELECT 1 FROM world_object cw JOIN world_chunk ch ON ch.id=cw.current_location_id " +
            "               WHERE cw.id=wb.chronicle_id AND ch.biome='GRASSLAND') " +
            "  THEN LEAST(100, GREATEST(0, draft_hunger - ? + " + cold + ")) ELSE LEAST(100, draft_hunger + ? + " + cold + ") END " +
            // EVERY kept animal, not only the ones that pull (#122). Hunger rose only for a species in
            // draft_species, while feedDraftBeasts below relieves every TAMED bond with no such filter — so a
            // keeper could feed a hen that was constitutionally incapable of being hungry, and a milk goat went
            // through her whole life at nought. The relief was already universal; this is the other half of it.
            "WHERE wb.bond_stage='TAMED'",
            DRAFT_GRAZE_RELIEF, DRAFT_HUNGER_PER_TURN);
    }

    /** At or below this, a grown beast is burning feed to keep its own heat. */
    private static final int HARD_FROST_C = 0;
    /** Rain under this chills a wet coat through. */
    private static final int WET_CHILL_C = 8;
    /** What a hard frost adds to a beast's hunger, on top of the ordinary turn. */
    private static final int FROST_HUNGER = 4;
    /** What cold rain adds — less than a frost, because a beast's own coat still does some of the work. */
    private static final int WET_COLD_HUNGER = 2;

    /**
     * What the cold costs a grown beast (#106/#108, V325) — a SQL fragment over {@code wildlife_bond wb}, the hunger
     * counterpart of {@link #heatOnStock()}.
     *
     * <p>Heat has cost stock water since V303 and cold cost them nothing: a horse stood through a January night on an
     * open hill as fed as one in a byre, so a stock blanket had nothing to be for. A beast keeps warm by burning feed,
     * so cold is paid in hunger.
     *
     * <p><b>The answers are not interchangeable.</b> An enclosed stock shelter keeps off frost and rain both; a roof
     * that does not enclose keeps off only the rain. A winter blanket answers both; a rain sheet only the wet. What
     * counts as a cover is {@code stock_cover}, and a cover is worn by one beast: a keeper with two blankets and three
     * horses keeps two warm — the first two by bond, so the answer never depends on the order rows happen to arrive.
     */
    private static String coldOnStock() {
        String weatherHere =
            "SELECT 1 FROM world_object cw3 JOIN world_chunk ch3 ON ch3.id=cw3.current_location_id " +
            "JOIN world_weather ww3 ON ww3.world_id=ch3.world_id WHERE cw3.id=wb.chronicle_id ";
        String shelterHere =
            "SELECT 1 FROM construction_project cp3 JOIN world_object sw3 ON sw3.id=cp3.object_id " +
            "JOIN construction_kind ck3 ON ck3.project_kind=cp3.project_kind " +
            "WHERE cp3.state='COMPLETED' AND cp3.integrity_percent>0 AND sw3.lifecycle_state='ACTIVE' " +
            "AND sw3.current_location_id=ch3.id AND ck3.shelters_stock " +
            // A roof is only a roof for a beast that fits under it (#108, V369).
            "AND body_size_rank(ck3.shelters_up_to_size) >= body_size_rank(" +
            "  (SELECT ws3.size_tier FROM wildlife_population wp3 JOIN wildlife_species ws3 ON ws3.species_key=wp3.species_key " +
            "    WHERE wp3.id=wb.population_id)) ";
        return "(CASE WHEN EXISTS (" + weatherHere + "AND ww3.ambient_temperature_c <= " + HARD_FROST_C +
               " AND NOT EXISTS (" + shelterHere + "AND ck3.encloses) AND NOT " + coveredBy("against_hard_cold") +
               ") THEN " + FROST_HUNGER + " ELSE 0 END" +
               " + CASE WHEN EXISTS (" + weatherHere + "AND ww3.weather_kind IN ('RAIN','STORM') AND ww3.ambient_temperature_c < " + WET_CHILL_C +
               " AND NOT EXISTS (" + shelterHere + ") AND NOT " + coveredBy("against_wet_cold") +
               ") THEN " + WET_COLD_HUNGER + " ELSE 0 END)";
    }

    /** Whether this beast is among the ones the keeper has a sound cover of this kind for — one cover, one beast. */
    private static String coveredBy(String flag) {
        return "((SELECT count(*) FROM item_instance ci3 JOIN world_object co3 ON co3.id=ci3.object_id " +
               "  JOIN stock_cover sc3 ON sc3.item_key=ci3.item_key " +
               "  WHERE co3.current_owner_id=wb.chronicle_id AND co3.lifecycle_state='ACTIVE' " +
               "    AND ci3.condition_state <> 'BROKEN' AND sc3." + flag + ") " +
               " >= (SELECT count(*) FROM wildlife_bond b3 JOIN wildlife_population p3 ON p3.id=b3.population_id " +
               "  JOIN draft_species d3 ON d3.species_key=p3.species_key " +
               "  WHERE b3.chronicle_id=wb.chronicle_id AND b3.bond_stage='TAMED' AND b3.id <= wb.id))";
    }

    /** Feed the keeper's draft beasts a bundle of cut fodder (#104): consumes one dry grass bundle to ease the hunger of
     *  every tamed draft beast bonded to them. Wants a bundle to hand and a hungry beast to feed. */
    @Transactional
    public String[] feedDraftBeasts(UUID chronicle, Instant at) {
        // EVERY kept animal, not only the ones that pull (#106). The relief below never filtered on draft_species
        // and the hunger tick stopped doing so for #122 — "a milk goat went through her whole life at nought" is
        // the comment on it — but THIS gate still did. So a kept goat grew hungry every turn of the world, by a
        // tick that was fixed to include her, and "feed the goat" answered "none of your draft beasts is hungry"
        // while she stood there at thirty-five. Measured on a booted world; the gate was the last holdout.
        Integer hungry = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wildlife_bond wb WHERE wb.chronicle_id=? AND wb.bond_stage='TAMED' AND wb.draft_hunger>0",
            Integer.class, chronicle);
        if (hungry == null || hungry == 0) return new String[]{"FAILED", "None of your animals is hungry — there is nothing of yours to feed."};
        if (!hasAtLeast(chronicle, "dry_grass_bundle", 1))
            return new String[]{"FAILED", "You have no fodder to hand, and your beasts stay as hungry as they were."};
        consumeOne(chronicle, "dry_grass_bundle", at);
        // A hay rack or a dry fodder store makes the same bundle go much further (#106): fodder shaken out on bare
        // ground is trampled and soiled, while a rack holds it at muzzle height and a store keeps it sweet. The
        // structure never feeds the beasts by itself — the bundle is still cut, carried, and consumed here.
        boolean racked = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM world_object cw JOIN construction_project cp ON cp.project_kind IN ('HAY_RACK','FODDER_STORE') " +
            "AND cp.state='COMPLETED' AND cp.integrity_percent>0 " +
            "JOIN world_object rw ON rw.id=cp.object_id AND rw.lifecycle_state='ACTIVE' AND rw.current_location_id=cw.current_location_id " +
            "WHERE cw.id=?)", Boolean.class, chronicle));
        jdbc.update("UPDATE wildlife_bond SET draft_hunger = GREATEST(0, draft_hunger - ?) WHERE chronicle_id=? AND bond_stage='TAMED'",
            racked ? DRAFT_FEED_RELIEF_RACKED : DRAFT_FEED_RELIEF, chronicle);
        return new String[]{"SUCCEEDED", racked
            ? "You fill the rack and let the beasts work through it at their own height, and almost none of the fodder is trodden into the ground and lost."
            : "You shake out the bundle of dry grass and let the beasts feed, muzzles working through the fodder until the sharp edge of their hunger is off."};
    }

    // ---- water handling (#71) ----------------------------------------------------------------------------
    // soapstone_bowl was listed as fireproof but not as a water vessel, so a Chronicle could boil water in one and
    // yet not collect any into it — a vessel you can set on the flame must be able to hold what goes in it. The
    // folded bark cup is a drinking vessel by construction, and matters early: it holds water before there is any
    // pottery to hold it in. bark_scoop is deliberately left out — a scoop lifts water, it does not carry it.
    private static final String[] WATER_VESSELS = {"waterskin","wooden_bucket","clay_pot","clay_jar","fired_bowl","fired_cup","clay_water_filter","wooden_bowl","wooden_trough","soapstone_bowl","folded_bark_cup"};
    /** Whether the Chronicle carries anything that can hold water to fill or boil in (#71). */
    @Transactional(readOnly = true)
    public boolean hasWaterVessel(UUID chronicle) { for (String v : WATER_VESSELS) if (hasAtLeast(chronicle, v, 1)) return true; return false; }

    /**
     * Is there water here to work a process in — retting flax, leaching a mordant, soaking bast?
     *
     * <p>This gate accepted the single string {@code "WETLAND"} and nothing else, so the only place in the world
     * a Chronicle could ret flax was a marsh. Not a river. Not a spring. Not a stream, and not a rainwater
     * catchment they had built for exactly this. Every other reader of "is there water here" in the codebase
     * already knew about all four — this one had been left behind, and running water (#156) made the gap plain:
     * standing on a river bank is the most obvious place there is to ret flax, and the world said the ground was
     * dry. Kept deliberately in step with ChronicleActionService.waterInReach.
     */
    @Transactional(readOnly = true)
    /**
     * Salt water within reach (#157) — a different question from {@link #waterToWorkWith}, and deliberately not
     * folded into it. Retting, tanning, liming and the rest want fresh water and would be spoiled by brine, so the
     * freshwater gate rightly excludes the shore; the shore is exactly where this one must succeed.
     *
     * <p>Standing on the shore counts. So does standing on ground that touches open sea, because a Chronicle on a
     * sea cliff or a spit can reach the water below without the chunk itself being a beach. A salt marsh counts
     * too — it is brine at the surface, which is why the world places salt there.
     */
    public boolean saltWaterToWorkWith(UUID location) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM world_chunk c WHERE c.id=? AND c.biome='COAST') " +
            "    OR EXISTS(SELECT 1 FROM world_chunk here JOIN world_chunk sea ON sea.world_id=here.world_id " +
            "              AND sea.biome='OCEAN' AND abs(sea.grid_x-here.grid_x)<=1 AND abs(sea.grid_y-here.grid_y)<=1 " +
            "              WHERE here.id=?) " +
            "    OR EXISTS(SELECT 1 FROM ecology_site es WHERE es.chunk_id=? AND lower(es.site_kind) LIKE '%salt%')",
            Boolean.class, location, location, location));
    }

    public boolean waterToWorkWith(UUID location) {
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
        if ("WETLAND".equals(biome) || "RIVER_BANK".equals(biome)) return true;
        Integer sites = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ecology_site WHERE chunk_id=? AND (site_category='WATER' OR " + com.devosphere.draugr.ecology.FreshWater.sites() + ")",
            Integer.class, location);
        if (sites != null && sites > 0) return true;
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "WHERE w.current_location_id=? AND cp.project_kind IN ('RAINWATER_CATCHMENT','WATERING_STATION','WELL') " +
            "AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND w.lifecycle_state='ACTIVE')", Boolean.class, location));
    }
    /** Vessels that can sit on the flame without charring or melting — fired clay or soapstone (#125). Only these
     *  boil water directly; a wooden or hide vessel needs a boiling_stone_set instead. */
    private static final String[] FIREPROOF_VESSELS = {"clay_pot","clay_jar","fired_bowl","fired_cup","clay_water_filter","soapstone_bowl"};
    public boolean hasFireproofVessel(UUID chronicle) { for (String v : FIREPROOF_VESSELS) if (hasAtLeast(chronicle, v, 1)) return true; return false; }
    /** Create up to {@code count} units of a water kind, capped by carry room. Returns how many were made. */
    @Transactional
    public int makeWater(UUID chronicle, String key, String name, int count, Instant at) {
        int made = 0;
        for (int i = 0; i < count; i++) { if (capacityHeadroomUnits(chronicle, key) < 1) break; createCarriedItem(chronicle, key, name, at, "COLLECTED"); made++; }
        return made;
    }
    /** Convert up to {@code max} carried units of one water kind into another (raw→filtered, raw→boiled). */
    @Transactional
    public int convertWater(UUID chronicle, String fromKey, String toKey, String toName, int max, Instant at) {
        int n = 0;
        for (int i = 0; i < max; i++) { if (!hasAtLeast(chronicle, fromKey, 1) || !consumeOne(chronicle, fromKey, at)) break; createCarriedItem(chronicle, toKey, toName, at, "PROCESSED"); n++; }
        return n;
    }
    /** The safest water the Chronicle carries — boiled &gt; filtered &gt; raw — or null if none. */
    @Transactional(readOnly = true)
    public String bestWaterCarried(UUID chronicle) {
        if (hasAtLeast(chronicle, "clean_water", 1)) return "clean_water";
        if (hasAtLeast(chronicle, "filtered_water", 1)) return "filtered_water";
        if (hasAtLeast(chronicle, "raw_water", 1)) return "raw_water";
        return null;
    }

    /** The chunk the Chronicle currently stands in — where a too-heavy craft is set down. */
    private UUID chronicleLocation(UUID chronicle) {
        return jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
    }

    /** Non-throwing carry-capacity test — the same rule as {@link #assertCarryCapacity} without the exception. */
    @Transactional(readOnly = true)
    public boolean withinCarryCapacity(UUID chronicle) {
        LoadState s = loadState(chronicle);
        return s.massGrams() <= s.sustainedMassCapacityGrams()
            && s.bulkMl() <= s.directBulkCapacityMl()
            && s.heaviestObjectGrams() <= s.maximumSingleLiftGrams();
    }

    /**
     * Place a freshly made object: carried if the Chronicle can bear it, otherwise set on the ground
     * in front of them. A craft must never fail with a raw carry-capacity error (GitHub #19) — if your
     * arms and pack are full, the thing you just made lies at your feet to pick up or leave. Never
     * throws. Returns true if it ended up carried, false if it was set down in front.
     */
    public boolean createCraftedItem(UUID chronicle, UUID location, UUID id, String itemKey, String displayName, Instant occurredAt, String transitionType, QualityGrade grade) {
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM',?,?)", id, displayName, chronicle);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state,quality_grade) VALUES (?,?,'SOUND',?)", id, itemKey, grade.name());
        // If this kind is a container with a declared default capacity, give the new object its container
        // properties so it can actually hold things (#56). Process-made containers (bark_container, leather_pouch,
        // burden_basket, and the V75 batch) used to be created without this and so could store nothing.
        jdbc.update("INSERT INTO container_properties (object_id,max_mass_grams,max_volume_ml) SELECT ?,max_mass_grams,max_volume_ml FROM container_capacity_default WHERE item_key=? ON CONFLICT (object_id) DO NOTHING", id, itemKey);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,?,jsonb_build_object('itemKey',?))", id, Timestamp.from(occurredAt), transitionType, itemKey);
        if (withinCarryCapacity(chronicle)) return true;
        jdbc.update("UPDATE world_object SET current_owner_id=NULL, current_location_id=? WHERE id=?", location, id);
        return false;
    }

    /**
     * The worst quality grade among the chronicle's reachable items of the given keys
     * — the grade that will flow into anything made from them. SOUND when none of the
     * keys is actually held (an untracked or absent input does not drag quality down).
     */
    @Transactional(readOnly = true)
    public QualityGrade worstGradeAmong(UUID chronicle, java.util.Collection<String> itemKeys) {
        QualityGrade worst = null;
        UUID location = chronicleLocation(chronicle);
        for (String k : itemKeys) {
            String g = jdbc.query(REACHABLE_CTE +
                "SELECT i.quality_grade FROM reachable r JOIN item_instance i ON i.object_id=r.id WHERE i.item_key=? " +
                "ORDER BY CASE i.quality_grade WHEN 'DEFECTIVE' THEN 0 WHEN 'POOR' THEN 1 WHEN 'SOUND' THEN 2 ELSE 3 END LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null, chronicle, location, k);
            if (g != null) { QualityGrade q = QualityGrade.of(g); worst = worst == null ? q : QualityGrade.worst(worst, q); }
        }
        return worst == null ? QualityGrade.SOUND : worst;
    }
    /** The id of one reachable item of a kind — carried, in a carried container, or in an on-site store — or null. */
    @Transactional(readOnly = true)
    public UUID findReachable(UUID chronicle, String itemKey) {
        return jdbc.query(REACHABLE_CTE + "SELECT r.id FROM reachable r JOIN item_instance i ON i.object_id=r.id WHERE i.item_key=? ORDER BY r.id LIMIT 1",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, chronicle, chronicleLocation(chronicle), itemKey);
    }
    /** Strip a workable sheet of bark from a tree — a writing surface. Wooded terrain only. */
    @Transactional
    public int stripBark(UUID chronicle, UUID location, Instant occurredAt) {
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
        if (!"TEMPERATE_FOREST".equals(biome)) return 0; // No trees with workable bark here.
        if (capacityHeadroomUnits(chronicle, "bark_sheet") <= 0) return 0;
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM','Bark sheet',?)", id, chronicle);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,'bark_sheet','SOUND')", id);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'GATHERED',jsonb_build_object('material','bark_sheet'))", id, Timestamp.from(occurredAt));
        return 1;
    }
    /** Dig clay from wet earth. Yield scales with biome: CLAY_DEPOSIT > RIVER_BANK > WETLAND. Dry biomes yield nothing. */
    @Transactional
    public int gatherClay(UUID chronicle, UUID location, Instant occurredAt) { return gatherClay(chronicle, location, occurredAt, 0); }
    @Transactional
    public int gatherClay(UUID chronicle, UUID location, Instant occurredAt, int extra) {
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
        int yield = switch (biome != null ? biome : "") {
            case "CLAY_DEPOSIT" -> 3;
            case "RIVER_BANK"   -> 2;
            case "WETLAND"      -> 1;
            default -> 0;
        };
        if (yield == 0) return 0;
        int desired = Math.min(yield + extra, capacityHeadroomUnits(chronicle, "clay_lump"));
        if (desired <= 0) return 0;
        for (int i = 0; i < desired; i++) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM','Clay lump',?)", id, chronicle);
            jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,'clay_lump','SOUND')", id);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'GATHERED',jsonb_build_object('biome',?))", id, Timestamp.from(occurredAt), biome);
        }
        assertCarryCapacity(chronicle);
        return desired;
    }
    /** Split a flat stone slab from rock — a heavy, permanent writing surface. Stony highland terrain only. */
    @Transactional
    public int gatherStoneSlab(UUID chronicle, UUID location, Instant occurredAt) {
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
        if (!"MOUNTAIN".equals(biome) && !"HIGHLAND".equals(biome)) return 0; // No workable rock face here.
        if (capacityHeadroomUnits(chronicle, "stone_slab") <= 0) return 0;
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM','Stone slab',?)", id, chronicle);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,'stone_slab','SOUND')", id);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'GATHERED',jsonb_build_object('biome',?))", id, Timestamp.from(occurredAt), biome);
        assertCarryCapacity(chronicle);
        return 1;
    }
    /** Take a piece of charcoal from a spent fire — a writing implement. Requires a fire that was lit here and has since burned out. */
    @Transactional
    public boolean makeCharcoal(UUID chronicle, UUID location, Instant occurredAt) {
        // Charcoal comes from wood that has actually burned. A fire pit that was
        // built but never lit is just a ring of cold stone. Require a fire_state
        // row (the pit was lit at least once) that is no longer burning.
        Integer spent = jdbc.queryForObject("SELECT COUNT(*) FROM construction_project cp JOIN world_object w ON w.id=cp.object_id JOIN fire_state fs ON fs.construction_id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind IN (SELECT project_kind FROM construction_kind WHERE holds_fire) AND cp.state='COMPLETED' AND fs.active=false", Integer.class, location);
        if (spent == null || spent == 0) return false;
        if (capacityHeadroomUnits(chronicle, "charcoal") <= 0) return false;
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM','Charcoal',?)", id, chronicle);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,'charcoal','SOUND')", id);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'MADE_CHARCOAL','{}'::jsonb)", id, Timestamp.from(occurredAt));
        return true;
    }
    /**
     * Gather a plant from the flora_drop table — mushroom, herb, berry, root, or
     * any non-tree flora. The action text is used to infer the target species; if
     * unrecognised, the first matching flora in the chunk's biome is used.
     * Season gates and tool requirements are enforced. Returns [outcome, narration].
     */
    /** Things that grow, by every name the catalogue has for them. Kept for an hour: the flora catalogue does
     *  not change during a sitting, and this is asked of every action a Chronicle takes. */
    private volatile java.util.Set<String> growingThings = java.util.Set.of();
    private volatile Instant growingThingsRead = Instant.EPOCH;

    /**
     * Words a foraging intent must never take, because another intent already does the work properly: GRAIN is
     * the crop (HARVEST_CROP reaps it) and BARK is stripped from a standing tree (STRIP_BARK, with its tool gate).
     * A phrase containing one of these is left alone entirely, so "gather willow bark" still reaches the act that
     * knows how bark comes off a tree rather than becoming a handful of something else.
     */
    private static final java.util.Set<String> OWNED_ELSEWHERE = java.util.Set.of("grain", "bark");

    /**
     * Whether these words name something that grows (#37) — asked of the catalogue rather than of a list somebody
     * wrote out. The classifier's forage rule carried thirty nouns, and "gather some grass" was UNKNOWN because
     * grass was not among them, though the world grows meadow grass and it drops four different things.
     *
     * <p>The same shape as {@link #namesAKeptAnimal}, and for the same reason: the catalogue is the authority on
     * what the world contains, and a hand-list beside it is a second answer that drifts.
     */
    public boolean namesSomethingThatGrows(String text) {
        if (text == null || text.isBlank()) return false;
        if (java.time.Duration.between(growingThingsRead, Instant.now()).compareTo(java.time.Duration.ofHours(1)) >= 0 || growingThings.isEmpty()) {
            try {
                growingThings = java.util.Set.copyOf(jdbc.queryForList(
                    "SELECT DISTINCT phrase FROM (" +
                    "  SELECT replace(flora_key,'_',' ') AS phrase FROM flora_definition WHERE organism_type <> 'TREE' " +
                    "  UNION ALL SELECT regexp_replace(replace(d.item_key,'_',' '), ' (bundle|tuft|head|piece|strip|sprig|handful)$', '') FROM flora_drop d JOIN flora_definition f ON f.flora_key=d.flora_key AND f.organism_type <> 'TREE' " +
                    "  UNION ALL SELECT lower(i.display_name) FROM flora_drop d JOIN item_definition i ON i.item_key=d.item_key JOIN flora_definition f2 ON f2.flora_key=d.flora_key AND f2.organism_type <> 'TREE'" +
                    ") named WHERE length(phrase) >= 4", String.class));
                growingThingsRead = Instant.now();
            } catch (RuntimeException couldNotRead) {
                return false; // A classifier that cannot read the catalogue holds nothing against the words.
            }
        }
        String said = " " + text.toLowerCase(java.util.Locale.ROOT) + " ";
        // Said the word at all, and the intent that owns it may have it: "gather willow bark" is bark-stripping,
        // even though the world does grow a willow.
        for (String owned : OWNED_ELSEWHERE) if (said.contains(" " + owned + " ") || said.contains(" " + owned + "s ")) return false;
        for (String phrase : growingThings) {
            if (OWNED_ELSEWHERE.stream().anyMatch(owned -> (" " + phrase + " ").contains(" " + owned + " "))) continue;
            if (said.contains(" " + phrase + " ") || said.contains(" " + phrase + "s ")) return true;
            // And the plain family word at the end of it (#37): the catalogue grows MEADOW grass and drops DRY
            // grass, and a person asks for grass. A head noun is only held to be a plant when it is long enough
            // to be unmistakable and is not a word another intent owns.
            int space = phrase.lastIndexOf(' ');
            if (space < 0) continue;
            String head = phrase.substring(space + 1);
            if (head.length() < 4 || OWNED_ELSEWHERE.contains(head)) continue;
            if (said.contains(" " + head + " ") || said.contains(" " + head + "s ")) return true;
        }
        return false;
    }

    @Transactional
    public String[] gatherPlant(UUID chronicle, UUID location, String actionText, Instant occurredAt) {
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
        String lower = actionText.toLowerCase(java.util.Locale.ROOT);
        String season = seasonOf(occurredAt);

        // Find a flora in this biome that the action text names (or any available)
        java.util.List<java.util.Map<String,Object>> candidates = jdbc.queryForList(
            "SELECT fd.flora_key, fd.organism_type, fd.tool_required, fd.is_poisonous, " +
            "  (SELECT d.item_key FROM flora_drop d WHERE d.flora_key=fd.flora_key ORDER BY d.item_key LIMIT 1) AS drop_item, " +
            "  (SELECT id.category FROM item_definition id JOIN flora_drop d ON d.item_key=id.item_key WHERE d.flora_key=fd.flora_key ORDER BY d.item_key LIMIT 1) AS drop_category " +
            "FROM flora_definition fd " +
            "JOIN chunk_flora cf ON cf.flora_key=fd.flora_key " +
            "WHERE cf.chunk_id=? AND cf.quantity > 0 AND fd.organism_type <> 'TREE' " +
            "ORDER BY fd.flora_key", location);

        // A recorded stand is a PATCH — a brake of blackberry, a bed of nettle — that a Chronicle works down and
        // that grows back. It was never meant to be the whole of what grows on a piece of ground, and until genesis
        // planted anything (#224) the distinction did not arise: a chunk had no stands at all, so this always fell
        // through to the biome's general growth below. Now that every chunk carries three, taking the stands as the
        // complete list would quietly narrow foraging from "what this country grows" to "these three things" — a
        // change nobody asked for, made as a side effect of planting. So the stands are ADDED to the general growth
        // rather than replacing it: the patch is depletable and visible, the rest of the country still grows what it
        // grows, and gathering is exactly as broad as it was.
        java.util.List<java.util.Map<String,Object>> general = jdbc.queryForList(
            "SELECT fd.flora_key, fd.organism_type, fd.tool_required, fd.is_poisonous, " +
            "  (SELECT d.item_key FROM flora_drop d WHERE d.flora_key=fd.flora_key ORDER BY d.item_key LIMIT 1) AS drop_item, " +
            "  (SELECT id.category FROM item_definition id JOIN flora_drop d ON d.item_key=id.item_key WHERE d.flora_key=fd.flora_key ORDER BY d.item_key LIMIT 1) AS drop_category " +
            "FROM flora_definition fd " +
            "WHERE fd.organism_type <> 'TREE' AND fd.biome_affinity ILIKE ? " +
            "  AND fd.flora_key NOT IN (SELECT cf.flora_key FROM chunk_flora cf WHERE cf.chunk_id=? AND cf.quantity > 0) " +
            "ORDER BY fd.flora_key", "%" + biome + "%", location);
        candidates = new java.util.ArrayList<>(candidates);
        candidates.addAll(general);

        if (candidates.isEmpty()) return new String[]{"FAILED", "You search through the growth, but find nothing here worth taking."};

        // Choose what to take, in order of how specific the request is:
        //   1. A species the text names outright — by the plant's own name, or by what it yields
        //      (so "gather vines" finds the climbing vine that drops vine, "gather birch polypore"
        //      finds the polypore).
        //   2. A generic food word — "mushroom"/"fungi" wants an EDIBLE fungus, "berries" wants a
        //      berry — so these no longer fall through to whatever sorts first.
        //   3. Failing a name, prefer anything EDIBLE (a FOOD drop) over a MATERIAL.
        // (GitHub #23: the old fallback took candidates.get(0), alphabetically birch_polypore — a
        // MATERIAL tinder fungus — so "gather mushrooms"/"gather berries" credited the wrong thing.)
        boolean wantsMushroom = lower.contains("mushroom") || lower.contains("fungi") || lower.contains("fungus");
        boolean wantsBerry = lower.contains("berr");
        final java.util.List<java.util.Map<String,Object>> pool = candidates;

        // A specifically named material (vine, bark, root, reed, sap...) that does not grow on this
        // ground must fail as "not here", never silently become whatever food sorts first — the plain
        // was crediting "gather tree vines" as berries (#42). Only a GENERIC forage ("gather plants",
        // "forage for greens") may fall through to best-available food below.
        String namedTarget = null;
        for (String s : new String[]{"vine","bark","root","reed","rush","cattail","bulrush","sap","resin",
                "nettle","yarrow","comfrey","mint","dandelion","wild garlic","burdock","watercress",
                "chanterelle","porcini","oyster mushroom","polypore","rose hip","elderberry","hawthorn",
                "juniper","hazel","willow","flax","hemp","withy","thatch","tuber"})
            if (lower.contains(s)) { namedTarget = s; break; }

        // What the catalogue itself calls the things that grow (#37). The list above is thirty words somebody
        // wrote out, and it is exactly why "gather dry grass" came back with six beech mast: grass is not on it,
        // so nothing was "named", and the request fell through to best-available-food. Three faults compounded —
        // the hand-list has no grass, the flora is called meadow_grass rather than grass, and the drop is
        // dry_grass_bundle rather than dry grass, so neither the plant nor its yield matched the words a person
        // would use for either.
        //
        // The world knows all of this: 102 plants and 126 distinct drops, each with a key and a display name. So
        // the question is asked of the catalogue, and the hand-list is kept only as a fallback for the words it
        // carries that the catalogue does not (bark, root, sap, tuber and the like are qualities of plants rather
        // than names of them).
        java.util.List<java.util.Map<String,Object>> vocabulary = jdbc.queryForList(
            // Trees are not foraged -- gatherPlant excludes them from its candidates above -- so naming one must not
            // produce the "none grows within reach" refusal on ground where an oak plainly stands.
            "SELECT fd.flora_key AS flora_key, replace(fd.flora_key,'_',' ') AS phrase, CAST(NULL AS varchar) AS drop_key FROM flora_definition fd " +
            "UNION ALL " +
            // The unit word is how the catalogue COUNTS a thing, not what anybody calls it: "dry grass bundle"
            // is a bundle of dry grass, and a person asks for dry grass.
            "SELECT d.flora_key, regexp_replace(replace(d.item_key,'_',' '), ' (bundle|tuft|head|piece|strip|sprig|handful)$', ''), d.item_key FROM flora_drop d " +
            "UNION ALL " +
            "SELECT d.flora_key, lower(i.display_name), d.item_key FROM flora_drop d JOIN item_definition i ON i.item_key=d.item_key");

        java.util.Map<String,Object> spoken = null;
        int longest = 0;
        for (java.util.Map<String,Object> word : vocabulary) {
            String phrase = (String) word.get("phrase");
            if (phrase == null || phrase.length() < 4 || phrase.length() <= longest) continue;
            if ((" " + lower + " ").contains(" " + phrase + " ") || (" " + lower + " ").contains(" " + phrase + "s ")) {
                spoken = word;
                longest = phrase.length();
            }
        }
        final String wantedDrop = spoken == null ? null : (String) spoken.get("drop_key");
        final String spokenFlora = spoken == null ? null : (String) spoken.get("flora_key");
        final String spokenPhrase = spoken == null ? null : (String) spoken.get("phrase");

        java.util.Optional<java.util.Map<String,Object>> named = pool.stream()
            .filter(c -> c.get("flora_key").equals(spokenFlora))
            .findFirst()
            // A TREE that the Chronicle named and that actually stands here. Trees are kept out of the general
            // pool on purpose — felling and coppicing are their own acts, and "forage for plants" should never
            // come back with acorns — but gathering fallen mast under an oak is an ordinary thing to do, and
            // "gather acorns" was answering with an arrowhead tuber because nothing could ever match it.
            .or(() -> java.util.Optional.ofNullable(spokenFlora).flatMap(key -> jdbc.query(
                "SELECT fd.flora_key, fd.organism_type, fd.tool_required, fd.is_poisonous, " +
                "  (SELECT d.item_key FROM flora_drop d WHERE d.flora_key=fd.flora_key ORDER BY d.item_key LIMIT 1) AS drop_item, " +
                "  (SELECT i.category FROM item_definition i JOIN flora_drop d ON d.item_key=i.item_key WHERE d.flora_key=fd.flora_key ORDER BY d.item_key LIMIT 1) AS drop_category " +
                "FROM flora_definition fd JOIN chunk_flora cf ON cf.flora_key=fd.flora_key " +
                "WHERE fd.flora_key=? AND fd.organism_type='TREE' AND cf.chunk_id=? AND cf.quantity > 0 LIMIT 1",
                rs -> {
                    if (!rs.next()) return java.util.Optional.empty();
                    java.util.Map<String,Object> row = new java.util.HashMap<>();
                    row.put("flora_key", rs.getString(1)); row.put("organism_type", rs.getString(2));
                    row.put("tool_required", rs.getString(3)); row.put("is_poisonous", rs.getBoolean(4));
                    row.put("drop_item", rs.getString(5)); row.put("drop_category", rs.getString(6));
                    return java.util.Optional.of(row);
                }, key, location)))
            .or(() -> pool.stream()
                .filter(c -> lower.contains(((String)c.get("flora_key")).replace("_"," "))
                    || (c.get("drop_item") != null && lower.contains(((String)c.get("drop_item")).replace("_"," "))))
                .findFirst());

        // Named something the world grows somewhere, but not here. That is a different answer from "nothing here
        // is worth taking", and the Chronicle is owed the difference.
        if (named.isEmpty() && spokenPhrase != null)
            return new String[]{"FAILED", "You look for " + spokenPhrase + " here, but none grows within reach — "
                + "this is the wrong ground for it."};
        if (named.isEmpty() && namedTarget != null && !wantsMushroom && !wantsBerry) {
            return new String[]{"FAILED", "You look for " + namedTarget + " here, but none grows within reach — this is the wrong ground for it. It wants damper cover, or the trees you would find it under."};
        }
        java.util.Map<String,Object> target = named
            .or(() -> pool.stream().filter(c -> !Boolean.TRUE.equals(c.get("is_poisonous")) && (
                (wantsMushroom && "FUNGI".equals(c.get("organism_type")) && "FOOD".equals(c.get("drop_category")))
                || (wantsBerry && c.get("drop_item") != null && ((String)c.get("drop_item")).contains("berr"))))
                .findFirst())
            .or(() -> pool.stream().filter(c -> "FOOD".equals(c.get("drop_category")) && !Boolean.TRUE.equals(c.get("is_poisonous"))).findFirst())
            .orElse(pool.get(0));

        String floraKey = (String) target.get("flora_key");
        String toolRequired = (String) target.get("tool_required");

        // Tool gate — only KNIFE_CLASS tools apply here (no flora needs axe bare-hand)
        if (toolRequired != null && toolRequired.contains("KNIFE") && !hasCuttingTool(chronicle)) {
            return new String[]{"FAILED", "You reach for the plant, but it needs cutting and you carry no blade."};
        }

        // Get drops for this flora, filtered by season
        java.util.List<java.util.Map<String,Object>> drops = jdbc.queryForList(
            "SELECT item_key, yield_min, yield_max, season FROM flora_drop " +
            "WHERE flora_key=? AND (season IS NULL OR season=? OR ? IS NULL) " +
            "ORDER BY item_key", floraKey, season, season);

        if (drops.isEmpty()) {
            return new String[]{"FAILED", "You find the plant but nothing here is ready to take — wrong season or nothing ripe."};
        }

        // The drop the Chronicle NAMED, where they named one (#37). Meadow grass yields dry grass, green grass,
        // straw and thatch, and this took whichever sorted first — so asking for thatch got you dry grass, and
        // the four drops the catalogue carefully distinguishes were one drop with three spare names.
        java.util.Map<String,Object> drop = drops.stream()
            .filter(d -> d.get("item_key").equals(wantedDrop)).findFirst().orElse(drops.get(0));
        String itemKey = (String) drop.get("item_key");
        int yieldMin = ((Number) drop.get("yield_min")).intValue();
        int yieldMax = ((Number) drop.get("yield_max")).intValue();
        int yield = yieldMin + (yieldMax > yieldMin ? (int)(Math.random() * (yieldMax - yieldMin + 1)) : 0);
        int available = capacityHeadroomUnits(chronicle, itemKey);
        int count = Math.min(yield, Math.max(1, available));
        if (available <= 0) return new String[]{"FAILED", "You cannot carry any more of what you find here."};

        // Look up display name from item_definition
        String displayName = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, itemKey);
        // Gathered food is perishable from the moment it is picked (V264). Before this, foraged plant food was
        // created with no preservation state at all and so never spoiled — only hunted and caught animal food was
        // ever registered. Produce keeps for days on the FRESH tier; nuts, mast and grain keep for weeks on DRIED.
        String category = jdbc.queryForObject("SELECT category FROM item_definition WHERE item_key=?", String.class, itemKey);
        String keepKind = "FOOD".equals(category) ? foragedKeepKind(itemKey) : null;

        for (int i = 0; i < count; i++) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM',?,?)", id, displayName, chronicle);
            jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,?,'SOUND')", id, itemKey);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'GATHERED',jsonb_build_object('floraKey',?,'biome',?))", id, Timestamp.from(occurredAt), floraKey, biome);
            if (keepKind != null) registerPreserved(id, keepKind, occurredAt);
        }
        // Deplete chunk_flora if present
        jdbc.update("UPDATE chunk_flora SET quantity=GREATEST(0,quantity-?), last_harvested_at=? WHERE chunk_id=? AND flora_key=?", count, Timestamp.from(occurredAt), location, floraKey);
        assertCarryCapacity(chronicle);
        String plantName = floraKey.replace("_", " ");
        return new String[]{"SUCCEEDED", "You gather " + (count == 1 ? "a" : count + "") + " " + displayName.toLowerCase() + " from the " + plantName + " growing here."};
    }

    /** How many mature trees a natural, uncut wooded chunk holds (#200/#201) — not a flat figure but a stand with its
     *  own density: a deep, well-watered wood stands thicker than a sparse one. Deterministic per ground, so the same
     *  chunk always reads the same but neighbouring ground differs. Generous overall (floor 10), so ordinary felling
     *  never notices the ceiling; sustained clear-cutting does. */
    public static int natStandFor(UUID chunk) {
        int h = Math.floorMod((chunk.toString() + ":stand").hashCode(), 100); // 0..99, fixed for this ground
        return 10 + (int) Math.round((h / 100.0) * 12);                       // 10 (sparse) .. 22 (deep wood)
    }

    /**
     * Fell a tree in the current chunk — requires an axe-class tool equipped or
     * carried. Yields logs and any secondary drops from flora_drop. Returns [outcome, narration].
     */
    @Transactional
    public String[] fellTree(UUID chronicle, UUID location, Instant occurredAt) {
        // Axe-class tool: stone_axe, stone_hatchet, or any future axe item. Pick the soundest one in reach, so a
        // spare good axe is used before a worn one; a tool wears with the work (V139), and a broken axe will not
        // bite until it is mended.
        java.util.Map<String,Object> axe = jdbc.query(
            "WITH RECURSIVE reachable(id) AS (" +
            "  SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE'" +
            "  UNION ALL SELECT ic.item_id FROM item_containment ic" +
            "  JOIN reachable r ON r.id=ic.container_id" +
            "  JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE')" +
            "SELECT i.object_id, i.condition_state, i.use_count, i.item_key FROM reachable r JOIN item_instance i ON i.object_id=r.id " +
            // Which items are axes is tool_profile's answer, not a list kept here (#93). The list this replaces
            // named 'hand_axe', which is not an item key the catalogue has ever held — a phantom that could never
            // match anything — and would have gone on missing any axe added after it was written.
            "WHERE i.item_key IN (SELECT item_key FROM tool_profile WHERE tool_class='AXE') " +
            "ORDER BY CASE i.condition_state WHEN 'SOUND' THEN 0 WHEN 'WORN' THEN 1 WHEN 'BROKEN' THEN 2 ELSE 3 END, i.use_count LIMIT 1",
            rs -> rs.next() ? java.util.Map.of("id", rs.getObject(1, UUID.class), "cond", rs.getString(2), "uses", rs.getInt(3), "key", rs.getString(4)) : null,
            chronicle);
        if (axe == null) return new String[]{"FAILED", "You set your hands against the trunk. Without an axe, you cannot fell a tree."};
        if ("BROKEN".equals(axe.get("cond")) || "DESTROYED".equals(axe.get("cond")))
            return new String[]{"FAILED", "Your axe is past biting — the head loose, the edge gone. Mend it against a whetstone before you can fell with it."};

        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
        // Check that there is a tree here via chunk_flora
        java.util.Map<String,Object> tree = jdbc.query(
            "SELECT cf.flora_key, fd.organism_type FROM chunk_flora cf " +
            "JOIN flora_definition fd ON fd.flora_key=cf.flora_key " +
            "WHERE cf.chunk_id=? AND fd.organism_type='TREE' AND cf.quantity>0 LIMIT 1",
            rs -> rs.next() ? java.util.Map.of("flora_key", rs.getString(1), "organism_type", rs.getString(2)) : null,
            location);

        if (tree == null) {
            // Fall back: biome has trees by nature even without chunk_flora rows
            String treeKey = switch (biome != null ? biome : "") {
                case "FOREST", "TEMPERATE_FOREST" -> "oak";
                case "MOUNTAIN" -> "spruce";
                case "HIGHLAND" -> "pine";
                case "WETLAND", "RIVER_BANK" -> "willow";
                default -> null;
            };
            if (treeKey == null) return new String[]{"FAILED", "There are no trees here to fell."};
            // #200/#201: bring natural woodland into the finite-stand system. A wooded chunk with no recorded stand
            // is entered lazily at a full natural stand the first time it is cut — so felling draws it down, the
            // regrowth clock runs (WildlifeSimulationService), and a clear-cut recolonises or must be replanted,
            // instead of the old bottomless fallback. A stand already worked to nothing gives no more here.
            Integer standQty = jdbc.query("SELECT quantity FROM chunk_flora WHERE chunk_id=? AND flora_key=?",
                rs -> rs.next() ? rs.getInt(1) : null, location, treeKey);
            if (standQty != null && standQty <= 0)
                return new String[]{"FAILED", "The stand here is cut out — only stumps and low brush remain. It will take years to come back on its own, unless you plant and let it grow."};
            if (standQty == null)
                jdbc.update("INSERT INTO chunk_flora (chunk_id, flora_key, quantity, capacity, last_harvested_at) VALUES (?,?,?,?,NULL)",
                    location, treeKey, natStandFor(location), natStandFor(location));
            tree = java.util.Map.of("flora_key", treeKey, "organism_type", "TREE");
        }

        String floraKey = (String) tree.get("flora_key");
        // Get log drop for this tree
        java.util.List<java.util.Map<String,Object>> drops = jdbc.queryForList(
            "SELECT item_key, yield_min, yield_max FROM flora_drop WHERE flora_key=? AND tool_condition='AXE_CLASS'", floraKey);
        if (drops.isEmpty()) return new String[]{"FAILED", "The tree stands but offers nothing your axe can shape."};

        String logKey = (String) drops.get(0).get("item_key");
        int yieldMin = ((Number)drops.get(0).get("yield_min")).intValue();
        int yieldMax = ((Number)drops.get(0).get("yield_max")).intValue();
        int count = Math.max(1, yieldMin + (yieldMax > yieldMin ? (int)(Math.random() * (yieldMax - yieldMin + 1)) : 0));
        // #201 stewardship: a young cohort — planted, or recolonised after a clear-cut — is thin poles, not timber,
        // until it has stood for its species' regrowth span. Felling it yields a single thin log, the standing cost of
        // stripping a wood bare rather than harvesting it selectively and letting it grow. An old natural stand
        // (established_at NULL) is mature and gives its full timber.
        boolean young = Boolean.TRUE.equals(jdbc.query(
            "SELECT cf.established_at IS NOT NULL AND cf.established_at > ?::timestamptz - make_interval(days => fd.regrowth_days) " +
            "FROM chunk_flora cf JOIN flora_definition fd ON fd.flora_key=cf.flora_key WHERE cf.chunk_id=? AND cf.flora_key=?",
            rs -> rs.next() ? rs.getBoolean(1) : Boolean.FALSE, Timestamp.from(occurredAt), location, floraKey));
        if (young) count = 1;
        String logName = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, logKey);

        // A felled trunk is not shouldered whole — it lies where it fell, on the ground at
        // this location (no owner), for the chronicle to buck and split into pieces they can
        // actually carry. Felling therefore never fails on carry capacity (GitHub #17); the
        // wood becomes portable only once worked (e.g. "split the log into planks").
        for (int i = 0; i < count; i++) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ITEM',?,?)", id, logName, location);
            jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,?,'SOUND')", id, logKey);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'FELLED',jsonb_build_object('floraKey',?,'biome',?,'placedAt',?::text))", id, Timestamp.from(occurredAt), floraKey, biome, location.toString());
        }
        jdbc.update("UPDATE chunk_flora SET quantity=GREATEST(0,quantity-1), last_harvested_at=? WHERE chunk_id=? AND flora_key=?", Timestamp.from(occurredAt), location, floraKey);
        // The axe wears with the felling (V139, #220). Each fell adds a use; at thresholds the edge and head give
        // way a step (SOUND -> WORN -> BROKEN). A worn axe still fells; a broken one will not, until it is mended
        // (which resets the wear). Kept in history when the condition steps down.
        UUID axeId = (UUID) axe.get("id");
        String axeCond = (String) axe.get("cond");
        int uses = (int) axe.get("uses") + 1;
        // Harder metal holds its edge through far more work: a bronze axe outlasts a knapped stone one, iron
        // outlasts bronze, steel outlasts iron. The wear thresholds scale with the metal, so the durability of the
        // metal is a real, felt payoff of the whole extraction chain (#180), beside its keener edge and finer work.
        int[] wear = toolWearThresholds((String) axe.get("key"));
        String wornCond = uses >= wear[1] ? "BROKEN" : uses >= wear[0] ? "WORN" : axeCond;
        jdbc.update("UPDATE item_instance SET use_count=?, condition_state=? WHERE object_id=?", uses, wornCond, axeId);
        if (!wornCond.equals(axeCond))
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'TOOL_WORN',jsonb_build_object('from',?,'to',?))", axeId, Timestamp.from(occurredAt), axeCond, wornCond);
        String treeName = floraKey.replace("_", " ");
        String logLower = logName.toLowerCase();
        return new String[]{"SUCCEEDED", "The " + treeName + " comes down with a crack that carries across the ground. " +
            count + " " + logLower + (count > 1 ? "s lie" : " lies") + " where " + (count > 1 ? "they" : "it") +
            " fell — far too heavy to shoulder whole, and nothing your arms can do will shift them from where they are." +
            (young ? " The trees here are young growth — thin poles, not the timber a grown wood gives; there is little to take from a stand not left to mature." : "")};
    }

    /** How long a coppiced stand must rest before its stools have thrown up rods worth cutting again (#204). */
    private static final int COPPICE_REGROWTH_DAYS = 90;

    /**
     * Coppice a wood in the current chunk (#200/#204): cut the rods and poles from living stools and leave the stools
     * to throw up fresh growth, so the stand yields a crop without ever being felled. Needs a cutting edge; a stand
     * cut out to nothing has no living stools; and the stools must have rested since the last cutting. The tree count
     * is never reduced — this is the renewable counterpart to felling. Returns [outcome, narration].
     */
    @Transactional
    public String[] coppice(UUID chronicle, UUID location, Instant occurredAt) {
        if (!hasCuttingTool(chronicle) && !hasAtLeast(chronicle,"stone_axe",1) && !hasAtLeast(chronicle,"stone_hatchet",1)
            && !hasAtLeast(chronicle,"copper_axe",1) && !hasAtLeast(chronicle,"bronze_axe",1) && !hasAtLeast(chronicle,"iron_axe",1) && !hasAtLeast(chronicle,"steel_axe",1))
            return new String[]{"FAILED", "You take hold of the rods, but without a blade to cut them there is no coppicing this stand."};

        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
        String treeKey = jdbc.query(
            "SELECT cf.flora_key FROM chunk_flora cf JOIN flora_definition fd ON fd.flora_key=cf.flora_key " +
            "WHERE cf.chunk_id=? AND fd.organism_type='TREE' AND cf.quantity>0 LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : null, location);
        if (treeKey == null) {
            treeKey = switch (biome != null ? biome : "") {
                case "FOREST", "TEMPERATE_FOREST" -> "oak";
                case "MOUNTAIN" -> "spruce";
                case "HIGHLAND" -> "pine";
                case "WETLAND", "RIVER_BANK" -> "willow";
                default -> null;
            };
            if (treeKey == null) return new String[]{"FAILED", "There is no wood here to coppice."};
            Integer standQty = jdbc.query("SELECT quantity FROM chunk_flora WHERE chunk_id=? AND flora_key=?",
                rs -> rs.next() ? rs.getInt(1) : null, location, treeKey);
            if (standQty != null && standQty <= 0)
                return new String[]{"FAILED", "The stand here is cut out — only dead stumps remain, and a dead stump throws up nothing to cut."};
            if (standQty == null)
                jdbc.update("INSERT INTO chunk_flora (chunk_id, flora_key, quantity, capacity, last_harvested_at) VALUES (?,?,?,?,NULL)",
                    location, treeKey, natStandFor(location), natStandFor(location));
        }
        // Conifers do not coppice — a pine or a spruce will not throw up new shoots from a cut stump the way a
        // broadleaf will. Coppicing is a craft of the broadleaf woods (oak, ash, willow, hazel, and the like).
        if (treeKey.equals("pine") || treeKey.equals("spruce"))
            return new String[]{"FAILED", "These are conifers — a " + treeKey + " will not throw up new growth from a cut stump, so there is nothing to coppice here. Coppicing is for the broadleaf woods."};

        Boolean rested = jdbc.query(
            "SELECT last_coppiced_at IS NULL OR last_coppiced_at <= ?::timestamptz - make_interval(days => ?) " +
            "FROM chunk_flora WHERE chunk_id=? AND flora_key=?",
            rs -> rs.next() ? (Boolean) rs.getObject(1) : Boolean.TRUE, Timestamp.from(occurredAt), COPPICE_REGROWTH_DAYS, location, treeKey);
        if (Boolean.FALSE.equals(rested))
            return new String[]{"FAILED", "The stools you cut before have not yet thrown up new rods worth taking — coppice needs a rest between cuttings."};

        int rods = 2 + (int) (Math.random() * 3); // 2..4 rods a cutting
        int room = capacityHeadroomUnits(chronicle, "hazel_rod");
        int take = Math.min(rods, room);
        if (take <= 0) return new String[]{"FAILED", "You have cut all the rods you can carry."};
        for (int i = 0; i < take; i++) createCarriedItem(chronicle, "hazel_rod", "Hazel Rod", occurredAt, "COPPICED");
        // The stools are cut but the tree lives on — the stand is NOT reduced. Only the coppice clock is set.
        jdbc.update("UPDATE chunk_flora SET last_coppiced_at=? WHERE chunk_id=? AND flora_key=?", Timestamp.from(occurredAt), location, treeKey);
        return new String[]{"SUCCEEDED", "You work along the stand cutting the low rods and poles from the living stools and bundling them, and leave the stools standing to throw up fresh growth again — a crop of rods taken without losing the wood."};
    }

    /**
     * Plant a tree seed to establish or restore a woodland stand here (#200/#204 — the counter-play to felling and
     * clear-cutting). A carried acorn grows an oak, a pine nut a pine — pressed into ground that suits the species
     * (its biome_affinity). It seeds a young stand (or adds a sapling to a thin one) with room to grow to a small
     * wood, and starts its regrowth clock, so a clear-cut a Chronicle replants comes back over the years where it
     * would otherwise have stayed bare. The seed comes from foraging (acorns under oaks, nuts from cones, #257).
     */
    @Transactional
    public String[] plantTree(UUID chronicle, UUID location, Instant occurredAt) {
        String seed = null, species = null;
        if (hasAtLeast(chronicle, "acorn", 1)) { seed = "acorn"; species = "oak"; }
        else if (hasAtLeast(chronicle, "pine_nut", 1)) { seed = "pine_nut"; species = "pine"; }
        if (seed == null) return new String[]{"FAILED", "You have no seed to plant — gather acorns under an oak, or pine nuts from the cones, and come back."};
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
        String affinity = jdbc.queryForObject("SELECT biome_affinity FROM flora_definition WHERE flora_key=?", String.class, species);
        if (biome == null || affinity == null || !java.util.Arrays.asList(affinity.split(",")).contains(biome))
            return new String[]{"FAILED", "This ground will not take a " + species + " — it does not grow in country like this."};
        if (!consumeOne(chronicle, seed, occurredAt)) return new String[]{"FAILED", "The " + seed + " is no longer in reach to plant."};
        Timestamp ts = Timestamp.from(occurredAt);
        int updated = jdbc.update("UPDATE chunk_flora SET quantity = LEAST(GREATEST(capacity, 3), quantity + 1), capacity = GREATEST(capacity, 3), last_harvested_at = ?, established_at = ? WHERE chunk_id=? AND flora_key=?", ts, ts, location, species);
        if (updated == 0)
            jdbc.update("INSERT INTO chunk_flora (chunk_id, flora_key, quantity, last_harvested_at, capacity, established_at) VALUES (?,?,1,?,3,?)", location, species, ts, ts);
        return new String[]{"SUCCEEDED", "You press the " + seed + " into the turned earth and firm the soil over it — a sapling that, given years and left to stand, will grow into a " + species + " where none was."};
    }

    @Transactional(readOnly = true)
    public boolean hasAtLeast(UUID chronicle, String itemKey, int required) {
        return reachCount(chronicle, chronicleLocation(chronicle), itemKey) >= required;
    }

    /**
     * True when this action text resolves to a VERIFIED material process, without side
     * effects (no miss recorded — this is a question, not an attempt).
     *
     * <p>The intent classifier in {@code ChronicleActionService} matches on raw
     * substrings and so cannot tell "salt the fish" (preserve) from "catch a fish"
     * (angle), or "carve a spoon" (make) from "carve a blaze" (mark). This lets it
     * defer those ambiguous, noun-driven intents to the two-axis matcher, which agrees
     * on category, keyword and subject before it claims anything — the word boundaries
     * the substring classifier lacks. It is the same authority {@code runProcess} uses,
     * so a "yes" here means the very next fallback to PROCESS_MATERIAL will resolve.
     */
    @Transactional(readOnly = true)
    public boolean actionMatchesProcess(String actionText) {
        return matcher.match(actionText) != null;
    }

    /**
     * Whether the material process this action resolves to burns a fire — a smoky working (a smelt, a kiln firing, a
     * forge, a charcoal char). Used to lay a smoke footprint (#219) when such a process succeeds. Non-recording match,
     * so asking does not touch the play-path matcher's miss log.
     */
    @Transactional(readOnly = true)
    public boolean actionIsFireProcess(String actionText) {
        String key = matcher.match(actionText);
        if (key == null) return false;
        return Boolean.TRUE.equals(jdbc.query(
            "SELECT requires_fire FROM material_process WHERE process_key=? AND review_state='VERIFIED'",
            rs -> rs.next() ? rs.getBoolean(1) : Boolean.FALSE, key));
    }

    /**
     * Whether the text reads as physical work on materials or the world (#68) — its verb classifies to a
     * material category — even when no process matches it yet. Lets an unresolved gather/prep/craft verb fail
     * with a grounded "you work the material but find no way" rather than the generic gibberish line.
     */
    @Transactional(readOnly = true)
    public boolean isMaterialWork(String actionText) {
        String c = matcher.activityCategory(actionText);
        return c != null && (c.equals("PROCESS") || c.equals("ACQUIRE") || c.equals("CRAFT") || c.equals("CONSTRUCT") || c.equals("MAINTAIN"));
    }

    /** Whether the chronicle could carry one more of an item without breaking capacity. */
    @Transactional(readOnly = true)
    public boolean hasCarryRoomFor(UUID chronicle, String itemKey) {
        return capacityHeadroomUnits(chronicle, itemKey) > 0;
    }

    /**
     * Run a material process from the declarative table (V52): splitting planks,
     * dressing stone, twisting cordage, tanning hide, rendering pitch, firing a pot.
     *
     * <p>The whole chain is data. Nothing here knows what a plank is — it reads which
     * process the action names, checks that the inputs, tool class, fire and water it
     * declares are actually present, then consumes and produces. Adding a material
     * chain is a migration, not a code change, which is the point: every gap in that
     * table is a moment the simulation cannot resolve on its own and has to spend an
     * AI call instead.
     *
     * @return [outcome, narration]
     */
    @Transactional
    public String[] runProcess(UUID chronicle, UUID location, String actionText, Instant at) {
        // A process may run only when the text agrees with it on the kind of work, the
        // verb, and the material — see ProcessMatcher, which holds the rule and the
        // reasoning. Keyword alone was how "split the fish" reached split_planks, and
        // a wrong match does not throw: it quietly does the wrong thing to a
        // chronicle's inventory. The narration says nothing about why nothing matched;
        // the world simply does not yet know how to do it.
        // matchAndRecord, not match: this is the play path, so a miss here is a real
        // gap a player walked into and belongs in the backlog (V56).
        ProcessMatcher.Result resolved = matcher.resolveAndRecord(actionText, chronicle);
        String key = resolved.processKey();
        // Words that fit several pieces of work equally — "carve a bowl" is soapstone and wood alike — used to run
        // whichever key sorted first, so a Chronicle holding wood was told they had no soapstone (#38). What is in
        // reach settles it when only one of them can be worked; otherwise the Chronicle is asked, by name, and
        // nothing is spent.
        if (resolved.ambiguous()) {
            java.util.List<String> workable = resolved.tied().stream().filter(k -> inputsInReach(chronicle, location, k)).toList();
            if (workable.size() == 1) key = workable.get(0);
            else {
                matcher.recordAmbiguity(actionText, resolved.tied());
                return new String[]{"FAILED", whichOfThese(workable.isEmpty() ? resolved.tied() : workable, workable.isEmpty())};
            }
        }
        // The words must have asked for WORK, not merely named a thing the world can make (#37).
        //
        // "wash the bowl" reached "carve a wooden bowl"; "break the ice on the trough" reached "carve a wooden
        // trough"; "shield the fire from the wind" reached "build a war shield". The matcher agrees on category,
        // keyword and subject — and none of those three is the VERB. 1,317 of the catalogue's 2,552 verified
        // keywords are bare nouns, so any sentence containing one of those nouns can reach the making of it,
        // whatever the sentence was doing to it. Measured: six of sixteen such sentences did.
        // Returned as the ordinary MISS, not as a process failure: ChronicleActionService reads this exact
        // opening to tell "a process ran and could not" from "no process was meant", and falls the action through
        // to the witness line for the second. Washing a bowl is not a failed carving; it is a thing the world does
        // not know how to do.
        if (key != null && sentenceMeantSomethingElse(actionText, key)) key = null;
        if (key != null) return executeProcess(chronicle, location, key, actionText, at);
        // A deterministic miss. The AI Procedure Interpreter may compose this from existing processes
        // (DR-0021), but that orchestration lives in ChronicleActionService where the AI seam is; here
        // the world simply does not yet know how, and says so.
        return new String[]{"FAILED", "You turn the material over in your hands, but no way to work it into what you meant comes to you here. Whatever that would take, it is not a thing your hands find on their own."};
    }

    /** The verbs that mean MAKING. A sentence carrying one of these is asking for work, whatever else it says. */
    private static final java.util.Set<String> MAKING_VERBS = java.util.Set.of(
        "make", "craft", "build", "carve", "knap", "weave", "sew", "shape", "cut", "split", "dry", "smoke",
        "salt", "boil", "grind", "render", "tan", "fire", "forge", "smelt", "cast", "haft", "lash", "twist",
        "braid", "press", "brew", "bake", "cook", "assemble", "work", "prepare", "dress", "scrape", "flesh",
        "char", "burn", "melt", "mix", "pour", "raise", "dig", "plait", "spin", "stitch", "roll", "fold",
        "temper", "quench", "polish", "sharpen", "hollow", "bore", "drill", "peel", "strip", "pound", "crush",
        "mill", "sift", "knead", "churn", "ferment", "steep", "infuse", "distil", "wind", "coil", "trim",
        "fit", "join", "pin", "bind", "rive", "whittle", "turn", "fashion", "construct", "put together");

    /**
     * Whether these words asked for something OTHER than making the thing they name (#37).
     *
     * <p>The matcher settles category, keyword and subject, and not one of those is the verb — so a bare-noun
     * keyword is reachable from any sentence containing the noun. "wash the bowl" reached <i>carve a wooden
     * bowl</i>; "break the ice on the trough" reached <i>carve a wooden trough</i>.
     *
     * <p>The test is deliberately conservative, and mostly asks the DATA rather than a list:
     * <ul>
     *   <li>a sentence with no leading verb at all is left alone, because that is how the plain family word
     *       reaches its family — "a poultice", "a bowl" — which is a thing this project worked to get;</li>
     *   <li>a sentence whose verb is a MAKING verb is left alone;</li>
     *   <li>a sentence whose verb appears in the matched process's OWN keywords is left alone, which is what
     *       keeps "break the flint" reaching the knapping that declares "break";</li>
     *   <li>only a sentence whose verb is none of those, against a keyword that carries no verb of its own, is
     *       refused — and it is refused honestly rather than answered with the wrong act.</li>
     * </ul>
     */
    private boolean sentenceMeantSomethingElse(String actionText, String processKey) {
        String said = actionText == null ? "" : actionText.toLowerCase(java.util.Locale.ROOT).trim();
        if (said.isEmpty()) return false;
        String verb = said.split("[^a-z]+")[0];
        if (verb.isEmpty() || MAKING_VERBS.contains(verb)) return false;
        // A sentence of one word is a NAME, not a verb phrase — "shield", "poultice", "bowl" — and reaching the
        // family from the bare family word is a thing this project worked to get. Several of those names are also
        // plain verbs, so without this they would be read as sentences about an object they never mention.
        if (said.split("[^a-z]+").length <= 1) return false;

        // What this process calls itself.
        java.util.List<String> keywords = jdbc.queryForList(
            "SELECT lower(keywords) FROM material_process WHERE process_key=?", String.class, processKey);
        String vocabulary = keywords.isEmpty() ? "" : keywords.get(0);
        boolean vocabularyKnowsTheVerb = (" " + vocabulary.replace(',', ' ') + " ").contains(" " + verb + " ");

        // What else the sentence is about. Articles, prepositions and pronouns say nothing about the subject, so
        // they are passed over; what is left is the thing the sentence names.
        java.util.List<String> aboutWords = new java.util.ArrayList<>();
        String[] words = said.split("[^a-z]+");
        for (int i = 1; i < words.length; i++)
            if (words[i].length() >= 3 && !SAYS_NOTHING_ABOUT_THE_SUBJECT.contains(words[i])) aboutWords.add(words[i]);
        boolean namesWhatTheProcessIsAbout = aboutWords.stream()
            .anyMatch(w -> com.devosphere.draugr.narration.Words.word(vocabulary, w));

        // The verb alone is not enough to make the sentence this process's. A process names itself with a bare
        // noun so that the plain family word reaches its family — "shield", "comb", "hoe", "roof", "wedge" — and
        // that bare noun was being read as a declaration of the VERB, so every one of them answered a sentence
        // about something else with a recipe for itself. The verb counts only when the sentence ALSO names
        // something this process is about, which is what keeps "break the stone" and "skin the fish".
        if (vocabularyKnowsTheVerb && namesWhatTheProcessIsAbout) return false;

        // A sentence that opens with an ordinary verb this process has never heard of, against a thing the
        // process happens to name. That is a sentence about the object, not a request to make one (#739).
        if (isAPlainVerb(verb)) return true;

        // And a sentence that names nothing this process is about matched on the verb and nothing else, whatever
        // that verb was. "skin the fire" is not a fish; "tar the path" is not a timber. A sentence carrying no
        // subject at all — "ret it" — is left alone, because there is nothing in it to contradict the process.
        return !aboutWords.isEmpty() && !namesWhatTheProcessIsAbout;
    }

    /** Words that are in every sentence and tell you nothing about what it is about. */
    private static final java.util.Set<String> SAYS_NOTHING_ABOUT_THE_SUBJECT = java.util.Set.of(
        "the", "and", "with", "for", "from", "into", "onto", "upon", "that", "this", "these", "those", "some",
        "any", "all", "out", "off", "down", "over", "under", "your", "mine", "them", "there", "here", "then",
        "more", "most", "less", "than", "what", "when", "where", "now", "again", "just", "one", "two", "bit",
        "little", "much", "very", "really", "properly", "carefully", "well", "good", "ready", "about", "around",
        "back", "away", "together", "enough", "few", "can", "will", "should", "would", "could", "have",
        "has", "had", "get", "got", "let", "its", "his", "her", "their", "our", "ours", "yours", "theirs");

    /** Whether a word is a verb a person would use for an ordinary act — as against a noun or an article. Kept
     *  short deliberately: a word that is not clearly a verb leaves the sentence alone. */
    private static boolean isAPlainVerb(String word) {
        return java.util.Set.of("wash", "clean", "rinse", "break", "smash", "shield", "protect", "cover",
            "clear", "carry", "bring", "hang", "move", "shift", "empty", "fill", "open", "close", "throw",
            "put", "lift", "drop", "sit", "look", "smell", "listen", "count", "wear", "ride", "feed", "water",
            "check", "test", "find", "search", "follow", "read", "eat", "drink", "sleep", "rest", "walk",
            "climb", "swim", "wait", "watch", "guard", "hide", "bury", "mourn", "thank", "sing", "shout")
            .contains(word);
    }

    /**
     * Whether every input a process wants is within reach: each fixed input in quantity, and at least one member of each
     * either/or group — the same test executeProcess applies before it spends anything. Read only to settle a tie (#38).
     */
    private boolean inputsInReach(UUID chronicle, UUID location, String processKey) {
        for (java.util.Map<String,Object> in : jdbc.queryForList(
                "SELECT item_key, quantity FROM material_process_input WHERE process_key=?", processKey))
            if (!hasAtLeastHere(chronicle, location, (String) in.get("item_key"), ((Number) in.get("quantity")).intValue())) return false;
        for (String g : jdbc.queryForList(
                "SELECT DISTINCT group_name FROM material_process_input_group WHERE process_key=?", String.class, processKey)) {
            boolean any = false;
            for (java.util.Map<String,Object> o : jdbc.queryForList(
                    "SELECT item_key, quantity FROM material_process_input_group WHERE process_key=? AND group_name=?", processKey, g))
                if (hasAtLeastHere(chronicle, location, (String) o.get("item_key"), ((Number) o.get("quantity")).intValue())) { any = true; break; }
            if (!any) return false;
        }
        return true;
    }

    /** The question put to a Chronicle whose words named more than one piece of work (#38). */
    private String whichOfThese(java.util.List<String> processKeys, boolean noneInReach) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String k : processKeys)
            names.add(jdbc.queryForObject("SELECT lower(display_name) FROM material_process WHERE process_key=?", String.class, k));
        String choices = names.size() == 2 ? names.get(0) + ", or " + names.get(1)
            : String.join(", ", names.subList(0, names.size() - 1)) + ", or " + names.get(names.size() - 1);
        return noneInReach
            ? "Those words fit more than one piece of work — " + choices + " — and you have the makings of none of them within reach. Say which you mean."
            : "Those words fit more than one piece of work you could do here — " + choices + ". Say which you mean, and your hands will know where to begin.";
    }

    /** True if a verified process by this key exists (canonical). Used to validate an AI-composed plan. */
    @Transactional(readOnly = true)
    public boolean processExists(String processKey) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM material_process WHERE process_key=? AND review_state='VERIFIED')", Boolean.class, processKey));
    }

    /** The distinct item keys the chronicle can reach — carried, in carried containers, or on the ground here. */
    @Transactional(readOnly = true)
    public java.util.List<String> reachableItemKeys(UUID chronicle, UUID location) {
        return jdbc.query(REACHABLE_CTE +
            "SELECT DISTINCT i.item_key FROM reachable r JOIN item_instance i ON i.object_id=r.id ORDER BY i.item_key",
            (rs, row) -> rs.getString(1), chronicle, location);
    }

    /**
     * The reachable pool as quantified "item_key x&lt;count&gt;" lines — the inventory the AI reasons over, so
     * it can weigh "5 branches is enough" rather than only "branches present" (F2). Same reachability model as
     * everything else (carried + on-site storage + racked tools), grouped with counts.
     */
    @Transactional(readOnly = true)
    public java.util.List<String> reachableInventory(UUID chronicle, UUID location) {
        return jdbc.query(REACHABLE_CTE +
            "SELECT i.item_key, COUNT(*) FROM reachable r JOIN item_instance i ON i.object_id=r.id GROUP BY i.item_key ORDER BY i.item_key",
            (rs, row) -> rs.getString(1) + " x" + rs.getInt(2), chronicle, location);
    }

    /**
     * If a needed item is not here but sits at a place the chronicle has NAMED in another chunk, the name of
     * that place — so a reject becomes a decision ("what there is of it sits at your Wood Store") rather than a
     * blank wall (DR-0022 reachability principle). Null when there is no such known store. Scoped to the
     * chronicle's own named settlements, never the whole world.
     */
    private String knownLocationOf(UUID chronicle, String itemKey, UUID currentLocation) {
        return jdbc.query(
            "SELECT nl.name FROM chronicle_named_location nl " +
            "JOIN world_object w ON w.current_location_id=nl.chunk_id AND w.lifecycle_state='ACTIVE' " +
            "JOIN item_instance i ON i.object_id=w.id " +
            "WHERE nl.chronicle_id=? AND nl.chunk_id<>? AND i.item_key=? LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : null, chronicle, currentLocation, itemKey);
    }

    /**
     * Run one specific, already-chosen material process end to end — the executor shared by a direct
     * matcher hit and an AI-composed plan step. Every gate (tool, fire, water, inputs, mass via the
     * fixed yields) still applies, so a composed step that does not physically fit fails exactly as a
     * typed one would.
     */
    @Transactional
    public String[] executeProcess(UUID chronicle, UUID location, String key, String actionText, Instant at) {
        java.util.Map<String,Object> match = jdbc.queryForMap(
            "SELECT process_key, display_name, output_item_key, output_min, output_max, tool_class, " +
            "requires_fire, requires_water, requires_salt_water, narration, station_kind FROM material_process WHERE process_key=?", key);

        // A workstation (bench/loom) sited within reach EASES this operation — it never gates it (bare-handed
        // always works) and never decides its grade (skill + materials do). See V69: it gives efficiency (less
        // waste) plus a minor, bounded quality assist. Detected through the unified reachability model (Layer 1),
        // so a bench standing in an on-site workshop counts.
        String stationKind = (String) match.get("station_kind");
        // A workstation you BUILT counts as the bench it is — which is true for a rack, and is NOT yet true for
        // the tables, so the claim is corrected here rather than left standing.
        //
        // WEAVING_TABLE, WOODWORKING_TABLE, STONEWORKING_TABLE, SEWING_TABLE and KNOWLEDGE_STATION are rows in
        // construction_kind and nothing anywhere creates a construction_project with any of those kinds: they have
        // no assembly, and craftFurniture makes an ITEM standing at a location, not a build. So the three cases in
        // stationStructureFor below are, for now, a mapping to something that cannot exist.
        //
        // Nothing is broken by that, because the item path is the live one and it works: craftFurniture places the
        // bench on the ground, and REACHABLE_CTE counts what is on the ground where the Chronicle stands, so a
        // crafted bench eases the work exactly as it should. The mapping is kept rather than deleted because the
        // same method now has a genuinely live structure path (a smoke rack, a drying rack), and because making
        // the tables raisable is the honest fix if #77 wants them.
        //
        // That fix is not free, and the reason is worth recording: CRAFT_DESK matches (craft|make|build|construct|
        // assemble) beside (desk|table|workbench|bench), so any assembly keyword naming a table or a bench is
        // shadowed by a Java intent before the assembly matcher ever sees it — the #513 trap. A buildable
        // workshop needs vocabulary that does not collide, not just a migration.
        boolean atStation = stationKind != null
            && (hasAtLeast(chronicle, stationKind, 1) || builtStationAt(location, stationKind));

        // The tool this work turns on (#220): pick the soundest one in reach. A broken tool no longer counts — it
        // must be mended first — and the chosen tool wears a little with the work (applied on success, below).
        // Tool classes without a keyed toolset gate on nothing, exactly as before.
        String toolClass = (String) match.get("tool_class");
        java.util.Map<String,Object> toolUsed = null;
        if (toolClass != null && (toolClass.equals("CUTTING") || toolClass.equals("STRIKING") || toolClass.equals("AXE"))) {
            toolUsed = soundestToolOfClass(chronicle, location, toolClass);
            if (toolUsed == null)
                // Name the work and name the tool (#37). "an edge, a hammer, an axe, whatever it needs" is a
                // shrug, and the process is not vague about it — tool_class is right there in the row that was
                // just read. #706 fixed the same shrug on assembly stages; this is the material-process half.
                return new String[]{"FAILED", capitalise(((String) match.get("display_name")).toLowerCase(java.util.Locale.ROOT))
                    + " turns on " + toolPhrase(toolClass) + ", and there is none within reach. Bare hands only bruise the material."};
            if ("BROKEN".equals(toolUsed.get("cond")) || "DESTROYED".equals(toolUsed.get("cond")))
                return new String[]{"FAILED", "The tool this work turns on is past biting — its edge gone or its head loose. Mend it against a whetstone or with cordage before it will serve."};
        }
        if (Boolean.TRUE.equals(match.get("requires_fire")) && !heatToWorkWith(location, at)) {
            return new String[]{"FAILED", "This work needs heat, and none is within reach of it — no fire burning, and no stone still holding yesterday's. Cold, the material will not give."};
        }
        if (Boolean.TRUE.equals(match.get("requires_salt_water")) && !saltWaterToWorkWith(location)) {
            return new String[]{"FAILED", "This work turns on salt water, and there is none within reach of it. Fresh water will not do — what it takes from the sea is not in a spring."};
        }
        if (Boolean.TRUE.equals(match.get("requires_water")) && !waterToWorkWith(location)) {
            return new String[]{"FAILED", "This work needs water, and there is none at hand to work it with. Dry, the process cannot even begin."};
        }

        // Fixed inputs, then each either/or group.
        // Inputs may be carried OR lying on the ground at the chronicle's location, so a
        // felled log is worked where it fell rather than needing to be shouldered whole (#17).
        java.util.List<java.util.Map<String,Object>> fixed = jdbc.queryForList(
            "SELECT item_key, quantity FROM material_process_input WHERE process_key=?", key);
        for (java.util.Map<String,Object> in : fixed)
            if (!hasAtLeastHere(chronicle, location, (String) in.get("item_key"), ((Number) in.get("quantity")).intValue())) {
                String need = ((String) in.get("item_key")).replace('_', ' ');
                String where = knownLocationOf(chronicle, (String) in.get("item_key"), location);
                // Three situations, and there used to be two sentences (#37). A Chronicle holding two bundles of
                // fibre for a three-bundle job was told "it lies wherever you last set it down, and you have not
                // brought it" — about fibre that was in their hands. Short is not the same as absent, and telling
                // a player to go and fetch what they are already carrying is worse than saying nothing.
                int have = reachCount(chronicle, location, (String) in.get("item_key"));
                int wants = ((Number) in.get("quantity")).intValue();
                return new String[]{"FAILED", have > 0
                    ? "You lay out what you have — " + have + " " + need + " — and it is not enough: this takes " + wants + "."
                    : where != null
                    ? "You have not got enough " + need + " within reach. What there is of it sits at " + where + ", not here."
                    : "You have not got enough " + need + " within reach. What is missing is not here — it lies wherever you last set it down, and you have not brought it."};
            }

        java.util.List<String> groups = jdbc.queryForList(
            "SELECT DISTINCT group_name FROM material_process_input_group WHERE process_key=?", String.class, key);
        java.util.Map<String,String> chosen = new java.util.LinkedHashMap<>();
        for (String g : groups) {
            String pick = null;
            for (java.util.Map<String,Object> o : jdbc.queryForList(
                    "SELECT item_key, quantity FROM material_process_input_group WHERE process_key=? AND group_name=?", key, g))
                if (hasAtLeastHere(chronicle, location, (String) o.get("item_key"), ((Number) o.get("quantity")).intValue())) { pick = (String) o.get("item_key"); break; }
            if (pick == null) return new String[]{"FAILED", "Nothing within reach is suitable to work from. What this step wants, you do not have here to give it."};
            chosen.put(g, pick);
        }

        String outKey = (String) match.get("output_item_key");

        // Quality flows: the output is never better than the worst input or the care
        // of the attempt (M3b). Read the input grades before consuming them.
        java.util.List<String> inputKeys = new java.util.ArrayList<>();
        for (java.util.Map<String,Object> in : fixed) inputKeys.add((String) in.get("item_key"));
        inputKeys.addAll(chosen.values());
        // Quality is majorly the craftsman: the attempt (skill/care) and the materials. A reachable workstation
        // lifts the attempt by ONE step (a stable held surface aids precision at the margin) — but worst() still
        // caps it against the materials, so the assist is minor and never rescues poor stock or poor work.
        QualityGrade attempt = QualityGrade.attempt(actionText);
        // A sewing kit — a bone awl to pierce the holes, a bone needle to draw the thread through — lifts the
        // stitching one step, the same minor, bounded assist a workstation gives, still capped against the
        // leather's own grade by worst(). Both were craftable but read by nothing until now (#257).
        boolean sewingKit = key.startsWith("sew_") && hasAtLeast(chronicle, "bone_needle", 1) && hasAtLeast(chronicle, "bone_awl", 1);
        // An antler pressure flaker presses fine flakes off the edge with a control a hammerstone's percussion
        // cannot match — it lifts the workmanship of a fine knap (arrowheads, a scraper, a burin) one step, the
        // same minor, bounded assist, still capped against the stone's own grade. Craftable but inert until now (#257).
        boolean flaker = (key.equals("knap_arrowheads") || key.equals("knap_scraper") || key.equals("knap_burin"))
            && hasAtLeast(chronicle, "antler_flaker", 1);
        // A bone comb cards and aligns the fibres before they are drawn out — combed wool spins to a finer, more
        // even thread, so it lifts a spin one grade (distinct from a drop spindle, which only speeds the yield).
        // Craftable but inert until now (#257); the finer yarn carries through to finer cloth by worst().
        boolean woolComb = key.equals("spin_wool_yarn") && hasAtLeast(chronicle, "bone_comb", 1);
        // A wooden spoon keeps a pot moving so it cooks evenly and nothing catches and scorches on the bottom —
        // it lifts a pot-cook one grade (stew, porridge, wilted greens, cooked mushrooms), the same bounded
        // assist, still capped against the ingredients by worst(). Terminal now that food grade scales
        // nourishment: a finer-cooked meal nourishes a little more (#271), so the spoon finally earns its keep (#257).
        boolean stirringSpoon = (key.equals("cook_root_stew") || key.equals("cook_porridge") || key.equals("cook_greens") || key.equals("cook_mushrooms"))
            && hasAtLeast(chronicle, "wooden_spoon", 1);
        // Seasoned wood holds true where green stock warps and checks as it dries, so joinery worked from seasoned
        // planks or timber comes out truer — it lifts the workmanship of a fit-and-assemble one grade, the same
        // minor, bounded assist, still capped against the stock's own grade by worst(). Closes the seasoned-wood
        // dead-read (#200/#203): the season processes made it, but nothing read it — a wood a Chronicle seasons
        // now earns better work from it. (Shares the single one-step lift, so it does not stack past the cap.)
        boolean seasonedStock = (key.equals("cut_mortise") || key.equals("cut_tenon") || key.equals("dovetail_corner")
                || key.equals("lap_joint_planks") || key.equals("scarf_joint") || key.equals("edge_join_boards")
                || key.equals("assemble_frame") || key.equals("turn_dowels") || key.equals("shape_components"))
            && (hasAtLeast(chronicle, "seasoned_plank", 1) || hasAtLeast(chronicle, "seasoned_timber", 1));
        // A copper chisel takes finer, more controlled parings than any stone or bone edge, and holds that edge
        // through the work — so a carve worked with one to hand comes out truer, lifting the workmanship one grade,
        // the same bounded assist (shared, capped by the stock's own grade). This is the first metal's terminal
        // payoff (#180/#184): the whole ore -> smelt -> forge chain finally earns better work than stone.
        boolean copperChisel = (key.equals("carve_wooden_bowl") || key.equals("carve_soapstone_bowl")
                || key.equals("carve_wooden_spoon") || key.equals("carve_water_ladle") || key.equals("carve_pegs"))
            && hasAtLeast(chronicle, "copper_chisel", 1);
        // A bronze knife holds a fine, edge-keeping blade that parts hide and fish far cleaner than a knapped flake
        // or a bone edge, so the close cutting work comes out truer — the same bounded, capped one-grade lift. The
        // era's everyday blade earning its keep beside the spear and axe (#180/#185).
        boolean bronzeKnife = (key.equals("skin_fish") || key.equals("fillet_fish") || key.equals("gut_fish")
                || key.equals("cut_lamellae") || key.equals("cut_boot_soles"))
            && hasAtLeast(chronicle, "bronze_knife", 1);
        if (atStation || sewingKit || flaker || woolComb || stirringSpoon || seasonedStock || copperChisel || bronzeKnife) attempt = attempt.up();
        QualityGrade grade = QualityGrade.worst(worstGradeAmong(chronicle, inputKeys), attempt);

        for (java.util.Map<String,Object> in : fixed)
            for (int i = 0; i < ((Number) in.get("quantity")).intValue(); i++) consumeOneHere(chronicle, location, (String) in.get("item_key"), at);
        for (java.util.Map.Entry<String,String> e : chosen.entrySet()) {
            Integer q = jdbc.queryForObject("SELECT quantity FROM material_process_input_group WHERE process_key=? AND group_name=? AND item_key=?", Integer.class, key, e.getKey(), e.getValue());
            for (int i = 0; i < (q == null ? 1 : q); i++) consumeOneHere(chronicle, location, e.getValue(), at);
        }

        // Yield: a workstation wastes less, so it biases the output toward the high end of the range (efficiency,
        // the station's real payoff) — the max of two rolls rather than one. Never changes the min guarantee.
        int lo = ((Number) match.get("output_min")).intValue(), hi = ((Number) match.get("output_max")).intValue();
        double roll = Math.random();
        // A right tool in hand biases a yield toward its high end, the same way a workstation eases a bench
        // craft (#257). It never gates the work — bare hands still manage — only improves it and only for the
        // processes it suits, so tools that were craftable-but-inert finally earn their keep: a mortar and
        // pestle for grinding grain/salt/pigment, a drop spindle for spinning fleece into yarn, and a drying mat
        // that spreads food so air passes above and below and it dries evenly — less spoils, so more is preserved.
        boolean toolAssist =
            ((key.equals("grind_flour") || key.equals("grind_salt") || key.equals("grind_pigment"))
                && hasAtLeast(chronicle, "stone_mortar", 1) && hasAtLeast(chronicle, "stone_pestle", 1))
            || (key.equals("spin_wool_yarn") && hasAtLeast(chronicle, "drop_spindle", 1))
            || ((key.equals("dry_fish") || key.equals("dry_meat") || key.equals("dry_mushrooms") || key.equals("dry_herbs"))
                && hasAtLeast(chronicle, "drying_mat", 1))
            // A wooden mallet drives the froe or wedge through the grain with even, controlled blows, so a log
            // rives cleaner and less is wasted to run-out — more usable stock off the same wood. The axe still
            // does the work; the mallet only wastes less. Craftable but inert until now (#257).
            || ((key.equals("split_planks") || key.equals("rive_shakes") || key.equals("rive_bow_stave") || key.equals("split_wedges"))
                && hasAtLeast(chronicle, "wooden_mallet", 1));
        if ((atStation || toolAssist) && hi > lo) roll = Math.max(roll, Math.random());
        int made = Math.max(1, lo + (hi > lo ? (int)(roll*(hi-lo+1)) : 0));
        String outName = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, outKey);
        String kind = keepKindFor(outKey);
        // Carried while there is room, then set on the ground in front — a process never fails for
        // want of carrying room (GitHub #19); the worked material lies where it was made.
        for (int i = 0; i < made; i++) {
            UUID madeId = UUID.randomUUID();
            createCraftedItem(chronicle, location, madeId, outKey, outName, at, "PROCESSED", grade);
            if (kind != null) registerPreserved(madeId, kind, at);
        }
        // Multi-output (V60): a process may yield several kinds in the same act, with
        // yield scaled by the grade of the work — a well-planned layout wastes less.
        double yieldFactor = switch (grade) { case DEFECTIVE -> 0.0; case POOR -> 0.34; case SOUND -> 0.67; case FINE -> 1.0; };
        for (java.util.Map<String,Object> o : jdbc.queryForList("SELECT item_key, qty_min, qty_max FROM material_process_output WHERE process_key=?", key)) {
            String ok = (String) o.get("item_key");
            int omin = ((Number) o.get("qty_min")).intValue(), omax = ((Number) o.get("qty_max")).intValue();
            int n = omin + (int) Math.round((omax - omin) * yieldFactor);
            if (n <= 0) continue;
            String on = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, ok);
            String okind = keepKindFor(ok);
            for (int i = 0; i < n; i++) { UUID mid = UUID.randomUUID(); createCraftedItem(chronicle, location, mid, ok, on, at, "PROCESSED", grade); if (okind != null) registerPreserved(mid, okind, at); }
        }
        // The tool wears with the work (#220), the same way the axe wears with felling: a use accrues and at
        // thresholds the edge/head steps down (SOUND->WORN at 8, ->BROKEN at 16), kept in history. A worn tool
        // still serves; a broken one is refused above until it is mended (which resets the wear).
        if (toolUsed != null) {
            UUID tid = (UUID) toolUsed.get("id");
            String tc = (String) toolUsed.get("cond");
            int tu = (int) toolUsed.get("uses") + 1;
            int[] tw = toolWearThresholds((String) toolUsed.get("key"));
            String nc = tu >= tw[1] ? "BROKEN" : tu >= tw[0] ? "WORN" : tc;
            jdbc.update("UPDATE item_instance SET use_count=?, condition_state=? WHERE object_id=?", tu, nc, tid);
            if (!nc.equals(tc))
                jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'TOOL_WORN',jsonb_build_object('from',?,'to',?))", tid, Timestamp.from(at), tc, nc);
        }
        return new String[]{"SUCCEEDED", (String) match.get("narration") + gearThatFitsNothingKept(chronicle, outKey)};
    }

    /**
     * A word at the one moment a keeper would want it (#106): they have just made a piece of draft gear that
     * will not go on any beast they keep.
     *
     * <p>V371 sized the gear to the body, and said nothing to anybody about it. {@code workDraftBeasts} runs in
     * the tick and has no narration — gear has always been silent — so a Chronicle whose only beasts are oxen
     * could make a collar harness, get no benefit from it for ever, and never be told why. Making it is the only
     * boundary where a person is stood over the thing with it in their hands, so this is where it is said.
     *
     * <p>It reports rather than refuses. The harness is real, it is theirs, and it will fit the horse they have
     * not tamed yet: a keeper may make gear ahead of the animal, and this is not the place to argue about it.
     * Silent for anything that is not draft gear, and for a keeper who keeps nothing at all — with no beasts,
     * there is nothing for it not to fit.
     */
    private String gearThatFitsNothingKept(UUID chronicle, String outKey) {
        if (outKey == null) return "";
        Integer ceiling = jdbc.query("SELECT body_size_rank(fits_up_to_size) FROM draft_gear WHERE item_key=?",
            rs -> rs.next() ? rs.getInt(1) : null, outKey);
        if (ceiling == null) return "";
        Integer biggest = jdbc.queryForObject(
            "SELECT COALESCE(MAX(body_size_rank(ws.size_tier)), 0) FROM wildlife_bond wb " +
            "JOIN wildlife_population wp ON wp.id=wb.population_id " +
            "JOIN wildlife_species ws ON ws.species_key=wp.species_key " +
            "JOIN draft_species ds ON ds.species_key=wp.species_key " +
            "WHERE wb.chronicle_id=? AND wb.bond_stage='TAMED'", Integer.class, chronicle);
        if (biggest == null || biggest == 0 || biggest <= ceiling) return "";
        return " You hold it up against the beasts you keep, and it is plainly too small for any of them — "
             + "made for something lighter-necked than anything standing here.";
    }

    /** Use-based wear thresholds {WORN-at, BROKEN-at} for a tool, scaled by its metal: harder metal holds its edge
     *  through far more work, so stone(8/16) &lt; copper(12/24) &lt; bronze(16/32) &lt; iron(24/48) &lt; steel(32/64).
     *  Keyed on the metal prefix so it covers every metal axe, knife, or other worn tool at once (#180 durability). */
    private static int[] toolWearThresholds(String key) {
        String k = key == null ? "" : key;
        if (k.startsWith("steel_"))  return new int[]{32, 64};
        if (k.startsWith("iron_"))   return new int[]{24, 48};
        if (k.startsWith("bronze_")) return new int[]{16, 32};
        if (k.startsWith("copper_")) return new int[]{12, 24};
        return new int[]{8, 16}; // knapped stone, bone, flint, and the bare-hand tools
    }

    /** A day of lying in standing water accrues a use of corrosion — a metal tool rusts noticeably over a wet fortnight. */
    private static final int RUST_HOURS_PER_USE = 12;

    /**
     * Corrode the iron and steel left on wet ground (#220 rust). Unlike use-wear (V139), this bites unmaintained metal
     * by exposure alone: an iron axe or a steel striker dropped in a bog or left out in the wet rusts as it lies there.
     * Only <b>unowned ground stock at a wet biome</b> corrodes — metal carried on the body is kept dry and maintained,
     * and bronze/copper/stone/bone do not rust. Elapsed hours since it was last accounted accrue use-count wear (a use
     * per {@link #RUST_HOURS_PER_USE} hours), and at the metal's thresholds the condition steps down the same
     * SOUND→WORN→BROKEN ladder work climbs; the step is kept as RUSTED evidence. Run in the world tick.
     */
    @Transactional
    public void weatherExposedMetal(Instant now) {
        java.sql.Timestamp ts = java.sql.Timestamp.from(now);
        java.util.List<java.util.Map<String,Object>> exposed = jdbc.queryForList(
            "SELECT i.object_id, i.item_key, i.use_count, i.condition_state, i.weathered_at " +
            "FROM item_instance i JOIN world_object w ON w.id=i.object_id JOIN world_chunk wc ON wc.id=w.current_location_id " +
            "WHERE w.lifecycle_state='ACTIVE' AND w.current_owner_id IS NULL AND wc.biome IN ('WETLAND','RIVER_BANK') " +
            "AND i.condition_state <> 'BROKEN' AND i.item_key <> 'iron_pyrite' " +
            "AND (i.item_key LIKE 'iron\\_%' OR i.item_key LIKE 'steel\\_%')");
        for (java.util.Map<String,Object> r : exposed) {
            java.util.UUID id = (java.util.UUID) r.get("object_id");
            java.sql.Timestamp weatheredAt = (java.sql.Timestamp) r.get("weathered_at");
            if (weatheredAt == null) { // first exposure: start the clock, corrode from here on
                jdbc.update("UPDATE item_instance SET weathered_at=? WHERE object_id=?", ts, id);
                continue;
            }
            long hours = java.time.Duration.between(weatheredAt.toInstant(), now).toHours();
            int add = (int) (hours / RUST_HOURS_PER_USE);
            if (add <= 0) continue;
            int newUses = ((Number) r.get("use_count")).intValue() + add;
            int[] wear = toolWearThresholds((String) r.get("item_key"));
            String cond = newUses >= wear[1] ? "BROKEN" : newUses >= wear[0] ? "WORN" : "SOUND";
            jdbc.update("UPDATE item_instance SET use_count=?, condition_state=?, weathered_at=? WHERE object_id=?", newUses, cond, ts, id);
            if (!cond.equals(r.get("condition_state")))
                jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'RUSTED',jsonb_build_object('condition',?))", id, ts, cond);
        }
    }

    /** A raw hide, pelt, or length of sinew left in the wet putrefies in a few days — the reason a fresh hide must be fleshed, dried, or salted before it is lost. */
    private static final int ROT_HOURS_TO_DESTROY = 96;

    /**
     * Rot the raw animal tissue left on wet ground (#220 rot). A fresh hide, pelt, or sinew is meat still: dropped in a
     * bog or left out in the wet, it putrefies and is lost. Only <b>unowned ground stock at a wet biome</b> rots —
     * carried stock is kept and worked, and a hide once salted (or tanned into leather goods) is preserved. Unlike
     * metal, which weakens by degrees, raw tissue rots away entirely: after {@link #ROT_HOURS_TO_DESTROY} hours of
     * exposure the item is destroyed (ROTTED), since a rotted material must actually be gone to matter — its condition
     * alone is not read. Shares the {@code weathered_at} exposure clock with corrosion. Run in the world tick.
     */
    /** Unfired clay is soft earth: hours of wet destroy it outright, faster than raw tissue rots. */
    private static final int SLAKE_HOURS_TO_DESTROY = 12;

    /**
     * Slake unfired greenware left out on wet ground (#59). A formed-but-unfired bowl or cup is still soft earth —
     * left in the wet it takes up water, slumps, and is lost; only the fire makes it permanent. Like
     * {@link #rotExposedOrganics}, only <b>unowned ground stock at a wet biome</b> slakes: greenware carried or set
     * under cover is kept dry, and a vessel once fired is stone and immune. Shares the {@code weathered_at} exposure
     * clock. Run in the world tick.
     */
    @Transactional
    public void slakeExposedGreenware(Instant now) {
        java.sql.Timestamp ts = java.sql.Timestamp.from(now);
        java.util.List<java.util.Map<String,Object>> exposed = jdbc.queryForList(
            "SELECT i.object_id, i.weathered_at FROM item_instance i JOIN world_object w ON w.id=i.object_id JOIN world_chunk wc ON wc.id=w.current_location_id " +
            "WHERE w.lifecycle_state='ACTIVE' AND w.current_owner_id IS NULL AND wc.biome IN ('WETLAND','RIVER_BANK') " +
            // Green ware is anything not yet fired, and the catalogue names it so. The two keys this replaces
            // missed unfired_vessel — which form_vessel makes and fire_vessel fires, wet clay by every test that
            // matters — so a bowl and a cup slumped in the rain while the same clay called a vessel did not.
            // clay_jar deliberately stays out: no process fires it, so treating it as green ware would leave it
            // slumping with no way to save it.
            "AND i.item_key LIKE 'unfired%'");
        for (java.util.Map<String,Object> r : exposed) {
            java.util.UUID id = (java.util.UUID) r.get("object_id");
            java.sql.Timestamp weatheredAt = (java.sql.Timestamp) r.get("weathered_at");
            if (weatheredAt == null) { // first exposure: start the clock, slake from here on
                jdbc.update("UPDATE item_instance SET weathered_at=? WHERE object_id=?", ts, id);
                continue;
            }
            if (java.time.Duration.between(weatheredAt.toInstant(), now).toHours() >= SLAKE_HOURS_TO_DESTROY) {
                jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED', destroyed_at=?, destroyed_location_id=current_location_id, destroyed_cause='SLAKED', current_location_id=NULL WHERE id=?", ts, id);
                jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'SLAKED','{}'::jsonb)", id, ts);
            }
        }
    }

    @Transactional
    public void rotExposedOrganics(Instant now) {
        java.sql.Timestamp ts = java.sql.Timestamp.from(now);
        java.util.List<java.util.Map<String,Object>> exposed = jdbc.queryForList(
            "SELECT i.object_id, i.weathered_at FROM item_instance i JOIN world_object w ON w.id=i.object_id JOIN world_chunk wc ON wc.id=w.current_location_id " +
            "WHERE w.lifecycle_state='ACTIVE' AND w.current_owner_id IS NULL AND wc.biome IN ('WETLAND','RIVER_BANK') " +
            "AND ((i.item_key LIKE '%hide' AND i.item_key <> 'salted_hide') OR i.item_key LIKE '%pelt' OR i.item_key = 'animal_sinew')");
        for (java.util.Map<java.lang.String,java.lang.Object> r : exposed) {
            java.util.UUID id = (java.util.UUID) r.get("object_id");
            java.sql.Timestamp weatheredAt = (java.sql.Timestamp) r.get("weathered_at");
            if (weatheredAt == null) { // first exposure: start the clock, rot from here on
                jdbc.update("UPDATE item_instance SET weathered_at=? WHERE object_id=?", ts, id);
                continue;
            }
            if (java.time.Duration.between(weatheredAt.toInstant(), now).toHours() >= ROT_HOURS_TO_DESTROY) {
                jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED', destroyed_at=?, destroyed_location_id=current_location_id, destroyed_cause='ROTTED', current_location_id=NULL WHERE id=?", ts, id);
                jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'ROTTED','{}'::jsonb)", id, ts);
            }
        }
    }

    /** How long sown grain takes to come up as a ripe stand — a season in the open ground. */
    private static final int CROP_MATURITY_DAYS = 30;
    /** One sown seed comes up as a stand worth several heads — the multiplication that makes cultivation worth the labour. */
    private static final int CROP_YIELD_HEADS = 4;
    /** A tilled seedbed yields a fuller stand — the reward for breaking the ground before sowing. */
    private static final int CROP_YIELD_HEADS_TILLED = 6;
    /** Below this the soil is worn and gives a thinner stand (#164). */
    private static final int FERTILITY_LOW_THRESHOLD = 60;
    /**
     * Heat enough to work by, here and now (#77) — a fire burning, or stone still holding one.
     *
     * <p>An earth oven is a pit lined with close-set stone, filled with fire until the rock is soaked through,
     * then raked out and covered. The stones hold that heat for hours and the food cooks in it with no flame at
     * all. That is the whole technology, it is how people have cooked in pits everywhere they have lived, and it
     * is the only reason to dig one rather than lay a fire on the ground.
     *
     * <p>Which structures do it, and for how long, is declared in {@code construction_kind.retained_heat_minutes}
     * rather than named here — the same move V289 made for what can hold a fire at all. A hearth with no retained
     * heat reads as zero and behaves exactly as it always has, so nothing that worked before changes.
     */
    private boolean heatToWorkWith(UUID location, Instant at) {
        Boolean burning = jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM fire_state fs JOIN world_object w ON w.id=fs.construction_id " +
            "WHERE w.current_location_id=? AND fs.active=true)", Boolean.class, location);
        if (Boolean.TRUE.equals(burning)) return true;
        // A fire that has gone out in something built to hold its heat still counts, until the stone gives it up.
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM fire_state fs " +
            "JOIN world_object w ON w.id=fs.construction_id " +
            "JOIN construction_project cp ON cp.object_id=fs.construction_id " +
            "JOIN construction_kind ck ON ck.project_kind=cp.project_kind " +
            "WHERE w.current_location_id=? AND fs.active=false AND ck.retained_heat_minutes > 0 " +
            // The cast is not decoration: without it Postgres cannot infer the parameter's type and reads the
            // whole expression as an interval — "operator does not exist: timestamp with time zone > interval".
            "  AND fs.last_updated_at > CAST(? AS timestamptz) - make_interval(mins => ck.retained_heat_minutes))",
            Boolean.class, location, java.sql.Timestamp.from(at)));
    }

    /** What one harvest takes from a field's fertility. */
    private static final int FERTILITY_COST_PER_HARVEST = 30;
    /** How much fertility a field wins back for each day left fallow. */
    private static final int FERTILITY_RECOVER_PER_DAY = 2;
    /**
     * What a floodplain wins back instead (#156). A floodplain is farmland that renews itself: the flood lays new
     * silt over it, which is why river valleys were cropped continuously for thousands of years while ground on
     * the terrace above had to be rested. It is the one piece of ground where working it hard is not a mistake.
     */
    private static final int FERTILITY_RECOVER_PER_DAY_ON_FLOODPLAIN = 5;

    /** A shell bed on this ground (#157) — dense enough to reseed itself, unlike a scatter over open shore. */
    /** Is the place this colony concentrates in standing on this ground (V339)? Matched as a fragment of the kind. */
    private boolean concentrationAt(UUID chunk, String siteKind) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM ecology_site WHERE chunk_id=? AND site_kind ILIKE ?)",
            Boolean.class, chunk, "%" + siteKind + "%"));
    }

    /** Ground the river renews — a floodplain site, which the world places on a river bank or a marsh margin. */
    private boolean floodplainAt(UUID chunk) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM ecology_site WHERE chunk_id=? AND site_kind ILIKE '%floodplain%')",
            Boolean.class, chunk));
    }

    /** A field's current fertility (#164), pristine ground reading full — the stored level plus what fallow time since
     *  it was last worked has restored (not persisted until the next harvest writes it). */
    private int fieldFertility(UUID chunk, Instant at) {
        java.util.Map<String,Object> soil = jdbc.query(
            "SELECT fertility, last_updated_at FROM field_soil WHERE chunk_id=?",
            rs -> rs.next() ? java.util.Map.of("f", rs.getInt(1), "t", rs.getTimestamp(2).toInstant()) : null, chunk);
        if (soil == null) return 100; // never cropped — pristine
        long fallowDays = Math.max(0, java.time.Duration.between((Instant) soil.get("t"), at).toDays());
        int perDay = floodplainAt(chunk) ? FERTILITY_RECOVER_PER_DAY_ON_FLOODPLAIN : FERTILITY_RECOVER_PER_DAY;
        // Dung and compost put back what a harvest took (#77, V332). A sound manure pit or compost bay on this ground
        // renews the field faster than resting it; the faster rate applies, since a flood already spreads what a heap
        // would. Read from construction_kind.fertilises_field, not named here.
        Integer dunged = jdbc.queryForObject(
            "SELECT COALESCE(MAX(ck.fertilises_field),0) FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "JOIN construction_kind ck ON ck.project_kind=cp.project_kind " +
            "WHERE w.current_location_id=? AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND w.lifecycle_state='ACTIVE'",
            Integer.class, chunk);
        if (dunged != null && dunged > perDay) perDay = dunged;
        return Math.min(100, (int) soil.get("f") + (int) (fallowDays * perDay));
    }

    /** Draw a field's fertility down by a harvest (#164), from its fallow-recovered current level, and stamp the time. */
    private void depleteFertility(UUID chunk, Instant at) {
        int next = Math.max(0, fieldFertility(chunk, at) - FERTILITY_COST_PER_HARVEST);
        java.sql.Timestamp ts = java.sql.Timestamp.from(at);
        jdbc.update("INSERT INTO field_soil (chunk_id, fertility, last_updated_at) VALUES (?,?,?) " +
            "ON CONFLICT (chunk_id) DO UPDATE SET fertility=EXCLUDED.fertility, last_updated_at=EXCLUDED.last_updated_at", chunk, next, ts);
    }
    /**
     * Open, workable ground in wet country (#156) — a floodplain, or a marsh island standing dry above the water.
     *
     * <p>This is what the floodplain was missing. The flood-silt fertility rule above was written against ground no
     * Chronicle could ever sow: a floodplain sits on RIVER_BANK or WETLAND, which {@link #isArable} refused, and
     * {@link #clearLand} takes woodland only — so the ground that renews itself fastest was ground you could not
     * put a field on at all. The rule was real and unreachable, which is the same as absent.
     */
    private boolean dryWorkableGroundAt(UUID chunk) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM ecology_site WHERE chunk_id=? " +
            "AND (site_kind ILIKE '%floodplain%' OR site_kind ILIKE '%marsh island%'))", Boolean.class, chunk));
    }

    /** Ground a crop will take (#164/#165): open grassland by nature, forest ground a Chronicle has cleared, or the
     *  dry silt of a floodplain or marsh island. Rock, ocean, mountain, open bog, and standing forest are not arable
     *  until the trees are taken off them. */
    private boolean isArable(UUID location) {
        String biome = jdbc.query("SELECT biome FROM world_chunk WHERE id=?", rs -> rs.next() ? rs.getString(1) : null, location);
        if ("GRASSLAND".equals(biome)) return true;
        if (dryWorkableGroundAt(location)) return true;
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM cleared_ground WHERE chunk_id=?)", Boolean.class, location));
    }

    /**
     * Clear wooded ground into arable land (#165 land-clearing). Cultivation began only on open grassland; a Chronicle
     * standing in forest could not make a field. With an axe, the trees are felled, the brush cut back, and the roots
     * grubbed out until an open patch of arable earth lies where the forest stood — ground that tilling and sowing will
     * then take. Clearing is permanent: the field, once won, stays open. Wants wooded ground and an axe to take it off.
     */
    @Transactional
    public String[] clearLand(UUID chronicle, UUID location, Instant at) {
        String biome = jdbc.query("SELECT biome FROM world_chunk WHERE id=?", rs -> rs.next() ? rs.getString(1) : null, location);
        if (!"TEMPERATE_FOREST".equals(biome) && !"FOREST".equals(biome))
            return new String[]{"FAILED", "This ground bears no timber or brush to clear for a field — clearing is for turning wooded ground into arable land, and this is not woodland."};
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM cleared_ground WHERE chunk_id=?)", Boolean.class, location)))
            return new String[]{"FAILED", "This ground is already cleared — the trees are off it and it lies open, arable. There is nothing left to clear."};
        boolean axe = Boolean.TRUE.equals(jdbc.queryForObject(
            "WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE'" +
            " UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id" +
            " JOIN world_object n ON n.id=ic.item_id WHERE n.lifecycle_state='ACTIVE')" +
            " SELECT EXISTS(SELECT 1 FROM reachable r JOIN item_instance i ON i.object_id=r.id" +
            // Same registry, same reason (#93): the felled-tree question is "have I an axe", and tool_profile is
            // where the catalogue answers it. Note a hand axe is deliberately not one: you can cut and scrape
            // with a knapped biface, but you cannot fell woodland with a stone held in the fist.
            " WHERE i.item_key IN (SELECT item_key FROM tool_profile WHERE tool_class='AXE')" +
            " AND i.condition_state <> 'BROKEN')", Boolean.class, chronicle));
        if (!axe)
            return new String[]{"FAILED", "Clearing wooded ground wants an axe to fell the trees and cut back the brush, and you have none sound to hand."};
        jdbc.update("INSERT INTO cleared_ground (chunk_id, cleared_at) VALUES (?,?) ON CONFLICT (chunk_id) DO NOTHING",
            location, java.sql.Timestamp.from(at));
        return new String[]{"SUCCEEDED", "You work the wooded ground clear — trees felled, brush cut and hauled off, the roots grubbed out — until an open patch of arable earth lies where the forest stood, ready to be broken for a seedbed."};
    }

    /** How long a tilled seedbed stays workable before the sod closes over again. */
    private static final int TILL_WINDOW_DAYS = 7;

    /**
     * Break and turn open ground into a seedbed (#165 land preparation). With a digging tool, a Chronicle loosens a
     * grassland patch so a sowing takes better root — tilled ground a later sowing draws on for a fuller stand. It
     * wants open, workable ground and a tool to break it; it holds for a few days before the sod closes over.
     */
    @Transactional
    public String[] tillGround(UUID chronicle, UUID location, Instant at) {
        if (!isArable(location))
            return new String[]{"FAILED", "Tillage wants open, workable ground — a grassland clearing, wooded ground you have first cleared, or the dry silt of a floodplain — and this ground is not it."};
        if (!hasAtLeast(chronicle, "digging_stick", 1) && !hasAtLeast(chronicle, "wooden_shovel", 1)
                && !hasAtLeast(chronicle, "fire_hardened_digging_stick", 1))
            return new String[]{"FAILED", "Breaking ground wants a tool — a digging stick or a shovel — and you have none to hand."};
        Integer growing = jdbc.queryForObject("SELECT COUNT(*) FROM crop_stand WHERE chunk_id=? AND harvested=false", Integer.class, location);
        if (growing != null && growing > 0)
            return new String[]{"FAILED", "A crop already stands on this ground; there is nothing to till until it is reaped."};
        jdbc.update("INSERT INTO tilled_ground (chunk_id, tilled_at) VALUES (?,?) ON CONFLICT (chunk_id) DO UPDATE SET tilled_at=EXCLUDED.tilled_at",
            location, java.sql.Timestamp.from(at));
        return new String[]{"SUCCEEDED", "You break and turn the open ground, loosening the sod into a seedbed ready to take the seed."};
    }
    /** How long a ripe stand yields in full before the heads begin to shatter and a late harvest saves less. */
    private static final int CROP_FULL_YIELD_DAYS = 14;

    /**
     * Sow seed grain into open ground (#162 agriculture — seed → soil → crop). The grain a Chronicle carries can be
     * worked into a grassland clearing as a crop, rather than eaten or ground: it comes up, over a season, as a stand
     * that yields far more than the seed put in. One stand per ground at a time; grain wants open workable ground, not
     * forest, mountain, or bog. This is the first step of farming — the wild-forage grain finally has somewhere to go
     * but the quern.
     */
    @Transactional
    public String[] sowCrop(UUID chronicle, UUID location, Instant at) {
        if (!isArable(location))
            return new String[]{"FAILED", "Grain wants open, workable ground — a grassland clearing, wooded ground you have first cleared, or the dry silt of a floodplain — and this ground is not it."};
        Integer growing = jdbc.queryForObject("SELECT COUNT(*) FROM crop_stand WHERE chunk_id=? AND harvested=false", Integer.class, location);
        if (growing != null && growing > 0)
            return new String[]{"FAILED", "A crop is already coming up on this ground; there is no room to sow another until it is reaped."};
        if (!hasAtLeast(chronicle, "wild_grain", 1))
            return new String[]{"FAILED", "You have no seed grain to sow — a handful of grain must come to hand first."};
        // A seedbed tilled within the last few days gives a fuller stand; sowing consumes that prepared ground.
        boolean tilled = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM tilled_ground WHERE chunk_id=? AND tilled_at >= ?)",
            Boolean.class, location, java.sql.Timestamp.from(at.minus(java.time.Duration.ofDays(TILL_WINDOW_DAYS)))));
        consumeOne(chronicle, "wild_grain", at);
        jdbc.update("INSERT INTO crop_stand (id, chunk_id, crop_key, sown_at, maturity_days, harvested, tilled) VALUES (?,?,?,?,?,false,?)",
            UUID.randomUUID(), location, "wild_grain", java.sql.Timestamp.from(at), CROP_MATURITY_DAYS, tilled);
        jdbc.update("DELETE FROM tilled_ground WHERE chunk_id=?", location);
        return new String[]{"SUCCEEDED", tilled
            ? "You work the seed grain into the tilled seedbed and cover it over — the broken ground will give it a fuller root, and a fuller stand come the season's turn."
            : "You work the seed grain into the unbroken ground and cover it over. It will come up, though thinner than from a tilled seedbed, by the season's turn."};
    }

    /**
     * Reap a ripe crop stand (#162 — harvest). A stand grown to maturity is cut and gathered as wild grain heads —
     * several for the one seed sown — which thresh to grain and grind to flour by the chain that already exists, so the
     * cultivated grain is functional end-to-end. A green stand is refused: reaping it early only wastes the crop.
     */
    @Transactional
    public String[] harvestCrop(UUID chronicle, UUID location, Instant at) {
        java.util.Map<String,Object> crop = jdbc.query(
            "SELECT id, sown_at, maturity_days, tilled, grazed, (weeded_at IS NOT NULL) AS weeded, (watered_at IS NOT NULL) AS watered, birds_scared_at FROM crop_stand WHERE chunk_id=? AND harvested=false ORDER BY sown_at LIMIT 1 FOR UPDATE",
            rs -> {
                if (!rs.next()) return null;
                java.util.Map<String,Object> row = new java.util.HashMap<>();
                row.put("id", rs.getObject(1, UUID.class));
                row.put("sown", rs.getTimestamp(2).toInstant());
                row.put("days", rs.getInt(3));
                row.put("tilled", rs.getBoolean(4));
                row.put("grazed", rs.getBoolean(5));
                row.put("weeded", rs.getBoolean(6));
                row.put("watered", rs.getBoolean(7));
                // Map.of refuses a null value, and a stand nobody scared birds off has none — which is exactly the
                // case this reads, so the row is a HashMap.
                row.put("birdsScaredAt", rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toInstant());
                return row;
            }, location);
        if (crop == null) return new String[]{"FAILED", "There is no crop growing here to reap."};
        Instant ripe = ((Instant) crop.get("sown")).plus(java.time.Duration.ofDays((int) crop.get("days")));
        if (at.isBefore(ripe))
            return new String[]{"FAILED", "The crop stands green and unripe; cut now, it would be wasted. It needs the rest of the season."};
        // A tilled seedbed gives a fuller base stand (#165). Reaped promptly, the full stand comes in; left standing
        // past the clean window, the ripe heads begin to shatter and the birds work at them, so a late harvest saves
        // less. (Left past the spoil window it is lost outright — advanceCrops takes it before it ever reaches here.)
        int base = ((boolean) crop.get("tilled")) ? CROP_YIELD_HEADS_TILLED : CROP_YIELD_HEADS;
        // Worn soil gives a thinner stand (#164): a field cropped without rest until its fertility is low yields less.
        if (fieldFertility(location, at) < FERTILITY_LOW_THRESHOLD) base = Math.max(2, base - 2);
        // A stand the animals got into is grazed down before it is reaped (#166) — a fence would have kept it whole.
        boolean grazed = (boolean) crop.get("grazed");
        if (grazed) base = Math.max(2, base - 2);
        // A stand worked clean through its season (#165 tending) fills out fuller: weeding at least once frees the
        // grain from the weeds that would compete with it for soil, light, and water, so a tended stand carries an
        // extra head over one left to itself. The reward for returning to the field to work it across the season.
        boolean weeded = (boolean) crop.get("weeded");
        if (weeded) base += 1;
        // A stand grown on dry ground and never watered fills out thinner (#37/#165). The same moisture that decides
        // how warm the body is here and whether a well reaches the table decides whether the roots got enough; the
        // answer to it is carrying water, which is why the penalty lifts for a stand that was watered. On damp
        // ground it never applies, and waterCrop refuses there rather than banking a bonus nobody earned.
        Integer moisture = jdbc.queryForObject("SELECT COALESCE(moisture, 500) FROM world_chunk WHERE id=?", Integer.class, location);
        boolean thirsted = moisture != null && moisture < GROUND_DRY_ENOUGH_TO_WANT_WATERING && !((boolean) crop.get("watered"));
        if (thirsted) base = Math.max(2, base - 2);
        // The small life working this ground fills the stand out (#162/#74). Bees carry the pollen a flowering crop
        // needs to set; worms open and enrich the soil it stands in. Declared on every colony kind and read by
        // nothing, so keeping bees beside a plot did exactly as much for the harvest as keeping none.
        int pollination = pollinationBonusAt(location, at);
        if (pollination >= 20) base += 2; else if (pollination >= 10) base += 1;
        long daysLate = java.time.Duration.between(ripe, at).toDays();
        // Keeping the birds off buys back part of the clean window (#37/#165) — the shattering rule already blamed
        // them, and there was no way to do anything about it. It buys TIME, not grain: the window widens while
        // somebody keeps walking the plot, and grain already shattered onto the ground is gone whatever is shouted.
        Instant scared = (Instant) crop.get("birdsScaredAt");
        boolean kept = scared != null && !scared.isBefore(ripe) && java.time.Duration.between(scared, at).toDays() <= BIRDS_STAY_OFF_DAYS;
        boolean shattering = daysLate > CROP_FULL_YIELD_DAYS + (kept ? BIRDS_STAY_OFF_DAYS : 0);
        int heads = shattering ? Math.max(2, base / 2) : base;
        // The harvest takes from the soil — the field's fertility falls, to be won back only by fallow rest.
        depleteFertility(location, at);
        for (int i = 0; i < heads; i++) createCarriedItem(chronicle, "wild_grain_head", "Wild grain head", at, "HARVESTED_CROP");
        jdbc.update("UPDATE crop_stand SET harvested=true, harvested_at=?, outcome='REAPED' WHERE id=?", java.sql.Timestamp.from(at), crop.get("id"));
        return new String[]{"SUCCEEDED", shattering
            ? "You reap the stand, but you left it late — much of the grain has already shattered from the heads and the birds have been at it. You gather what is left."
            : grazed
            ? "You reap what the animals left: the stand has been cropped and trampled where they grazed through it, and a fence would have kept it whole. You gather the rest."
            : pollination >= 20
            ? "You cut the ripe stand and gather the heavy heads. The bees have been over this ground all season and the ears show it — filled out to the tip, hardly a blank among them."
            : "You cut the ripe stand and gather the heavy heads — far more grain than the handful you sowed, the season's increase come in."};
    }

    /**
     * Tend a growing crop — weed the stand (#165 tending). A sown crop is a living thing that must be worked across
     * its season, not a set-and-forget button: a stand left to the weeds gives a thinner harvest, for they compete
     * with the grain for the soil, the light, and the water. Working it clean at least once through the season keeps
     * the yield full. Wants a stand still growing here; a ripe or reaped stand has nothing left to tend.
     */
    @Transactional
    public String[] tendCrop(UUID chronicle, UUID location, Instant at) {
        java.util.Map<String,Object> crop = jdbc.query(
            "SELECT id, (weeded_at IS NOT NULL) AS weeded FROM crop_stand WHERE chunk_id=? AND harvested=false ORDER BY sown_at LIMIT 1 FOR UPDATE",
            rs -> rs.next() ? java.util.Map.of("id", rs.getObject(1, UUID.class), "weeded", rs.getBoolean(2)) : null, location);
        if (crop == null) return new String[]{"FAILED", "There is no crop growing here to tend."};
        jdbc.update("UPDATE crop_stand SET weeded_at=? WHERE id=?", java.sql.Timestamp.from(at), crop.get("id"));
        return new String[]{"SUCCEEDED", ((boolean) crop.get("weeded"))
            ? "You work down the rows again, pulling the weeds that have crept back in. The stand stands clean, the grain with room to fill."
            : "You work down the rows, pulling the weeds crowding the young grain and loosening the soil around the stems. The stand stands clean, given the room to fill out its heads."};
    }

    /**
     * Below this, the ground is dry enough that a stand wants water carried to it — the same moisture scale that
     * decides how warm the body is where it stands (#709) and whether a well reaches the table (#726).
     *
     * <p>MEASURED, not chosen. At 450 this caught <b>50 of the world's 86 grassland chunks</b> — more than half of
     * all ordinary farmland would carry a permanent penalty unless somebody hauled water to it, which is a silent
     * rebalancing of every field in the game rather than a drought. CI found it the honest way: it erased the
     * difference an existing test asserts between a dunged field and a bare one, because two penalties of two and
     * a floor of two leave nothing to tell them apart. Those three fields sit at 394, 405 and 441.
     *
     * <p>At 350 it catches 16 of 86 grassland, 8 of 100 highland, 4 of 51 wetland and 1 of 141 forest: dry
     * COUNTRY, where carrying water is the difference, and ordinary fields left alone. The well keeps 450 because
     * it asks a different question — how deep the water table is, not how dry the topsoil is.
     */
    private static final int GROUND_DRY_ENOUGH_TO_WANT_WATERING = 350;

    /**
     * Carry water to a growing stand (#37/#165). The field reckoned six things — the tilled seedbed, the soil's
     * fertility, whether the animals got in, whether it was weeded, what the bees did for it, how late it was cut —
     * and not this one, though it is where most of a dry season's work goes. "water the seedlings" reached nothing
     * at all.
     *
     * <p>It matters where it would matter. On ground that is damp of itself there is nothing for a bucket to add,
     * and the refusal says so rather than quietly banking a bonus. On dry ground it is the difference between a
     * thin stand and a full one, which is read at the harvest.
     *
     * <p>Wants water within reach of the plot — this is hauling water, not wishing it. A well counts (#726),
     * which is the whole point of sinking one on dry ground: it is what makes a dry plot workable at all.
     */
    @Transactional
    public String[] waterCrop(UUID chronicle, UUID location, Instant at) {
        java.util.Map<String,Object> crop = jdbc.query(
            "SELECT id, (watered_at IS NOT NULL) AS watered FROM crop_stand WHERE chunk_id=? AND harvested=false ORDER BY sown_at LIMIT 1 FOR UPDATE",
            rs -> rs.next() ? java.util.Map.of("id", rs.getObject(1, UUID.class), "watered", rs.getBoolean(2)) : null, location);
        if (crop == null) return new String[]{"FAILED", "There is no crop growing here to water."};
        Integer moisture = jdbc.queryForObject("SELECT COALESCE(moisture, 500) FROM world_chunk WHERE id=?", Integer.class, location);
        if (moisture != null && moisture >= GROUND_DRY_ENOUGH_TO_WANT_WATERING)
            return new String[]{"FAILED", "You stoop to the rows and find the soil already dark and damp between the "
                + "stems. This ground holds its own water; carrying more to it would only tire you."};
        if (!waterToWorkWith(location))
            return new String[]{"FAILED", "The rows want water and there is none to give them: no stream, no spring, "
                + "no standing water and no well within reach of this ground to carry any from."};
        jdbc.update("UPDATE crop_stand SET watered_at=? WHERE id=?", java.sql.Timestamp.from(at), crop.get("id"));
        return new String[]{"SUCCEEDED", ((boolean) crop.get("watered"))
            ? "You carry water down the rows again and pour it in at the stems, where it will reach the roots rather than stand on the surface and go."
            : "You carry water to the plot and work along the rows, pouring it in close at the stems. The dry soil takes it slowly, then darkens."};
    }

    /**
     * Drive the birds off a standing crop (#37/#165). The harvest prose has always said "the birds have been at
     * it" about a stand cut late — and there was no way to do anything about the birds. "scare the birds off the
     * crop" reached nothing.
     *
     * <p>What it buys is time, not grain: keeping them off extends the clean window, and grain that has already
     * shattered onto the ground is gone whatever anybody shouts. A stand still green has nothing on it they want.
     */
    @Transactional
    public String[] scareBirdsFromCrop(UUID chronicle, UUID location, Instant at) {
        java.util.Map<String,Object> crop = jdbc.query(
            "SELECT id, sown_at, maturity_days FROM crop_stand WHERE chunk_id=? AND harvested=false ORDER BY sown_at LIMIT 1 FOR UPDATE",
            rs -> rs.next() ? java.util.Map.of("id", rs.getObject(1, UUID.class), "sown", rs.getTimestamp(2).toInstant(), "days", rs.getInt(3)) : null, location);
        if (crop == null) return new String[]{"FAILED", "There is no crop standing here for anything to be at."};
        Instant ripe = ((Instant) crop.get("sown")).plus(java.time.Duration.ofDays((int) crop.get("days")));
        if (at.isBefore(ripe.minus(java.time.Duration.ofDays(7))))
            return new String[]{"FAILED", "The stand is still green, and there is nothing in it yet that a bird "
                + "would cross a field for. They are working the open ground instead."};
        jdbc.update("UPDATE crop_stand SET birds_scared_at=? WHERE id=?", java.sql.Timestamp.from(at), crop.get("id"));
        return new String[]{"SUCCEEDED", "You walk the plot shouting and clapping, and they go up off the heads in a "
            + "loose cloud and settle in the trees to wait you out. While you keep coming back, they take less."};
    }

    /** How long the birds stay off a stand after being driven from it — and so how much of the clean window
     *  walking the plot buys back. Shorter than the window itself: it delays the loss, it does not cancel it. */
    private static final int BIRDS_STAY_OFF_DAYS = 5;


    /** The plain noun in a garment's display name: "fur boot (left)" is a boot, "grass rain cape" is a cape.
     *  A player names the thing, not the catalogue row, and a side is not part of what they call it. */
    private static String garmentNoun(String displayName) {
        String plain = displayName.replaceAll("\\(.*?\\)", " ").replaceAll("[^a-z ]", " ").trim();
        int space = plain.lastIndexOf(' ');
        return space < 0 ? plain : plain.substring(space + 1);
    }

    /**
     * Stuff or line a worn garment with soft material (#37). "stuff my boots with dry grass" reached nothing at
     * all, and the five things anybody would use for it sat in the catalogue declaring an insulation value of
     * zero — correctly, because loose grass is not a garment; it is what you put INSIDE one.
     *
     * <p>Worth doing only since #709. The body used to be warmed as though it stood in the lowlands wherever it
     * really was, so a few points of insulation bought almost nothing; now that altitude, biome and shelter all
     * reach the skin, the difference between bare boots and boots packed with grass is time on a mountain.
     *
     * <p>Lines the garment the Chronicle NAMES. It does not guess: with nothing named it says what is being worn
     * and leaves the choice, because stuffing the wrong thing wastes material that was gathered by hand.
     */
    @Transactional
    public String[] lineGarment(UUID chronicle, String text, Instant at) {
        String said = text == null ? "" : text.toLowerCase(java.util.Locale.ROOT);

        java.util.List<java.util.Map<String,Object>> worn = jdbc.queryForList(
            "SELECT e.item_id, lower(w.display_name) AS name, i.item_key, i.lining_bonus, d.insulation_value " +
            "FROM equipment_attachment e JOIN world_object w ON w.id=e.item_id " +
            "JOIN item_instance i ON i.object_id=e.item_id JOIN item_definition d ON d.item_key=i.item_key " +
            "WHERE e.chronicle_id=? AND w.lifecycle_state='ACTIVE' ORDER BY length(w.display_name) DESC", chronicle);
        if (worn.isEmpty())
            return new String[]{"FAILED", "You have nothing on you to line — what you would stuff has to be "
                + "something you are wearing."};

        // Matched on the word a person would actually use. The display name is "fur boot (left)" and nobody has
        // ever typed that: they say "my boots". So the full name matches, and so does the noun at the end of it
        // once the side is stripped — which is the same defect, in my own code, that #720 found in the catalogue.
        java.util.Map<String,Object> garment = worn.stream()
            .filter(g -> said.contains((String) g.get("name")) || said.contains(garmentNoun((String) g.get("name"))))
            .findFirst().orElse(null);
        if (garment == null) {
            java.util.List<String> names = worn.stream().map(g -> (String) g.get("name")).distinct().limit(6).toList();
            return new String[]{"FAILED", "You would have to say which — you are wearing " + String.join(", ", names)
                + ", and stuffing the wrong one wastes what you gathered."};
        }

        java.util.List<java.util.Map<String,Object>> stuffings = jdbc.queryForList(
            "SELECT lm.item_key, lm.adds_insulation FROM lining_material lm " +
            "WHERE EXISTS (SELECT 1 FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "              WHERE i.item_key=lm.item_key AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE') " +
            "ORDER BY lm.adds_insulation DESC", chronicle);
        if (stuffings.isEmpty())
            return new String[]{"FAILED", "You have nothing soft and dry to pack in — no grass, no moss, no wool "
                + "or shed fur in hand to do it with."};
        // The best of what is carried, unless the Chronicle named something else they are holding.
        java.util.Map<String,Object> stuffing = stuffings.stream()
            .filter(m -> said.contains(((String) m.get("item_key")).replace('_', ' '))
                      || said.contains(((String) m.get("item_key")).split("_")[0]))
            .findFirst().orElse(stuffings.get(0));

        int already = ((Number) garment.get("lining_bonus")).intValue();
        int adds = ((Number) stuffing.get("adds_insulation")).intValue();
        // "dry grass bundle" and "shed fur tuft" are how the catalogue counts the stuff; "dry grass" and "shed
        // fur" are what it is. The unit word is dropped so the prose reads as a person handling a material.
        String material = ((String) stuffing.get("item_key")).replace('_', ' ')
            .replaceAll("\\s+(bundle|tuft|handful|wad)$", "");
        String garmentName = garmentNoun((String) garment.get("name")).isEmpty()
            ? (String) garment.get("name")
            : ((String) garment.get("name")).replaceAll("\\(.*?\\)", "").trim();
        if (already >= adds)
            return new String[]{"FAILED", "The " + garmentName + " is already packed out with as much as "
                + material + " would add to it. More would only make it tight."};

        consumeOne(chronicle, (String) stuffing.get("item_key"), at);
        jdbc.update("UPDATE item_instance SET lining_bonus=? WHERE object_id=?", adds, garment.get("item_id"));
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) " +
            "VALUES (?,?,'LINED',jsonb_build_object('material',?,'addsInsulation',?))",
            garment.get("item_id"), java.sql.Timestamp.from(at), stuffing.get("item_key"), adds);
        return new String[]{"SUCCEEDED", already > 0
            ? "You pull the old packing out of the " + garmentName + " and work " + material
              + " in its place, pressing it down into every corner. It sits warmer than it did."
            : "You work " + material + " into the " + garmentName + ", pressing it well down and packing "
              + "it out to the seams. It is close and warm against you where it was only cloth before."};
    }

    /** How long a ripe stand holds before it goes over — three weeks past its season, then the heads shatter and the birds have it. */
    private static final int CROP_SPOIL_WINDOW_DAYS = 21;

    /**
     * Run the harvest deadline in the world tick (#162): a crop stand left un-reaped past its season goes over and is
     * lost — the heads shatter, the birds and weather take it — so the harvest is a decision under time, not a stand
     * that waits forever. A stand still within its ripe window, or already reaped, is untouched. Set-based: it loses
     * every over-ripe stand at once, marking the loss LOST (distinct from a REAPED harvest) so the ground reads spent.
     */
    @Transactional
    public void advanceCrops(Instant now) {
        java.sql.Timestamp ts = java.sql.Timestamp.from(now);
        // A ripe stand on ground a grazing animal reaches, with no fence to keep it out, is eaten and trampled (#166):
        // the reward for fencing the field, or reaping before the animals find it, is a fuller harvest. Marked as the
        // stand ripens; a wattle/brush fence, or simply no grazers near, keeps it whole.
        jdbc.update(
            "UPDATE crop_stand cs SET grazed=true WHERE cs.harvested=false AND cs.grazed=false " +
            "AND ? >= cs.sown_at + make_interval(days => cs.maturity_days) " +
            "AND EXISTS (SELECT 1 FROM wildlife_population wp JOIN ecology_site es ON es.id=wp.site_id " +
            "  WHERE es.chunk_id=cs.chunk_id AND wp.ecological_role='HERBIVORE' AND wp.population_count>0) " +
            "AND NOT EXISTS (SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "  WHERE w.current_location_id=cs.chunk_id AND cp.project_kind IN ('WATTLE_FENCE','BRUSH_FENCE') " +
            "  AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND w.lifecycle_state='ACTIVE')",
            ts);
        // A stand left un-reaped past its whole season goes over and is lost outright.
        jdbc.update(
            "UPDATE crop_stand SET harvested=true, harvested_at=?, outcome='LOST' " +
            "WHERE harvested=false AND sown_at + make_interval(days => maturity_days + ?) <= ?",
            ts, CROP_SPOIL_WINDOW_DAYS, ts);
    }
    /** The soundest reachable tool of a process tool-class (owned or on-site), sound before worn before broken, so
     *  a spare good tool is used before a failing one — or null when none is in reach. Shares the tool key sets
     *  with the executeProcess gate and {@code hasCuttingTool}. Used to gate (a broken tool is refused) and to wear. */
    private java.util.Map<String,Object> soundestToolOfClass(UUID chronicle, UUID location, String toolClass) {
        // Which items serve as this tool class is data-driven (#93): tool_profile is the single source of truth, so a
        // newly crafted knife/hammer/axe works the moment its row exists, no Java change. Guard the class name so an
        // unknown class still returns null (the old switch's default).
        if (!("CUTTING".equals(toolClass) || "STRIKING".equals(toolClass) || "AXE".equals(toolClass))) return null;
        return jdbc.query(REACHABLE_CTE +
            "SELECT i.object_id, i.condition_state, i.use_count, i.item_key FROM reachable r JOIN item_instance i ON i.object_id=r.id " +
            "WHERE i.item_key IN (SELECT item_key FROM tool_profile WHERE tool_class='" + toolClass + "') " +
            "ORDER BY CASE i.condition_state WHEN 'SOUND' THEN 0 WHEN 'WORN' THEN 1 WHEN 'BROKEN' THEN 2 ELSE 3 END, i.use_count LIMIT 1",
            rs -> rs.next() ? java.util.Map.of("id", rs.getObject(1, UUID.class), "cond", rs.getString(2), "uses", rs.getInt(3), "key", rs.getString(4)) : null,
            chronicle, location);
    }

    /** The preservation kind a made food keeps as, or null if it is not a made food that keeps by a tier (V60/M4).
     *  Beyond the explicitly-preserved fish/meat, the cooked dishes (V92) keep by their nature: a dense, dry baked
     *  bread or cake keeps for weeks like other dried food, while a boiled or stewed wet dish keeps only days —
     *  before this they fell through to null and so never spoiled at all, an immortal pot of stew. */
    /** The built workstation that stands in for a bench a process asks for by item key. */
    private static String stationStructureFor(String stationKind) {
        return switch (stationKind) {
            case "woodworking_bench"  -> "WOODWORKING_TABLE";
            case "stoneworking_bench" -> "STONEWORKING_TABLE";
            case "loom"               -> "WEAVING_TABLE";
            default -> null;   // furnaces, kilns, querns and jigs remain objects you make, not builds you raise
        };
    }

    /** Whether a completed workstation of the right kind stands on this ground, standing in for the bench itself. */
    private boolean builtStationAt(UUID location, String stationKind) {
        if (location == null || stationKind == null) return false;
        String kind = stationStructureFor(stationKind);
        // A station can be a structure in its own right, not only a bench that also exists as an item you carry.
        // A smoke rack and a drying rack are raised and never carried, so the process names the construction kind
        // directly and it is looked up in the registry rather than mapped by a hand-written case. Without this a
        // SMOKE_RACK could be built and read by absolutely nothing.
        if (kind == null && Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM construction_kind WHERE project_kind=?)", Boolean.class, stationKind)))
            kind = stationKind;
        if (kind == null) return false;
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "WHERE w.current_location_id=? AND cp.project_kind=? AND cp.state='COMPLETED' AND cp.integrity_percent>0 " +
            "AND w.lifecycle_state='ACTIVE')", Boolean.class, location, kind));
    }

    /** How long gathered produce keeps before it turns — days, not the 18 hours of raw meat (V264). */
    private static final int FRESH_PRODUCE_HOURS = 96;

    /**
     * How a foraged food keeps. Gathered plant food was created with no preservation state at all, so berries,
     * mushrooms, greens and roots never spoiled — only animal food was ever registered. Dry keepers (nuts, mast,
     * grain) genuinely do keep for weeks, so they take the DRIED tier; everything else is perishable produce and
     * takes FRESH. Returns null for anything that is not food, which is left untracked as before.
     *
     * <p>Two FOOD-category things are held back by name, because for them a spoilage clock would be the lie:
     * <b>raw honey does not spoil</b> — that is a real property of honey and the reason it was left out of the
     * named map in the first place — and <b>water is not perishable food</b>, though the catalogue files the three
     * water items under FOOD so that drinking can find them. Water carries its own risk, judged where it is drawn
     * and drunk, and a bucket does not go over in four days.
     */
    private static String foragedKeepKind(String itemKey) {
        String k = itemKey.toLowerCase(java.util.Locale.ROOT);
        if (KEEPS_INDEFINITELY.contains(k)) return null;
        boolean dryKeeper = k.contains("nut") || k.contains("mast") || k.contains("grain") || k.contains("rice")
                         || k.contains("acorn") || k.contains("chestnut") || k.contains("seed");
        return dryKeeper ? "DRIED" : "FRESH";
    }

    /** FOOD-category items that genuinely do not go over, each one for its own stated reason. */
    private static final java.util.Set<String> KEEPS_INDEFINITELY =
        java.util.Set.of("raw_honey", "clean_water", "filtered_water", "raw_water");

    private static String preservationKind(String itemKey) {
        return switch (itemKey) {
            // Lacto-fermented vegetables are preserved in their own brine (the recipe takes ground salt), so they
            // keep on the salted tier — long, but not forever. Before this they fell through to null and never
            // spoiled at all, an immortal crock of sauerkraut (#60).
            case "salted_fish", "salted_meat", "fermented_vegetables" -> "SALTED";
            case "smoked_fish", "smoked_meat", "smoked_fowl" -> "SMOKED";
            case "dried_fish", "dried_meat", "dried_mushroom", "pemmican", "preserved_berries",
                 "acorn_flatbread", "grain_flatbread", "trail_cake" -> "DRIED"; // dense, dry keeping breads and cakes
            // A wet pot of greens keeps no better than the other wet dishes — it was the one cooked dish still
            // missing from this map, and so never spoiled (#60).
            case "root_vegetable_stew", "grain_porridge", "herbal_infusion", "cooked_mushrooms", "berry_compote",
                 "cooked_greens" -> "COOKED";
            // Dressing a fish does not preserve it. A caught raw_fish is spoilage-tracked from the moment it comes
            // out of the water, but gutting/filleting/splitting it produced UNTRACKED objects — so cleaning a fish
            // laundered perishable food into food that never spoiled at all. These stay raw, and keep raw's span.
            case "gutted_fish", "fish_fillet", "fish_side" -> "RAW";
            // Shellfish and grubs off the water's edge go over faster than anything (V270): out of the water they
            // are dead within the day, and a mussel that has sat is the classic way to poison yourself.
            case "freshwater_mussel", "river_snail", "caddis_grub" -> "RAW";
            default -> null;
        };
    }

    /** Preserved food keeps far longer than raw: salted longest, then dried, then smoked, then a plain cooked dish. */
    private void registerPreserved(UUID item, String kind, Instant at) {
        // RAW must match FoodPreservationService.registerRaw (18h) — without its own case it would fall to the
        // dried default of 1080h and a gutted fish would keep for 45 days. FRESH is the produce span (V264).
        long hours = switch (kind) { case "SALTED" -> 1440; case "SMOKED" -> 720; case "COOKED" -> 72; case "RAW" -> 18; case "FRESH" -> FRESH_PRODUCE_HOURS; default -> 1080; };
        jdbc.update("INSERT INTO food_preservation_state (object_id,preparation_kind,safe_until,pest_checked_at) VALUES (?,?,?,?) ON CONFLICT (object_id) DO NOTHING",
            item, kind, Timestamp.from(at.plus(java.time.Duration.ofHours(hours))), Timestamp.from(at));
    }

    /**
     * Search the ground and exposed rock for a mineral (V50). What can be found is
     * decided by the geology of the biome, and whether it IS found by the mineral's
     * own rarity — searching stone for a flint nodule is patient work that often
     * comes to nothing. A named mineral is searched for specifically; an unnamed
     * search turns up whatever this ground most readily offers.
     *
     * @return [outcome, narration]
     */
    /** How much of a given mineral a fresh seam holds here before it is worked out (#181) — not a flat figure but a
     *  seam with its own richness. It scales with the mineral's commonness (common ores form broad seams, rare ones
     *  small pockets) and varies from one patch of ground to the next, deterministically, so some ground is genuinely
     *  richer than other ground for the same mineral. Generous overall, so only sustained extraction exhausts a seam. */
    /**
     * Ground where the rock has already been opened — a quarry face, or an outcrop that has been worked (#158).
     *
     * <p>Kept beside {@link #mineralSeedFor} rather than folded into it because that method is static and pure on
     * the chunk id, which is what makes the base richness of a piece of ground reproducible. The site is a fact
     * about the world rather than about the mineral, so it multiplies the result instead of changing the seed.
     */
    public boolean stoneWorkingsAt(UUID location) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM ecology_site WHERE chunk_id=? " +
            "AND (site_kind ILIKE '%quarry%' OR site_kind ILIKE '%outcrop%'))", Boolean.class, location));
    }

    public static int mineralSeedFor(UUID chunk, String mineralKey, double rarity) {
        int base = 20 + (int) Math.round(rarity * 50);               // rarity 0.15 -> 28, 0.40 -> 40, 0.55 -> 48
        int h = Math.floorMod((chunk.toString() + ":" + mineralKey).hashCode(), 100); // 0..99, fixed for this ground
        double richness = 0.6 + (h / 100.0) * 0.9;                    // 0.6 (poor) .. ~1.5 (rich)
        return Math.max(12, (int) Math.round(base * richness));
    }

    /**
     * Whether the text names, whole-word, a mineral that has to be broken out of the rock.
     *
     * <p>This exists so the intent classifier can stop keeping its own hand-written copy of part of
     * {@code mineral_definition}. It is deliberately narrowed to minerals with a {@code tool_required} rather
     * than asking about all of them, and the narrowing is what makes it safe to add: those are exactly the ones
     * a player must ask for by name, while the toolless ones are the ones whose names would be taken from
     * intents that already own them — "gather field stone" belongs to GATHER_STONE, "dig clay" to GATHER_CLAY,
     * and both are minerals here too.
     *
     * <p>Matched on a normalised copy with word boundaries, so " ore " does not fire on "forest" and a mineral
     * named "Lead ore (galena)" is reached by its key rather than by a display name no player would type.
     */
    @Transactional(readOnly = true)
    public boolean namesADugMineral(String actionText) {
        if (actionText == null || actionText.isBlank()) return false;
        String v = " " + actionText.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim() + " ";
        for (java.util.Map<String,Object> m : jdbc.queryForList(
                // Toolless minerals are left out so "field stone", "surface clay" and "river sand" stay with GATHER_STONE
                // and GATHER_CLAY (V305). A province-bound mineral is the exception: it is found only at its own site,
                // so it is only ever had by asking for it there, and its name — iron sand, obsidian, ochre — is no
                // one else's gather phrase. Without this, toolless iron sand (V320) could be smelted and never dug.
                "SELECT mineral_key, display_name FROM mineral_definition WHERE tool_required IS NOT NULL " +
                "OR mineral_key IN (SELECT mineral_key FROM mineral_province)")) {
            if (v.contains(" " + ((String) m.get("mineral_key")).replace('_', ' ') + " ")) return true;
            String shown = ((String) m.get("display_name")).toLowerCase(java.util.Locale.ROOT);
            if (v.contains(" " + shown + " ")) return true;
        }
        return false;
    }

    /**
     * Whether the text names a species a keeper actually keeps (#79): anything that pulls ({@code draft_species}) or
     * gives ({@code tamed_yield}). The feeding and tending intents named their animals as literals — ox, deer,
     * reindeer, goat — so a species added to the catalogue, a yak, could be kept and never fed or tended by name.
     * Asking the table is the same fix {@link #namesADugMineral} made for minerals.
     */
    @Transactional(readOnly = true)
    public boolean namesAKeptAnimal(String actionText) {
        if (actionText == null || actionText.isBlank()) return false;
        String v = " " + actionText.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim() + " ";
        for (String key : jdbc.queryForList(
                "SELECT species_key FROM draft_species UNION SELECT species_key FROM tamed_yield", String.class)) {
            String spoken = key.replace('_', ' ');
            if (v.contains(" " + spoken + " ") || v.contains(" " + spoken + "s ")) return true;
            // ...and by the word a keeper actually uses for it (#106). The catalogue was asked, correctly, and
            // asked only for the WHOLE compound key: the species are mountain_goat and bighorn_sheep, so "groom
            // the goat" and "shear the sheep" named nothing the table would own to. Nobody says "mountain goat"
            // twice a day about an animal they milk. The head noun is the last word of the key — goat, sheep,
            // goose, duck, buffalo, fowl, turkey, pigeon, ox, deer — and it is the name the animal goes by.
            //
            // Only the head noun, and only as a whole word: a prefix would make "water buffalo" answer to
            // "water" and a substring would find "ox" inside "oxbow" and "box". This project has been bitten by
            // the substring four times over; HeadNounsNameOnlyAnimalsIntegrationTest holds the line.
            int cut = spoken.lastIndexOf(' ');
            if (cut > 0) {
                String head = spoken.substring(cut + 1);
                if (v.contains(" " + head + " ") || v.contains(" " + head + "s ")) return true;
            }
        }
        return false;
    }

    /**
     * Whether the sentence names a beast the keeper ALREADY KEEPS (#106).
     *
     * <p>Taming claims "feed" together with a species — goat, fowl, deer, duck — because offering food to a wild
     * animal is how it is tamed. But a keeper feeding their own goat says exactly the same words, and got
     * <i>"it lets you come nearer than last time, and holds there, watching"</i>: the approach to a wild animal,
     * offered to someone whose goat is in the pen behind them. The words cannot tell the two apart. The data can,
     * and this is the question it answers — so taming yields the sentence once the animal is yours.
     *
     * <p>No Chronicle id: one lives at a time, which {@code one_living_chronicle} enforces, and the taming rule is
     * reached from {@link #namesAKeptAnimal}'s side of the classifier where there is none to pass.
     */
    @Transactional(readOnly = true)
    public boolean keepsSuchABeast(String actionText) {
        if (actionText == null || actionText.isBlank()) return false;
        String v = " " + actionText.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim() + " ";
        for (String key : jdbc.queryForList(
                "SELECT DISTINCT wp.species_key FROM wildlife_bond wb " +
                "JOIN wildlife_population wp ON wp.id = wb.population_id " +
                "JOIN chronicle c ON c.id = wb.chronicle_id AND c.life_state = 'LIVING' " +
                "WHERE wb.bond_stage = 'TAMED'", String.class)) {
            String spoken = key.replace('_', ' ');
            if (v.contains(" " + spoken + " ") || v.contains(" " + spoken + "s ")) return true;
            int cut = spoken.lastIndexOf(' ');
            if (cut > 0) {
                String head = spoken.substring(cut + 1);
                if (v.contains(" " + head + " ") || v.contains(" " + head + "s ")) return true;
            }
        }
        return false;
    }

    @Transactional
    public String[] gatherMineral(UUID chronicle, UUID location, String actionText, Instant occurredAt) {
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
        String v = actionText.toLowerCase(java.util.Locale.ROOT);

        // A chunk carrying a resource deposit reads as that deposit for mineral affinity, on top of
        // its underlying biome: a salt flat is a SALT_DEPOSIT, a clay bank a CLAY_DEPOSIT. This is
        // what makes the SALT_DEPOSIT affinity resolve to the salt sites the generator already places
        // (a saline spring/flat), so rock salt is gatherable there and not only on the oceanside.
        java.util.List<String> affinity = new java.util.ArrayList<>();
        if (biome != null) affinity.add("%" + biome + "%");
        for (String deposit : jdbc.queryForList(
                "SELECT DISTINCT CASE WHEN lower(site_kind) LIKE '%salt%' THEN 'SALT_DEPOSIT' " +
                "WHEN lower(site_kind) LIKE '%clay%' THEN 'CLAY_DEPOSIT' END FROM ecology_site " +
                "WHERE chunk_id=? AND site_category='RESOURCE' AND (lower(site_kind) LIKE '%salt%' OR lower(site_kind) LIKE '%clay%')",
                String.class, location)) {
            affinity.add("%" + deposit + "%");
        }
        String affinityOr = affinity.stream().map(a -> "biome_affinity ILIKE ?").collect(java.util.stream.Collectors.joining(" OR "));

        // #160 geology: a broad biome label is not a deposit. A mineral with a province in mineral_province is
        // found ONLY on a chunk carrying its own site, on top of its affinity — both conditions, so a province
        // narrows and never widens. A mineral with no province is gated by affinity alone exactly as before,
        // which is what keeps flint, stone, clay, sand, iron and copper working as they always have.
        java.util.List<Object> args = new java.util.ArrayList<>(affinity);
        args.add(location);
        java.util.List<java.util.Map<String,Object>> here = jdbc.queryForList(
            "SELECT mineral_key, display_name, rarity, tool_required, yield_min, yield_max FROM mineral_definition md " +
            "WHERE (" + affinityOr + ") AND (" +
            "  NOT EXISTS (SELECT 1 FROM mineral_province p WHERE p.mineral_key = md.mineral_key) " +
            "  OR EXISTS (SELECT 1 FROM mineral_province p JOIN ecology_site s ON s.site_kind = p.site_kind " +
            "              WHERE p.mineral_key = md.mineral_key AND s.chunk_id = ?)) " +
            "ORDER BY rarity DESC", args.toArray());

        java.util.Map<String,Object> target = here.stream()
            .filter(m -> com.devosphere.draugr.narration.Words.names(v, (String)m.get("mineral_key"))
                || com.devosphere.draugr.narration.Words.word(v, ((String)m.get("display_name")).toLowerCase()))
            .findFirst().orElse(null);

        // Asking by name for something whose province is elsewhere must say so. A gate that answers "nothing but
        // dirt" teaches the player that the mineral does not exist; one that names the ground it belongs to
        // teaches them what to look for, which is the whole value of binding a mineral to its geology.
        if (target == null) {
            for (java.util.Map<String,Object> m : jdbc.queryForList(
                    "SELECT DISTINCT md.mineral_key, md.display_name, p.site_kind FROM mineral_definition md " +
                    "JOIN mineral_province p ON p.mineral_key = md.mineral_key " +
                    "WHERE NOT EXISTS (SELECT 1 FROM ecology_site s WHERE s.chunk_id=? AND s.site_kind=p.site_kind)", location)) {
                String askedKey = (String) m.get("mineral_key"), askedName = (String) m.get("display_name");
                if (!com.devosphere.draugr.narration.Words.names(v, askedKey)
                    && !com.devosphere.draugr.narration.Words.word(v, askedName.toLowerCase(java.util.Locale.ROOT))) continue;
                return new String[]{"FAILED", "You work over the ground looking for " + askedName.toLowerCase(java.util.Locale.ROOT)
                    + ". It does not come out of country like this — it comes from a " + ((String) m.get("site_kind")).toLowerCase(java.util.Locale.ROOT)
                    + ", and there is none here."};
            }
        }

        if (here.isEmpty())
            return new String[]{"FAILED", "You turn over what stone there is. This ground has nothing in it but dirt."};

        boolean named = target != null;
        if (target == null) target = here.get(0);

        String key = (String) target.get("mineral_key");
        String name = (String) target.get("display_name");
        String tool = (String) target.get("tool_required");
        if (tool != null && !hasCuttingTool(chronicle) && !hasAtLeast(chronicle,"stone_hammer",1) && !hasAtLeast(chronicle,"primitive_pickaxe",1) && !hasAtLeast(chronicle,"bronze_pickaxe",1) && !hasAtLeast(chronicle,"iron_pickaxe",1) && !hasAtLeast(chronicle,"granite_cobble",1) && !hasAtLeast(chronicle,"basalt_cobble",1))
            return new String[]{"FAILED", "The " + name.toLowerCase() + " is locked in the rock, and you have nothing to break it free with."};

        // #181 finite deposits: a seam holds only so much. If this ground has already been worked out for this
        // mineral, it gives nothing more here — fresh ground must be found. A seam not yet recorded is lazily full.
        Integer remainingHere = jdbc.query("SELECT remaining_units FROM mineral_deposit WHERE chunk_id=? AND mineral_key=?",
            rs -> rs.next() ? rs.getInt(1) : null, location, key);
        if (remainingHere != null && remainingHere <= 0)
            return new String[]{"FAILED", "The " + name.toLowerCase() + " here is worked out — the seam is spent, and you will have to find fresh ground for more."};

        // Searching for one specific mineral is harder than taking what is plainly there.
        double chance = ((Number) target.get("rarity")).doubleValue() * (named ? 0.75 : 1.0);
        if (Math.random() > chance)
            return new String[]{"FAILED", named
                ? "You work along the rock looking for " + name.toLowerCase() + ", turning over what looks promising. None of it is."
                : "You search the stone for a long while and come away with nothing worth carrying."};

        int lo = ((Number)target.get("yield_min")).intValue(), hi = ((Number)target.get("yield_max")).intValue();
        int want = lo + (hi > lo ? (int)(Math.random()*(hi-lo+1)) : 0);
        int room = capacityHeadroomUnits(chronicle, key);
        int take = Math.min(want, room);
        // A seam cannot give more than it still holds — the last working of it takes only what is left.
        if (remainingHere != null) take = Math.min(take, remainingHere);
        if (take <= 0) return new String[]{"FAILED", "You find what you were after and cannot carry another thing."};
        // Minerals are the quality materials — tool stone, flint — that seed the craft chains, so a
        // careful, deliberate search selects a FINE nodule while a careless one turns up poor stock.
        // This is the reachable source of FINE-grade inputs: careful gathering here, then careful work
        // downstream, is what lets a chain finish FINE rather than being capped at SOUND.
        QualityGrade grade = QualityGrade.attempt(actionText);
        // A metal pickaxe (bronze/iron) breaks the nodule out whole where a cobble or a stone hammer crushes it, so
        // a worker with one wins a finer stone — the same bounded one-grade lift a workstation gives, capped at FINE.
        // The smelted metal earning better ore, closing the extraction loop (#180): finer ore -> finer metal -> finer tools.
        if (hasAtLeast(chronicle,"bronze_pickaxe",1) || hasAtLeast(chronicle,"iron_pickaxe",1)) grade = grade.up();
        for (int i = 0; i < take; i++) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM',?,?)", id, name, chronicle);
            jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state,quality_grade) VALUES (?,?,'SOUND',?)", id, key, grade.name());
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'GATHERED',jsonb_build_object('mineral',?,'biome',?,'grade',?))", id, Timestamp.from(occurredAt), key, biome, grade.name());
        }
        assertCarryCapacity(chronicle);
        // #181: draw the seam down by what was taken. The first working of this ground records the deposit at full;
        // thereafter it is decremented, and floored at zero so it reads as worked out next time.
        int seam = mineralSeedFor(location, key, ((Number) target.get("rarity")).doubleValue());
        // Ground already opened gives up more of whatever is in it (#158). A quarry or a worked outcrop is rock
        // that has been broken into and left with a face standing, so the seam runs further before it is worked
        // out. Deliberately indifferent to WHICH mineral: what a quarry gives you is access to the rock, and
        // everything here is in that rock — a limestone face does not know it is supposed to withhold the flint.
        //
        // This is what makes those sites worth placing at all, and the state it corrects was worse than nothing.
        // The two "Stone outcrop" markers the world has always placed were not inert: a RESOURCE site raises the
        // gathering profile by 12, but ResourceEcologyService applies that to plant_fiber, wild_berries and
        // dry_branch only. Mineral richness came from mineralSeedFor(chunk, mineral, rarity) alone and took no
        // account of sites at all. So an outcrop made the BERRIES better and the STONE no better whatever — it
        // enriched everything except the one thing it is named for.
        if (stoneWorkingsAt(location)) seam = (int) Math.round(seam * 1.6);
        jdbc.update("INSERT INTO mineral_deposit (chunk_id, mineral_key, remaining_units) VALUES (?,?,?) " +
            "ON CONFLICT (chunk_id, mineral_key) DO UPDATE SET remaining_units = GREATEST(0, mineral_deposit.remaining_units - ?)",
            location, key, Math.max(0, seam - take), take);
        return new String[]{"SUCCEEDED", "You work it loose and turn it over in your hand: " + name.toLowerCase() + (take > 1 ? ", and more of it nearby." : ".")};
    }

    /**
     * Sew or weave a garment. Hide and fur come off animals the chronicle killed;
     * a woven tunic comes off the plants they gathered. Cutting work needs a blade.
     * The garment is created worn-ready but not equipped — putting it on is the
     * player's own decision, through EQUIP.
     *
     * @return [outcome, narration]
     */
    @Transactional
    public String[] craftGarment(UUID chronicle, String actionText, Instant occurredAt) {
        String v = actionText.toLowerCase(java.util.Locale.ROOT);
        record Pattern(String itemKey, String name, String hideKind, int hides, int fiber, boolean needsBlade) { }
        Pattern p =
            v.contains("cloak") || v.contains("fur")       ? new Pattern("fur_cloak",    "Fur cloak",     "pelt", 2, 1, true)
          : v.contains("legging") || v.contains("trouser") ? new Pattern("hide_leggings","Hide leggings", "hide", 1, 1, true)
          : v.contains("boot") || v.contains("shoe")       ? new Pattern("hide_boots",   "Hide boots",    "hide", 1, 1, true)
          : v.contains("tunic") || v.contains("woven")     ? new Pattern("fiber_tunic",  "Woven tunic",   null,   0, 6, false)
          :                                                  new Pattern("hide_coat",    "Hide coat",     "hide", 2, 1, true);

        if (p.needsBlade() && !hasCuttingTool(chronicle))
            return new String[]{"FAILED", "You lay the material out and reach for something to cut it with. You have no blade."};

        // Pelts are warmer than plain hide, so a cloak asks for them specifically;
        // anything else takes whatever skin is to hand.
        // Any worked skin or cloth serves as garment material; a fur cloak still asks for pelts specifically,
        // but a coat/tunic/leggings/boots take tanned leathers and woven/felted cloth as well as raw hide.
        java.util.List<String> hideKeys = "pelt".equals(p.hideKind())
            ? java.util.List.of("wolf_pelt","bear_pelt","fox_pelt","lynx_pelt","rabbit_pelt","dire_wolf_pelt")
            : java.util.List.of("animal_hide","deer_hide","boar_hide","troll_hide","wolf_pelt","bear_pelt","fox_pelt","lynx_pelt","rabbit_pelt",
                                "fish_skin_leather","leather_offcut","snake_skin","wool_cloth","felt_sheet","textile_material","leather_boot_sole","dyed_cloth");

        int haveHides = 0;
        for (String k : hideKeys) { Integer n = jdbc.queryForObject(
            "WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object n ON n.id=ic.item_id WHERE n.lifecycle_state='ACTIVE') SELECT COUNT(*) FROM reachable r JOIN item_instance i ON i.object_id=r.id WHERE i.item_key=?",
            Integer.class, chronicle, k); haveHides += n == null ? 0 : n; }

        if (haveHides < p.hides())
            return new String[]{"FAILED", p.hides() == 0 ? "You have nothing to work with." :
                "You spread out what skins you have and turn them over. There is not enough here to make " + p.name().toLowerCase() + " that would cover anything."};
        if (!hasAtLeast(chronicle, "plant_fiber", p.fiber()) && !hasAtLeast(chronicle, "animal_sinew", p.fiber()) && !hasAtLeast(chronicle, "fiber_cordage", p.fiber())
                && !hasAtLeast(chronicle, "silk_fiber", p.fiber()) && !hasAtLeast(chronicle, "spider_silk_thread", p.fiber()))
            return new String[]{"FAILED", "The pieces sit together well enough, but you have nothing to stitch them with."};

        int taken = 0;
        for (String k : hideKeys) { while (taken < p.hides() && consumeOne(chronicle, k, occurredAt)) taken++; if (taken >= p.hides()) break; }
        for (int i = 0; i < p.fiber(); i++)
            if (!consumeOne(chronicle,"animal_sinew",occurredAt) && !consumeOne(chronicle,"fiber_cordage",occurredAt)
                    && !consumeOne(chronicle,"silk_fiber",occurredAt) && !consumeOne(chronicle,"spider_silk_thread",occurredAt))
                consumeOne(chronicle,"plant_fiber",occurredAt);

        createCarriedItem(chronicle, p.itemKey(), p.name(), occurredAt, "CRAFTED");
        return new String[]{"SUCCEEDED", "You work the material to shape and stitch it closed. The " + p.name().toLowerCase() + " is finished, and it is warm in the hand."};
    }

    /** Above this a want is worth naming, well before {@link #NOT_IN_CONDITION} stops the animal doing anything. */
    private static final int WORTH_NAMING = 25;

    /**
     * How the stock are (#106). The plainest question a keeper asks, and nothing could answer it.
     *
     * <p><b>What the world already knew.</b> {@code wildlife_bond} carries hunger, thirst, fatigue and sickness on
     * every tamed beast; {@link #advanceDraftHunger} and {@link #advanceDraftThirst} move them every turn, the
     * haul formula weighs all three, breeding is gated on all four, and a thirsty beast gives less milk. Not one
     * sentence could ask after any of it. "How is the goat", "is the goat sick" and "check on the animals"
     * reached nothing at all, and <b>"water the animals" was answered "none of your draft beasts is hungry"</b> —
     * a question about thirst answered about appetite, by the only rule that would take the sentence.
     *
     * <p>Read-only, like {@link #breedingProspects} beside it: it reports the columns the tick maintains and
     * changes none of them. What relieves each want is named, because a keeper told their beast is thirsty and not
     * told that thirst falls on wet ground or at a watering station has been given a fact and no use for it.
     *
     * <p>When the sentence is about water, thirst is named first and named even when it is slight — "water the
     * animals" is an instruction, and the honest reply to it is what the animals' water actually depends on.
     */
    @Transactional(readOnly = true)
    public String[] stockWelfare(UUID chronicle, UUID location, String actionText) {
        String v = actionText == null ? "" : actionText.toLowerCase(java.util.Locale.ROOT);
        boolean aboutWater = v.contains("water") || v.contains("thirst") || v.contains("drink") || v.contains("trough");

        java.util.List<java.util.Map<String,Object>> kinds = jdbc.queryForList(
            "SELECT wp.species_key, COUNT(*) AS kept, MAX(wb.draft_hunger) AS hunger, MAX(wb.draft_thirst) AS thirst, " +
            "       MAX(wb.draft_fatigue) AS fatigue, MAX(wb.sickness) AS sickness " +
            "FROM wildlife_bond wb JOIN wildlife_population wp ON wp.id = wb.population_id " +
            "WHERE wb.chronicle_id = ? AND wb.bond_stage = 'TAMED' " +
            "GROUP BY wp.species_key ORDER BY wp.species_key", chronicle);
        if (kinds.isEmpty())
            return new String[]{"FAILED", "You keep no tamed animals, and there is nothing of yours to look over."};

        // If they named a kind, answer about that kind — by its catalogue name or by the word it goes by (#106).
        java.util.List<java.util.Map<String,Object>> asked = new java.util.ArrayList<>();
        for (java.util.Map<String,Object> k : kinds) {
            String spoken = ((String) k.get("species_key")).replace('_', ' ');
            String head = spoken.contains(" ") ? spoken.substring(spoken.lastIndexOf(' ') + 1) : spoken;
            if (v.contains(spoken) || v.contains(spoken + "s")
                || com.devosphere.draugr.narration.Words.word(v, head)
                || com.devosphere.draugr.narration.Words.word(v, head + "s")) asked.add(k);
        }
        if (asked.isEmpty()) asked = kinds;

        boolean wetGround = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM world_chunk ch WHERE ch.id=? AND (ch.biome IN ('WETLAND','RIVER_BANK') " +
            "  OR EXISTS(SELECT 1 FROM ecology_site es WHERE es.chunk_id=ch.id AND (" +
            com.devosphere.draugr.ecology.FreshWater.sites("es") + "))))", Boolean.class, location));
        boolean watered = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object tw ON tw.id=cp.object_id " +
            "WHERE cp.project_kind IN ('WATERING_STATION','RAINWATER_CATCHMENT') AND cp.state='COMPLETED' " +
            "  AND cp.integrity_percent>0 AND tw.lifecycle_state='ACTIVE' AND tw.current_location_id=?)",
            Boolean.class, location));

        StringBuilder b = new StringBuilder();
        for (java.util.Map<String,Object> k : asked) {
            String name = ((String) k.get("species_key")).replace('_', ' ');
            int kept = ((Number) k.get("kept")).intValue();
            int hunger = ((Number) k.get("hunger")).intValue(), thirst = ((Number) k.get("thirst")).intValue();
            int fatigue = ((Number) k.get("fatigue")).intValue(), sickness = ((Number) k.get("sickness")).intValue();

            // Phrased so the sentence reads for one animal or for a dozen, and worst first: a sick beast matters
            // more than a tired one, and a keeper reading a list wants the thing that needs them at the front.
            java.util.List<String> wants = new java.util.ArrayList<>();
            if (sickness >= TOO_SICK_TO_GIVE) wants.add("sick enough to want tending");
            else if (sickness >= WORTH_NAMING) wants.add("off colour");
            if (thirst >= NOT_IN_CONDITION) wants.add("badly short of water");
            else if (thirst >= WORTH_NAMING || (aboutWater && thirst > 0)) wants.add("in want of water");
            if (hunger >= NOT_IN_CONDITION) wants.add("hungry enough that it is telling on them");
            else if (hunger >= WORTH_NAMING) wants.add("getting hungry");
            if (fatigue >= NOT_IN_CONDITION) wants.add("worked past what they will stand");
            else if (fatigue >= WORTH_NAMING) wants.add("tired");

            b.append(kept == 1 ? "The " + name + " is" : "The " + name + "s, " + kept + " of them, are");
            if (wants.isEmpty()) b.append(" in good order — fed, watered, rested and sound. ");
            else b.append(' ').append(joinAnd(wants)).append(". ");
        }

        // And what the want depends on, which is the half a keeper can act on.
        if (aboutWater || asked.stream().anyMatch(k -> ((Number) k.get("thirst")).intValue() >= WORTH_NAMING)) {
            b.append(wetGround
                ? "They drink where they stand; this ground holds water of its own and their thirst falls as fast as it rises."
                : watered
                  ? "The watering station here keeps them, and they come off it settled."
                  : "There is no water on this ground and nothing built to hold any, so what they drink has to be "
                    + "what you bring or where you take them — wet ground, a spring or a pool, or a watering "
                    + "station raised where they are kept.");
        }
        return new String[]{"SUCCEEDED", b.toString().trim()};
    }

    /**
     * Why the stock is not in calf (#37). Breeding is simulated and automatic — {@link #advanceBreeding} puts
     * tamed animals in kind and in condition together on the tick — and every one of its five gates was
     * invisible. A keeper whose goats would not settle had no way to learn that the reason was a shelter too
     * small for them, and no amount of waiting was going to tell them.
     *
     * <p>Read-only: it reports the same conditions the tick enforces, and changes nothing. Kept beside
     * advanceBreeding deliberately, so the two are edited together.
     */
    @Transactional(readOnly = true)
    public String[] breedingProspects(UUID chronicle, UUID location, String actionText) {
        String v = actionText == null ? "" : actionText.toLowerCase(java.util.Locale.ROOT);
        java.util.List<java.util.Map<String,Object>> kinds = jdbc.queryForList(
            "SELECT wp.species_key, COUNT(*) AS kept, " +
            "       MAX(wb.draft_hunger) AS hunger, MAX(wb.draft_thirst) AS thirst, " +
            "       MAX(wb.draft_fatigue) AS fatigue, MAX(wb.sickness) AS sickness, " +
            "       BOOL_OR(bp.species_key IS NOT NULL) AS can_breed, " +
            "       MAX(body_size_rank(ws.size_tier)) AS body_rank " +
            "FROM wildlife_bond wb " +
            "JOIN wildlife_population wp ON wp.id = wb.population_id " +
            "JOIN wildlife_species ws ON ws.species_key = wp.species_key " +
            "LEFT JOIN breeding_profile bp ON bp.species_key = wp.species_key " +
            "WHERE wb.chronicle_id = ? AND wb.bond_stage = 'TAMED' " +
            "GROUP BY wp.species_key ORDER BY wp.species_key", chronicle);
        if (kinds.isEmpty())
            return new String[]{"FAILED", "You keep no tamed animals, and nothing of yours is going to be in calf."};

        // If they named a kind, answer about that kind. Otherwise answer about all of them.
        java.util.List<java.util.Map<String,Object>> asked = new java.util.ArrayList<>();
        for (java.util.Map<String,Object> k : kinds) {
            String spoken = ((String) k.get("species_key")).replace('_', ' ');
            if (v.contains(spoken) || v.contains(spoken + "s")) asked.add(k);
        }
        if (asked.isEmpty()) asked = kinds;

        StringBuilder b = new StringBuilder();
        for (java.util.Map<String,Object> k : asked) {
            String name = ((String) k.get("species_key")).replace('_', ' ');
            int kept = ((Number) k.get("kept")).intValue();
            if (!Boolean.TRUE.equals(k.get("can_breed"))) {
                b.append("The ").append(name).append(" will not breed in your keeping at all. ");
                continue;
            }
            if (kept < 2) { b.append("You keep one ").append(name).append(", and one is not a pair. "); continue; }
            java.util.List<String> wanting = new java.util.ArrayList<>();
            if (((Number) k.get("hunger")).intValue() >= NOT_IN_CONDITION) wanting.add("they are too hungry");
            if (((Number) k.get("thirst")).intValue() >= NOT_IN_CONDITION) wanting.add("they are short of water");
            if (((Number) k.get("fatigue")).intValue() >= NOT_IN_CONDITION) wanting.add("they are worked too hard");
            if (((Number) k.get("sickness")).intValue() >= TOO_SICK_TO_GIVE) wanting.add("there is sickness among them");
            boolean housed = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object sw ON sw.id = cp.object_id " +
                "JOIN construction_kind ck ON ck.project_kind = cp.project_kind AND ck.shelters_stock " +
                "WHERE cp.state = 'COMPLETED' AND cp.integrity_percent > 0 AND sw.lifecycle_state = 'ACTIVE' " +
                "  AND sw.current_location_id = ? AND body_size_rank(ck.shelters_up_to_size) >= ?)",
                Boolean.class, location, ((Number) k.get("body_rank")).intValue()));
            if (!housed) wanting.add("there is nothing standing here that would house stock their size");
            b.append("You keep ").append(kept).append(" ").append(name).append(kept == 1 ? "" : "s").append(": ");
            if (wanting.isEmpty()) b.append("nothing stands in the way, and they will settle to it in their own time. ");
            else b.append(String.join(", and ", wanting)).append(". ");
        }
        return new String[]{"SUCCEEDED", b.toString().trim()};
    }

    /** The outcome of an insect harvest: what happened, how it read, and any hazard the body took. */
    public record InsectHarvest(String outcome, String narration, int hazardSeverity, String hazardKind) { }

    /**
     * Raid a hive or nest for its products — honey and wax from bees, venom from
     * hornets. Bee stings are suppressed when the raider works smoke (an active
     * fire in the chunk and smoke described in the action); hornets sting
     * regardless. The colony kind is inferred from the biome when no colony
     * instance is placed here, mirroring flora gathering.
     */
    @Transactional
    public InsectHarvest raidHive(UUID chronicle, UUID location, String actionText, Instant occurredAt) {
        return harvestColony(chronicle, location, actionText, occurredAt, "RAID_HIVE");
    }

    /**
     * Collect insects by hand — silk cocoons, ant chitin, edible grasshoppers and
     * crickets, earthworm bait, spider silk. Some colonies bite or bear venom; the
     * hazard is applied by the caller from the returned severity and kind.
     */
    @Transactional
    public InsectHarvest collectInsects(UUID chronicle, UUID location, String actionText, Instant occurredAt) {
        return harvestColony(chronicle, location, actionText, occurredAt, "COLLECT_INSECTS");
    }

    /** A year, because a queenless colony does not come back before one (#122). */
    private static final int QUEENLESS_FOR_DAYS = 365;

    /** End a colony by taking the one creature that holds it together: nothing stands here now, and nothing will. */
    private void queenTaken(UUID location, String colonyKind, Instant at) {
        Timestamp ts = Timestamp.from(at);
        Timestamp comesBack = Timestamp.from(at.plus(java.time.Duration.ofDays(QUEENLESS_FOR_DAYS)));
        int updated = jdbc.update("UPDATE insect_colony SET health=0, product_ready_at=?, last_disturbed_at=?, queen_taken_at=? " +
            "WHERE chunk_id=? AND colony_kind=?", comesBack, ts, ts, location, colonyKind);
        if (updated == 0) {
            UUID object = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'INSECT_COLONY',?,?)",
                object, capitalise(colonyKind.replace('_', ' ')), location);
            jdbc.update("INSERT INTO insect_colony (object_id,colony_kind,chunk_id,health,product_ready_at,last_disturbed_at,queen_taken_at) " +
                "VALUES (?,?,?,0,?,?,?)", object, colonyKind, location, comesBack, ts, ts);
        }
    }

    /** Does the action name this colony — by its own word, with the kind of place it is left off? */
    private static boolean namedIn(String lower, java.util.Map<String,Object> kind) {
        return lower.contains(((String) kind.get("colony_kind")).replace("_", " ")
            .replace(" colony", "").replace(" swarm", "").replace(" patch", "").replace(" den", "")
            .replace(" nest", "").replace(" bed", "").replace(" shallows", "").replace(" hive", ""));
    }

    /** Has this colony been worked recently enough that there is nothing yet to take? */
    private static boolean workedOut(java.util.Map<String,Object> kind, Instant at) {
        Object ready = kind.get("ready_at");
        return ready instanceof Timestamp t && at.isBefore(t.toInstant());
    }

    private static String capitalise(String s) { return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    /**
     * How much the small life working this ground lifts what a field gives (#162/#74). {@code pollination_bonus}
     * was declared on every colony kind — 30 for a honeybee hive, 10 for a worm patch — and read by nothing at all,
     * so keeping bees beside a plot did exactly as much for the harvest as keeping none.
     *
     * <p>Reads the ground's standing colonies rather than any record of working them, because a hive pollinates a
     * field whether or not anyone has ever robbed it. A colony broken down to nothing stops giving: rob it flat and
     * the field feels it too.
     */
    private int pollinationBonusAt(UUID location, Instant at) {
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
        if (biome == null) return 0;
        // A hive somebody KEEPS (#77, V338). The wild bees are where the world put them — forest, highland, open
        // grass — and they do not work the river bank, the marsh margin or the shore, which is exactly where the
        // best fields are: a floodplain wins its fertility back at 5 a day against a meadow's 2. So the ground a
        // Chronicle most wants to farm had no pollinator on it and nothing they could do about that. Beekeeping is
        // the oldest answer: you do not go to the bees, you bring them. Where a sound skep stands, a keepable
        // colony counts on ground its own affinity never reached — but the SEASON still rules, because bees do not
        // fly in winter and a straw dome does not change that.
        boolean kept = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN construction_kind ck ON ck.project_kind=cp.project_kind " +
            "JOIN world_object w ON w.id=cp.object_id " +
            "WHERE w.current_location_id=? AND ck.keeps_bees AND cp.state='COMPLETED' AND cp.integrity_percent>0 " +
            "  AND w.lifecycle_state='ACTIVE')", Boolean.class, location));
        Integer best = jdbc.queryForObject(
            "SELECT COALESCE(MAX(ck.pollination_bonus),0) FROM insect_colony_kind ck " +
            "WHERE ck.pollination_bonus > 0 AND (ck.biome_affinity ILIKE ? OR (?::boolean AND ck.can_be_kept)) " +
            "  AND (ck.season_active='ALL' OR ck.season_active ILIKE ?) " +
            "  AND NOT EXISTS (SELECT 1 FROM insect_colony ic WHERE ic.chunk_id=? AND ic.colony_kind=ck.colony_kind AND ic.health <= 0)",
            Integer.class, "%" + biome + "%", kept, "%" + seasonOf(at) + "%", location);
        return best == null ? 0 : best;
    }

    /** Can these hands work this colony at all — does it want a tool, and is one within reach (V270)? */
    private boolean canWorkColony(java.util.Map<String,Object> kind, UUID chronicle) {
        String needed = (String) kind.get("requires_tool_class");
        return needed == null || needed.isBlank() || hasToolOfClass(chronicle, needed);
    }

    /** What to call the missing tool in the refusal, so it names a thing rather than a class name. */
    /**
     * What a tool class is called when a refusal has to name it. THE list: the colony refusal, the material
     * process refusal and the assembly stage refusal all come here, because three copies of this vocabulary had
     * already grown apart — a stage wanted "a cutting edge" while a hive wanted "a blade or a pry", and the
     * material processes had none of it and shrugged instead. Settled on the assembly wording from #706.
     */
    public static String toolPhrase(String toolClass) {
        return switch (toolClass == null ? "" : toolClass) {
            case "CUTTING" -> "a cutting edge";
            case "STRIKING" -> "something to strike with";
            case "AXE" -> "an axe";
            default -> "a tool";
        };
    }

    private InsectHarvest harvestColony(UUID chronicle, UUID location, String actionText, Instant occurredAt, String intent) {
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
        String lower = actionText.toLowerCase(java.util.Locale.ROOT);
        String season = seasonOf(occurredAt);

        // Candidate colony kinds for this intent, present in this biome and season.
        java.util.List<java.util.Map<String,Object>> kinds = jdbc.queryForList(
            "SELECT ck.colony_kind, ck.hazard_kind, ck.hazard_min, ck.hazard_max, ck.smoke_suppresses, ck.requires_tool_class, ck.regrowth_days, ck.shellfish, ck.concentrated_at, " +
            "  ck.has_a_queen, " +
            "  (SELECT ic.product_ready_at FROM insect_colony ic WHERE ic.chunk_id=? AND ic.colony_kind=ck.colony_kind) AS ready_at, " +
            "  (SELECT ic.queen_taken_at FROM insect_colony ic WHERE ic.chunk_id=? AND ic.colony_kind=ck.colony_kind) AS queen_gone " +
            "FROM insect_colony_kind ck " +
            "WHERE ck.harvest_intent=? AND ck.biome_affinity ILIKE ? " +
            "AND (ck.season_active='ALL' OR ck.season_active ILIKE ?) " +
            "ORDER BY ck.colony_kind", location, location, intent, "%" + biome + "%", "%" + season + "%");
        if (kinds.isEmpty()) {
            return new InsectHarvest("FAILED", intent.equals("RAID_HIVE")
                ? "You search for a hive or nest to raid, but find none here to work."
                : "You turn over the ground and growth for insects, but find nothing worth taking here.", 0, null);
        }
        // A colony worked here recently is a colony worked out. insect_colony carried health, product_ready_at and
        // last_disturbed_at, and insect_colony_kind carried regrowth_days, for a depletion model that never ran:
        // nothing anywhere created an insect_colony row, so the UPDATE recording disturbance matched nothing and a
        // single patch of ground yielded grubs, honey and silk forever. Colonies are a standing resource now — a
        // stretch of ground worked out has to be left alone to come back.
        // A colony the action names is answered for by name, whether or not it has anything left. Matching the
        // name against only the workable ones meant that naming the hive you emptied last week got you the
        // hornets' nest across the clearing instead, reported as a success — the same quiet substitution the
        // missing-tool branch below refuses to make.
        java.util.Optional<java.util.Map<String,Object>> named = kinds.stream().filter(k -> namedIn(lower, k)).findFirst();
        if (named.isPresent() && workedOut(named.get(), occurredAt)) {
            java.util.Map<String,Object> gone = named.get();
            String what = ((String) gone.get("colony_kind")).replace("_", " ");
            return new InsectHarvest("FAILED", gone.get("queen_gone") != null
                ? "You come back to the " + what + " and there is nothing there to come back to. What you left of it "
                  + "has gone quiet and cold, and nothing has taken the ground over yet."
                : "You find where you broke into the " + what + " before. It is still bare, and nowhere near ready to "
                  + "be worked again.", 0, null);
        }

        java.util.List<java.util.Map<String,Object>> ready = kinds.stream().filter(k -> !workedOut(k, occurredAt)).toList();
        if (ready.isEmpty()) {
            return new InsectHarvest("FAILED", intent.equals("RAID_HIVE")
                ? "You find where you broke into it before. The comb is still bare and the colony is nowhere near ready to be robbed again."
                : "You go over the same ground you worked before. It has not come back yet — turn it again and there would be nothing left to come back at all.", 0, null);
        }
        final java.util.List<java.util.Map<String,Object>> workable = ready;

        // Prefer a colony the action text names; otherwise the first one this pair of hands can actually work.
        // Some colonies need a tool: a mussel prised off its stone without a blade is a mussel lost with the shell
        // (V270). A named colony is attempted whatever the player carries — they asked for that one, and being
        // told plainly why it will not open is more use than quietly working something else.
        java.util.Map<String,Object> kind = named
            .orElseGet(() -> workable.stream().filter(k -> canWorkColony(k, chronicle)).findFirst().orElse(workable.get(0)));
        if (!canWorkColony(kind, chronicle)) {
            String needed = (String) kind.get("requires_tool_class");
            return new InsectHarvest("FAILED", "You find the " + ((String) kind.get("colony_kind")).replace("_", " ")
                + ", but it will not open to bare hands — this wants " + toolPhrase(needed)
                + ", and without one you would spoil what you were after.", 0, null);
        }
        String colonyKind = (String) kind.get("colony_kind");
        String hazardKind = (String) kind.get("hazard_kind");
        int hazardMin = ((Number) kind.get("hazard_min")).intValue();
        int hazardMax = ((Number) kind.get("hazard_max")).intValue();
        boolean smokeSuppresses = Boolean.TRUE.equals(kind.get("smoke_suppresses"));

        // Smoke is real only when there is an active fire in the chunk and the
        // action actually describes using its smoke.
        boolean describesSmoke = lower.contains("smoke") || lower.contains("smok") || lower.contains("smoulder") || lower.contains("smolder");
        boolean activeFire = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id JOIN fire_state fs ON fs.construction_id=cp.object_id WHERE w.current_location_id=? AND fs.active=true)",
            Boolean.class, location));
        boolean smokeWorking = describesSmoke && activeFire;

        int hazardSeverity = 0;
        if (hazardKind != null && hazardMax > 0) {
            boolean suppressed = smokeSuppresses && smokeWorking;
            if (!suppressed) hazardSeverity = hazardMin + (hazardMax > hazardMin ? (int)(Math.random() * (hazardMax - hazardMin + 1)) : 0);
        }

        // Yield each product by its rarity roll, respecting carry capacity.
        java.util.List<java.util.Map<String,Object>> products = jdbc.queryForList(
            // Only what the season gives (#161, V323): a hive holds no honey worth taking in winter, and crickets are
            // not adults until midsummer.
            //
            // ONE NOTION OF NOW PER HARVEST (#37). This asked in_season(), which reads the GLOBAL
            // simulation_clock, while the colony KIND five statements above is chosen from seasonOf(occurredAt)
            // — the moment this harvest actually happens. So a caller working a summer morning got summer
            // colonies and then had their products filtered by whatever month the world clock happened to
            // stand in. Cricket and grasshopper yields exist only in months 6-9, so with the clock outside
            // June-September a summer harvest of either came away EMPTY FOR EVER — and because nothing was
            // taken, the ground was never stamped as worked, so it could never be worked out either.
            //
            // Asked of occurredAt instead. in_season() stays as it is for the callers that genuinely mean
            // "now"; this one means "then", and says so.
            "SELECT item_key, yield_min, yield_max, rarity FROM insect_colony_product " +
            "WHERE colony_kind=? AND (available_months IS NULL OR ?::int = ANY(available_months)) ORDER BY rarity DESC",
            colonyKind, occurredAt.atZone(java.time.ZoneOffset.UTC).getMonthValue());
        int totalTaken = 0; String firstItemName = null;
        for (java.util.Map<String,Object> p : products) {
            double rarity = ((Number) p.get("rarity")).doubleValue();
            if (Math.random() > rarity) continue;
            String itemKey = (String) p.get("item_key");
            int ymin = ((Number) p.get("yield_min")).intValue();
            int ymax = ((Number) p.get("yield_max")).intValue();
            int want = ymin + (ymax > ymin ? (int)(Math.random() * (ymax - ymin + 1)) : 0);
            int room = capacityHeadroomUnits(chronicle, itemKey);
            int take = Math.min(want, room);
            if (take <= 0) continue;
            String displayName = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, itemKey);
            if (firstItemName == null) firstItemName = displayName;
            // A colony harvest wrote item rows and nothing else, so anything perishable that came off one kept
            // forever — a mussel out of the water is dead within the day (V270). Honey and chitin still keep
            // indefinitely: chitin is not food at all, and raw honey is held back by name in foragedKeepKind
            // because not spoiling is a real property of honey rather than an omission from a list.
            String keepKind = keepKindFor(itemKey);
            for (int i = 0; i < take; i++) {
                UUID id = UUID.randomUUID();
                jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM',?,?)", id, displayName, chronicle);
                jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,?,'SOUND')", id, itemKey);
                jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'GATHERED',jsonb_build_object('colonyKind',?,'biome',?))", id, Timestamp.from(occurredAt), colonyKind, biome);
                if (keepKind != null) registerPreserved(id, keepKind, occurredAt);
            }
            totalTaken += take;
        }
        // The queen (#122). Cutting her out is not robbing a colony, it is ending one: the brood comb comes away
        // with her and what is left cannot make another. A colony that has lost its queen is health 0 with nothing
        // ready for a year, which is the same state as one robbed flat — reached in a single act, on purpose, and
        // recorded as its own thing so the world can tell the difference afterwards.
        boolean wantsTheQueen = lower.contains("queen") || lower.contains("brood comb") || lower.contains("cut out the comb")
            || lower.contains("take the comb") || lower.contains("whole nest") || lower.contains("destroy the");
        boolean hasAQueen = Boolean.TRUE.equals(kind.get("has_a_queen"));
        if (wantsTheQueen && hasAQueen) {
            queenTaken(location, colonyKind, occurredAt);
            return new InsectHarvest(totalTaken > 0 ? "SUCCEEDED" : "PARTIAL",
                "You cut the brood comb out whole and the queen with it. What is left of the colony boils out over your hands and "
                + "goes on working a thing that has already ended — there will be no more of them on this ground for a long while.",
                hazardSeverity, hazardKind);
        }
        if (wantsTheQueen)
            return new InsectHarvest(totalTaken > 0 ? "SUCCEEDED" : "PARTIAL",
                "You go looking for one creature the rest of them answer to, and there is not one: this is a colony of equals, "
                + "and taking it apart would only leave you with the parts." + (totalTaken > 0 ? " You take what the ground gives instead." : ""),
                hazardSeverity, hazardKind);

        // Record the working. The colony is materialised here the first time it is touched — the same lazy pattern
        // fish stock and mineral seams use — so untouched ground carries no rows, and worked ground remembers.
        if (totalTaken > 0) {
            int regrowth = ((Number) kind.get("regrowth_days")).intValue();
            // A concentration comes back faster than a scatter (#157/#224). A mussel bed is a dense mat cemented
            // to itself and the rock, and that density is what lets it recover: spat settles on the shells already
            // there, so the bed reseeds from its own population. A hollow tree a swarm has held for years is the
            // same fact about a different animal. Work a thin scatter as hard and you have taken the seed with the
            // crop.
            //
            // Which colony counts as concentrated WHERE is catalogue (V339 concentrated_at), not a boolean and a
            // site name spelled out here: those were two ways of saying one thing, and a third animal could not be
            // added without touching this file. A colony with no such site keeps the rate it always had.
            String concentration = (String) kind.get("concentrated_at");
            if (concentration != null && concentrationAt(location, concentration))
                regrowth = Math.max(1, regrowth / 2);
            Timestamp readyAgain = Timestamp.from(occurredAt.plus(java.time.Duration.ofDays(Math.max(1, regrowth))));
            UUID colonyId = jdbc.query("SELECT object_id FROM insect_colony WHERE chunk_id=? AND colony_kind=?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, location, colonyKind);
            if (colonyId == null) {
                colonyId = UUID.randomUUID();
                jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE',?,?)",
                    colonyId, capitalise(colonyKind.replace('_', ' ')), location);
                jdbc.update("INSERT INTO insect_colony (object_id,colony_kind,chunk_id,health,product_ready_at,last_disturbed_at) VALUES (?,?,?,?,?,?)",
                    colonyId, colonyKind, location, 85, readyAgain, Timestamp.from(occurredAt));
            } else {
                jdbc.update("UPDATE insect_colony SET last_disturbed_at=?, product_ready_at=?, health=GREATEST(0,health-15) WHERE object_id=?",
                    Timestamp.from(occurredAt), readyAgain, colonyId);
            }
        }
        if (totalTaken > 0) assertCarryCapacity(chronicle);

        String name = colonyKind.replace("_", " ");
        String narration;
        if (totalTaken == 0) {
            narration = intent.equals("RAID_HIVE")
                ? "You break into the " + name + ", but it yields nothing you can carry away this time."
                : "You work at the " + name + " for a while, but come away empty-handed.";
        } else if (hazardSeverity > 0) {
            narration = "You take " + firstItemName.toLowerCase() + " from the " + name + ", and the colony makes you pay for it before you withdraw.";
        } else {
            narration = "You take " + firstItemName.toLowerCase() + " from the " + name + ", working steadily until you have what you came for.";
        }
        String outcome = totalTaken > 0 ? "SUCCEEDED" : "FAILED";
        return new InsectHarvest(outcome, narration, hazardSeverity, hazardKind);
    }

    // A basket is woven from whatever flexible stock is to hand — split withies, vines, reeds, rush, fibre, or
    // twisted cordage — not plant fibre alone (#34). What counts, and how many "weave units" each length is worth,
    // is weaving_stock (#134, V314): this comment named withies for years while the code never accepted them.
    // Eight units make a basket. This is why a player carrying cordage, branches,
    // and vines can now actually reach a basket instead of being told, wrongly, that only fibre will do.
    private static final int BASKET_WEAVE_UNITS = 8;

    /** Reachable weave units for a basket, summed over weaving_stock — the same reach query craftBasket locks. */
    @Transactional(readOnly = true)
    public int basketWeaveUnitsInReach(UUID chronicle) {
        UUID location = chronicleLocation(chronicle);
        Integer units = jdbc.queryForObject(REACHABLE_CTE +
            "SELECT COALESCE(SUM(ws.weave_units),0) FROM reachable r JOIN item_instance i ON i.object_id=r.id " +
            "JOIN weaving_stock ws ON ws.item_key=i.item_key", Integer.class, chronicle, location);
        return units == null ? 0 : units;
    }

    @Transactional
    public ItemView craftBasket() {
        UUID chronicle=activeChronicle(); UUID location=chronicleLocation(chronicle);
        // Reachable flexible stock in weaving_stock.spare_order, most plentiful first so cordage (scarcer, wanted
        // by many recipes) is spared where fibre, rush or withies will do. Each row is one physical length carrying
        // its declared weave units. Reach is the
        // SAME as the dispatch guard (basketWeaveUnitsInReach) — carried, in carried containers, and on the
        // ground here — so the guard and the craft never disagree and drop a raw error on a stock mismatch.
        List<java.util.Map<String,Object>> stock=jdbc.query(REACHABLE_CTE + "SELECT r.id, i.item_key, ws.weave_units FROM reachable r JOIN item_instance i ON i.object_id=r.id JOIN weaving_stock ws ON ws.item_key=i.item_key ORDER BY ws.spare_order, r.id FOR UPDATE OF i", (rs,row)->java.util.Map.of("id",rs.getObject(1,UUID.class),"key",rs.getString(2),"units",rs.getInt(3)), chronicle, location);
        int units=0; for (java.util.Map<String,Object> m : stock) units += (Integer) m.get("units");
        if(units<BASKET_WEAVE_UNITS) throw new IllegalStateException("Weaving a basket needs eight lengths of flexible stock — fibre, vine, withies, reed, rush or cordage — within reach of the Chronicle.");
        Instant now=Instant.now(); UUID basket=UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM','Woven basket',?)",basket,chronicle);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,'woven_basket','SOUND')",basket);
        jdbc.update("INSERT INTO container_properties (object_id,max_mass_grams,max_volume_ml) VALUES (?,12000,18000)",basket);
        int consumed=0; for (java.util.Map<String,Object> m : stock) { if (consumed>=BASKET_WEAVE_UNITS) break; String key=(String)m.get("key"); retire((UUID)m.get("id"),now,"CONSUMED_FOR_CRAFTING",key); consumed += (Integer) m.get("units"); }
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CRAFTED',jsonb_build_object('recipe','woven_basket'))",basket,Timestamp.from(now));
        // A crafted thing goes to the carried load if it fits, not onto the body — the Chronicle
        // decides what to wear or wield ("sling the basket on my back" equips it). If there is no
        // room to carry it, it is set on the ground in front rather than failing (GitHub #19).
        if (!withinCarryCapacity(chronicle)) jdbc.update("UPDATE world_object SET current_owner_id=NULL, current_location_id=? WHERE id=?", chronicleLocation(chronicle), basket);
        return new ItemView(basket,"Woven basket","woven_basket",chronicle,null);
    }
    @Transactional public ItemView craftPrimitiveSpear(Instant at) { UUID chronicle=activeChronicle(); if(!hasAtLeast(chronicle,"dry_branch",1)||!hasAtLeast(chronicle,"field_stone",1)||!hasAtLeast(chronicle,"plant_fiber",1))throw new IllegalStateException("Insufficient physical material."); if(!consumeOne(chronicle,"dry_branch",at)||!consumeOne(chronicle,"field_stone",at)||!consumeOne(chronicle,"plant_fiber",at))throw new IllegalStateException("Material changed."); UUID spear=UUID.randomUUID(); createCraftedItem(chronicle,chronicleLocation(chronicle),spear,"primitive_spear","Primitive spear",at,"CRAFTED",QualityGrade.SOUND); return new ItemView(spear,"Primitive spear","primitive_spear",chronicle,null); }
    @Transactional public ItemView craftPrimitiveTool(String itemKey, String displayName, boolean needsBranch, Instant at) { UUID chronicle=activeChronicle(); if(!hasAtLeast(chronicle,"field_stone",1)||!hasAtLeast(chronicle,"plant_fiber",1)||(needsBranch&&!hasAtLeast(chronicle,"dry_branch",1)))throw new IllegalStateException("Insufficient physical material."); if(!consumeOne(chronicle,"field_stone",at)||!consumeOne(chronicle,"plant_fiber",at)||(needsBranch&&!consumeOne(chronicle,"dry_branch",at)))throw new IllegalStateException("Material changed."); UUID tool=UUID.randomUUID(); createCraftedItem(chronicle,chronicleLocation(chronicle),tool,itemKey,displayName,at,"CRAFTED",QualityGrade.SOUND); return new ItemView(tool,displayName,itemKey,chronicle,null); }

    /** Reachable count of a material — carried, in carried containers, or on the ground/store at {@code location}. */
    @Transactional(readOnly = true)
    public int reachCountAt(UUID chronicle, UUID location, String itemKey) { return reachCount(chronicle, location, itemKey); }

    /**
     * Weave a net from processed fibre cordage (#43/#44). A full fishing net is mesh only; a landing net
     * adds a bent-branch hoop. The knotting needs a blade to trim and start the cordage, so a cutting tool
     * is required as with the other primitive crafts. Materials are consumed from reach (carried + on-site
     * store), and the finished net is a persistent object carried or set down if the load is already full.
     */
    @Transactional public ItemView craftFishingNet(boolean landing, Instant at) {
        UUID chronicle = activeChronicle();
        int cordage = landing ? 3 : 6;
        if (!hasCuttingTool(chronicle)) throw new IllegalStateException("Without an edge the cordage only frays where you try to part it, and the net cannot be started.");
        if (!hasAtLeast(chronicle, "fiber_cordage", cordage) || (landing && !hasAtLeast(chronicle, "dry_branch", 2)))
            throw new IllegalStateException("Insufficient physical material.");
        for (int i = 0; i < cordage; i++) if (!consumeOne(chronicle, "fiber_cordage", at)) throw new IllegalStateException("Material changed.");
        if (landing) for (int i = 0; i < 2; i++) if (!consumeOne(chronicle, "dry_branch", at)) throw new IllegalStateException("Material changed.");
        String key = landing ? "landing_net" : "fishing_net";
        String name = landing ? "Hoop landing net" : "Woven fishing net";
        UUID net = UUID.randomUUID();
        createCraftedItem(chronicle, chronicleLocation(chronicle), net, key, name, at, "CRAFTED", QualityGrade.SOUND);
        return new ItemView(net, name, key, chronicle, null);
    }

    /**
     * Make a primitive utility belt (#35): a fibre strap with tool loops. Catalogued in Phase 0 with a
     * technique but no runnable route, so it never assembled. Cordage for the strap + plant fibre for the
     * loops, and a blade to cut and fit them; the belt equips to the waist (compatibility seeded in V47).
     */
    @Transactional public ItemView craftUtilityBelt(Instant at) {
        UUID chronicle = activeChronicle();
        if (!hasCuttingTool(chronicle)) throw new IllegalStateException("Without an edge the cordage only frays where you try to part it, and the strap cannot be cut to length.");
        if (!hasAtLeast(chronicle, "fiber_cordage", 2) || !hasAtLeast(chronicle, "plant_fiber", 2))
            throw new IllegalStateException("Insufficient physical material.");
        for (int i = 0; i < 2; i++) if (!consumeOne(chronicle, "fiber_cordage", at)) throw new IllegalStateException("Material changed.");
        for (int i = 0; i < 2; i++) if (!consumeOne(chronicle, "plant_fiber", at)) throw new IllegalStateException("Material changed.");
        UUID belt = UUID.randomUUID();
        createCraftedItem(chronicle, chronicleLocation(chronicle), belt, "utility_belt", "Primitive utility belt", at, "CRAFTED", QualityGrade.SOUND);
        return new ItemView(belt, "Primitive utility belt", "utility_belt", chronicle, null);
    }

    /**
     * Whether the Chronicle can reach a sound tool of this class, read from the tool registry rather than a
     * hand-written list. tool_profile is what the material processes already consult, but the assembly stages and
     * this cutting check each carried their own hardcoded lists, and the lists had fallen behind the registry: ten
     * registered CUTTING tools were invisible to them (bronze, obsidian, chert and flint knives among them), four
     * STRIKING ones, and every metal AXE — so a Chronicle could smelt an iron axe and still be unable to notch a log
     * that a stone axe could. Reading the registry keeps one answer to "can I cut this" everywhere.
     */
    @Transactional(readOnly = true)
    public boolean hasToolOfClass(UUID chronicle, String toolClass) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' " +
            "  UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id " +
            "  JOIN world_object n ON n.id=ic.item_id WHERE n.lifecycle_state='ACTIVE') " +
            "SELECT EXISTS(SELECT 1 FROM reachable x JOIN item_instance i ON i.object_id=x.id " +
            "  JOIN tool_profile t ON t.item_key=i.item_key " +
            "  WHERE t.tool_class=? AND i.condition_state NOT IN ('BROKEN','DESTROYED'))",
            Boolean.class, chronicle, toolClass));
    }

    /** True if the Chronicle can reach any blade capable of cutting or carving — every registered CUTTING tool. */
    @Transactional(readOnly = true)
    public boolean hasCuttingTool(UUID chronicle) {
        return hasToolOfClass(chronicle, "CUTTING");
    }

    /**
     * Light and spend a portable light to work by in the dark (#75): a rushlight or tallow candle burns down to
     * nothing for the task; an oil lamp keeps, but a measure of fish oil is burned. Returns false when the
     * Chronicle has no light to strike — then the fine work cannot be done. A fire in reach is checked by the
     * caller and needs none of these.
     */
    @Transactional
    public boolean consumePortableLight(UUID chronicle, Instant at) {
        // A bare flame will not stand in weather. The stone lantern cover exists for exactly this — the catalogue
        // calls it "a stone cover to hood a flame and control its light" — and nothing read it, so a Chronicle
        // could shape one, wear it, and be no better off in a gale than with a naked candle. Hooded, the flame
        // holds; unhooded, wind and rain take it before it lights anything. An oil lamp is a covered flame by its
        // own construction, so it is not gutted, and a fire in reach is the caller's business.
        if (guttering(chronicle) && !hasAtLeast(chronicle,"stone_lantern_cover",1)) {
            boolean lampInstead = hasAtLeast(chronicle,"oil_lamp",1)
                && (hasAtLeast(chronicle,"fish_oil",1) || hasAtLeast(chronicle,"rendered_tallow",1));
            if (!lampInstead) return false;
        }
        if (hasAtLeast(chronicle,"firebrand",1))     return consumeOne(chronicle,"firebrand",at);   // a burning brand is a light before it is anything else
        if (hasAtLeast(chronicle,"resin_torch",1))   return consumeOne(chronicle,"resin_torch",at); // the primitive bare-hand light (#125)
        if (hasAtLeast(chronicle,"rush_light",1))    return consumeOne(chronicle,"rush_light",at);
        if (hasAtLeast(chronicle,"tallow_candle",1)) return consumeOne(chronicle,"tallow_candle",at);
        if (hasAtLeast(chronicle,"oil_lamp",1) && hasAtLeast(chronicle,"fish_oil",1)) return consumeOne(chronicle,"fish_oil",at);
        // The same lamp burns rendered tallow as readily as fish oil — a fat lamp, the commonest light there was, so
        // working after dark does not hang on having fished. Fish oil is spent first when both are to hand (#182 gives
        // rendered_tallow a use beyond candles).
        if (hasAtLeast(chronicle,"oil_lamp",1) && hasAtLeast(chronicle,"rendered_tallow",1)) return consumeOne(chronicle,"rendered_tallow",at);
        return false;
    }

    /** Weather on the Chronicle's own ground that would take a bare flame: driven rain, snow, or a real wind. */
    public boolean weatherWouldGutterALight(UUID chronicle) { return guttering(chronicle); }

    /**
     * Whether the weather here would take a flame before it caught.
     *
     * <p>Not under rock. A cave mouth already counts as shelter for the body ({@code shelterInReach}) and
     * {@code BiomeClimate} blocks its wind; a cave interior has no weather at all. Anything that keeps the rain
     * off a Chronicle keeps it off the flame in their hand, so neither can gutter a light.
     *
     * <p>This was a real fault, and an intermittent one. The refusal message checks guttering BEFORE it checks
     * the rock, so a Chronicle in a cave during a storm was told the weather was taking their flame — untrue of
     * where they were standing, and it made the cave's own "the daylight gets a few paces into the rock"
     * message unreachable whenever it happened to be raining outside. CI failed on exactly that, in a run whose
     * only other change was narration prose.
     */
    private boolean guttering(UUID chronicle) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM world_object body JOIN world_chunk c ON c.id=body.current_location_id " +
            "JOIN world_weather ww ON ww.world_id=c.world_id " +
            "WHERE body.id=? AND c.biome NOT IN ('CAVE_MOUTH','CAVE_INTERIOR') " +
            "  AND (ww.weather_kind IN ('STORM','RAIN','SNOW') OR ww.wind_speed_kph >= 30))",
            Boolean.class, chronicle));
    }
    /** Northern-hemisphere season derived from the simulated instant's month, until a dedicated world-clock season exists. */
    private static String seasonOf(Instant at) {
        int month = at.atZone(java.time.ZoneOffset.UTC).getMonthValue();
        return switch (month) {
            case 3, 4, 5 -> "SPRING";
            case 6, 7, 8 -> "SUMMER";
            case 9, 10, 11 -> "AUTUMN";
            default -> "WINTER";
        };
    }

    /** Carve a hearth board and spindle from a dry branch — the reusable friction-fire kit. Needs a blade. */
    @Transactional
    public boolean craftFireKit(Instant at) {
        UUID chronicle=activeChronicle();
        if(!hasCuttingTool(chronicle) || !hasAtLeast(chronicle,"dry_branch",1)) return false;
        if(capacityHeadroomUnits(chronicle,"hearth_board")<=0 || capacityHeadroomUnits(chronicle,"fire_spindle")<=0) return false;
        if(!consumeOne(chronicle,"dry_branch",at)) return false;
        createCarriedItem(chronicle,"hearth_board","Hearth board",at,"CRAFTED");
        createCarriedItem(chronicle,"fire_spindle","Fire spindle",at,"CRAFTED");
        return true;
    }

    /**
     * Make one piece of ignition kit beyond the basic board and spindle (V49).
     * Each of the nine fire-making methods needs its own gear, and a method whose
     * kit cannot be made is a method that does not exist — the flaw the item
     * reachability invariant now guards against.
     *
     * @return [outcome, narration]
     */
    @Transactional
    public String[] craftFireTool(UUID chronicle, String actionText, Instant at) {
        String v = actionText.toLowerCase(java.util.Locale.ROOT);
        record Kit(String key, String name, String needKey, int needQty, boolean blade, String made) { }
        Kit k =
            v.contains("bow")                                   ? new Kit("fire_bow","Fire bow","dry_branch",1,true,"You bend a springy branch and string it with cord until it draws true. The bow will drive a spindle far faster than palms ever could.")
          : v.contains("socket")||v.contains("bearing")||v.contains("handhold") ? new Kit("fire_socket","Bearing block","field_stone",1,true,"You hollow a socket into the stone, smooth enough that the spindle can spin under your whole weight without binding.")
          : v.contains("plough")||v.contains("plow")            ? new Kit("plough_board","Fire plough board","dry_branch",2,true,"You cut a long straight groove down the face of the board — a track for the point to run and pile its own dust ahead of it.")
          : v.contains("saw")                                   ? new Kit("fire_saw_set","Fire saw set","dry_branch",2,true,"You split the wood and notch it, so one piece can be sawn hard across the other.")
          : v.contains("char")                                  ? new Kit("char_tinder","Charred tinder","plant_fiber",2,false,"You char the fiber slowly and smother it before it burns away. What is left will take a spark that raw fiber would shrug off.")
          : v.contains("ember")||v.contains("bundle")           ? new Kit("ember_bundle","Ember bundle","plant_fiber",3,false,"You pack the fiber into a tight bundle with a hollow at its heart — somewhere an ember can travel and stay alive.")
          :                                                       null;
        if (k == null) return new String[]{"FAILED","You turn the wood over in your hands without settling on what to make of it."};
        if (k.blade() && !hasCuttingTool(chronicle))
            return new String[]{"FAILED","The shaping needs a blade, and you have none."};
        if (!hasAtLeast(chronicle, k.needKey(), k.needQty()))
            return new String[]{"FAILED","You have not got the material to hand for that."};
        // An ember bundle is only worth carrying if there is a live fire to take from.
        if ("ember_bundle".equals(k.key())) {
            Boolean live = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM fire_state fs JOIN world_object w ON w.id=fs.construction_id JOIN world_object body ON body.current_location_id=w.current_location_id WHERE body.id=? AND fs.active=true)", Boolean.class, chronicle);
            if (!Boolean.TRUE.equals(live)) return new String[]{"FAILED","You build the bundle and hold it ready, but there is no live fire here to take an ember from. It stays cold in your hands."};
        }
        for (int i = 0; i < k.needQty(); i++) consumeOne(chronicle, k.needKey(), at);
        createCarriedItem(chronicle, k.key(), k.name(), at, "CRAFTED");
        return new String[]{"SUCCEEDED", k.made()};
    }

    /**
     * Build a piece of furniture and set it down at the chronicle's location — a
     * world object fixed to a place, not carried. Woodworking needs a blade and
     * dry branches lashed with fiber; a stone shelf is built up from slabs. A
     * container piece (a shelf) gains storage. Returns false on missing material.
     */
    @Transactional
    public boolean craftFurniture(UUID chronicle, UUID location, String itemKey, String displayName, int branches, int slabs, boolean container, int capMass, int capVol, Instant at) {
        if (branches > 0 && (!hasCuttingTool(chronicle) || !hasAtLeast(chronicle,"dry_branch",branches) || !hasAtLeast(chronicle,"plant_fiber",1))) return false;
        if (slabs > 0 && !hasAtLeast(chronicle,"stone_slab",slabs)) return false;
        if (branches == 0 && slabs == 0) return false;
        for (int i=0;i<branches;i++) if(!consumeOne(chronicle,"dry_branch",at)) return false;
        for (int i=0;i<slabs;i++) if(!consumeOne(chronicle,"stone_slab",at)) return false;
        if (branches > 0) consumeOne(chronicle,"plant_fiber",at);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ITEM',?,?)", id, displayName, location);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,?,'SOUND')", id, itemKey);
        if (container) jdbc.update("INSERT INTO container_properties (object_id,max_mass_grams,max_volume_ml) VALUES (?,?,?)", id, capMass, capVol);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CRAFTED',jsonb_build_object('recipe',?,'placedAt',?::text))", id, Timestamp.from(at), itemKey, location.toString());
        return true;
    }
    /** Tease a fine, dry nest that can catch an ember — from plant fibre, or from cattail down or tinder
     *  fungus, both of which take a spark as well or better (#75 real-world tinders). */
    @Transactional
    public boolean craftTinder(Instant at) {
        UUID chronicle=activeChronicle();
        if(capacityHeadroomUnits(chronicle,"tinder_nest")<=0) return false;
        String from = null;
        // Bare-hand tinder stock (#192): dry leaf litter, dry twigs, and a tuft of shed fur all take a spark.
        for (String k : new String[]{"cattail_fluff","birch_polypore","fatwood_stick","wood_shaving","plant_fiber","fallen_leaf_litter","dry_twig","shed_fur_tuft"})
            if (hasAtLeast(chronicle, k, 1)) { from = k; break; }
        if(from==null || !consumeOne(chronicle,from,at)) return false;
        createCarriedItem(chronicle,"tinder_nest","Tinder nest",at,"CRAFTED");
        return true;
    }

    @Transactional
    public void placeInContainer(UUID item, UUID container) {
        UUID chronicle=activeChronicle(); assertAccessible(item,chronicle); assertAccessible(container,chronicle);
        jdbc.update("DELETE FROM equipment_attachment WHERE item_id=?",item); jdbc.update("DELETE FROM item_containment WHERE item_id=?",item);
        jdbc.update("INSERT INTO item_containment (item_id,container_id) VALUES (?,?)",item,container);
        jdbc.update("UPDATE world_object SET current_owner_id=?,current_location_id=NULL WHERE id=?",container,item);
        assertCarryCapacity(chronicle);
        jdbc.update("INSERT INTO object_transition (object_id,transition_type,to_container_id,payload) VALUES (?,'PLACED_IN_CONTAINER',?, '{}'::jsonb)",item,container);
    }

    @Transactional
    public void equip(UUID item,String position,String layer) {
        UUID chronicle=activeChronicle(); assertAccessible(item,chronicle);
        Integer compatible=jdbc.queryForObject("SELECT COUNT(*) FROM item_instance i JOIN item_equipment_compatibility c ON c.item_key=i.item_key WHERE i.object_id=? AND c.body_position=? AND c.layer=?",Integer.class,item,position,layer);
        if(compatible==null||compatible==0) throw new IllegalArgumentException("This item cannot be attached at that body position and layer.");
        // Whatever already occupies this slot is displaced back to carried — a new tool
        // taken in hand puts the old one away rather than colliding on the unique slot
        // key. Without this, auto-equipping a crafted tool into an occupied hand, or
        // equipping over one, throws a duplicate-key error that poisons the whole action.
        jdbc.update("DELETE FROM equipment_attachment WHERE chronicle_id=? AND body_position=? AND layer=? AND item_id<>?",chronicle,position,layer,item);
        jdbc.update("DELETE FROM item_containment WHERE item_id=?",item); jdbc.update("INSERT INTO equipment_attachment (item_id,chronicle_id,body_position,layer) VALUES (?,?,?,?) ON CONFLICT (item_id) DO NOTHING",item,chronicle,position,layer);
        jdbc.update("UPDATE world_object SET current_owner_id=?,current_location_id=NULL WHERE id=?",chronicle,item);
        assertCarryCapacity(chronicle);
        jdbc.update("INSERT INTO object_transition (object_id,transition_type,to_attachment,payload) VALUES (?,'EQUIPPED',?, '{}'::jsonb)",item,position+":"+layer);
    }
    @Transactional
    public boolean unequip(UUID item, Instant occurredAt) {
        UUID chronicle = activeChronicle(); assertAccessible(item, chronicle);
        Integer equipped = jdbc.queryForObject("SELECT COUNT(*) FROM equipment_attachment WHERE item_id=?", Integer.class, item);
        if (equipped == null || equipped == 0) return false;
        jdbc.update("DELETE FROM equipment_attachment WHERE item_id=?", item);
        jdbc.update("UPDATE world_object SET current_owner_id=? WHERE id=?", chronicle, item);
        assertCarryCapacity(chronicle);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'UNEQUIPPED','{}'::jsonb)", item, Timestamp.from(occurredAt));
        return true;
    }
    @Transactional
    public boolean drop(UUID item, UUID location, Instant occurredAt) {
        UUID chronicle = activeChronicle(); assertAccessible(item, chronicle);
        jdbc.update("DELETE FROM equipment_attachment WHERE item_id=?", item);
        jdbc.update("DELETE FROM item_containment WHERE item_id=?", item);
        jdbc.update("UPDATE world_object SET current_owner_id=NULL,current_location_id=? WHERE id=?", location, item);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'DROPPED',jsonb_build_object('locationId',?::text))", item, Timestamp.from(occurredAt), location.toString());
        return true;
    }
    /**
     * Take a named object up into the carried load — from the ground where it was dropped (#29/#41) or out of a
     * reachable container (#40 retrieve). The object keeps its UUID and full history; only its owner changes.
     * Fails specifically (not carried by that name / no room) rather than with generic no-effect narration.
     */
    @Transactional
    public String[] pickUp(UUID chronicle, UUID location, String text, Instant at) {
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        java.util.List<java.util.Map<String,Object>> cands = jdbc.queryForList(
            "WITH RECURSIVE reach(id) AS (" +
            "SELECT id FROM world_object WHERE lifecycle_state='ACTIVE' AND (current_owner_id=? OR (current_owner_id IS NULL AND current_location_id=?)) " +
            "UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reach r ON r.id=ic.container_id JOIN world_object n ON n.id=ic.item_id WHERE n.lifecycle_state='ACTIVE') " +
            "SELECT w.id, w.display_name, i.item_key FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "WHERE w.lifecycle_state='ACTIVE' AND (" +
            "  (w.current_owner_id IS NULL AND w.current_location_id=?) " +                                  // on the ground here
            "  OR w.id IN (SELECT c.item_id FROM item_containment c WHERE c.container_id IN (SELECT id FROM reach))) " + // in a reachable store
            "ORDER BY length(w.display_name) DESC",
            chronicle, location, location);
        if (cands.isEmpty()) return new String[]{"FAILED", "There is nothing here on the ground or in your stores to pick up."};
        java.util.Map<String,Object> match = cands.stream()
            .filter(c -> lower.contains(((String)c.get("display_name")).toLowerCase(java.util.Locale.ROOT))).findFirst().orElse(null);
        if (match == null) return new String[]{"FAILED", "You look about, but nothing here by that name lies within reach to take up."};
        UUID id = (UUID) match.get("id"); String key = (String) match.get("item_key"); String name = ((String) match.get("display_name")).toLowerCase(java.util.Locale.ROOT);
        // A closed or sealed container must be opened before anything can be taken out of it (#67).
        String contState = jdbc.query("SELECT cp.access_state FROM item_containment ic JOIN container_properties cp ON cp.object_id=ic.container_id WHERE ic.item_id=?",
            rs -> rs.next() ? rs.getString(1) : null, id);
        if (contState != null && !"OPEN".equals(contState))
            return new String[]{"FAILED", "The " + name + " lies inside a " + contState.toLowerCase(java.util.Locale.ROOT) + " container — open it before you can take anything out."};
        if (capacityHeadroomUnits(chronicle, key) < 1)
            return new String[]{"FAILED", "You cannot carry the " + name + " — your load is already as much as you can bear. Set something down first."};
        jdbc.update("DELETE FROM item_containment WHERE item_id=?", id);
        jdbc.update("UPDATE world_object SET current_owner_id=?, current_location_id=NULL WHERE id=?", chronicle, id);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'PICKED_UP',jsonb_build_object('itemKey',?))", id, Timestamp.from(at), key);
        return new String[]{"SUCCEEDED", "You take up the " + name + " and add it to what you carry."};
    }

    /** Free (mass_grams, volume_ml) left in a container after its full nested contents. */
    private int[] containerFreeSpace(UUID container) {
        return jdbc.query(
            "WITH RECURSIVE contents(id) AS (SELECT item_id FROM item_containment WHERE container_id=? " +
            "UNION ALL SELECT ic.item_id FROM item_containment ic JOIN contents c ON ic.container_id=c.id) " +
            "SELECT cp.max_mass_grams - COALESCE((SELECT SUM(d.unit_mass_grams) FROM contents JOIN item_instance ii ON ii.object_id=contents.id JOIN item_definition d ON d.item_key=ii.item_key),0), " +
            "       cp.max_volume_ml  - COALESCE((SELECT SUM(d.unit_volume_ml)  FROM contents JOIN item_instance ii ON ii.object_id=contents.id JOIN item_definition d ON d.item_key=ii.item_key),0) " +
            "FROM container_properties cp WHERE cp.object_id=?",
            rs -> rs.next() ? new int[]{rs.getInt(1), rs.getInt(2)} : new int[]{0, 0}, container, container);
    }

    /**
     * Move a named reachable item into a named reachable container (#40): the same UUID moves into the
     * container's containment, if mass/volume allow. Capacity is pre-checked in Java so a full container fails
     * gracefully rather than tripping the containment trigger into a hard rollback of the whole action.
     */
    @Transactional
    public String[] storeInContainer(UUID chronicle, UUID location, String text, Instant at) {
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        java.util.List<java.util.Map<String,Object>> containers = jdbc.query(REACHABLE_CTE +
            "SELECT w.id, w.display_name FROM reachable r JOIN world_object w ON w.id=r.id JOIN container_properties cp ON cp.object_id=w.id ORDER BY length(w.display_name) DESC",
            (rs,row) -> java.util.Map.of("id", rs.getObject(1,UUID.class), "name", rs.getString(2)), chronicle, location);
        if (containers.isEmpty()) return new String[]{"FAILED", "You have no container within reach to store anything in."};
        java.util.Map<String,Object> container = containers.stream()
            .filter(c -> sentenceNamesContainer(lower, (String) c.get("name"))).findFirst()
            .orElse(containers.size() == 1 ? containers.get(0) : null);
        if (container == null) return new String[]{"FAILED", "You cannot tell which container you mean — name the one to store it in."};
        UUID containerId = (UUID) container.get("id"); String containerName = ((String) container.get("name")).toLowerCase(java.util.Locale.ROOT);
        String access = jdbc.queryForObject("SELECT access_state FROM container_properties WHERE object_id=?", String.class, containerId);
        if (!"OPEN".equals(access)) return new String[]{"FAILED", "The " + containerName + " is " + access.toLowerCase(java.util.Locale.ROOT) + " — open it before you can put anything in it."};
        // Prefer a loosely-carried item over one already nested in some container: an item that is
        // already contained would collide on the item_containment primary key (one home per item), and
        // "put the stone in the sack" plainly means the stone in hand, not one buried in another basket.
        java.util.List<java.util.Map<String,Object>> stock = jdbc.query(REACHABLE_CTE +
            "SELECT w.id, w.display_name, i.item_key FROM reachable r JOIN world_object w ON w.id=r.id JOIN item_instance i ON i.object_id=w.id " +
            "WHERE w.id<>? AND w.id NOT IN (SELECT item_id FROM item_containment WHERE container_id=?) " +
            "ORDER BY (w.id IN (SELECT item_id FROM item_containment)) ASC, length(w.display_name) DESC",
            (rs,row) -> java.util.Map.of("id", rs.getObject(1,UUID.class), "name", rs.getString(2), "key", rs.getString(3)), chronicle, location, containerId, containerId);
        java.util.Map<String,Object> item = stock.stream()
            .filter(c -> lower.contains(((String)c.get("name")).toLowerCase(java.util.Locale.ROOT))).findFirst().orElse(null);
        if (item == null) return new String[]{"FAILED", "You have nothing by that name within reach to put in the " + containerName + "."};
        UUID itemId = (UUID) item.get("id"); String itemName = ((String) item.get("name")).toLowerCase(java.util.Locale.ROOT);
        // Nesting rule: a container that forbids nested containers rejects another container.
        Boolean isContainer = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM container_properties WHERE object_id=?)", Boolean.class, itemId);
        Boolean allowsNested = jdbc.queryForObject("SELECT allows_nested_containers FROM container_properties WHERE object_id=?", Boolean.class, containerId);
        if (Boolean.TRUE.equals(isContainer) && Boolean.FALSE.equals(allowsNested))
            return new String[]{"FAILED", "The " + containerName + " will not take another container inside it."};
        int[] free = containerFreeSpace(containerId);
        Integer im = jdbc.queryForObject("SELECT d.unit_mass_grams FROM item_instance i JOIN item_definition d ON d.item_key=i.item_key WHERE i.object_id=?", Integer.class, itemId);
        Integer iv = jdbc.queryForObject("SELECT d.unit_volume_ml  FROM item_instance i JOIN item_definition d ON d.item_key=i.item_key WHERE i.object_id=?", Integer.class, itemId);
        if (im != null && iv != null && (im > free[0] || iv > free[1]))
            return new String[]{"FAILED", "The " + containerName + " has no room left for the " + itemName + "."};
        // Clear any prior home (equipped slot or another container) before re-homing, so re-storing an
        // already-placed item moves it rather than colliding on item_containment's per-item primary key.
        jdbc.update("DELETE FROM equipment_attachment WHERE item_id=?", itemId);
        jdbc.update("DELETE FROM item_containment WHERE item_id=?", itemId);
        jdbc.update("INSERT INTO item_containment (item_id, container_id, placed_at) VALUES (?,?,?)", itemId, containerId, Timestamp.from(at));
        jdbc.update("UPDATE world_object SET current_owner_id=?, current_location_id=NULL WHERE id=?", containerId, itemId);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'STORED',jsonb_build_object('containerId',?::text))", itemId, Timestamp.from(at), containerId.toString());
        return new String[]{"SUCCEEDED", "You place the " + itemName + " into the " + containerName + "."};
    }

    /**
     * Open, close, or seal a named reachable container (#67). Storing and retrieving both require it OPEN, so a
     * closed or sealed one must be opened first. The change is recorded in the container's immutable history.
     */
    @Transactional
    public String[] setContainerAccess(UUID chronicle, UUID location, String text, String newState, Instant at) {
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        java.util.List<java.util.Map<String,Object>> containers = jdbc.query(REACHABLE_CTE +
            "SELECT w.id, w.display_name, cp.access_state FROM reachable r JOIN world_object w ON w.id=r.id JOIN container_properties cp ON cp.object_id=w.id ORDER BY length(w.display_name) DESC",
            (rs,row) -> java.util.Map.of("id", rs.getObject(1,UUID.class), "name", rs.getString(2), "state", rs.getString(3)), chronicle, location);
        if (containers.isEmpty()) return new String[]{"FAILED", "There is no container within reach to open or close."};
        java.util.Map<String,Object> container = containers.stream()
            .filter(c -> sentenceNamesContainer(lower, (String) c.get("name"))).findFirst()
            .orElse(containers.size() == 1 ? containers.get(0) : null);
        if (container == null) return new String[]{"FAILED", "You cannot tell which container you mean — name the one to open or close."};
        UUID id = (UUID) container.get("id"); String name = ((String) container.get("name")).toLowerCase(java.util.Locale.ROOT); String cur = (String) container.get("state");
        if (cur.equals(newState)) return new String[]{"SUCCEEDED", "The " + name + " is already " + newState.toLowerCase(java.util.Locale.ROOT) + "."};
        jdbc.update("UPDATE container_properties SET access_state=? WHERE object_id=?", newState, id);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'ACCESS_CHANGED',jsonb_build_object('from',?,'to',?))", id, Timestamp.from(at), cur, newState);
        String verb = switch (newState) { case "OPEN" -> "open"; case "SEALED" -> "seal shut"; default -> "close"; };
        return new String[]{"SUCCEEDED", "You " + verb + " the " + name + "."};
    }

    /**
     * Take everything out of a reachable container (#37).
     *
     * <p>The container system could open, close and seal a container, put a named thing in, and take a named
     * thing out — and had no way to <b>empty</b> one. "empty the pot" reached nothing at all, and a Chronicle who
     * could not remember what they had put by had to name each thing in turn to get it back.
     *
     * <p>Honours the rules the single take already honours: a closed or sealed container must be opened first,
     * and what will not fit is left inside and said so, rather than vanishing or overloading the Chronicle.
     *
     * @return [outcome, narration]
     */
    @Transactional
    public String[] emptyContainer(UUID chronicle, UUID location, String text, Instant at) {
        String lower = text == null ? "" : text.toLowerCase(java.util.Locale.ROOT);
        java.util.List<java.util.Map<String,Object>> containers = jdbc.query(REACHABLE_CTE +
            "SELECT w.id, w.display_name, cp.access_state FROM reachable r JOIN world_object w ON w.id=r.id " +
            "JOIN container_properties cp ON cp.object_id=w.id ORDER BY length(w.display_name) DESC",
            (rs,row) -> java.util.Map.of("id", rs.getObject(1,UUID.class), "name", rs.getString(2), "state", rs.getString(3)),
            chronicle, location);
        if (containers.isEmpty()) return new String[]{"FAILED", "There is no container within reach to empty."};
        java.util.Map<String,Object> container = containers.stream()
            .filter(c -> sentenceNamesContainer(lower, (String) c.get("name"))).findFirst()
            .orElse(containers.size() == 1 ? containers.get(0) : null);
        if (container == null)
            return new String[]{"FAILED", "You cannot tell which container you mean — name the one to empty."};

        UUID id = (UUID) container.get("id");
        String name = ((String) container.get("name")).toLowerCase(java.util.Locale.ROOT);
        String access = (String) container.get("state");
        if (!"OPEN".equals(access))
            return new String[]{"FAILED", "The " + name + " is " + access.toLowerCase(java.util.Locale.ROOT)
                + " — open it before you can take anything out."};

        java.util.List<java.util.Map<String,Object>> inside = jdbc.queryForList(
            "SELECT ic.item_id, w.display_name, i.item_key FROM item_containment ic " +
            "JOIN world_object w ON w.id=ic.item_id JOIN item_instance i ON i.object_id=ic.item_id " +
            "WHERE ic.container_id=? AND w.lifecycle_state='ACTIVE' ORDER BY w.display_name", id);
        if (inside.isEmpty()) return new String[]{"SUCCEEDED", "The " + name + " is already empty."};

        java.util.List<String> took = new java.util.ArrayList<>();
        java.util.List<String> left = new java.util.ArrayList<>();
        for (java.util.Map<String,Object> row : inside) {
            UUID item = (UUID) row.get("item_id");
            String itemKey = (String) row.get("item_key");
            String itemName = ((String) row.get("display_name")).toLowerCase(java.util.Locale.ROOT);
            // Asked per item and after each take, because the headroom shrinks as the pack fills.
            if (capacityHeadroomUnits(chronicle, itemKey) < 1) { left.add(itemName); continue; }
            jdbc.update("DELETE FROM item_containment WHERE item_id=?", item);
            jdbc.update("UPDATE world_object SET current_owner_id=?, current_location_id=NULL WHERE id=?", chronicle, item);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) " +
                "VALUES (?,?,'PICKED_UP',jsonb_build_object('itemKey',?,'from','CONTAINER'))",
                item, Timestamp.from(at), itemKey);
            took.add(itemName);
        }
        if (took.isEmpty())
            return new String[]{"FAILED", "You cannot carry what is in the " + name
                + " — your load is already as much as you can bear. Set something down first."};
        StringBuilder s = new StringBuilder("You turn the ").append(name).append(" out and take up ")
            .append(joinAnd(took)).append(".");
        // The asymmetry matters: a part-emptied container must not read like an emptied one.
        if (!left.isEmpty()) s.append(" ").append(joinAnd(left))
            .append(left.size() == 1 ? " stays in it — you have no room for it." : " stay in it — you have no room for them.");
        return new String[]{"SUCCEEDED", s.toString()};
    }

    /**
     * Whether a sentence names this container (#37).
     *
     * <p>Every container act — open, close, seal, store in, empty — asked whether the sentence contained the
     * whole display name, so <b>"empty the basket" could not find a "Primitive backpack basket"</b> and
     * "open the pot" could not find a "Fired clay cooking pot". A Chronicle had to say the catalogue's name
     * back to it, which is the plain-family-word defect in its own corner of the game.
     *
     * <p>The head noun is the last word of the name, which is what the thing IS — a basket, a pot, a sack, a
     * creel. Callers order by name length descending, so where two containers answer to the same head word the
     * longer, more specific name is tried first and a sentence naming it in full still wins.
     */
    private static boolean sentenceNamesContainer(String lower, String displayName) {
        String name = displayName.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains(name)) return true;
        String[] words = name.split("\\s+");
        String head = words[words.length - 1];
        return head.length() >= 3 && com.devosphere.draugr.narration.Words.word(lower, head);
    }

    /**
     * Repair a worn or broken reachable item (#69 repair/fix/mend/reinforce/sharpen): binds and reinforces it
     * one condition step better (BROKEN→WORN→SOUND), consuming a length of cordage or fibre. A sound item needs
     * none; a destroyed one is past mending; with no binding material to hand it cannot be done. Keeps the item's
     * UUID and history — repair is a state change on the same object, never a new one.
     */
    @Transactional
    public String[] repairNamedItem(UUID chronicle, UUID location, String text, Instant at) {
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        java.util.List<java.util.Map<String,Object>> items = jdbc.query(REACHABLE_CTE +
            "SELECT w.id, w.display_name, i.condition_state FROM reachable r JOIN world_object w ON w.id=r.id JOIN item_instance i ON i.object_id=w.id ORDER BY length(w.display_name) DESC",
            (rs,row) -> java.util.Map.of("id", rs.getObject(1,UUID.class), "name", rs.getString(2), "cond", rs.getString(3)), chronicle, location);
        if (items.isEmpty()) return new String[]{"FAILED", "You have nothing within reach in need of mending."};
        java.util.Map<String,Object> item = items.stream()
            .filter(c -> lower.contains(((String)c.get("name")).toLowerCase(java.util.Locale.ROOT))).findFirst().orElse(null);
        if (item == null) return new String[]{"FAILED", "You have nothing by that name within reach to mend."};
        String name = ((String) item.get("name")).toLowerCase(java.util.Locale.ROOT); String cond = (String) item.get("cond");
        if ("SOUND".equals(cond)) return new String[]{"SUCCEEDED", "You look the " + name + " over, but it is sound and whole — it needs no mending yet."};
        if ("DESTROYED".equals(cond)) return new String[]{"FAILED", "The " + name + " is past mending — there is nothing left to work with."};
        // Sharpening (#75) is not mending: a dulled edge is drawn back against a whetstone or grit-stone, not
        // bound with cordage. The stone is reusable, so it is used but not consumed.
        if (lower.contains("sharpen") || lower.contains("whet") || lower.contains("hone") || lower.contains("abrade") || (lower.contains("grind") && lower.contains("edge"))) {
            // A dressed stone_whetstone is a whetstone by any other name — it was craftable but read by
            // nothing, so honing accepts it too (#257), alongside the found whetstone and the grit-stones.
            if (!hasAtLeast(chronicle, "whetstone", 1) && !hasAtLeast(chronicle, "stone_whetstone", 1) && !hasAtLeast(chronicle, "sandstone_piece", 1) && !hasAtLeast(chronicle, "pumice_piece", 1)
                    && !hasAtLeast(chronicle, "sharpening_kit", 1) && !hasAtLeast(chronicle, "weapon_maintenance_roll", 1))
                return new String[]{"FAILED", "You go to put an edge on the " + name + ", but you have no whetstone or grit-stone to draw it against."};
            String honed = "BROKEN".equals(cond) ? "WORN" : "SOUND";
            jdbc.update("UPDATE item_instance SET condition_state=?, use_count=0 WHERE object_id=?", honed, item.get("id"));
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'SHARPENED',jsonb_build_object('from',?,'to',?))", item.get("id"), Timestamp.from(at), cond, honed);
            return new String[]{"SUCCEEDED", "You draw the " + name + " against the stone in long, even strokes until a keen edge comes back to it."};
        }
        String binder = hasAtLeast(chronicle, "fiber_cordage", 1) ? "fiber_cordage"
                       : hasAtLeast(chronicle, "leather_cord", 1) ? "leather_cord"
                       : hasAtLeast(chronicle, "plant_fiber", 1) ? "plant_fiber" : null;
        if (binder == null) return new String[]{"FAILED", "You turn the " + name + " over, but you have no cordage or fibre in reach to bind and reinforce it with."};
        consumeOne(chronicle, binder, at);
        String newCond = "BROKEN".equals(cond) ? "WORN" : "SOUND";
        jdbc.update("UPDATE item_instance SET condition_state=?, use_count=0 WHERE object_id=?", newCond, item.get("id"));
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'REPAIRED',jsonb_build_object('from',?,'to',?))", item.get("id"), Timestamp.from(at), cond, newCond);
        String state = "SOUND".equals(newCond) ? "whole and sound again" : "holding together better, though still worn";
        return new String[]{"SUCCEEDED", "You bind and reinforce the " + name + " with " + binder.replace('_', ' ') + ". It is " + state + "."};
    }

    /** Retires a physical item without deleting its identity or immutable transition history. */
    @Transactional
    public void retire(UUID item, Instant occurredAt, String transitionType, String itemKey) {
        jdbc.update("DELETE FROM equipment_attachment WHERE item_id=?", item);
        jdbc.update("DELETE FROM item_containment WHERE item_id=?", item);
        Timestamp occurred = Timestamp.from(occurredAt);
        // Where the object met its end — resolved from its own location, its owner's,
        // or, for a carried item, the living chronicle's. The row is kept; only its
        // status changes and the place/cause of destruction are recorded on it.
        UUID where = jdbc.query("SELECT COALESCE(item.current_location_id,(SELECT o.current_location_id FROM world_object o WHERE o.id=item.current_owner_id),(SELECT w.current_location_id FROM world_object w JOIN chronicle c ON c.id=w.id WHERE c.life_state='LIVING')) FROM world_object item WHERE item.id=?", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, item);
        jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED',destroyed_at=?,destroyed_location_id=?,destroyed_cause=?,current_owner_id=NULL,current_location_id=NULL WHERE id=?", occurred, where, transitionType, item);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,?,jsonb_build_object('itemKey',?,'destroyedLocationId',?::text))", item, occurred, transitionType, itemKey, where == null ? null : where.toString());
    }
    private UUID activeChronicle(){ UUID id=jdbc.query("SELECT id FROM chronicle WHERE life_state='LIVING'",rs->rs.next()?rs.getObject(1,UUID.class):null); if(id==null) throw new IllegalStateException("No living Chronicle exists."); return id; }
    private void assertAccessible(UUID item,UUID chronicle){ Integer present=jdbc.queryForObject("WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') SELECT COUNT(*) FROM reachable WHERE id=?",Integer.class,chronicle,item); if(present==null||present==0) throw new IllegalArgumentException("The item is not physically reachable by the Chronicle."); }
    /**
     * How many additional units of an item the Chronicle can still physically carry,
     * from remaining mass and bulk headroom (0 if a single unit exceeds the lift limit).
     * Lets gathering take only what fits instead of failing the whole attempt.
     */
    private int capacityHeadroomUnits(UUID chronicle, String itemKey) {
        LoadState s = loadState(chronicle);
        int[] dims = jdbc.query("SELECT unit_mass_grams,unit_volume_ml FROM item_definition WHERE item_key=?", rs -> rs.next() ? new int[]{rs.getInt(1), rs.getInt(2)} : new int[]{0, 0}, itemKey);
        int unitMass = dims[0], unitVol = dims[1];
        if (unitMass > 0 && unitMass > s.maximumSingleLiftGrams()) return 0;
        int byMass = unitMass > 0 ? Math.max(0, (s.sustainedMassCapacityGrams() - s.massGrams()) / unitMass) : Integer.MAX_VALUE;
        int byVolume = unitVol > 0 ? Math.max(0, (s.directBulkCapacityMl() - s.bulkMl()) / unitVol) : Integer.MAX_VALUE;
        return Math.max(0, Math.min(byMass, byVolume));
    }
    private void assertCarryCapacity(UUID chronicle) {
        LoadState state=loadState(chronicle); Capacity cap=new Capacity(state.sustainedMassCapacityGrams(),state.directBulkCapacityMl(),state.maximumSingleLiftGrams()); Load load=new Load(state.massGrams(),state.bulkMl(),state.heaviestObjectGrams());
        if(load.mass()>cap.mass()||load.volume()>cap.volume()||load.largest()>cap.singleLift()) throw new IllegalStateException("The Chronicle cannot physically carry that load.");
    }
    /** What the Chronicle is carrying and what they can carry — public so movement can ask before deep water (#156/#157). */
    public LoadState currentLoad(UUID chronicle) { return loadState(chronicle); }

    /**
     * What you are carrying, what state it is in, and how near your limit you are (#37).
     *
     * <p>Act fifteen found the whole of this unaskable. {@code item_instance} has carried
     * {@code condition_state} (SOUND / WORN / BROKEN), {@code use_count} and {@code quality_grade} since the
     * table existed, and the LOAD is computed on every single action — mass, bulk, the single-lift limit, carry
     * aids, a draft team's haul. None of it could be asked for:
     *
     * <pre>
     *   what am I carrying      check my tools        what tools do I have
     *   is anything broken      how worn is the knife how is my axe
     *   am I carrying too much
     * </pre>
     *
     * <p>{@code how heavy is my pack} did reach MEASURE, and answered <i>"You have nothing by that name in hand
     * to weigh"</i> — the load computed on every action, answered as though a pack were an object to put on
     * scales.
     *
     * <p>Read-only and nothing new: the same {@link #loadState} the carry checks are made against, said out
     * loud. Worn and broken things are named FIRST, because a broken tool is the thing a person most needs to
     * know and the condition that decides whether work can be done at all.
     */
    @Transactional(readOnly = true)
    public String gearStocktake(UUID chronicle) { return gearStocktake(chronicle, null); }

    /** As above, and if the sentence NAMES a thing, answer about that thing. */
    @Transactional(readOnly = true)
    public String gearStocktake(UUID chronicle, String actionText) {
        record Held(String name, int count, String condition) { }
        java.util.List<Held> held = jdbc.query(REACHABLE_CTE +
            "SELECT lower(d.display_name), COUNT(*)::int, MIN(i.condition_state) " +
            "FROM reachable r JOIN item_instance i ON i.object_id=r.id JOIN item_definition d ON d.item_key=i.item_key " +
            "JOIN world_object w ON w.id=r.id WHERE w.current_owner_id=? " +
            "GROUP BY lower(d.display_name) " +
            // Broken first, then worn, then sound: the order a person would look in.
            "ORDER BY CASE MIN(i.condition_state) WHEN 'BROKEN' THEN 0 WHEN 'WORN' THEN 1 ELSE 2 END, 1",
            (rs, row) -> new Held(rs.getString(1), rs.getInt(2), rs.getString(3)),
            chronicle, chronicleLocation(chronicle), chronicle);

        LoadState load = loadState(chronicle);
        // A sentence that NAMES a thing is a question about that thing. Asked "how is my axe" and answered
        // with a list of your clothes is the approximate answer this project triages above a missing one.
        String said0 = actionText == null ? "" : actionText.toLowerCase(java.util.Locale.ROOT);
        if (!said0.isEmpty()) {
            Held named = held.stream().filter(h -> com.devosphere.draugr.narration.Words.word(said0, h.name())
                    || said0.contains(h.name())).findFirst().orElse(null);
            if (named == null) {
                // Or by the LAST word of its name, which is what the thing is: an axe, a knife, a pot.
                named = held.stream().filter(h -> {
                    String[] w = h.name().split("\s+");
                    String head = w[w.length - 1];
                    return head.length() >= 3 && com.devosphere.draugr.narration.Words.word(said0, head);
                }).findFirst().orElse(null);
            }
            if (named != null) return oneThing(named.name(), named.count(), named.condition());
        }
        if (held.isEmpty())
            return "You are carrying nothing at all — not a tool, not a scrap. Whatever you mean to do here, you "
                 + "will be doing it with your hands.";

        java.util.List<String> broken = new java.util.ArrayList<>(), worn = new java.util.ArrayList<>(), sound = new java.util.ArrayList<>();
        for (Held h : held) {
            String named = h.count() > 1 ? h.count() + " " + h.name() : h.name();
            if ("BROKEN".equals(h.condition())) broken.add(named);
            else if ("WORN".equals(h.condition())) worn.add(named);
            else sound.add(named);
        }
        StringBuilder said = new StringBuilder();
        if (!broken.isEmpty()) said.append("Past use until it is mended: ").append(joinAnd(broken)).append(". ");
        if (!worn.isEmpty()) said.append("Worn and wanting attention: ").append(joinAnd(worn)).append(". ");
        if (!sound.isEmpty()) said.append("Sound and to hand: ").append(joinAnd(sound)).append(". ");

        // And how near the limit, which is the other half of the question and was computed all along.
        int massPct = load.sustainedMassCapacityGrams() <= 0 ? 0
                    : (int) Math.round(100.0 * load.massGrams() / load.sustainedMassCapacityGrams());
        int bulkPct = load.directBulkCapacityMl() <= 0 ? 0
                    : (int) Math.round(100.0 * load.bulkMl() / load.directBulkCapacityMl());
        int worst = Math.max(massPct, bulkPct);
        said.append(worst >= 95 ? "You are loaded to the limit of what you can bear, and the next thing you pick up will have to replace something."
                  : worst >= 70 ? "You are carrying a good load — heavy enough to feel on a long walk, with room for a little more."
                  : worst >= 35 ? "The load sits easily enough on you, with room for a good deal more."
                  : "You are travelling light, with room for whatever the day turns up.");
        return said.toString().trim();
    }

    /** What one named thing is worth, by its condition — the answer to "how is my axe" (#37). */
    private static String oneThing(String name, int count, String condition) {
        String many = count > 1 ? " (you have " + count + ")" : "";
        return switch (condition == null ? "SOUND" : condition) {
            case "BROKEN" -> "The " + name + " is broken" + many + " — past use until it is mended.";
            case "WORN" -> "The " + name + " is worn" + many + ": it still serves, but it has had use out of it and will want mending before long.";
            default -> "The " + name + " is sound" + many + ", with no fault in it you can find.";
        };
    }

    /**
     * The biggest load-bed among the sound draft vehicles this keeper owns, in {@code max_mass_grams} or
     * {@code max_volume_ml} — and 0 when they own no vehicle at all, which is what makes a team with nothing to
     * pull add nothing.
     *
     * <p><b>Four vehicles were one vehicle.</b> The haul was gated on {@code EXISTS(... draft_vehicle ...)} — a
     * yes-or-no — and then took the whole of the team's pull, so a travois, a sledge, a cart and a pack-saddle were
     * interchangeable. Two of the tests in this suite said so outright, asserting {@code before + 250000} for a
     * sledge and {@code before + 250000} for a pack-saddle: the same number, because the vehicle never entered the
     * sum. A 2 kg pack-saddle hauled exactly what an 8 kg cart hauled, and the cart cost two turned wheels and
     * two hours more to build for nothing. That is the generic equipment class this ticket's acceptance criterion
     * forbids, in the one place nobody had looked.
     *
     * <p>Nothing here is invented: the world already declares all four beds, and has since V187–V194 —
     * pack-saddle 120 kg, travois 250 kg, sledge 400 kg, cart 600 kg. The code simply never read them. A keeper's
     * team can bring home what the team can pull or what the bed can hold, whichever runs out first, which is why
     * a cart is worth its wheels the moment there is more than one ox in the yoke.
     *
     * <p>Read per OBJECT first ({@code container_properties}, set when the vehicle was made) and only then from
     * the item's declared default, so a particular cart answers for itself. A vehicle with neither limits nothing
     * rather than hauling nothing — an undeclared bed must not silently become a bed of zero.
     */
    private static String bestBed(String column) {
        return "COALESCE((SELECT MAX(COALESCE(cp." + column + ", ccd." + column + ", 2147483647)) " +
               " FROM item_instance ti JOIN world_object tw ON tw.id=ti.object_id " +
               " JOIN draft_vehicle dv ON dv.item_key=ti.item_key " +
               " LEFT JOIN container_properties cp ON cp.object_id=tw.id " +
               " LEFT JOIN container_capacity_default ccd ON ccd.item_key=ti.item_key " +
               " WHERE ti.condition_state <> 'BROKEN' AND tw.current_owner_id=c.chronicle_id AND tw.lifecycle_state='ACTIVE'), 0)";
    }

    private LoadState loadState(UUID chronicle) {
        // A carrying aid (pole/yoke/harness/pack frame) worn or held adds its bonus to sustained mass / bulk
        // capacity while equipped (#57 carry_aid_bonus). The single-object lift limit is unchanged — an aid
        // spreads a load, it does not make one object lighter to heave. A tamed draft animal hitched to a travois
        // (#100) adds its species' haul to sustained mass/bulk — the beast drags the frame, so the handler moves
        // far beyond their own back; it needs BOTH a travois to hand and a TAMED draft-capable beast, and adds 0
        // otherwise. The single-object lift limit is again unchanged — a travois hauls a heap, not one heavier thing.
        Capacity cap=jdbc.query("SELECT c.sustained_mass_grams, c.direct_bulk_ml, c.maximum_single_lift_grams, COALESCE(a.load_conditioning,0), COALESCE(a.recovery_readiness,.5), " +
            "COALESCE((SELECT SUM(b.mass_bonus_grams) FROM equipment_attachment e JOIN item_instance ii ON ii.object_id=e.item_id JOIN carry_aid_bonus b ON b.item_key=ii.item_key WHERE e.chronicle_id=c.chronicle_id),0), " +
            "COALESCE((SELECT SUM(b.bulk_bonus_ml)    FROM equipment_attachment e JOIN item_instance ii ON ii.object_id=e.item_id JOIN carry_aid_bonus b ON b.item_key=ii.item_key WHERE e.chronicle_id=c.chronicle_id),0), " +
            // What the TEAM can pull, capped by what the thing they are pulling can hold (#106). The cap was
            // missing, so a pack-saddle hauled what a cart hauled: see bestBed below.
            "LEAST(COALESCE((SELECT SUM(ds.haul_bonus_grams * (100 - GREATEST(wb.draft_fatigue, wb.draft_hunger, wb.draft_thirst) * (200 - wb.draft_conditioning) / 200) / 100) FROM wildlife_bond wb JOIN wildlife_population wp ON wp.id=wb.population_id JOIN draft_species ds ON ds.species_key=wp.species_key WHERE wb.chronicle_id=c.chronicle_id AND wb.bond_stage='TAMED'),0), " + bestBed("max_mass_grams") + "), " +
            "LEAST(COALESCE((SELECT SUM(ds.bulk_bonus_ml * (100 - GREATEST(wb.draft_fatigue, wb.draft_hunger, wb.draft_thirst) * (200 - wb.draft_conditioning) / 200) / 100)    FROM wildlife_bond wb JOIN wildlife_population wp ON wp.id=wb.population_id JOIN draft_species ds ON ds.species_key=wp.species_key WHERE wb.chronicle_id=c.chronicle_id AND wb.bond_stage='TAMED'),0), " + bestBed("max_volume_ml") + ") " +
            "FROM chronicle_carry_capacity c LEFT JOIN chronicle_capability_adaptation a ON a.chronicle_id=c.chronicle_id WHERE c.chronicle_id=?",rs->rs.next()?new Capacity((int)(rs.getInt(1)*(1+rs.getDouble(4)*.12*rs.getDouble(5)))+rs.getInt(6)+rs.getInt(8),rs.getInt(2)+rs.getInt(7)+rs.getInt(9),(int)(rs.getInt(3)*(1+rs.getDouble(4)*.08*rs.getDouble(5)))):new Capacity(0,0,0),chronicle);
        Load load=jdbc.query("WITH RECURSIVE carried(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' UNION ALL SELECT ic.item_id FROM item_containment ic JOIN carried c ON ic.container_id=c.id) SELECT COALESCE(SUM(d.unit_mass_grams),0),COALESCE(SUM(d.unit_volume_ml),0),COALESCE(MAX(d.unit_mass_grams),0) FROM carried JOIN item_instance i ON i.object_id=carried.id JOIN item_definition d ON d.item_key=i.item_key",rs->rs.next()?new Load(rs.getInt(1),rs.getInt(2),rs.getInt(3)):new Load(0,0,0),chronicle);
        return new LoadState(load.mass(),load.volume(),load.largest(),cap.mass(),cap.volume(),cap.singleLift());
    }
    private List<ContainerView> containers(UUID chronicle) {
        return jdbc.query("WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE'), contents(container_id,id) AS (SELECT ic.container_id,ic.item_id FROM item_containment ic UNION ALL SELECT c.container_id,ic.item_id FROM contents c JOIN item_containment ic ON ic.container_id=c.id) SELECT w.id,w.display_name,cp.max_mass_grams,cp.max_volume_ml,COALESCE(SUM(d.unit_mass_grams),0),COALESCE(SUM(d.unit_volume_ml),0) FROM reachable r JOIN container_properties cp ON cp.object_id=r.id JOIN world_object w ON w.id=r.id LEFT JOIN contents ct ON ct.container_id=r.id LEFT JOIN item_instance i ON i.object_id=ct.id LEFT JOIN item_definition d ON d.item_key=i.item_key GROUP BY w.id,w.display_name,cp.max_mass_grams,cp.max_volume_ml ORDER BY w.display_name",(rs,row)->new ContainerView(rs.getObject(1,UUID.class),rs.getString(2),rs.getInt(3),rs.getInt(4),rs.getInt(5),rs.getInt(6)),chronicle);
    }
    private record Capacity(int mass,int volume,int singleLift){} private record Load(int mass,int volume,int largest){}
    public record ItemView(UUID id,String displayName,String itemKey,UUID ownerId,UUID containerId){}
    public record EquippedView(UUID id,String displayName,String itemKey,String bodyPosition,String layer){}
    public record LoadState(int massGrams,int bulkMl,int heaviestObjectGrams,int sustainedMassCapacityGrams,int directBulkCapacityMl,int maximumSingleLiftGrams){}
    public record ContainerView(UUID id,String displayName,int maxMassGrams,int maxVolumeMl,int usedMassGrams,int usedVolumeMl){}
    public record ItemState(UUID chronicleId,List<ItemView> carried,List<EquippedView> equipped,LoadState load,List<ContainerView> containers){}
}
