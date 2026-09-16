package com.devosphere.draugr.survival;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.quality.QualityGrade;

import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;

/** Simulated-time food freshness. Spoilage changes state; it never removes the physical object. */
@Service
public class FoodPreservationService {
    private final JdbcTemplate jdbc; private final PhysicalItemService items;
    public FoodPreservationService(JdbcTemplate jdbc, PhysicalItemService items) { this.jdbc = jdbc; this.items = items; }
    @Transactional public void registerRaw(UUID item, Instant at) { register(item,"RAW",at,at.plus(Duration.ofHours(18))); }
    @Transactional public void registerCooked(UUID item, Instant at) { register(item,"COOKED",at,at.plus(Duration.ofHours(72))); }
    /** Produce — milk, eggs, picked greens — keeps for days, not the 18 hours of raw meat (V264/V266). */
    @Transactional public void registerFresh(UUID item, Instant at) { register(item,"FRESH",at,at.plus(Duration.ofHours(96))); }

    /**
     * Register a fresh taking from an animal — a butchered carcass, a catch out of the water (#54).
     *
     * <p>Three yield loops built their items straight from {@code wildlife_drop} and registered nothing, because
     * each named the one item key it expected: game meat and raw fish were registered by hand, and everything else
     * the same tables could yield fell through. Crayfish meat, fowl meat and a bird's egg were therefore immortal —
     * they never spoiled, and smoking fowl bought a keeper nothing, because the raw bird kept just as well forever.
     *
     * <p>Keyed off the catalogue rather than a list of item keys, so the next drop added to a species is covered the
     * day it is added: anything that is not FOOD is left alone, an egg or milk keeps as produce, and flesh keeps as
     * raw. Idempotent, so a caller that already registered by hand is not a duplicate-key failure.
     */
    @Transactional public void registerTaking(UUID item, String itemKey, Instant at) {
        String category = jdbc.query("SELECT category FROM item_definition WHERE item_key=?",
            rs -> rs.next() ? rs.getString(1) : null, itemKey);
        if (!"FOOD".equals(category)) return;
        boolean produce = itemKey.contains("egg") || itemKey.contains("milk");
        register(item, produce ? "FRESH" : "RAW", at,
            at.plus(Duration.ofHours(produce ? 96 : 18)));
    }
    @Transactional public void advanceTo(Instant now) {
        Timestamp occurredAt = Timestamp.from(now);
        // #218 — pests at a fouled camp gnaw at a Chronicle's food stores. Food held by a Chronicle whose ground is
        // choked with refuse (>=50) loses shelf life faster (2 hours docked per whole hour of exposure, on top of
        // the hour that passed); a clean camp keeps food its full span. pest_checked_at advances for ALL un-spoiled
        // food so only real elapsed time counts, but shelf life is docked only while the keeper stands on fouled
        // ground — so passing through does not permanently ruin a well-kept larder. Whole hours only, like the
        // chunk_disturbance decay; the EXISTS references the target column in its WHERE (allowed), not a FROM join.
        // A signed adjustment to shelf life for each whole elapsed hour: fouled ground docks it (pests, #218), and —
        // its counterpart (#77 cold store) — ground holding a COMPLETED cold store CREDITS it, the cool, stable air
        // holding a larder near-suspended. Fouled ground takes precedence over a cellar (pests get in even there).
        // The credit equals the hour that passed, so stored food holds its remaining freshness steady while it stays
        // by the store, and resumes its clock once carried away — it never gains freshness beyond what it had, and
        // cannot un-spoil (guarded by spoiled_at IS NULL), so no matter is created.
        //
        // WHERE THE FOOD ACTUALLY IS (#77). The cellar clause used to ask for food owned by exactly one thing that
        // itself stood on the cellar's ground, which left out the two most natural ways anybody has ever used a
        // cellar: food SET DOWN in it (owned by nobody, so the join found nothing) and food in a sack inside a
        // chest (owned by the sack, which has no ground of its own). A cellar you actually put your harvest in kept
        // it no better than a hillside; only food a Chronicle stood there holding was kept. `resting` walks the
        // ownership chain — container inside container, or a body — until it reaches whatever rests on the ground,
        // so the answer is the chunk the food is physically on however it is stowed. Depth-bounded, so a cycle in
        // ownership cannot spin here.
        jdbc.update("WITH RECURSIVE resting(food_id, holder, location, depth) AS (" +
            "  SELECT f.object_id, w.current_owner_id, w.current_location_id, 0 " +
            "    FROM food_preservation_state f JOIN world_object w ON w.id=f.object_id WHERE f.spoiled_at IS NULL " +
            "  UNION ALL " +
            "  SELECT r.food_id, o.current_owner_id, o.current_location_id, r.depth+1 " +
            "    FROM resting r JOIN world_object o ON o.id=r.holder WHERE r.location IS NULL AND r.depth < 8), " +
            // The cold stores are read from the catalogue (V334) rather than named here: a third one built later
            // keeps food the day it is added, without this query knowing its name.
            "kept_cool AS (" +
            "  SELECT DISTINCT r.food_id FROM resting r " +
            "    JOIN world_object cw ON cw.current_location_id=r.location AND cw.lifecycle_state='ACTIVE' " +
            "    JOIN construction_project cp ON cp.object_id=cw.id AND cp.state='COMPLETED' AND cp.integrity_percent>0 " +
            "    JOIN construction_kind ck ON ck.project_kind=cp.project_kind AND ck.keeps_food_cool " +
            "   WHERE r.location IS NOT NULL) " +
            "UPDATE food_preservation_state f SET " +
            "safe_until = safe_until + make_interval(hours => (CASE " +
            "  WHEN EXISTS (SELECT 1 FROM world_object food JOIN world_object body ON body.id=food.current_owner_id " +
            "    JOIN chunk_refuse cr ON cr.chunk_id=body.current_location_id " +
            "    WHERE food.id=f.object_id AND cr.refuse_level >= 50 " +
            // A mouser on the ground (#79, V327). The vermin a fouled camp draws are exactly what a kept cat or ferret
            // hunts: a tamed pest hunter living where the keeper stands keeps them off the stores, so the food does not
            // lose its span to them — the refuse still costs everything else it costs. What counts as one is
            // pest_hunter, read here rather than named.
            "      AND NOT EXISTS (SELECT 1 FROM wildlife_bond wb JOIN wildlife_population wp ON wp.id=wb.population_id " +
            "        JOIN ecology_site es ON es.id=wp.site_id JOIN pest_hunter ph ON ph.species_key=wp.species_key " +
            "        WHERE wb.chronicle_id=body.id AND wb.bond_stage='TAMED' AND wp.population_count > 0 " +
            "          AND es.chunk_id=body.current_location_id)) " +
            "  THEN -FLOOR(EXTRACT(EPOCH FROM (?::timestamptz - f.pest_checked_at))/3600.0 * 2)::int " +
            "  WHEN f.object_id IN (SELECT food_id FROM kept_cool) " +
            "  THEN FLOOR(EXTRACT(EPOCH FROM (?::timestamptz - f.pest_checked_at))/3600.0)::int " +
            "  ELSE 0 END)), " +
            "pest_checked_at = ? " +
            "WHERE f.spoiled_at IS NULL AND EXTRACT(EPOCH FROM (?::timestamptz - f.pest_checked_at)) >= 3600",
            occurredAt, occurredAt, occurredAt, occurredAt);
        jdbc.update("UPDATE food_preservation_state SET spoiled_at=? WHERE spoiled_at IS NULL AND safe_until<=?", occurredAt, occurredAt);
        jdbc.update("UPDATE world_object w SET display_name='Spoiled ' || lower(w.display_name),updated_at=? FROM food_preservation_state f WHERE f.object_id=w.id AND f.spoiled_at=? AND w.lifecycle_state='ACTIVE' AND w.display_name NOT LIKE 'Spoiled %'", occurredAt, occurredAt);
    }
    @Transactional public Consumption consume(UUID chronicle, String itemKey, Instant at) {
        FoodItem item=jdbc.query("WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') SELECT r.id,f.spoiled_at IS NOT NULL,i.quality_grade FROM reachable r JOIN item_instance i ON i.object_id=r.id JOIN food_preservation_state f ON f.object_id=r.id WHERE i.item_key=? ORDER BY r.id FOR UPDATE LIMIT 1",rs->rs.next()?new FoodItem(rs.getObject(1,UUID.class),rs.getBoolean(2),rs.getString(3)):null,chronicle,itemKey);
        if(item==null) return new Consumption(false,false,QualityGrade.SOUND);
        items.retire(item.id(), at, "CONSUMED", itemKey);
        return new Consumption(true,item.spoiled(),QualityGrade.of(item.grade()));
    }
    private void register(UUID item,String kind,Instant createdAt,Instant safeUntil) { jdbc.update("INSERT INTO food_preservation_state (object_id,preparation_kind,safe_until,pest_checked_at) VALUES (?,?,?,?) ON CONFLICT (object_id) DO NOTHING",item,kind,Timestamp.from(safeUntil),Timestamp.from(createdAt)); }
    private record FoodItem(UUID id,boolean spoiled,String grade) { }
    /** How the eaten food was made: whether it was still consumed, whether it had spoiled, and its workmanship
     *  grade — a FINER cooked/preserved food nourishes a little more, a poorer one a little less (#271). */
    public record Consumption(boolean consumed,boolean spoiled,QualityGrade grade) { }
}
