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
        // ...something fine enough to catch it (consumed either way). Charred tinder counts: taking a spark that raw
        // fibre would shrug off is the whole reason to char it, and its own crafting narration says so — but only the
        // tinder nest was ever accepted here, so char_tinder was craftable and then consumed by nothing at all.
        // Char is spent first when both are carried, since it is the thing made for this.
        String tinder = items.hasAtLeast(chronicle,"char_tinder",1) ? "char_tinder"
                      : items.hasAtLeast(chronicle,"tinder_nest",1) ? "tinder_nest" : null;
        if(tinder == null) return LightResult.NO_TINDER;
        // ...and dry fuel to build the caught flame into a fire (consumed on success).
        if(!items.hasAtLeast(chronicle,"dry_branch",1)) return LightResult.NO_FUEL;
        if(!items.consumeOne(chronicle,tinder,now)) return LightResult.NO_TINDER;
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
    /** The active fire burning here, or null (#71). */
    private UUID activeFirePit(UUID location) {
        return jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id JOIN fire_state fs ON fs.construction_id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='STONE_FIRE_PIT' AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE' AND fs.active=true LIMIT 1 FOR UPDATE", rs->rs.next()?rs.getObject(1,UUID.class):null, location);
    }
    /** Put a fire out (#71): the flame dies and the fuel is done. */
    @Transactional
    public boolean extinguish(UUID location, Instant now) {
        UUID pit=activeFirePit(location);
        if(pit==null) return false;
        jdbc.update("UPDATE fire_state SET active=false, fuel_minutes=0, last_updated_at=? WHERE construction_id=?",Timestamp.from(now),pit);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'FIRE_EXTINGUISHED','{}'::jsonb)",pit,Timestamp.from(now));
        return true;
    }
    /** Bank a fire (#71): rake the coals together and cover them so the embers hold far longer than an open flame. */
    @Transactional
    public boolean bank(UUID location, Instant now) {
        UUID pit=activeFirePit(location);
        if(pit==null) return false;
        jdbc.update("UPDATE fire_state SET fuel_minutes=LEAST(fuel_minutes+90,240), last_updated_at=? WHERE construction_id=?",Timestamp.from(now),pit);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'FIRE_BANKED','{}'::jsonb)",pit,Timestamp.from(now));
        return true;
    }
    /** Cook the raw game meat the Chronicle carries over an active fire here. Returns how many pieces were
     *  cooked (0 if there is no fire or no meat). A cooking tripod (skewers over the flames) or a stone
     *  griddle (a hot surface) lets several pieces cook in one turn; over a bare fire, one at a time (#257). */
    @Transactional
    public int cookGameMeat(UUID chronicle, UUID location, Instant now) {
        Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM construction_project cp JOIN world_object w ON w.id=cp.object_id JOIN fire_state fs ON fs.construction_id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='STONE_FIRE_PIT' AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE' AND fs.active=true", Integer.class, location);
        if(active==null || active==0) return 0;
        int max = (items.hasAtLeast(chronicle,"stone_griddle",1) || items.hasAtLeast(chronicle,"cooking_tripod",1)) ? 3 : 1;
        int cooked=0;
        for(int i=0;i<max;i++){
            if(!items.consumeOne(chronicle,"raw_game_meat",now)) break;
            UUID c=items.createCarriedItem(chronicle,"cooked_game_meat","Cooked game meat",now,"COOKED_AT_FIRE");
            food.registerCooked(c,now);
            cooked++;
        }
        return cooked;
    }
    @Transactional
    public void advanceTo(Instant now) {
        // Collect the active fires under lock, then burn each down — and let a roaring one scorch what stands beside
        // it. Collecting first (rather than updating inside the open cursor) keeps the per-fire hazard queries clear
        // of the cursor's own connection.
        java.util.List<Object[]> fires = jdbc.query(
            "SELECT construction_id,last_updated_at,fuel_minutes FROM fire_state WHERE active=true FOR UPDATE",
            (rs, i) -> new Object[]{rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant(), rs.getInt(3)});
        for (Object[] f : fires) {
            UUID id = (UUID) f[0]; Instant last = (Instant) f[1]; int fuelBefore = (int) f[2];
            int remaining = Math.max(0, fuelBefore - (int) Duration.between(last, now).toMinutes());
            jdbc.update("UPDATE fire_state SET fuel_minutes=?,active=?,last_updated_at=? WHERE construction_id=?", remaining, remaining > 0, Timestamp.from(now), id);
            scorchNearbyFlammables(id, last, now, fuelBefore);
        }
    }

    /**
     * An unattended, well-fed fire is a hazard to what stands beside it (#219 fire containment). The stone pit
     * contains the flame itself, but a roaring hearth throws heat and embers, and in dry weather a thatch lean-to or
     * a rack of drying firewood set too close catches and chars. Only the flammable field structures at the fire's
     * own ground take the harm; its integrity falls with the hours it was exposed to a roaring fire, and one left to
     * roar long enough scorches to ruin (the #220 weathering system then reads the wreck). Rain or snow keeps the
     * embers from catching, and an unknown sky (no weather recorded yet) is left alone. Exposure is capped at the
     * fuel actually on hand — a fire cannot roar longer than it can burn — so a hearth tended and banked in good
     * order never bites; only a big fire left alone does.
     */
    private void scorchNearbyFlammables(UUID pit, Instant last, Instant now, int fuelBefore) {
        if (fuelBefore < 120) return; // only a well-fed, roaring fire throws enough heat and embers to catch nearby thatch
        long hours = Math.min(Duration.between(last, now).toHours(), fuelBefore / 60L);
        if (hours <= 0) return;
        UUID chunk = jdbc.query("SELECT current_location_id FROM world_object WHERE id=?", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, pit);
        if (chunk == null) return;
        String weather = jdbc.query(
            "SELECT ww.weather_kind FROM world_weather ww JOIN world_chunk wc ON wc.world_id=ww.world_id WHERE wc.id=?",
            rs -> rs.next() ? rs.getString(1) : null, chunk);
        if (!("CLEAR".equals(weather) || "OVERCAST".equals(weather))) return; // wet or unknown sky: the embers do not catch
        int scorch = (int) Math.min(100, hours * 4);
        Timestamp ts = Timestamp.from(now);
        for (UUID s : jdbc.queryForList(
            "SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "WHERE w.current_location_id=? AND w.lifecycle_state='ACTIVE' AND cp.state='COMPLETED' AND cp.integrity_percent>0 " +
            "AND cp.project_kind IN ('LEAN_TO','FUEL_RACK','BRUSH_FENCE')", UUID.class, chunk)) {
            jdbc.update("UPDATE construction_project SET integrity_percent=GREATEST(0, integrity_percent-?), last_structural_update=? WHERE object_id=?", scorch, ts, s);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'FIRE_SCORCHED',jsonb_build_object('scorch',?))", s, ts, scorch);
        }
        // Loose flammable fuel piled on the ground beside a roaring fire catches and burns up — a stock of branches,
        // tinder, or shavings left too close is eaten early by the very fire it was meant to feed later. Only unowned
        // ground stock at the fire's chunk: fuel carried on the body moves with the Chronicle and is not at risk. A
        // piece an hour of roaring exposure, so a small pile set by the hearth goes quickly, a large one over a night.
        int consumable = (int) hours;
        if (consumable > 0) {
            for (UUID item : jdbc.queryForList(
                "SELECT w.id FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
                "WHERE w.current_location_id=? AND w.current_owner_id IS NULL AND w.lifecycle_state='ACTIVE' " +
                "AND i.item_key IN ('dry_branch','tinder_nest','wood_shaving') ORDER BY w.id LIMIT ?", UUID.class, chunk, consumable)) {
                jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED', destroyed_at=?, destroyed_location_id=current_location_id, destroyed_cause='FIRE_SPREAD', current_location_id=NULL WHERE id=?", ts, item);
                jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'BURNED_IN_FIRE_SPREAD','{}'::jsonb)", item, ts);
            }
        }
    }
}
