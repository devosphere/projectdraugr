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
    /**
     * Which fire-making technique the chronicle described. Detection is on the
     * action text alone: a player who names a bow drill gets a bow drill, and one
     * who names nothing gets the method their kit best supports — because someone
     * carrying flint and pyrite reaching for "light a fire" plainly means to strike
     * it, not to spin a spindle for an hour.
     */
    @Transactional(readOnly = true)
    public String detectMethod(UUID chronicle, String actionText) {
        String v = actionText == null ? "" : actionText.toLowerCase(java.util.Locale.ROOT);
        if (v.contains("ember") && (v.contains("carry")||v.contains("bring")||v.contains("transfer")||v.contains("bundle"))) return "ember_transfer";
        if (v.contains("bow drill")||v.contains("bow-drill")||(v.contains("bow")&&v.contains("drill"))) return "bow_drill";
        if (v.contains("hand drill")||v.contains("hand-drill")||(v.contains("palms")&&v.contains("spindle"))) return "hand_drill";
        if (v.contains("plough")||v.contains("plow")) return "fire_plough";
        if (v.contains("fire saw")||v.contains("saw")) return "fire_saw";
        if (v.contains("pyrite")||v.contains("marcasite")) return "flint_and_pyrite";
        if (v.contains("steel")&&v.contains("flint")) return "flint_and_steel";
        if (v.contains("lens")||v.contains("magnify")||v.contains("sunlight")||v.contains("focus the sun")) return "solar_lens";
        if (v.contains("piston")||v.contains("compress")) return "fire_piston";
        if (v.contains("flint")) return "flint_and_pyrite";
        if (v.contains("spindle")||v.contains("friction")||v.contains("hearth")) return "bow_drill";
        // Nothing named: pick the easiest method this chronicle is actually equipped for.
        String best = jdbc.query(
            "SELECT m.method_key FROM fire_method m WHERE NOT EXISTS (" +
            "  SELECT 1 FROM fire_method_requirement r WHERE r.method_key=m.method_key AND (" +
            "    SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "    WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key=r.item_key) < r.quantity)" +
            " AND EXISTS (SELECT 1 FROM fire_method_requirement r2 WHERE r2.method_key=m.method_key)" +
            " ORDER BY m.difficulty LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : null, chronicle);
        return best != null ? best : "hand_drill";
    }

    /** What a method needs, how hard it is, and whether the sky permits it. */
    public record MethodProfile(String key, String displayName, int difficulty, boolean requiresDaylight,
                                boolean requiresDry, java.util.List<String> missing) { }

    @Transactional(readOnly = true)
    public MethodProfile profile(UUID chronicle, String methodKey) {
        java.util.Map<String,Object> m = jdbc.queryForMap(
            "SELECT method_key, display_name, difficulty, requires_daylight, requires_dry FROM fire_method WHERE method_key=?", methodKey);
        java.util.List<String> missing = jdbc.queryForList(
            "SELECT r.item_key FROM fire_method_requirement r WHERE r.method_key=? AND (" +
            "  SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "  WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key=r.item_key) < r.quantity",
            String.class, methodKey, chronicle);
        return new MethodProfile((String)m.get("method_key"), (String)m.get("display_name"),
            ((Number)m.get("difficulty")).intValue(), (Boolean)m.get("requires_daylight"),
            (Boolean)m.get("requires_dry"), missing);
    }

    /** Legacy entry point: friction kit assumed. Kept so existing callers and tests are unaffected. */
    @Transactional
    public LightResult light(UUID chronicle, UUID location, Instant now, boolean emberCaught) {
        UUID pit=jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='STONE_FIRE_PIT' AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE' LIMIT 1",rs->rs.next()?rs.getObject(1,UUID.class):null,location);
        if(pit==null) return LightResult.NO_PIT;
        // Friction fire needs a hearth board and spindle to raise an ember...
        if(!items.hasAtLeast(chronicle,"hearth_board",1) || !items.hasAtLeast(chronicle,"fire_spindle",1)) return LightResult.NO_KIT;
        return lightCore(chronicle, location, now, emberCaught, pit, null);
    }

    /**
     * Light by a named method (V49). The method's own kit stands in for the friction
     * kit — someone striking flint on pyrite needs no hearth board — and a consumed
     * requirement, like a carried ember, is spent whether or not the fire takes.
     */
    @Transactional
    public LightResult light(UUID chronicle, UUID location, Instant now, boolean emberCaught, String methodKey) {
        UUID pit=jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='STONE_FIRE_PIT' AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE' LIMIT 1",rs->rs.next()?rs.getObject(1,UUID.class):null,location);
        if(pit==null) return LightResult.NO_PIT;
        MethodProfile p = profile(chronicle, methodKey);
        if(!p.missing().isEmpty()) return LightResult.NO_KIT;
        return lightCore(chronicle, location, now, emberCaught, pit, methodKey);
    }

    private LightResult lightCore(UUID chronicle, UUID location, Instant now, boolean emberCaught, UUID pit, String methodKey) {
        // ...a tinder nest fine enough to catch it (consumed either way)...
        if(!items.hasAtLeast(chronicle,"tinder_nest",1)) return LightResult.NO_TINDER;
        // ...and dry fuel to build the caught flame into a fire (consumed on success).
        if(!items.hasAtLeast(chronicle,"dry_branch",1)) return LightResult.NO_FUEL;
        if(!items.consumeOne(chronicle,"tinder_nest",now)) return LightResult.NO_TINDER;
        // A consumed requirement — a carried ember, charred tinder — is spent by the
        // attempt itself, exactly as the tinder is, whether or not the fire takes.
        if(methodKey != null)
            for(String key : jdbc.queryForList("SELECT item_key FROM fire_method_requirement WHERE method_key=? AND consumed", String.class, methodKey))
                items.consumeOne(chronicle, key, now);
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
