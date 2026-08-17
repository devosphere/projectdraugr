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
    @Transactional public void registerRaw(UUID item, Instant at) { register(item,"RAW",at.plus(Duration.ofHours(18))); }
    @Transactional public void registerCooked(UUID item, Instant at) { register(item,"COOKED",at.plus(Duration.ofHours(72))); }
    @Transactional public void advanceTo(Instant now) {
        Timestamp occurredAt = Timestamp.from(now);
        jdbc.update("UPDATE food_preservation_state SET spoiled_at=? WHERE spoiled_at IS NULL AND safe_until<=?", occurredAt, occurredAt);
        jdbc.update("UPDATE world_object w SET display_name='Spoiled ' || lower(w.display_name),updated_at=? FROM food_preservation_state f WHERE f.object_id=w.id AND f.spoiled_at=? AND w.lifecycle_state='ACTIVE' AND w.display_name NOT LIKE 'Spoiled %'", occurredAt, occurredAt);
    }
    @Transactional public Consumption consume(UUID chronicle, String itemKey, Instant at) {
        FoodItem item=jdbc.query("WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') SELECT r.id,f.spoiled_at IS NOT NULL,i.quality_grade FROM reachable r JOIN item_instance i ON i.object_id=r.id JOIN food_preservation_state f ON f.object_id=r.id WHERE i.item_key=? ORDER BY r.id FOR UPDATE LIMIT 1",rs->rs.next()?new FoodItem(rs.getObject(1,UUID.class),rs.getBoolean(2),rs.getString(3)):null,chronicle,itemKey);
        if(item==null) return new Consumption(false,false,QualityGrade.SOUND);
        items.retire(item.id(), at, "CONSUMED", itemKey);
        return new Consumption(true,item.spoiled(),QualityGrade.of(item.grade()));
    }
    private void register(UUID item,String kind,Instant safeUntil) { jdbc.update("INSERT INTO food_preservation_state (object_id,preparation_kind,safe_until) VALUES (?,?,?)",item,kind,Timestamp.from(safeUntil)); }
    private record FoodItem(UUID id,boolean spoiled,String grade) { }
    /** How the eaten food was made: whether it was still consumed, whether it had spoiled, and its workmanship
     *  grade — a FINER cooked/preserved food nourishes a little more, a poorer one a little less (#271). */
    public record Consumption(boolean consumed,boolean spoiled,QualityGrade grade) { }
}
