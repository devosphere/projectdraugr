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
        // A brand walked from one fire to the next (#75). Checked before the ember, because someone who names a
        // brand means the brand — the ember is the other way of doing the same thing, not a synonym for it.
        if (v.contains("brand") && (v.contains("carry")||v.contains("bring")||v.contains("transfer")||v.contains("walk")||v.contains("light")||v.contains("start"))) return "brand_transfer";
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
        UUID pit=jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind IN (SELECT project_kind FROM construction_kind WHERE holds_fire) AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE' LIMIT 1",rs->rs.next()?rs.getObject(1,UUID.class):null,location);
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
        UUID pit=jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind IN (SELECT project_kind FROM construction_kind WHERE holds_fire) AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE' LIMIT 1",rs->rs.next()?rs.getObject(1,UUID.class):null,location);
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
        UUID pit=jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id JOIN fire_state fs ON fs.construction_id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind IN (SELECT project_kind FROM construction_kind WHERE holds_fire) AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE' AND fs.active=true LIMIT 1 FOR UPDATE",rs->rs.next()?rs.getObject(1,UUID.class):null,location);
        if(pit==null || !items.consumeOne(chronicle,"dry_branch",now)) return false;
        jdbc.update("UPDATE fire_state SET fuel_minutes=fuel_minutes+45,last_updated_at=? WHERE construction_id=?",Timestamp.from(now),pit);
        return true;
    }
    /** The active fire burning here, or null (#71). */
    private UUID activeFirePit(UUID location) {
        return jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id JOIN fire_state fs ON fs.construction_id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind IN (SELECT project_kind FROM construction_kind WHERE holds_fire) AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE' AND fs.active=true LIMIT 1 FOR UPDATE", rs->rs.next()?rs.getObject(1,UUID.class):null, location);
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
    /**
     * Bank a fire (#71): rake the coals together and cover them so the embers hold far longer than an open flame.
     *
     * <p>A fire poker is exactly the tool for this — the catalogue calls it "a poker to tend a fire and reach hot
     * coals" and nothing read it, so a Chronicle could cut one and bank no better than with their hands and a
     * green stick. With one in reach the coals are raked into a tighter heap and hold longer (#75).
     */
    @Transactional
    public boolean bank(UUID chronicle, UUID location, Instant now) {
        UUID pit=activeFirePit(location);
        if(pit==null) return false;
        boolean poker = chronicle != null && items.hasAtLeast(chronicle,"fire_poker",1);
        jdbc.update("UPDATE fire_state SET fuel_minutes=LEAST(fuel_minutes+?,?), last_updated_at=? WHERE construction_id=?",
            poker?130:90, poker?320:240, Timestamp.from(now),pit);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'FIRE_BANKED','{}'::jsonb)",pit,Timestamp.from(now));
        return true;
    }
    /**
     * Cook a raw food the Chronicle carries over an active fire here (#37, V396).
     *
     * <p><b>What the fire can turn into what is a table, not a pair of string literals.</b> This method read
     * {@code raw_game_meat} in and {@code cooked_game_meat} out, so of the raw foods the catalogue holds — game
     * meat, a whole fish, a gutted fish, fowl off a snare — only game meat could ever be cooked. Every one of
     * them could already be smoked, dried, salted, brined and cured by a named process; only one could be put
     * over a fire and eaten, and there was no cooked fish in the catalogue at all.
     *
     * <p>A food the player NAMES is cooked ("cook the fish"); otherwise whatever raw food is to hand, game meat
     * first, since that is what the fire has always preferred and a replay should not change its mind.
     *
     * <p>A cooking tripod (skewers over the flames) or a stone griddle (a hot surface) lets several pieces cook
     * in one turn; over a bare fire, one at a time (#257).
     *
     * @return how many pieces were cooked — 0 if there is no live fire or nothing raw within reach
     */
    @Transactional
    public int cookGameMeat(UUID chronicle, UUID location, Instant now) {
        return cookOverFire(chronicle, location, now, null).pieces();
    }

    /**
     * What came off the fire: how many pieces, what they are called, and whether there was a fire at all — so
     * the refusal can name its reason instead of saying the same vague thing to a cold camp and a full pack.
     */
    public record CookedAtFire(int pieces, String what, boolean hadFire) { }

    /** As above, cooking whatever raw food the sentence names if it names one. */
    @Transactional
    public CookedAtFire cookOverFire(UUID chronicle, UUID location, Instant now, String actionText) {
        Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM construction_project cp JOIN world_object w ON w.id=cp.object_id JOIN fire_state fs ON fs.construction_id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind IN (SELECT project_kind FROM construction_kind WHERE holds_fire) AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE' AND fs.active=true", Integer.class, location);
        if(active==null || active==0) return new CookedAtFire(0, null, false);
        int max = (items.hasAtLeast(chronicle,"stone_griddle",1) || items.hasAtLeast(chronicle,"cooking_tripod",1)) ? 3 : 1;

        // The catalogue's own answer to "what does the fire make of this", game meat first so the long-standing
        // behaviour of a bare "cook" is unchanged.
        java.util.List<java.util.Map<String,Object>> pairs = jdbc.queryForList(
            "SELECT fc.raw_item_key, fc.cooked_item_key, fc.keywords, d.display_name FROM fire_cooking fc " +
            "JOIN item_definition d ON d.item_key=fc.cooked_item_key " +
            "ORDER BY (fc.raw_item_key <> 'raw_game_meat'), fc.raw_item_key");
        String said = actionText == null ? "" : actionText.toLowerCase(java.util.Locale.ROOT);
        java.util.Map<String,Object> chosen = null;
        // Named by the player: "cook the fish" must cook the fish and not whatever else is in the pack. The
        // words come from the row, because nobody says "cook the raw fowl meat" — a first cut of this derived
        // the spoken form from the item key, matched nothing, and cooked game meat when asked for fowl.
        //
        // Longest keyword first, so "gutted fish" is preferred over the bare "fish" it contains.
        if (!said.isEmpty()) {
            String bestWord = "";
            for (java.util.Map<String,Object> p : pairs) {
                if (!items.hasAtLeast(chronicle, (String) p.get("raw_item_key"), 1)) continue;
                for (String kw : ((String) p.get("keywords")).split(",")) {
                    String w = kw.trim();
                    if (w.isEmpty() || w.length() <= bestWord.length()) continue;
                    if (com.devosphere.draugr.narration.Words.word(said, w)) { bestWord = w; chosen = p; }
                }
            }
        }
        if (chosen == null)
            for (java.util.Map<String,Object> p : pairs)
                if (items.hasAtLeast(chronicle, (String) p.get("raw_item_key"), 1)) { chosen = p; break; }
        if (chosen == null) return new CookedAtFire(0, null, true);

        String raw = (String) chosen.get("raw_item_key");
        String out = (String) chosen.get("cooked_item_key");
        String outName = (String) chosen.get("display_name");
        int cooked=0;
        for(int i=0;i<max;i++){
            if(!items.consumeOne(chronicle,raw,now)) break;
            UUID c=items.createCarriedItem(chronicle,out,outName,now,"COOKED_AT_FIRE");
            food.registerCooked(c,now);
            cooked++;
        }
        // Named by the plain word for the thing, which is the row's first keyword — you hold "the fish" over the
        // heat, not "the cooked fish", and not "the raw fowl meat" either. The display names of both sides read
        // wrong in that sentence, which is why the words a person uses are in the table.
        String plainWord = ((String) chosen.get("keywords")).split(",")[0].trim();
        return new CookedAtFire(cooked, plainWord.isEmpty() ? outName.toLowerCase(java.util.Locale.ROOT) : plainWord, true);
    }
    /** What one branch adds, and what a feeding is worth — the same numbers {@link #feed} and {@link #light} use. */
    private static final int MINUTES_PER_FEEDING = 45;
    /** The fuel above which a fire throws embers, exactly as {@code scorchNearbyFlammables} applies it. */
    private static final int ROARING_ENOUGH_TO_THROW_EMBERS = 120;

    /**
     * How the fire is (#37).
     *
     * <p><b>What the world already knew.</b> {@code fire_state.fuel_minutes} is the burning time remaining, to the
     * minute: {@link #light} sets it, {@link #feed} adds to it, {@link #bank} rakes the coals tighter and raises
     * both it and its ceiling, {@link #advanceTo} counts it down every turn of the world, the body reads it when
     * it decides how fast a Chronicle loses heat, and the Auditor holds that a fire cannot be alight with none of
     * it left. <b>Not one sentence could ask.</b> "Is the fire still going", "how long will it burn", "will it
     * last the night" and "is there enough wood" all reached nothing — over a number the simulation maintains
     * more carefully than almost any other.
     *
     * <p>Read-only. It reports the same fuel the tick spends and names what would add to it.
     *
     * @param darkHoursLeft hours until first light, or 0 by day — the caller owns the daylight convention, which
     *                      lives in one place so this answer can never disagree with the sky reading
     */
    @Transactional(readOnly = true)
    public String fireReading(UUID chronicle, UUID location, Instant now, int darkHoursLeft) {
        java.util.Map<String,Object> fire = jdbc.query(
            "SELECT fs.active, fs.fuel_minutes, w.display_name FROM fire_state fs " +
            "JOIN construction_project cp ON cp.object_id = fs.construction_id " +
            "JOIN world_object w ON w.id = cp.object_id " +
            "WHERE w.current_location_id = ? AND w.lifecycle_state = 'ACTIVE' AND cp.state = 'COMPLETED' " +
            "ORDER BY fs.active DESC, fs.fuel_minutes DESC LIMIT 1",
            rs -> rs.next() ? java.util.Map.of("active", rs.getBoolean(1), "fuel", rs.getInt(2),
                                               "what", rs.getString(3)) : null, location);

        // Fuel to hand. dry_branch is what feed() consumes, so that is what is counted — the reading must not
        // promise burning time from wood the fire will not take.
        Integer held = chronicle == null ? 0 : jdbc.queryForObject(
            "WITH RECURSIVE reach(id) AS (" +
            "  SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' " +
            "  UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reach r ON r.id=ic.container_id " +
            "    JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') " +
            "SELECT COUNT(*) FROM reach x JOIN item_instance i ON i.object_id=x.id WHERE i.item_key='dry_branch'",
            Integer.class, chronicle);
        int branches = held == null ? 0 : held;
        String carried = branches > 0
            ? "You have " + branches + " length" + (branches == 1 ? "" : "s") + " of wood by you, which is "
              + (branches * MINUTES_PER_FEEDING / 60 > 0
                 ? "something over " + (branches * MINUTES_PER_FEEDING / 60) + " hour"
                   + (branches * MINUTES_PER_FEEDING / 60 == 1 ? "" : "s") + " of burning"
                 : "under an hour of burning") + " if you put it all on."
            : "You have no wood by you at all, and a fire eats what it is given.";

        if (fire == null)
            return "There is no hearth on this ground — nothing built to hold a fire, so there is none to ask "
                + "after. A ring of stone comes first. " + carried;

        String what = ((String) fire.get("what")).toLowerCase(java.util.Locale.ROOT);
        int fuel = ((Number) fire.get("fuel")).intValue();
        if (!Boolean.TRUE.equals(fire.get("active"))) {
            // Cold — but a hearth that held heat recently is still worth something, and the catalogue says which
            // kinds do and for how long. That is the difference between a dead fire and a usable one.
            boolean warmStill = items.heatToWorkWith(location, now);
            return "The " + what + " is cold" + (fuel > 0 ? ", though there is fuel in it still" : " and empty")
                + ". " + (warmStill
                    ? "The stone has not given up yesterday's heat yet, and work that wants warmth can still be done at it. "
                    : "") + carried;
        }

        String howLong = fuel >= 120 ? "something over " + (fuel / 60) + " hours"
                       : fuel >= 60 ? "about an hour" + (fuel >= 90 ? " and a half" : "")
                       : fuel >= 20 ? "perhaps " + (fuel / 10 * 10) + " minutes"
                       : "minutes only";
        StringBuilder b = new StringBuilder();
        b.append("The ").append(what).append(" is alight — ")
         .append(fuel >= 180 ? "burning well" : fuel >= 60 ? "steady" : "low, and sinking")
         .append(", with ").append(howLong).append(" of fuel in it. ");
        // WHAT A ROARING FIRE WILL DO TO WHAT STANDS BESIDE IT (#219, and unaskable until now). All three
        // conditions scorchNearbyFlammables applies are readable, and it is the same query: flammability is a
        // property of the KIND (V274 made it so, after three hardcoded names left a reed hut fireproof), the
        // hazard begins at 120 fuel-minutes, and wet weather puts the embers out before they catch. A keeper
        // whose lean-to is quietly losing 4% an hour to their own hearth was told nothing at all.
        if (fuel >= ROARING_ENOUGH_TO_THROW_EMBERS) {
            String dryEnough = jdbc.query(
                "SELECT ww.weather_kind FROM world_weather ww JOIN world_chunk wc ON wc.world_id=ww.world_id WHERE wc.id=?",
                rs -> rs.next() ? rs.getString(1) : null, location);
            boolean embersCatch = "CLEAR".equals(dryEnough) || "OVERCAST".equals(dryEnough);
            java.util.List<String> atRisk = jdbc.queryForList(
                "SELECT DISTINCT lower(w.display_name) FROM construction_project cp " +
                "JOIN world_object w ON w.id=cp.object_id " +
                "JOIN construction_kind ck ON ck.project_kind=cp.project_kind AND ck.flammable " +
                "WHERE w.current_location_id=? AND w.lifecycle_state='ACTIVE' AND cp.state='COMPLETED' " +
                "  AND cp.integrity_percent>0 ORDER BY 1", String.class, location);
            if (!atRisk.isEmpty())
                b.append(embersCatch
                    ? "It is throwing embers, and the " + String.join(" and the ", atRisk) + " stand"
                      + (atRisk.size() == 1 ? "s" : "") + " close enough to catch in this dry air — left to burn "
                      + "like this they char by the hour. "
                    : "It is throwing embers, but the wet sky puts them out before they can catch in the "
                      + String.join(" or the ", atRisk) + ". ");
        }
        if (darkHoursLeft > 0) {
            // The question a keeper actually asks at dusk, answered by the two numbers that decide it.
            int needed = darkHoursLeft * 60;
            b.append(fuel >= needed
                ? "That will see the night out with something to spare. "
                : "That will not see the night out: " + darkHoursLeft + " hours of dark left and "
                  + (needed - fuel) / 60 + " hour" + ((needed - fuel) / 60 == 1 ? "" : "s")
                  + " short of fuel for it. Bank it, or put more on. ");
        }
        return b.append(carried).toString().trim();
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
        // Only a well-fed, roaring fire throws enough heat and embers to catch nearby thatch. The threshold is
        // shared with fireReading, which must warn at exactly the fuel this begins to bite at.
        if (fuelBefore < ROARING_ENOUGH_TO_THROW_EMBERS) return;
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
            // What can catch is a property of the KIND, not a list in this file (V274). It was three hardcoded
            // names — LEAN_TO, FUEL_RACK, BRUSH_FENCE — while the catalogue grew to 45 kinds, twenty-odd of them
            // built out of thatch, reed, brush, bark, hide and untrimmed timber. A reed hut or a hay rack stood
            // beside a roaring hearth in dry weather completely fireproof, because its name was not on the list.
            "JOIN construction_kind ck ON ck.project_kind=cp.project_kind AND ck.flammable " +
            "WHERE w.current_location_id=? AND w.lifecycle_state='ACTIVE' AND cp.state='COMPLETED' AND cp.integrity_percent>0",
            UUID.class, chunk)) {
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
                // What catches is loose_fuel (V316), as what structure catches is construction_kind.flammable (V274).
                // It was three names here, and dry grass, fatwood, birch bark and char tinder never caught.
                "AND i.item_key IN (SELECT item_key FROM loose_fuel) ORDER BY w.id LIMIT ?", UUID.class, chunk, consumable)) {
                jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED', destroyed_at=?, destroyed_location_id=current_location_id, destroyed_cause='FIRE_SPREAD', current_location_id=NULL WHERE id=?", ts, item);
                jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'BURNED_IN_FIRE_SPREAD','{}'::jsonb)", item, ts);
            }
        }
    }
}
