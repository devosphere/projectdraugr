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
    private boolean consumeFromReach(UUID chronicle, UUID location, String itemKey, Instant occurredAt) {
        UUID item = jdbc.query(REACHABLE_CTE +
            "SELECT r.id FROM reachable r JOIN item_instance i ON i.object_id=r.id JOIN world_object w ON w.id=r.id " +
            "WHERE i.item_key=? ORDER BY CASE WHEN w.current_owner_id=? THEN 0 ELSE 1 END, r.id FOR UPDATE OF i LIMIT 1",
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
        String g = jdbc.query(REACHABLE_CTE +
            "SELECT i.quality_grade FROM reachable r JOIN item_instance i ON i.object_id=r.id JOIN world_object w ON w.id=r.id " +
            "WHERE i.item_key=? ORDER BY CASE WHEN w.current_owner_id=? THEN 0 ELSE 1 END, r.id LIMIT 1",
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
    private static final int DRAFT_REST_RECOVERY = 40;

    /** Work the Chronicle's hitched draft beasts (#101): moving or travelling with a draft vehicle to hand tires every
     *  tamed draft-capable beast bonded to them, up to spent. A beast with no vehicle to pull is not worked. */
    @Transactional
    public void workDraftBeasts(UUID chronicle) {
        jdbc.update(
            "UPDATE wildlife_bond wb SET draft_fatigue = LEAST(100, draft_fatigue + ?), draft_conditioning = LEAST(100, draft_conditioning + 3) " +
            "WHERE wb.chronicle_id=? AND wb.bond_stage='TAMED' " +
            "AND EXISTS (SELECT 1 FROM wildlife_population wp JOIN draft_species ds ON ds.species_key=wp.species_key WHERE wp.id=wb.population_id) " +
            "AND EXISTS (SELECT 1 FROM item_instance ti JOIN world_object tw ON tw.id=ti.object_id " +
            "  WHERE ti.item_key IN (SELECT item_key FROM draft_vehicle) AND tw.current_owner_id=? AND tw.lifecycle_state='ACTIVE')",
            DRAFT_FATIGUE_PER_WORK, chronicle, chronicle);
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

    /** Rest the draft beasts of any keeper who has a completed animal pen at hand (#108): each turn of the world, a
     *  beast whose keeper stands where a pen stands recovers draft-fatigue even as the keeper works — so a pen keeps
     *  the draft team fresh. Set-based over the whole world; runs in the tick. */
    @Transactional
    public void restPennedDraftBeasts(Instant now) {
        jdbc.update(
            "UPDATE wildlife_bond wb SET draft_fatigue = GREATEST(0, draft_fatigue - ?) " +
            "WHERE wb.draft_fatigue > 0 AND EXISTS (" +
            "  SELECT 1 FROM world_object cw JOIN construction_project cp ON cp.project_kind='ANIMAL_PEN' AND cp.state='COMPLETED' " +
            "  JOIN world_object pw ON pw.id=cp.object_id AND pw.lifecycle_state='ACTIVE' AND pw.current_location_id=cw.current_location_id " +
            "  WHERE cw.id=wb.chronicle_id)", PEN_REST_RECOVERY);
    }

    // ---- water handling (#71) ----------------------------------------------------------------------------
    private static final String[] WATER_VESSELS = {"waterskin","wooden_bucket","clay_pot","clay_jar","fired_bowl","fired_cup","clay_water_filter","wooden_bowl","wooden_trough"};
    /** Whether the Chronicle carries anything that can hold water to fill or boil in (#71). */
    @Transactional(readOnly = true)
    public boolean hasWaterVessel(UUID chronicle) { for (String v : WATER_VESSELS) if (hasAtLeast(chronicle, v, 1)) return true; return false; }
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
        Integer spent = jdbc.queryForObject("SELECT COUNT(*) FROM construction_project cp JOIN world_object w ON w.id=cp.object_id JOIN fire_state fs ON fs.construction_id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='STONE_FIRE_PIT' AND cp.state='COMPLETED' AND fs.active=false", Integer.class, location);
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

        if (candidates.isEmpty()) {
            // No chunk_flora rows — fall back to biome affinity check
            candidates = jdbc.queryForList(
                "SELECT fd.flora_key, fd.organism_type, fd.tool_required, fd.is_poisonous, " +
                "  (SELECT d.item_key FROM flora_drop d WHERE d.flora_key=fd.flora_key ORDER BY d.item_key LIMIT 1) AS drop_item, " +
                "  (SELECT id.category FROM item_definition id JOIN flora_drop d ON d.item_key=id.item_key WHERE d.flora_key=fd.flora_key ORDER BY d.item_key LIMIT 1) AS drop_category " +
                "FROM flora_definition fd " +
                "WHERE fd.organism_type <> 'TREE' AND fd.biome_affinity ILIKE ? " +
                "ORDER BY fd.flora_key", "%" + biome + "%");
        }
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

        java.util.Optional<java.util.Map<String,Object>> named = pool.stream()
            .filter(c -> lower.contains(((String)c.get("flora_key")).replace("_"," "))
                || (c.get("drop_item") != null && lower.contains(((String)c.get("drop_item")).replace("_"," "))))
            .findFirst();
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

        // Pick first available drop (simplest — can expand to multi-drop later)
        java.util.Map<String,Object> drop = drops.get(0);
        String itemKey = (String) drop.get("item_key");
        int yieldMin = ((Number) drop.get("yield_min")).intValue();
        int yieldMax = ((Number) drop.get("yield_max")).intValue();
        int yield = yieldMin + (yieldMax > yieldMin ? (int)(Math.random() * (yieldMax - yieldMin + 1)) : 0);
        int available = capacityHeadroomUnits(chronicle, itemKey);
        int count = Math.min(yield, Math.max(1, available));
        if (available <= 0) return new String[]{"FAILED", "You cannot carry any more of what you find here."};

        // Look up display name from item_definition
        String displayName = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, itemKey);

        for (int i = 0; i < count; i++) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM',?,?)", id, displayName, chronicle);
            jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,?,'SOUND')", id, itemKey);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'GATHERED',jsonb_build_object('floraKey',?,'biome',?))", id, Timestamp.from(occurredAt), floraKey, biome);
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
            "WHERE i.item_key IN ('stone_axe','stone_hatchet','copper_axe','bronze_axe','iron_axe','steel_axe','hand_axe') " +
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
                case "WETLAND", "RIVERBANK" -> "willow";
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
            " fell — far too heavy to shoulder whole. You will need to buck and split the wood into pieces you can carry." +
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
                case "WETLAND", "RIVERBANK" -> "willow";
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
        String key = matcher.matchAndRecord(actionText, chronicle);
        if (key != null) return executeProcess(chronicle, location, key, actionText, at);
        // A deterministic miss. The AI Procedure Interpreter may compose this from existing processes
        // (DR-0021), but that orchestration lives in ChronicleActionService where the AI seam is; here
        // the world simply does not yet know how, and says so.
        return new String[]{"FAILED", "You turn the material over in your hands, but no way to work it into what you meant comes to you here. Whatever that would take, it is not a thing your hands find on their own."};
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
            "requires_fire, requires_water, narration, station_kind FROM material_process WHERE process_key=?", key);

        // A workstation (bench/loom) sited within reach EASES this operation — it never gates it (bare-handed
        // always works) and never decides its grade (skill + materials do). See V69: it gives efficiency (less
        // waste) plus a minor, bounded quality assist. Detected through the unified reachability model (Layer 1),
        // so a bench standing in an on-site workshop counts.
        String stationKind = (String) match.get("station_kind");
        boolean atStation = stationKind != null && hasAtLeast(chronicle, stationKind, 1);

        // The tool this work turns on (#220): pick the soundest one in reach. A broken tool no longer counts — it
        // must be mended first — and the chosen tool wears a little with the work (applied on success, below).
        // Tool classes without a keyed toolset gate on nothing, exactly as before.
        String toolClass = (String) match.get("tool_class");
        java.util.Map<String,Object> toolUsed = null;
        if (toolClass != null && (toolClass.equals("CUTTING") || toolClass.equals("STRIKING") || toolClass.equals("AXE"))) {
            toolUsed = soundestToolOfClass(chronicle, location, toolClass);
            if (toolUsed == null)
                return new String[]{"FAILED", "This work turns on a tool you have not got in reach — an edge, a hammer, an axe, whatever it needs. Bare hands only bruise the material."};
            if ("BROKEN".equals(toolUsed.get("cond")) || "DESTROYED".equals(toolUsed.get("cond")))
                return new String[]{"FAILED", "The tool this work turns on is past biting — its edge gone or its head loose. Mend it against a whetstone or with cordage before it will serve."};
        }
        if (Boolean.TRUE.equals(match.get("requires_fire"))) {
            Boolean fire = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM fire_state fs JOIN world_object w ON w.id=fs.construction_id WHERE w.current_location_id=? AND fs.active=true)", Boolean.class, location);
            if (!Boolean.TRUE.equals(fire)) return new String[]{"FAILED", "This work needs heat, and no fire burns within reach of it. Cold, the material will not give."};
        }
        if (Boolean.TRUE.equals(match.get("requires_water"))) {
            String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
            if (!"WETLAND".equals(biome)) return new String[]{"FAILED", "This work needs water, and there is none at hand to work it with. Dry, the process cannot even begin."};
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
                return new String[]{"FAILED", where != null
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
        String kind = preservationKind(outKey);
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
            String okind = preservationKind(ok);
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
        return new String[]{"SUCCEEDED", (String) match.get("narration")};
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
            "WHERE w.lifecycle_state='ACTIVE' AND w.current_owner_id IS NULL AND wc.biome IN ('WETLAND','RIVERBANK') " +
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
    @Transactional
    public void rotExposedOrganics(Instant now) {
        java.sql.Timestamp ts = java.sql.Timestamp.from(now);
        java.util.List<java.util.Map<String,Object>> exposed = jdbc.queryForList(
            "SELECT i.object_id, i.weathered_at FROM item_instance i JOIN world_object w ON w.id=i.object_id JOIN world_chunk wc ON wc.id=w.current_location_id " +
            "WHERE w.lifecycle_state='ACTIVE' AND w.current_owner_id IS NULL AND wc.biome IN ('WETLAND','RIVERBANK') " +
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
    /** What one harvest takes from a field's fertility. */
    private static final int FERTILITY_COST_PER_HARVEST = 30;
    /** How much fertility a field wins back for each day left fallow. */
    private static final int FERTILITY_RECOVER_PER_DAY = 2;

    /** A field's current fertility (#164), pristine ground reading full — the stored level plus what fallow time since
     *  it was last worked has restored (not persisted until the next harvest writes it). */
    private int fieldFertility(UUID chunk, Instant at) {
        java.util.Map<String,Object> soil = jdbc.query(
            "SELECT fertility, last_updated_at FROM field_soil WHERE chunk_id=?",
            rs -> rs.next() ? java.util.Map.of("f", rs.getInt(1), "t", rs.getTimestamp(2).toInstant()) : null, chunk);
        if (soil == null) return 100; // never cropped — pristine
        long fallowDays = Math.max(0, java.time.Duration.between((Instant) soil.get("t"), at).toDays());
        return Math.min(100, (int) soil.get("f") + (int) (fallowDays * FERTILITY_RECOVER_PER_DAY));
    }

    /** Draw a field's fertility down by a harvest (#164), from its fallow-recovered current level, and stamp the time. */
    private void depleteFertility(UUID chunk, Instant at) {
        int next = Math.max(0, fieldFertility(chunk, at) - FERTILITY_COST_PER_HARVEST);
        java.sql.Timestamp ts = java.sql.Timestamp.from(at);
        jdbc.update("INSERT INTO field_soil (chunk_id, fertility, last_updated_at) VALUES (?,?,?) " +
            "ON CONFLICT (chunk_id) DO UPDATE SET fertility=EXCLUDED.fertility, last_updated_at=EXCLUDED.last_updated_at", chunk, next, ts);
    }
    /** Ground a crop will take (#164/#165): open grassland by nature, or forest ground a Chronicle has cleared. Rock,
     *  ocean, mountain, and standing forest are not arable until the trees are taken off them. */
    private boolean isArable(UUID location) {
        String biome = jdbc.query("SELECT biome FROM world_chunk WHERE id=?", rs -> rs.next() ? rs.getString(1) : null, location);
        if ("GRASSLAND".equals(biome)) return true;
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
            " WHERE i.item_key IN ('stone_axe','stone_hatchet','copper_axe','bronze_axe','iron_axe','steel_axe','hand_axe')" +
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
            return new String[]{"FAILED", "Tillage wants open, workable ground — a grassland clearing, or wooded ground you have first cleared — and this ground is not it."};
        if (!hasAtLeast(chronicle, "digging_stick", 1) && !hasAtLeast(chronicle, "wooden_shovel", 1))
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
            return new String[]{"FAILED", "Grain wants open, workable ground — a grassland clearing, or wooded ground you have first cleared — and this ground is not it."};
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
            "SELECT id, sown_at, maturity_days, tilled, grazed, (weeded_at IS NOT NULL) AS weeded FROM crop_stand WHERE chunk_id=? AND harvested=false ORDER BY sown_at LIMIT 1 FOR UPDATE",
            rs -> rs.next() ? java.util.Map.of("id", rs.getObject(1, UUID.class), "sown", rs.getTimestamp(2).toInstant(), "days", rs.getInt(3), "tilled", rs.getBoolean(4), "grazed", rs.getBoolean(5), "weeded", rs.getBoolean(6)) : null, location);
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
        long daysLate = java.time.Duration.between(ripe, at).toDays();
        boolean shattering = daysLate > CROP_FULL_YIELD_DAYS;
        int heads = shattering ? Math.max(2, base / 2) : base;
        // The harvest takes from the soil — the field's fertility falls, to be won back only by fallow rest.
        depleteFertility(location, at);
        for (int i = 0; i < heads; i++) createCarriedItem(chronicle, "wild_grain_head", "Wild grain head", at, "HARVESTED_CROP");
        jdbc.update("UPDATE crop_stand SET harvested=true, harvested_at=?, outcome='REAPED' WHERE id=?", java.sql.Timestamp.from(at), crop.get("id"));
        return new String[]{"SUCCEEDED", shattering
            ? "You reap the stand, but you left it late — much of the grain has already shattered from the heads and the birds have been at it. You gather what is left."
            : grazed
            ? "You reap what the animals left: the stand has been cropped and trampled where they grazed through it, and a fence would have kept it whole. You gather the rest."
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
        java.util.List<String> keys = switch (toolClass) {
            case "CUTTING"  -> java.util.List.of("stone_knife","stone_hatchet","stone_flake","stone_adze","stone_chisel","flint_burin","flint_scraper","bone_scraper","bronze_knife");
            case "STRIKING" -> java.util.List.of("stone_hammer","primitive_pickaxe","field_stone","granite_cobble","basalt_cobble");
            case "AXE"      -> java.util.List.of("stone_axe","stone_hatchet","copper_axe","bronze_axe","iron_axe","steel_axe");
            default -> null;
        };
        if (keys == null) return null;
        String inList = keys.stream().map(k -> "'" + k + "'").collect(java.util.stream.Collectors.joining(","));
        return jdbc.query(REACHABLE_CTE +
            "SELECT i.object_id, i.condition_state, i.use_count, i.item_key FROM reachable r JOIN item_instance i ON i.object_id=r.id " +
            "WHERE i.item_key IN (" + inList + ") " +
            "ORDER BY CASE i.condition_state WHEN 'SOUND' THEN 0 WHEN 'WORN' THEN 1 WHEN 'BROKEN' THEN 2 ELSE 3 END, i.use_count LIMIT 1",
            rs -> rs.next() ? java.util.Map.of("id", rs.getObject(1, UUID.class), "cond", rs.getString(2), "uses", rs.getInt(3), "key", rs.getString(4)) : null,
            chronicle, location);
    }

    /** The preservation kind a made food keeps as, or null if it is not a made food that keeps by a tier (V60/M4).
     *  Beyond the explicitly-preserved fish/meat, the cooked dishes (V92) keep by their nature: a dense, dry baked
     *  bread or cake keeps for weeks like other dried food, while a boiled or stewed wet dish keeps only days —
     *  before this they fell through to null and so never spoiled at all, an immortal pot of stew. */
    private static String preservationKind(String itemKey) {
        return switch (itemKey) {
            case "salted_fish", "salted_meat" -> "SALTED";
            case "smoked_fish", "smoked_meat", "smoked_fowl" -> "SMOKED";
            case "dried_fish", "dried_meat", "dried_mushroom", "pemmican", "preserved_berries",
                 "acorn_flatbread", "grain_flatbread", "trail_cake" -> "DRIED"; // dense, dry keeping breads and cakes
            case "root_vegetable_stew", "grain_porridge", "herbal_infusion", "cooked_mushrooms", "berry_compote" -> "COOKED";
            default -> null;
        };
    }

    /** Preserved food keeps far longer than raw: salted longest, then dried, then smoked, then a plain cooked dish. */
    private void registerPreserved(UUID item, String kind, Instant at) {
        long hours = switch (kind) { case "SALTED" -> 1440; case "SMOKED" -> 720; case "COOKED" -> 72; default -> 1080; };
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
    public static int mineralSeedFor(UUID chunk, String mineralKey, double rarity) {
        int base = 20 + (int) Math.round(rarity * 50);               // rarity 0.15 -> 28, 0.40 -> 40, 0.55 -> 48
        int h = Math.floorMod((chunk.toString() + ":" + mineralKey).hashCode(), 100); // 0..99, fixed for this ground
        double richness = 0.6 + (h / 100.0) * 0.9;                    // 0.6 (poor) .. ~1.5 (rich)
        return Math.max(12, (int) Math.round(base * richness));
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
        java.util.List<java.util.Map<String,Object>> here = jdbc.queryForList(
            "SELECT mineral_key, display_name, rarity, tool_required, yield_min, yield_max FROM mineral_definition " +
            "WHERE " + affinityOr + " ORDER BY rarity DESC", affinity.toArray());
        if (here.isEmpty())
            return new String[]{"FAILED", "You turn over what stone there is. This ground has nothing in it but dirt."};

        java.util.Map<String,Object> target = here.stream()
            .filter(m -> v.contains(((String)m.get("mineral_key")).replace('_',' ')) || v.contains(((String)m.get("display_name")).toLowerCase()))
            .findFirst().orElse(null);
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

    private InsectHarvest harvestColony(UUID chronicle, UUID location, String actionText, Instant occurredAt, String intent) {
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
        String lower = actionText.toLowerCase(java.util.Locale.ROOT);
        String season = seasonOf(occurredAt);

        // Candidate colony kinds for this intent, present in this biome and season.
        java.util.List<java.util.Map<String,Object>> kinds = jdbc.queryForList(
            "SELECT ck.colony_kind, ck.hazard_kind, ck.hazard_min, ck.hazard_max, ck.smoke_suppresses " +
            "FROM insect_colony_kind ck " +
            "WHERE ck.harvest_intent=? AND ck.biome_affinity ILIKE ? " +
            "AND (ck.season_active='ALL' OR ck.season_active ILIKE ?) " +
            "ORDER BY ck.colony_kind", intent, "%" + biome + "%", "%" + season + "%");
        if (kinds.isEmpty()) {
            return new InsectHarvest("FAILED", intent.equals("RAID_HIVE")
                ? "You search for a hive or nest to raid, but find none here to work."
                : "You turn over the ground and growth for insects, but find nothing worth taking here.", 0, null);
        }

        // Prefer a colony the action text names; otherwise the first available.
        java.util.Map<String,Object> kind = kinds.stream()
            .filter(k -> lower.contains(((String)k.get("colony_kind")).replace("_"," ").replace(" colony","").replace(" swarm","").replace(" patch","").replace(" den","").replace(" nest","").replace(" hive","")))
            .findFirst().orElse(kinds.get(0));
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
            "SELECT item_key, yield_min, yield_max, rarity FROM insect_colony_product WHERE colony_kind=? ORDER BY rarity DESC", colonyKind);
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
            for (int i = 0; i < take; i++) {
                UUID id = UUID.randomUUID();
                jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM',?,?)", id, displayName, chronicle);
                jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,?,'SOUND')", id, itemKey);
                jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'GATHERED',jsonb_build_object('colonyKind',?,'biome',?))", id, Timestamp.from(occurredAt), colonyKind, biome);
            }
            totalTaken += take;
        }
        // Record disturbance on any placed instance of this colony in the chunk.
        jdbc.update("UPDATE insect_colony SET last_disturbed_at=?, health=GREATEST(0,health-15) WHERE chunk_id=? AND colony_kind=?", Timestamp.from(occurredAt), location, colonyKind);
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

    // A basket is woven from whatever flexible stock is to hand — split withies, vines, plant fibre, or
    // twisted cordage — not plant fibre alone (#34). Each length is one "weave unit"; cordage is stronger and
    // longer, so it counts double. Eight units make a basket. This is why a player carrying cordage, branches,
    // and vines can now actually reach a basket instead of being told, wrongly, that only fibre will do.
    private static final int BASKET_WEAVE_UNITS = 8;

    /** Reachable weave units for a basket: plant fibre / vine (1 each) + cordage (2 each). */
    @Transactional(readOnly = true)
    public int basketWeaveUnitsInReach(UUID chronicle) {
        UUID location = chronicleLocation(chronicle);
        return reachCount(chronicle, location, "plant_fiber")
             + reachCount(chronicle, location, "vine")
             + 2 * reachCount(chronicle, location, "fiber_cordage");
    }

    @Transactional
    public ItemView craftBasket() {
        UUID chronicle=activeChronicle(); UUID location=chronicleLocation(chronicle);
        // Reachable flexible stock, weakest/most-plentiful first so cordage (scarcer, stronger) is spared where
        // fibre or vine will do. Each row is one physical length; cordage carries two weave units. Reach is the
        // SAME as the dispatch guard (basketWeaveUnitsInReach) — carried, in carried containers, and on the
        // ground here — so the guard and the craft never disagree and drop a raw error on a stock mismatch.
        List<java.util.Map<String,Object>> stock=jdbc.query(REACHABLE_CTE + "SELECT r.id, i.item_key FROM reachable r JOIN item_instance i ON i.object_id=r.id WHERE i.item_key IN ('plant_fiber','vine','fiber_cordage') ORDER BY CASE i.item_key WHEN 'plant_fiber' THEN 0 WHEN 'vine' THEN 1 ELSE 2 END, r.id FOR UPDATE OF i", (rs,row)->java.util.Map.of("id",rs.getObject(1,UUID.class),"key",rs.getString(2)), chronicle, location);
        int units=0; for (java.util.Map<String,Object> m : stock) units += "fiber_cordage".equals(m.get("key")) ? 2 : 1;
        if(units<BASKET_WEAVE_UNITS) throw new IllegalStateException("Weaving a basket needs eight lengths of flexible stock — plant fibre, vine, or cordage — within reach of the Chronicle.");
        Instant now=Instant.now(); UUID basket=UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM','Woven basket',?)",basket,chronicle);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,'woven_basket','SOUND')",basket);
        jdbc.update("INSERT INTO container_properties (object_id,max_mass_grams,max_volume_ml) VALUES (?,12000,18000)",basket);
        int consumed=0; for (java.util.Map<String,Object> m : stock) { if (consumed>=BASKET_WEAVE_UNITS) break; String key=(String)m.get("key"); retire((UUID)m.get("id"),now,"CONSUMED_FOR_CRAFTING",key); consumed += "fiber_cordage".equals(key) ? 2 : 1; }
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
        if (!hasCuttingTool(chronicle)) throw new IllegalStateException("A blade is needed to cut and start the cordage.");
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
        if (!hasCuttingTool(chronicle)) throw new IllegalStateException("A blade is needed to cut and fit the strap.");
        if (!hasAtLeast(chronicle, "fiber_cordage", 2) || !hasAtLeast(chronicle, "plant_fiber", 2))
            throw new IllegalStateException("Insufficient physical material.");
        for (int i = 0; i < 2; i++) if (!consumeOne(chronicle, "fiber_cordage", at)) throw new IllegalStateException("Material changed.");
        for (int i = 0; i < 2; i++) if (!consumeOne(chronicle, "plant_fiber", at)) throw new IllegalStateException("Material changed.");
        UUID belt = UUID.randomUUID();
        createCraftedItem(chronicle, chronicleLocation(chronicle), belt, "utility_belt", "Primitive utility belt", at, "CRAFTED", QualityGrade.SOUND);
        return new ItemView(belt, "Primitive utility belt", "utility_belt", chronicle, null);
    }

    /** True if the Chronicle can reach any blade capable of carving wood. */
    @Transactional(readOnly = true)
    /** Any edged tool that can do cutting/scraping work. Beyond the knife/hatchet/flake, the adze, chisel,
     *  burin, and stone/bone scrapers are all worked edges — each was craftable but satisfied no tool gate,
     *  so making one did nothing; now it counts as the blade a CUTTING process or a knife-gated act needs. */
    public boolean hasCuttingTool(UUID chronicle) {
        return hasAtLeast(chronicle,"stone_knife",1) || hasAtLeast(chronicle,"stone_hatchet",1) || hasAtLeast(chronicle,"stone_flake",1)
            || hasAtLeast(chronicle,"stone_adze",1) || hasAtLeast(chronicle,"stone_chisel",1) || hasAtLeast(chronicle,"flint_burin",1)
            || hasAtLeast(chronicle,"flint_scraper",1) || hasAtLeast(chronicle,"bone_scraper",1);
    }

    /**
     * Light and spend a portable light to work by in the dark (#75): a rushlight or tallow candle burns down to
     * nothing for the task; an oil lamp keeps, but a measure of fish oil is burned. Returns false when the
     * Chronicle has no light to strike — then the fine work cannot be done. A fire in reach is checked by the
     * caller and needs none of these.
     */
    @Transactional
    public boolean consumePortableLight(UUID chronicle, Instant at) {
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
            .filter(c -> lower.contains(((String)c.get("name")).toLowerCase(java.util.Locale.ROOT))).findFirst()
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
            .filter(c -> lower.contains(((String)c.get("name")).toLowerCase(java.util.Locale.ROOT))).findFirst()
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
        if (lower.contains("sharpen") || lower.contains("whet") || lower.contains("hone") || (lower.contains("grind") && lower.contains("edge"))) {
            // A dressed stone_whetstone is a whetstone by any other name — it was craftable but read by
            // nothing, so honing accepts it too (#257), alongside the found whetstone and the grit-stones.
            if (!hasAtLeast(chronicle, "whetstone", 1) && !hasAtLeast(chronicle, "stone_whetstone", 1) && !hasAtLeast(chronicle, "sandstone_piece", 1) && !hasAtLeast(chronicle, "pumice_piece", 1))
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
            "CASE WHEN EXISTS(SELECT 1 FROM item_instance ti JOIN world_object tw ON tw.id=ti.object_id WHERE ti.item_key IN (SELECT item_key FROM draft_vehicle) AND tw.current_owner_id=c.chronicle_id AND tw.lifecycle_state='ACTIVE') " +
            " THEN COALESCE((SELECT SUM(ds.haul_bonus_grams * (100 - wb.draft_fatigue * (200 - wb.draft_conditioning) / 200) / 100) FROM wildlife_bond wb JOIN wildlife_population wp ON wp.id=wb.population_id JOIN draft_species ds ON ds.species_key=wp.species_key WHERE wb.chronicle_id=c.chronicle_id AND wb.bond_stage='TAMED'),0) ELSE 0 END, " +
            "CASE WHEN EXISTS(SELECT 1 FROM item_instance ti JOIN world_object tw ON tw.id=ti.object_id WHERE ti.item_key IN (SELECT item_key FROM draft_vehicle) AND tw.current_owner_id=c.chronicle_id AND tw.lifecycle_state='ACTIVE') " +
            " THEN COALESCE((SELECT SUM(ds.bulk_bonus_ml * (100 - wb.draft_fatigue * (200 - wb.draft_conditioning) / 200) / 100)    FROM wildlife_bond wb JOIN wildlife_population wp ON wp.id=wb.population_id JOIN draft_species ds ON ds.species_key=wp.species_key WHERE wb.chronicle_id=c.chronicle_id AND wb.bond_stage='TAMED'),0) ELSE 0 END " +
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
