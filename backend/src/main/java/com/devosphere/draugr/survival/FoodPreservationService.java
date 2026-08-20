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
    @Transactional public void advanceTo(Instant now) {
        Timestamp occurredAt = Timestamp.from(now);
        // #218 — pests at a fouled camp gnaw at a Chronicle's food stores. Food held by a Chronicle whose ground is
        // choked with refuse (>=50) loses shelf life faster (2 hours docked per whole hour of exposure, on top of
        // the hour that passed); a clean camp keeps food its full span. pest_checked_at advances for ALL un-spoiled
        // food so only real elapsed time counts, but shelf life is docked only while the keeper stands on fouled
        // ground — so passing through does not permanently ruin a well-kept larder. Whole hours only, like the
        // chunk_disturbance decay; the EXISTS references the target column in its WHERE (allowed), not a FROM join.
        jdbc.update("UPDATE food_preservation_state f SET " +
            "safe_until = safe_until - make_interval(hours => (CASE WHEN EXISTS (" +
            "  SELECT 1 FROM world_object food JOIN world_object body ON body.id=food.current_owner_id " +
            "  JOIN chunk_refuse cr ON cr.chunk_id=body.current_location_id " +
            "  WHERE food.id=f.object_id AND cr.refuse_level >= 50) " +
            " THEN FLOOR(EXTRACT(EPOCH FROM (?::timestamptz - f.pest_checked_at))/3600.0 * 2)::int ELSE 0 END)), " +
            "pest_checked_at = ? " +
            "WHERE f.spoiled_at IS NULL AND EXTRACT(EPOCH FROM (?::timestamptz - f.pest_checked_at)) >= 3600",
            occurredAt, occurredAt, occurredAt);
        jdbc.update("UPDATE food_preservation_state SET spoiled_at=? WHERE spoiled_at IS NULL AND safe_until<=?", occurredAt, occurredAt);
        jdbc.update("UPDATE world_object w SET display_name='Spoiled ' || lower(w.display_name),updated_at=? FROM food_preservation_state f WHERE f.object_id=w.id AND f.spoiled_at=? AND w.lifecycle_state='ACTIVE' AND w.display_name NOT LIKE 'Spoiled %'", occurredAt, occurredAt);
    }
    @Transactional public Consumption consume(UUID chronicle, String itemKey, Instant at) {
        FoodItem item=jdbc.query("WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') SELECT r.id,f.spoiled_at IS NOT NULL,i.quality_grade FROM reachable r JOIN item_instance i ON i.object_id=r.id JOIN food_preservation_state f ON f.object_id=r.id WHERE i.item_key=? ORDER BY r.id FOR UPDATE LIMIT 1",rs->rs.next()?new FoodItem(rs.getObject(1,UUID.class),rs.getBoolean(2),rs.getString(3)):null,chronicle,itemKey);
        if(item==null) return new Consumption(false,false,QualityGrade.SOUND);
        items.retire(item.id(), at, "CONSUMED", itemKey);
        return new Consumption(true,item.spoiled(),QualityGrade.of(item.grade()));
    }
    private void register(UUID item,String kind,Instant createdAt,Instant safeUntil) { jdbc.update("INSERT INTO food_preservation_state (object_id,preparation_kind,safe_until,pest_checked_at) VALUES (?,?,?,?)",item,kind,Timestamp.from(safeUntil),Timestamp.from(createdAt)); }
    private record FoodItem(UUID id,boolean spoiled,String grade) { }
    /** How the eaten food was made: whether it was still consumed, whether it had spoiled, and its workmanship
     *  grade — a FINER cooked/preserved food nourishes a little more, a poorer one a little less (#271). */
    public record Consumption(boolean consumed,boolean spoiled,QualityGrade grade) { }
}
