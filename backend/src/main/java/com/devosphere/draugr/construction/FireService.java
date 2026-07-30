package com.devosphere.draugr.construction;

import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.survival.FoodPreservationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.Duration;
import java.sql.Timestamp;
import java.util.UUID;

@Service
public class FireService {
    private final JdbcTemplate jdbc; private final PhysicalItemService items; private final FoodPreservationService food;
    public FireService(JdbcTemplate jdbc, PhysicalItemService items, FoodPreservationService food) { this.jdbc=jdbc; this.items=items; this.food=food; }
    /** Outcome of a fire-lighting attempt, distinct enough for the narrator to witness what physically happened. */
    public enum LightResult { LIT, NO_PIT, NO_KIT, NO_TINDER, NO_FUEL, NO_CATCH }

    /**
     * Attempt to light a fire. The hard physical gate (pit, kit, tinder, fuel) is
     * checked first; then {@code emberCaught} — the caller's success roll from the
     * text-specificity and familiarity layers — decides whether the ember takes.
     * A failed attempt still burns through the tinder, as a real one does.
     */
    @Transactional
    public LightResult light(UUID chronicle, UUID location, Instant now, boolean emberCaught) {
        UUID pit=jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='STONE_FIRE_PIT' AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE' LIMIT 1",rs->rs.next()?rs.getObject(1,UUID.class):null,location);
        if(pit==null) return LightResult.NO_PIT;
        // Friction fire needs a hearth board and spindle to raise an ember...
        if(!items.hasAtLeast(chronicle,"hearth_board",1) || !items.hasAtLeast(chronicle,"fire_spindle",1)) return LightResult.NO_KIT;
        // ...a tinder nest fine enough to catch it (consumed either way)...
        if(!items.hasAtLeast(chronicle,"tinder_nest",1)) return LightResult.NO_TINDER;
        // ...and dry fuel to build the caught flame into a fire (consumed on success).
        if(!items.hasAtLeast(chronicle,"dry_branch",1)) return LightResult.NO_FUEL;
        if(!items.consumeOne(chronicle,"tinder_nest",now)) return LightResult.NO_TINDER;
        if(!emberCaught) return LightResult.NO_CATCH; // The attempt spent the tinder without taking hold.
        if(!items.consumeOne(chronicle,"dry_branch",now)) return LightResult.NO_FUEL;
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,45,?) ON CONFLICT (construction_id) DO UPDATE SET active=true,fuel_minutes=fire_state.fuel_minutes+45,last_updated_at=EXCLUDED.last_updated_at",pit,Timestamp.from(now));
        return LightResult.LIT;
    }
    @Transactional
    public boolean feed(UUID chronicle, UUID location, Instant now) {
        UUID pit=jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id JOIN fire_state fs ON fs.construction_id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='STONE_FIRE_PIT' AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE' AND fs.active=true LIMIT 1 FOR UPDATE",rs->rs.next()?rs.getObject(1,UUID.class):null,location);
        if(pit==null || !items.consumeOne(chronicle,"dry_branch",now)) return false;
        jdbc.update("UPDATE fire_state SET fuel_minutes=fuel_minutes+45,last_updated_at=? WHERE construction_id=?",Timestamp.from(now),pit);
        return true;
    }
    @Transactional
    public boolean cookGameMeat(UUID chronicle, UUID location, Instant now) {
        Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM construction_project cp JOIN world_object w ON w.id=cp.object_id JOIN fire_state fs ON fs.construction_id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='STONE_FIRE_PIT' AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE' AND fs.active=true", Integer.class, location);
        if(active==null || active==0 || !items.consumeOne(chronicle,"raw_game_meat",now)) return false;
        UUID cooked=items.createCarriedItem(chronicle,"cooked_game_meat","Cooked game meat",now,"COOKED_AT_FIRE");
        food.registerCooked(cooked,now);
        return true;
    }
    @Transactional
    public void advanceTo(Instant now) {
        jdbc.query("SELECT construction_id,last_updated_at,fuel_minutes FROM fire_state WHERE active=true FOR UPDATE",rs->{while(rs.next()){UUID id=rs.getObject(1,UUID.class);Instant last=rs.getTimestamp(2).toInstant();int remaining=Math.max(0,rs.getInt(3)-(int)Duration.between(last,now).toMinutes());jdbc.update("UPDATE fire_state SET fuel_minutes=?,active=?,last_updated_at=? WHERE construction_id=?",remaining,remaining>0,Timestamp.from(now),id);}return null;});
    }
}
