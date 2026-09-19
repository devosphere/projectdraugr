package com.devosphere.draugr.people;

import com.devosphere.draugr.item.PhysicalItemService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A native community lives on the world clock (#111, epic #109).
 *
 * <p>Deterministic and compact, as the ticket asks: one step per simulated day per community, with no AI turn and no
 * per-individual scheduling. Each day the community's workers bring food into its store, everyone eats from the
 * store, and what that leaves decides how the community stands: fed and at its ordinary posture, hungry, closed to
 * visitors, or on the move. Every change of state is written to the community's history, which cannot be edited.
 *
 * <p>The food is real. Workers' takings are item instances held by the store's world object, spoiling on the same
 * clock as food anywhere; eating destroys the oldest sound item, one at a time. A store can therefore run out,
 * and that is the whole point: a people who can go hungry is a people whose goods are worth something to them, which
 * is what #113's trade will rest on.
 */
@Service
public class NativeCommunityService {

    /** Days simulated at most in one step: a world left alone for a year catches up in bounded work. */
    static final int MAX_DAYS_PER_STEP = 60;
    /** Days short before the community closes its store and its gates to outsiders. */
    static final int SHORTAGE_CLOSES = 3;
    /** Days short before the community leaves to find food elsewhere. */
    static final int SHORTAGE_MOVES = 14;

    private final JdbcTemplate jdbc;
    private final PhysicalItemService items;
    private final TerritoryService territory;
    private final AgreementService agreements;

    public NativeCommunityService(JdbcTemplate jdbc, PhysicalItemService items, TerritoryService territory, AgreementService agreements) {
        this.jdbc = jdbc;
        this.items = items;
        this.territory = territory;
        this.agreements = agreements;
    }

    // ── Where the reedkin live (#115, DR-0024). ────────────────────────────────────────────────────────────────────

    /** At most this many reedkin isles in a world: a people, not a population filling every marsh. */
    static final int REEDKIN_COMMUNITY_CAP = 2;
    /** Grid distance kept between two isles, so each has its own fishing water. */
    static final int REEDKIN_SPACING = 4;
    /** What an isle has put by when a Chronicle first comes to it: about three days for eight people. */
    static final int REEDKIN_STARTING_STORE = 24;

    /** What reedkin hands make from marsh and water, in the order they turn to it (DR-0024). */
    static final String[] MADE_GOODS = {"reed_mat", "fiber_cordage", "woven_basket", "fish_trap"};
    /** How many made things an isle keeps in its store before its makers turn to other work. */
    static final int MADE_GOODS_KEPT = 8;

    private static final String[] ISLE_NAMES = {"Sedge Holm", "Weir Isle"};
    private static final String[][] KIN = {{"Reed-bank kin", "Far-channel kin"}, {"Alder kin", "Low-water kin"}};
    /** Two households each: who speaks for the isle, who keeps its memory, who fishes, gathers and makes. */
    private static final String[][] HOUSEHOLDS = {
        {"HEADSPERSON", "FISHER", "FISHER", "CHILD"},
        {"ELDER", "FISHER", "FORAGER", "MAKER"}};
    private static final String[] NAMES = {
        "Ashreed", "Tamsin", "Keld", "Morrow", "Wren", "Fenna", "Lark", "Sedge",
        "Brannoch", "Otterley", "Mere", "Rushe", "Tern", "Holm", "Weiran", "Carra"};

    /**
     * Place the reedkin's isles in a world that has none yet, or fewer than it should: freshwater marsh where it
     * meets running water, never on a monster's ground, and never within {@link #REEDKIN_SPACING} of another isle.
     * Deterministic by grid position, so the same world always has its isles in the same places. Additive and
     * idempotent: run at genesis and on every boot through reconcile, it places only what is missing, and a world
     * with its isles is left alone.
     *
     * @return how many isles were founded by this call
     */
    @Transactional
    public int seedPeoples(UUID worldId) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM cognition_profile WHERE species_key='reedkin' AND cognition_class='PEOPLE')", Boolean.class)))
            return 0;
        List<Map<String, Object>> existing = jdbc.queryForList(
            "SELECT c.grid_x, c.grid_y FROM native_community n JOIN world_chunk c ON c.id=n.home_chunk_id " +
            "WHERE n.world_id=? AND n.species_key='reedkin'", worldId);
        if (existing.size() >= REEDKIN_COMMUNITY_CAP) return 0;

        // Marsh where it meets running water first; any freshwater marsh after that. WETLAND in this world is
        // freshwater marsh with reeds and fish in it, so plain marsh is honest reedkin ground — and a world made
        // before the generator had rivers (#156) has no river bank anywhere, so without the fallback the one world
        // actually being played would never have a people in it.
        List<Map<String, Object>> candidates = jdbc.queryForList(
            "SELECT c.id, c.grid_x, c.grid_y FROM world_chunk c WHERE c.world_id=? AND c.biome='WETLAND' " +
            "AND NOT EXISTS (SELECT 1 FROM ecology_site s WHERE s.chunk_id=c.id AND s.site_category='MONSTER') " +
            "ORDER BY EXISTS (SELECT 1 FROM world_chunk r WHERE r.world_id=c.world_id AND r.biome='RIVER_BANK' " +
            "                 AND abs(r.grid_x-c.grid_x)<=1 AND abs(r.grid_y-c.grid_y)<=1) DESC, " +
            "         md5(c.grid_x || ',' || c.grid_y || ':reedkin'), c.grid_x, c.grid_y", worldId);

        List<int[]> taken = new java.util.ArrayList<>();
        for (Map<String, Object> e : existing) taken.add(new int[]{((Number) e.get("grid_x")).intValue(), ((Number) e.get("grid_y")).intValue()});
        Instant now = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class).toInstant();
        int founded = 0;
        for (Map<String, Object> cand : candidates) {
            if (taken.size() >= REEDKIN_COMMUNITY_CAP) break;
            int x = ((Number) cand.get("grid_x")).intValue(), y = ((Number) cand.get("grid_y")).intValue();
            boolean clear = taken.stream().allMatch(t -> Math.max(Math.abs(t[0] - x), Math.abs(t[1] - y)) >= REEDKIN_SPACING);
            if (!clear) continue;
            foundReedkinIsle(worldId, (UUID) cand.get("id"), taken.size(), now);
            taken.add(new int[]{x, y});
            founded++;
        }
        return founded;
    }

    private void foundReedkinIsle(UUID worldId, UUID chunk, int ordinal, Instant now) {
        String isle = ISLE_NAMES[ordinal % ISLE_NAMES.length];
        UUID community = UUID.randomUUID();
        Timestamp ts = Timestamp.from(now);
        jdbc.update("INSERT INTO native_community (id,world_id,species_key,name,home_chunk_id,territory_radius,governance," +
            "base_trade_policy,trade_policy,base_security_posture,security_posture,staple_item_key,daily_ration,founded_at,last_simulated_at) " +
            "VALUES (?,?,'reedkin',?,?,1,'ELDERS','SELECTIVE','SELECTIVE','WARY','WARY','dried_fish',1,?,?)",
            community, worldId, isle, chunk, ts, ts);

        UUID village = place("NATIVE_SITE", isle + " village", chunk);
        jdbc.update("INSERT INTO native_settlement_site (object_id,community_id,site_kind,access_rule) VALUES (?,?,'VILLAGE','INVITED')", village, community);
        UUID store = place("NATIVE_SITE", isle + " store house", chunk);
        jdbc.update("INSERT INTO native_settlement_site (object_id,community_id,site_kind,access_rule,holds_stores) VALUES (?,?,'STORE_HOUSE','INVITED',TRUE)", store, community);

        int named = ordinal * 8;
        for (int h = 0; h < HOUSEHOLDS.length; h++) {
            UUID kin = UUID.randomUUID();
            jdbc.update("INSERT INTO native_kin_group (id,community_id,name,housing_site_id) VALUES (?,?,?,?)",
                kin, community, KIN[ordinal % KIN.length][h], village);
            for (String role : HOUSEHOLDS[h]) {
                String name = NAMES[named++ % NAMES.length];
                UUID body = place("NATIVE_PERSON", name + " of " + isle, chunk);
                jdbc.update("INSERT INTO native_individual (object_id,community_id,kin_group_id,given_name,role) VALUES (?,?,?,?,?)",
                    body, community, kin, name, role);
            }
        }
        String fish = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key='dried_fish'", String.class);
        for (int i = 0; i < REEDKIN_STARTING_STORE; i++) items.createHeldItem(store, "dried_fish", fish, now, "PUT_BY_COMMUNITY");
        // And what their hands have made, which is what they have to trade that is not food (#113).
        for (String good : new String[]{"reed_mat", "reed_mat", "fiber_cordage", "fiber_cordage", "woven_basket", "fish_trap"}) {
            String name = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, good);
            items.createHeldItem(store, good, name, now, "MADE_BY_COMMUNITY");
        }
        record(community, now, "FOUNDED", Map.of("people", 8, "isle", isle));
    }

    private UUID place(String type, String name, UUID chunk) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,?,?,?)", id, type, name, chunk);
        return id;
    }

    /** Bring every living community up to {@code now}, a whole day at a time. */
    @Transactional
    public void advanceTo(Instant now) {
        List<Map<String, Object>> communities = jdbc.queryForList(
            "SELECT id, last_simulated_at FROM native_community WHERE lifecycle <> 'DISPERSED' ORDER BY founded_at, id FOR UPDATE");
        for (Map<String, Object> c : communities) {
            UUID id = (UUID) c.get("id");
            Instant last = ((Timestamp) c.get("last_simulated_at")).toInstant();
            long days = Duration.between(last, now).toDays();
            if (days <= 0) continue;
            long stepped = Math.min(days, MAX_DAYS_PER_STEP);
            for (long d = 1; d <= stepped; d++) liveADay(id, last.plus(Duration.ofDays(d)));
            // A world far behind is caught up to the present in one go; the days beyond the bound are not replayed,
            // they are simply not simulated, which is the same choice every other bounded catch-up here makes.
            Instant reached = days > MAX_DAYS_PER_STEP ? now : last.plus(Duration.ofDays(stepped));
            jdbc.update("UPDATE native_community SET last_simulated_at=? WHERE id=?", Timestamp.from(reached), id);
        }
    }

    /** One day of one community: gather, eat, and stand accordingly. */
    void liveADay(UUID community, Instant day) {
        Map<String, Object> c = jdbc.queryForMap(
            "SELECT staple_item_key, daily_ration, shortage_days, lifecycle, base_trade_policy, base_security_posture, " +
            "       trade_policy, security_posture FROM native_community WHERE id=?", community);
        String staple = (String) c.get("staple_item_key");
        int ration = ((Number) c.get("daily_ration")).intValue();
        int shortage = ((Number) c.get("shortage_days")).intValue();

        UUID store = jdbc.query(
            "SELECT s.object_id FROM native_settlement_site s JOIN world_object w ON w.id=s.object_id " +
            "WHERE s.community_id=? AND s.holds_stores AND w.lifecycle_state='ACTIVE'",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, community);

        // Gathering: those whose work is food bring in what the season gives. Hunger does not stop them — it is what
        // sends them out — but injury and sickness do, and the dead gather nothing.
        // A community with no standing store has nowhere to keep a take, so it lives hand to mouth: what it
        // gathers is eaten, never kept.
        int workers = count("SELECT COUNT(*) FROM native_individual n JOIN world_object w ON w.id=n.object_id " +
            "WHERE n.community_id=? AND n.role IN ('FORAGER','FISHER','HUNTER') AND n.life_stage <> 'ELDER' AND n.condition IN ('WELL','HUNGRY') AND w.lifecycle_state='ACTIVE'", community);
        // New water, not yet fished (#111): a people who have just moved take more from it for the first month.
        int gathered = workers * (yieldPerWorker(day) + (onFreshWater(community, day) ? 1 : 0));
        String stapleName = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, staple);
        if (store != null)
            for (int i = 0; i < gathered; i++) items.createHeldItem(store, staple, stapleName, day, "GATHERED_BY_COMMUNITY");

        // Making (#113): those whose work is making add to the store every third day, in turn through the goods
        // their material culture makes, until the store holds as many made things as a small isle keeps. This is
        // what the isle has to trade that is not food, and it is finite: a mat traded away is a mat they no longer
        // have, and makes are replaced only as fast as hands make them.
        if (store != null && day.atZone(ZoneOffset.UTC).getDayOfYear() % 3 == 0) {
            int made = count("SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
                "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key <> ?", store, staple);
            int makers = count("SELECT COUNT(*) FROM native_individual n JOIN world_object w ON w.id=n.object_id " +
                "WHERE n.community_id=? AND n.role='MAKER' AND n.condition='WELL' AND w.lifecycle_state='ACTIVE'", community);
            for (int m = 0; m < makers && made < MADE_GOODS_KEPT; m++, made++) {
                String good = MADE_GOODS[(day.atZone(ZoneOffset.UTC).getDayOfYear() / 3 + m) % MADE_GOODS.length];
                String name = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, good);
                items.createHeldItem(store, good, name, day, "MADE_BY_COMMUNITY");
            }
        }

        // Eating: everyone living eats from the store, the soundest-oldest first. Food that has gone bad is not
        // eaten; it is thrown out, which is what a store-keeper does and what keeps the store honest.
        int eaters = count("SELECT COUNT(*) FROM native_individual n JOIN world_object w ON w.id=n.object_id " +
            "WHERE n.community_id=? AND n.condition <> 'DEAD' AND w.lifecycle_state='ACTIVE'", community);
        int need = eaters * ration;
        int eaten = 0;
        if (store != null) {
            for (UUID spoiled : jdbc.queryForList(
                    "SELECT w.id FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
                    "JOIN food_preservation_state f ON f.object_id=w.id " +
                    "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND f.spoiled_at IS NOT NULL", UUID.class, store))
                items.retire(spoiled, day, "ROTTED", staple);
            List<UUID> larder = jdbc.queryForList(
                "SELECT w.id FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
                "LEFT JOIN food_preservation_state f ON f.object_id=w.id " +
                "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key=? AND f.spoiled_at IS NULL " +
                "ORDER BY f.safe_until NULLS LAST, w.created_at LIMIT ?", UUID.class, store, staple, need);
            for (UUID item : larder) { items.retire(item, day, "EATEN_BY_COMMUNITY", staple); eaten++; }
        } else {
            eaten = Math.min(need, gathered);
        }

        // Their ground (#211): what was done in their territory yesterday, and how they answer it.
        territory.watch(community, day);
        // Promises (#113): work owed past its date is a broken promise; a wage owed is paid when the store can.
        agreements.reckon(community, day);

        boolean fed = eaten >= need;
        int nextShortage = fed ? 0 : shortage + 1;
        if (!fed && shortage == 0) record(community, day, "SHORTAGE_BEGAN", Map.of("ate", eaten, "needed", need));
        if (fed && shortage > 0) record(community, day, "SHORTAGE_ENDED", Map.of("days", shortage));
        jdbc.update("UPDATE native_individual SET condition = ? WHERE community_id=? AND condition = ?",
            fed ? "WELL" : "HUNGRY", community, fed ? "HUNGRY" : "WELL");
        jdbc.update("UPDATE native_community SET shortage_days=? WHERE id=?", nextShortage, community);

        // A life course (#121): growing up, growing old, being born, and dying, of age or of hunger.
        lifeCourse(community, day, nextShortage);

        // How the community stands. Closing up is what hungry people do with a store they cannot spare and ground
        // they cannot share; moving on is what they do when the ground has stopped feeding them. Recovery returns
        // them to what they are when fed, never past it.
        String trade = (String) c.get("trade_policy"), security = (String) c.get("security_posture");
        if (nextShortage >= SHORTAGE_CLOSES && !"CLOSED".equals(trade)) {
            jdbc.update("UPDATE native_community SET trade_policy='CLOSED', security_posture=CASE WHEN security_posture IN ('AT_EASE','WARY') THEN 'GUARDED' ELSE security_posture END WHERE id=?", community);
            jdbc.update("UPDATE native_settlement_site SET access_rule='CLOSED' WHERE community_id=?", community);
            record(community, day, "CLOSED_TO_OUTSIDERS", Map.of("shortageDays", nextShortage));
        } else if (nextShortage == 0 && (!trade.equals(c.get("base_trade_policy")) || !security.equals(c.get("base_security_posture")))) {
            jdbc.update("UPDATE native_community SET trade_policy=base_trade_policy, security_posture=base_security_posture WHERE id=?", community);
            jdbc.update("UPDATE native_settlement_site SET access_rule='INVITED' WHERE community_id=? AND access_rule='CLOSED'", community);
            record(community, day, "REOPENED", Map.of());
        }
        if (nextShortage >= SHORTAGE_MOVES && "SETTLED".equals(c.get("lifecycle"))) {
            jdbc.update("UPDATE native_community SET lifecycle='MOVING' WHERE id=?", community);
            record(community, day, "LEFT_TO_FIND_FOOD", Map.of("shortageDays", nextShortage));
        } else if (nextShortage == 0 && "MOVING".equals(c.get("lifecycle"))) {
            jdbc.update("UPDATE native_community SET lifecycle='SETTLED' WHERE id=?", community);
            record(community, day, "SETTLED_AGAIN", Map.of());
        }

        // Home and hearth (#111): what is damaged is mended, what is lost is rebuilt, and a people on the move arrives.
        mendAndRebuild(community, day);
        relocateIfMoving(community, day);
    }

    // ── Home and hearth (#111, #114). ──────────────────────────────────────────────────────────────────────────────

    /** Days after a building is lost before its replacement stands. */
    static final int REBUILD_AFTER_DAYS = 3;
    /** Days on the move before a people arrives on new ground. */
    static final int ARRIVES_AFTER_DAYS = 3;
    /** How far a people will go to find new ground, in chunks. */
    static final int MOVES_AT_MOST = 8;
    /** How long new water stays better fished than the old. */
    static final Duration FRESH_WATER_LASTS = Duration.ofDays(30);
    private static final String[] MENDING_GOODS = {"reed_mat", "reed_bundle", "fiber_cordage"};

    /**
     * Mend what is damaged and rebuild what is gone. Mending uses what the store holds: a day's work with a mat or a
     * bundle of reed puts back far more than a day's work with bare hands. A building burnt or wrecked to nothing is
     * raised again a few days later from the marsh's own reeds, and begins empty: what the fire spared lies where the old
     * store stood, for whoever picks it up.
     */
    void mendAndRebuild(UUID community, Instant day) {
        Map<String, Object> c = jdbc.queryForMap("SELECT home_chunk_id, lifecycle, name FROM native_community WHERE id=?", community);
        if (!"SETTLED".equals(c.get("lifecycle"))) return;
        int hands = count("SELECT COUNT(*) FROM native_individual n JOIN world_object w ON w.id=n.object_id " +
            "WHERE n.community_id=? AND n.life_stage='ADULT' AND n.condition IN ('WELL','HUNGRY') AND w.lifecycle_state='ACTIVE'", community);
        if (hands == 0) return;
        UUID home = (UUID) c.get("home_chunk_id");
        UUID store = jdbc.query("SELECT s.object_id FROM native_settlement_site s JOIN world_object w ON w.id=s.object_id " +
            "WHERE s.community_id=? AND s.holds_stores AND w.lifecycle_state='ACTIVE'", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, community);

        for (Map<String, Object> site : jdbc.queryForList(
                "SELECT s.object_id, s.condition_percent FROM native_settlement_site s JOIN world_object w ON w.id=s.object_id " +
                "WHERE s.community_id=? AND w.lifecycle_state='ACTIVE' AND s.condition_percent < 100", community)) {
            UUID material = store == null ? null : jdbc.query(
                "SELECT w.id FROM world_object w JOIN item_instance i ON i.object_id=w.id WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' " +
                "AND i.item_key IN (?,?,?) ORDER BY w.created_at LIMIT 1",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, store, MENDING_GOODS[0], MENDING_GOODS[1], MENDING_GOODS[2]);
            if (material != null)
                items.retire(material, day, "USED_IN_REPAIR", jdbc.queryForObject("SELECT item_key FROM item_instance WHERE object_id=?", String.class, material));
            int mended = Math.min(100, ((Number) site.get("condition_percent")).intValue() + (material != null ? 15 : 5));
            jdbc.update("UPDATE native_settlement_site SET condition_percent=? WHERE object_id=?", mended, site.get("object_id"));
            if (mended == 100) record(community, day, "REPAIRED", Map.of("site", site.get("object_id").toString()));
        }

        // Rebuilding: a village or store lost for long enough is raised again on the same ground.
        for (String kind : new String[]{"VILLAGE", "STORE_HOUSE"}) {
            boolean standing = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM native_settlement_site s JOIN world_object w ON w.id=s.object_id " +
                "WHERE s.community_id=? AND s.site_kind=? AND w.lifecycle_state='ACTIVE')", Boolean.class, community, kind));
            if (standing) continue;
            Timestamp lost = jdbc.query(
                "SELECT MAX(w.destroyed_at) FROM native_settlement_site s JOIN world_object w ON w.id=s.object_id WHERE s.community_id=? AND s.site_kind=?",
                rs -> rs.next() ? rs.getTimestamp(1) : null, community, kind);
            if (lost == null || lost.toInstant().isAfter(day.minus(Duration.ofDays(REBUILD_AFTER_DAYS)))) continue;
            String name = c.get("name") + ("STORE_HOUSE".equals(kind) ? " store house" : " village");
            UUID raised = place("NATIVE_SITE", name, home);
            jdbc.update("INSERT INTO native_settlement_site (object_id,community_id,site_kind,access_rule,holds_stores,condition_percent) VALUES (?,?,?,'INVITED',?,60)",
                raised, community, kind, "STORE_HOUSE".equals(kind));
            record(community, day, "REBUILT", Map.of("site", kind));
        }
    }

    /**
     * A people on the move arrives on new ground: the nearest ground of the same kind as the home they left, within a
     * few days' going, clear of monsters and of another community's water. They carry everything that walks and
     * everything they built; their graves stay where they lie. New water has not been fished, and feeds them better
     * for the first month.
     */
    void relocateIfMoving(UUID community, Instant day) {
        Map<String, Object> c = jdbc.queryForMap(
            "SELECT n.lifecycle, n.home_chunk_id, h.world_id, h.grid_x, h.grid_y, h.biome FROM native_community n JOIN world_chunk h ON h.id=n.home_chunk_id WHERE n.id=?", community);
        if (!"MOVING".equals(c.get("lifecycle"))) return;
        boolean setOut = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT (SELECT MAX(occurred_at) FROM native_event WHERE community_id=? AND event_kind='LEFT_TO_FIND_FOOD') <= ?",
            Boolean.class, community, Timestamp.from(day.minus(Duration.ofDays(ARRIVES_AFTER_DAYS)))));
        boolean movedLately = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM native_event WHERE community_id=? AND event_kind='RELOCATED' AND occurred_at > ?)",
            Boolean.class, community, Timestamp.from(day.minus(Duration.ofDays(60)))));
        if (!setOut || movedLately) return;
        UUID from = (UUID) c.get("home_chunk_id");
        UUID to = jdbc.query(
            "SELECT k.id FROM world_chunk k WHERE k.world_id=? AND k.biome=? AND k.id <> ? " +
            "AND greatest(abs(k.grid_x-?), abs(k.grid_y-?)) <= ? " +
            "AND NOT EXISTS (SELECT 1 FROM ecology_site s WHERE s.chunk_id=k.id AND s.site_category='MONSTER') " +
            "AND NOT EXISTS (SELECT 1 FROM native_community o JOIN world_chunk oh ON oh.id=o.home_chunk_id WHERE o.id <> ? AND o.lifecycle <> 'DISPERSED' " +
            "                AND greatest(abs(oh.grid_x-k.grid_x), abs(oh.grid_y-k.grid_y)) < ?) " +
            "ORDER BY greatest(abs(k.grid_x-?), abs(k.grid_y-?)), md5(k.grid_x || ',' || k.grid_y) LIMIT 1",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
            c.get("world_id"), c.get("biome"), from, c.get("grid_x"), c.get("grid_y"), MOVES_AT_MOST, community, REEDKIN_SPACING, c.get("grid_x"), c.get("grid_y"));
        if (to == null) return;   // nowhere within reach: they stay on the move where they are
        // Everyone living and everything they built goes with them; graves and the dead stay.
        jdbc.update("UPDATE world_object w SET current_location_id=?, updated_at=now() FROM native_individual n " +
            "WHERE n.object_id=w.id AND n.community_id=? AND n.condition <> 'DEAD' AND w.lifecycle_state='ACTIVE'", to, community);
        jdbc.update("UPDATE world_object w SET current_location_id=?, updated_at=now() FROM native_settlement_site s " +
            "WHERE s.object_id=w.id AND s.community_id=? AND w.lifecycle_state='ACTIVE'", to, community);
        // Still hungry on arrival, and still closed to strangers, but the count toward moving on starts again here.
        jdbc.update("UPDATE native_community SET home_chunk_id=?, lifecycle='SETTLED', shortage_days=LEAST(shortage_days, ?) WHERE id=?", to, SHORTAGE_CLOSES, community);
        record(community, day, "RELOCATED", Map.of("from", from.toString(), "to", to.toString()));
    }

    /** Whether this community arrived on new, unfished water within the last month. */
    private boolean onFreshWater(UUID community, Instant day) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM native_event WHERE community_id=? AND event_kind='RELOCATED' AND occurred_at > ?)",
            Boolean.class, community, Timestamp.from(day.minus(FRESH_WATER_LASTS))));
    }

    // ── A life course (#121). ──────────────────────────────────────────────────────────────────────────────────────

    /** Age at which a child takes up adult work. */
    static final int COMES_OF_AGE = 14;
    /** Age at which an adult becomes an elder: keeper of memory, no longer out on the water. */
    static final int BECOMES_ELDER = 55;
    /** Age from which an elder may die of age; the year is spread by who they are, so a people does not lose every elder at once. */
    static final int DIES_OF_AGE_FROM = 70;
    /** The most people a small isle holds before births stop. */
    static final int ISLE_HOLDS = 12;
    /** Days of hunger after which the weakest begin to die, and how often after that. */
    static final int FAMINE_TAKES_FROM = 21, FAMINE_TAKES_EVERY = 7;

    /**
     * Growing up, growing old, being born and dying, as physical events on the community's own clock. Everything that
     * happens here is in the history, and a death leaves a grave on the isle (a place, #211), never a vanished body.
     */
    void lifeCourse(UUID community, Instant day, int shortageDays) {
        java.time.LocalDate today = day.atZone(ZoneOffset.UTC).toLocalDate();
        UUID home = jdbc.queryForObject("SELECT home_chunk_id FROM native_community WHERE id=?", UUID.class, community);

        // Coming of age: into the work the isle is shortest of.
        for (Map<String, Object> child : jdbc.queryForList(
                "SELECT n.object_id, n.given_name FROM native_individual n JOIN world_object w ON w.id=n.object_id " +
                "WHERE n.community_id=? AND n.life_stage='CHILD' AND n.condition <> 'DEAD' AND w.lifecycle_state='ACTIVE' " +
                "AND n.born_on <= ?", community, java.sql.Date.valueOf(today.minusYears(COMES_OF_AGE)))) {
            String work = jdbc.queryForObject(
                "SELECT r FROM unnest(ARRAY['FISHER','FORAGER','MAKER']) r " +
                "ORDER BY (SELECT COUNT(*) FROM native_individual n WHERE n.community_id=? AND n.role=r AND n.condition <> 'DEAD'), r LIMIT 1",
                String.class, community);
            jdbc.update("UPDATE native_individual SET life_stage='ADULT', role=? WHERE object_id=?", work, child.get("object_id"));
            record(community, day, "CAME_OF_AGE", Map.of("name", child.get("given_name"), "work", work));
        }

        // Growing old.
        jdbc.update("UPDATE native_individual SET life_stage='ELDER' WHERE community_id=? AND life_stage='ADULT' AND condition <> 'DEAD' AND born_on <= ?",
            community, java.sql.Date.valueOf(today.minusYears(BECOMES_ELDER)));

        // Dying of age: each elder in their own year past the threshold.
        for (Map<String, Object> elder : jdbc.queryForList(
                "SELECT n.object_id, n.given_name, n.born_on FROM native_individual n JOIN world_object w ON w.id=n.object_id " +
                "WHERE n.community_id=? AND n.life_stage='ELDER' AND n.condition <> 'DEAD' AND w.lifecycle_state='ACTIVE' AND n.born_on <= ?",
                community, java.sql.Date.valueOf(today.minusYears(DIES_OF_AGE_FROM)))) {
            java.time.LocalDate born = ((java.sql.Date) elder.get("born_on")).toLocalDate();
            int span = DIES_OF_AGE_FROM + Math.floorMod(elder.get("object_id").hashCode(), 15);
            if (!born.plusYears(span).isAfter(today)) die(community, home, (UUID) elder.get("object_id"), (String) elder.get("given_name"), day, "age");
        }

        // Famine takes the weakest first: the old, then the young, then anyone.
        if (shortageDays >= FAMINE_TAKES_FROM && (shortageDays - FAMINE_TAKES_FROM) % FAMINE_TAKES_EVERY == 0) {
            jdbc.queryForList(
                "SELECT n.object_id, n.given_name FROM native_individual n JOIN world_object w ON w.id=n.object_id " +
                "WHERE n.community_id=? AND n.condition <> 'DEAD' AND w.lifecycle_state='ACTIVE' " +
                "ORDER BY CASE n.life_stage WHEN 'ELDER' THEN 0 WHEN 'CHILD' THEN 1 ELSE 2 END, n.born_on LIMIT 1", community)
                .forEach(weakest -> die(community, home, (UUID) weakest.get("object_id"), (String) weakest.get("given_name"), day, "hunger"));
        }

        // A birth, once a spring, to a fed isle that has room and two adults to raise a child.
        int living = count("SELECT COUNT(*) FROM native_individual n JOIN world_object w ON w.id=n.object_id " +
            "WHERE n.community_id=? AND n.condition <> 'DEAD' AND w.lifecycle_state='ACTIVE'", community);
        int adults = count("SELECT COUNT(*) FROM native_individual n JOIN world_object w ON w.id=n.object_id " +
            "WHERE n.community_id=? AND n.life_stage='ADULT' AND n.condition <> 'DEAD' AND w.lifecycle_state='ACTIVE'", community);
        boolean spring = today.getMonthValue() == 4 || today.getMonthValue() == 5;
        boolean bornThisYear = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM native_event WHERE community_id=? AND event_kind='BORN' AND occurred_at > ?)",
            Boolean.class, community, Timestamp.from(day.minus(Duration.ofDays(300)))));
        if (spring && !bornThisYear && shortageDays == 0 && living < ISLE_HOLDS && adults >= 2) {
            String isle = jdbc.queryForObject("SELECT name FROM native_community WHERE id=?", String.class, community);
            int n = count("SELECT COUNT(*) FROM native_individual WHERE community_id=?", community);
            String name = NAMES[(n * 5 + 3) % NAMES.length];
            UUID kin = jdbc.queryForObject("SELECT k.id FROM native_kin_group k WHERE k.community_id=? " +
                "ORDER BY (SELECT COUNT(*) FROM native_individual i WHERE i.kin_group_id=k.id AND i.life_stage='CHILD' AND i.condition <> 'DEAD'), k.name LIMIT 1",
                UUID.class, community);
            UUID body = place("NATIVE_PERSON", name + " of " + isle, home);
            jdbc.update("INSERT INTO native_individual (object_id,community_id,kin_group_id,given_name,role,life_stage,born_on) VALUES (?,?,?,?,'CHILD','CHILD',?)",
                body, community, kin, name, java.sql.Date.valueOf(today));
            record(community, day, "BORN", Map.of("name", name));
        }

        // A community with no one left living has ended; what it built and who it buried stay where they are.
        if (count("SELECT COUNT(*) FROM native_individual WHERE community_id=? AND condition <> 'DEAD'", community) == 0) {
            jdbc.update("UPDATE native_community SET lifecycle='DISPERSED' WHERE id=?", community);
            record(community, day, "NO_ONE_LEFT", Map.of());
        }
    }

    /** A death in the community: the person is marked dead, laid in a grave on the isle, and the grave is a place. */
    private void die(UUID community, UUID home, UUID body, String name, Instant day, String cause) {
        jdbc.update("UPDATE native_individual SET condition='DEAD', available=FALSE WHERE object_id=?", body);
        jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED', destroyed_at=?, destroyed_location_id=?, destroyed_cause='BURIED', " +
            "current_location_id=NULL, current_owner_id=NULL WHERE id=?", Timestamp.from(day), home, body);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'BURIED',jsonb_build_object('cause',?::text))",
            body, Timestamp.from(day), cause);
        UUID grave = place("NATIVE_GRAVE", "The grave of " + name, home);
        record(community, day, "hunger".equals(cause) ? "DIED_OF_HUNGER" : "DIED_OF_AGE", Map.of("name", name, "grave", grave.toString()));
    }

    /**
     * What one worker brings in a day, by season: the growing months enough to put by, the shoulders of the year
     * less, and deep winter a little, won through ice and from traps. Never nothing — a people that has lived on
     * this ground for generations is one that can get through its winters in an ordinary year — but never enough
     * in winter to feed everyone either, so the store they built in summer is what carries them. Dried food keeps
     * about six weeks, which is what makes a long winter a real danger rather than a formality.
     * Northern-hemisphere months, as the rest of the world's seasons are.
     */
    static int yieldPerWorker(Instant day) {
        int month = day.atZone(ZoneOffset.UTC).getMonthValue();
        return switch (month) {
            case 12, 1, 2 -> 1;
            case 3, 11 -> 2;
            default -> 3;
        };
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private void record(UUID community, Instant at, String kind, Map<String, Object> payload) {
        StringBuilder json = new StringBuilder("{");
        for (Map.Entry<String, Object> e : payload.entrySet()) {
            if (json.length() > 1) json.append(',');
            json.append('"').append(e.getKey()).append("\":").append(e.getValue() instanceof Number ? e.getValue() : "\"" + e.getValue() + "\"");
        }
        json.append('}');
        jdbc.update("INSERT INTO native_event (community_id, occurred_at, event_kind, payload) VALUES (?,?,?,?::jsonb)",
            community, Timestamp.from(at), kind, json.toString());
    }
}
