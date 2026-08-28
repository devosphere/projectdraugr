package com.devosphere.draugr.construction;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.devosphere.draugr.item.PhysicalItemService;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Service
public class ConstructionService {
    private final JdbcTemplate jdbc; private final PhysicalItemService items;
    public ConstructionService(JdbcTemplate jdbc, PhysicalItemService items) { this.jdbc = jdbc; this.items=items; }

    @Transactional public boolean startLeanTo(UUID chronicle, UUID location, UUID action, Instant at) { Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='LEAN_TO' AND cp.state IN ('PLANNED','IN_PROGRESS')",Integer.class,location); if(count!=null&&count>0)return false; UUID id=UUID.randomUUID(); jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Lean-to frame',?)",id,location); jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,created_from_action_id) VALUES (?,'LEAN_TO','PLANNED',0,?)",id,action); return true; }
    @Transactional public boolean abandonLeanTo(UUID location, Instant at) { UUID id=jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='LEAN_TO' AND cp.state IN ('PLANNED','IN_PROGRESS') ORDER BY w.created_at LIMIT 1 FOR UPDATE",rs->rs.next()?rs.getObject(1,UUID.class):null,location); if(id==null)return false; jdbc.update("UPDATE construction_project SET state='ABANDONED' WHERE object_id=?",id); Timestamp ts=Timestamp.from(at); jdbc.update("UPDATE world_object SET display_name='Abandoned lean-to frame',updated_at=? WHERE id=?",ts,id); jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTION_ABANDONED','{}'::jsonb)",id,ts); return true; }
    @Transactional public boolean resumeLeanTo(UUID location, Instant at) { UUID id=jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='LEAN_TO' AND cp.state='ABANDONED' ORDER BY w.created_at LIMIT 1 FOR UPDATE",rs->rs.next()?rs.getObject(1,UUID.class):null,location); if(id==null)return false; jdbc.update("UPDATE construction_project SET state='IN_PROGRESS' WHERE object_id=?",id); Timestamp ts=Timestamp.from(at); jdbc.update("UPDATE world_object SET display_name='Lean-to frame',updated_at=? WHERE id=?",ts,id); jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTION_RESUMED','{}'::jsonb)",id,ts); return true; }
    @Transactional public boolean workLeanTo(UUID chronicle, UUID location, Instant at) { UUID id=jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='LEAN_TO' AND cp.state IN ('PLANNED','IN_PROGRESS') ORDER BY w.created_at LIMIT 1 FOR UPDATE",rs->rs.next()?rs.getObject(1,UUID.class):null,location); if(id==null || !items.hasAtLeast(chronicle,"dry_branch",1) || !items.hasAtLeast(chronicle,"plant_fiber",1))return false; if(!items.consumeOne(chronicle,"dry_branch",at) || !items.consumeOne(chronicle,"plant_fiber",at)) throw new IllegalStateException("Reachable construction materials changed during the action."); Integer prior=jdbc.queryForObject("SELECT progress_percent FROM construction_project WHERE object_id=?",Integer.class,id); int progress=Math.min(100,(prior==null?0:prior)+25); jdbc.update("UPDATE construction_project SET state=?,progress_percent=?,completed_at=? WHERE object_id=?",progress==100?"COMPLETED":"IN_PROGRESS",progress,progress==100?Timestamp.from(at):null,id); jdbc.update("UPDATE world_object SET display_name=? WHERE id=?",progress==100?"Lean-to":"Lean-to frame",id); return true; }
    @Transactional public boolean repairLeanTo(UUID chronicle, UUID location, Instant at) { UUID id=jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='LEAN_TO' AND cp.state='COMPLETED' AND cp.integrity_percent<100 LIMIT 1 FOR UPDATE",rs->rs.next()?rs.getObject(1,UUID.class):null,location); if(id==null||!items.hasAtLeast(chronicle,"dry_branch",1)||!items.hasAtLeast(chronicle,"plant_fiber",1))return false; if(!items.consumeOne(chronicle,"dry_branch",at)||!items.consumeOne(chronicle,"plant_fiber",at))throw new IllegalStateException("Reachable repair materials changed during the action."); Timestamp ts=Timestamp.from(at); jdbc.update("UPDATE construction_project SET integrity_percent=LEAST(100,integrity_percent+25),last_structural_update=? WHERE object_id=?",ts,id); jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTION_REPAIRED','{}'::jsonb)",id,ts); return true; }
    @Transactional public void advanceTo(Instant now) {
        Timestamp occurredAt = Timestamp.from(now);
        jdbc.update("UPDATE construction_project cp SET integrity_percent=GREATEST(0,cp.integrity_percent-FLOOR(EXTRACT(EPOCH FROM (? - cp.last_structural_update))/86400)::int*4),last_structural_update=? FROM world_object w JOIN world_chunk c ON c.id=w.current_location_id JOIN world_weather ww ON ww.world_id=c.world_id WHERE cp.object_id=w.id AND cp.project_kind='LEAN_TO' AND cp.state='COMPLETED' AND ww.weather_kind='STORM' AND EXTRACT(EPOCH FROM (? - cp.last_structural_update))>=86400",occurredAt,occurredAt,occurredAt);
        List<UUID> collapsed = jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE cp.project_kind='LEAN_TO' AND cp.state='COMPLETED' AND cp.integrity_percent=0 AND w.lifecycle_state='ACTIVE' FOR UPDATE", (rs, row) -> rs.getObject(1, UUID.class));
        for (UUID objectId : collapsed) {
            jdbc.update("UPDATE construction_project SET state='DESTROYED' WHERE object_id=?", objectId);
            jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED',destroyed_at=?,destroyed_location_id=current_location_id,destroyed_cause='CONSTRUCTION_COLLAPSED',current_location_id=NULL,updated_at=? WHERE id=?", occurredAt, occurredAt, objectId);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTION_COLLAPSED',jsonb_build_object('cause','storm_decay'))", objectId, occurredAt);
        }

        // General weathering (#215/#220) — every decaying FIELD structure (fences, lookouts, fuel racks,
        // latrines, alarms — not shelters, which have their own upkeep above, nor workstations) loses integrity
        // slowly over time, faster in foul weather. Left unmended it eventually collapses; the build actions each
        // mend one back to whole, so the decays flag and those repair paths finally read against something real.
        // Whole days only, so frequent ticks do not reset the clock and lose the wear.
        jdbc.update("UPDATE construction_project cp SET " +
            "integrity_percent = GREATEST(0, cp.integrity_percent - FLOOR(EXTRACT(EPOCH FROM (?::timestamptz - cp.last_structural_update))/86400)::int * " +
            "(CASE ww.weather_kind WHEN 'STORM' THEN 3 WHEN 'RAIN' THEN 2 WHEN 'SNOW' THEN 2 ELSE 1 END)), last_structural_update = ? " +
            "FROM world_object w JOIN world_chunk c ON c.id=w.current_location_id JOIN world_weather ww ON ww.world_id=c.world_id " +
            "WHERE cp.object_id=w.id AND cp.state='COMPLETED' " +
            "AND EXISTS (SELECT 1 FROM construction_kind ck WHERE ck.project_kind=cp.project_kind AND ck.decays AND NOT ck.is_shelter AND NOT ck.is_workstation) " +
            "AND EXTRACT(EPOCH FROM (?::timestamptz - cp.last_structural_update)) >= 86400", occurredAt, occurredAt, occurredAt);
        List<UUID> weathered = jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "JOIN construction_kind ck ON ck.project_kind=cp.project_kind " +
            "WHERE ck.decays AND NOT ck.is_shelter AND NOT ck.is_workstation AND cp.state='COMPLETED' AND cp.integrity_percent=0 AND w.lifecycle_state='ACTIVE' FOR UPDATE",
            (rs, row) -> rs.getObject(1, UUID.class));
        for (UUID objectId : weathered) {
            jdbc.update("UPDATE construction_project SET state='DESTROYED' WHERE object_id=?", objectId);
            jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED',destroyed_at=?,destroyed_location_id=current_location_id,destroyed_cause='CONSTRUCTION_COLLAPSED',current_location_id=NULL,updated_at=? WHERE id=?", occurredAt, occurredAt, objectId);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTION_COLLAPSED',jsonb_build_object('cause','weathering'))", objectId, occurredAt);
        }
    }

    /** Returns false for an unsuccessful physical attempt without disclosing hidden prerequisites to narration. */
    @Transactional
    public boolean buildFirePit(UUID chronicleId, UUID locationId, UUID actionId, Instant occurredAt) {
        List<UUID> stones = jdbc.query("WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') SELECT r.id FROM reachable r JOIN item_instance i ON i.object_id=r.id WHERE i.item_key='field_stone' ORDER BY r.id FOR UPDATE", (rs, row) -> rs.getObject(1, UUID.class), chronicleId);
        if (stones.size() < 4) return false;
        UUID projectId = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit',?)", projectId, locationId);
        Timestamp occurred = Timestamp.from(occurredAt);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,created_from_action_id,completed_at) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?,?)", projectId, actionId, occurred);
        for (UUID stone : stones.subList(0, 4)) items.retire(stone, occurredAt, "CONSUMED_FOR_CONSTRUCTION", "field_stone");
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTED',jsonb_build_object('projectKind','STONE_FIRE_PIT'))", projectId, occurred);
        return true;
    }

    /** Line stock a trip-line can be strung from, best/strongest first (#126/#127 camp alarm). */
    private static final String[] ALARM_LINE = {"withy_rope", "fiber_cordage", "plant_fiber"};
    /** Anything that clatters a warning when the line is knocked — hung to sound off an approach. */
    private static final String[][] ALARM_CLATTER = {{"animal_bone", "knocking bones"}, {"deer_antler", "antler"}, {"dry_branch", "hung branches"}};

    /**
     * String a perimeter trip-line alarm here (#126/#127): a length of line strung low across the approaches
     * with anything that clatters — bone, antler, dry branches — hung from it, so nothing crosses into the camp
     * unheard. It is a real, persistent CAMP_ALARM construction; while it stands, {@link
     * com.devosphere.draugr.ecology.WildlifeEncounterService#passiveEncounter} robs a stalking predator of its
     * surprise (the ambush chance drops), turning a fatal ambush into a warning the Chronicle can act on. It
     * grants no protection of its own — it buys warning time, nothing more. Fails grounded with no line or no
     * clatter to hand; re-strings an existing alarm here rather than stacking a second.
     */
    @Transactional
    public String[] buildCampAlarm(UUID chronicle, UUID location, Instant at) {
        // Re-hanging an alarm that already stands here needs only fresh clatter — the line is already strung —
        // so resolve that case before demanding a new length of line the refresh would never consume.
        String clatter = null, clabel = null; for (String[] c : ALARM_CLATTER) if (items.hasAtLeast(chronicle, c[0], 1)) { clatter = c[0]; clabel = c[1]; break; }
        if (clatter == null) return new String[]{"FAILED", "You have nothing to hang on the line that would sound a warning — bone, antler, or dry branches to knock together."};
        UUID existing = jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='CAMP_ALARM' AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE' LIMIT 1 FOR UPDATE", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, location);
        Timestamp ts = Timestamp.from(at);
        if (existing != null) {
            if (!items.consumeOne(chronicle, clatter, at)) throw new IllegalStateException("Reachable alarm material changed during the action.");
            jdbc.update("UPDATE construction_project SET integrity_percent=100,last_structural_update=? WHERE object_id=?", ts, existing);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'ALARM_RESTRUNG',jsonb_build_object('clatter',?))", existing, ts, clatter);
            return new String[]{"SUCCEEDED", "You re-string the trip-line and re-hang the " + clabel + ", and it stands taut across the approach again."};
        }
        String line = null; for (String l : ALARM_LINE) if (items.hasAtLeast(chronicle, l, 1)) { line = l; break; }
        if (line == null) return new String[]{"FAILED", "You have no line to string a trip-line with — a length of cordage, withy rope, or plant fibre must come first."};
        if (!items.consumeOne(chronicle, line, at)) throw new IllegalStateException("Reachable line changed during the action.");
        items.consumeOne(chronicle, clatter, at);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Perimeter trip-line alarm',?)", id, location);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'CAMP_ALARM','COMPLETED',100,?)", id, ts);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTED',jsonb_build_object('projectKind','CAMP_ALARM','line',?,'clatter',?))", id, ts, line, clatter);
        return new String[]{"SUCCEEDED", "You string a line low across the approaches and hang " + clabel + " from it, so nothing crosses into the camp without knocking a warning."};
    }

    /** Withies for a woven wattle wall, strongest first — cut and woven between stakes with a blade (#127). */
    private static final String[] WATTLE_WITHY = {"hazel_rod", "willow_branch"};
    /** Lashing to bind a piled brush fence together (#127). */
    private static final String[] BRUSH_BIND = {"withy_rope", "fiber_cordage", "plant_fiber"};

    /**
     * Raise (or mend) a perimeter fence here (#127, EPIC #123): a physical barrier between the camp and a
     * predator's rush — the barrier layer the defence catalogue lacked alongside the alarm (warning) and the
     * escape (flight). A woven WATTLE_FENCE — withies cut and woven between stakes, needing a blade — stands
     * stronger than a piled BRUSH_FENCE of dead branches lashed together, and {@link
     * com.devosphere.draugr.ecology.WildlifeEncounterService#passiveEncounter} reads the stronger wall as the
     * greater deterrent. It is no fortress: it buys time and turns many a rush aside, nothing more. Fails
     * grounded with no barrier stock to hand; mends an existing fence here rather than stacking a second.
     */
    @Transactional
    public String[] buildFence(UUID chronicle, UUID location, String text, Instant at) {
        String v = text.toLowerCase(java.util.Locale.ROOT);
        boolean forceBrush = v.contains("brush");
        boolean forceWattle = v.contains("wattle");
        Timestamp ts = Timestamp.from(at);
        // Mend a fence that already stands here before raising a second — a top-up of its own stock.
        java.util.Map<String,Object> existing = jdbc.query(
            "SELECT cp.object_id AS id, cp.project_kind AS kind FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "WHERE w.current_location_id=? AND cp.project_kind IN ('BRUSH_FENCE','WATTLE_FENCE') AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE' LIMIT 1 FOR UPDATE",
            rs -> rs.next() ? java.util.Map.of("id", rs.getObject("id", UUID.class), "kind", rs.getString("kind")) : null, location);
        if (existing != null) {
            String kind = (String) existing.get("kind");
            if ("WATTLE_FENCE".equals(kind)) {
                String w = null; for (String x : WATTLE_WITHY) if (items.hasAtLeast(chronicle, x, 2)) { w = x; break; }
                if (w == null) return new String[]{"FAILED", "The wattle has gaps you cannot close — you have no withies to hand to mend it."};
                items.consumeOne(chronicle, w, at); items.consumeOne(chronicle, w, at);
            } else {
                if (!items.hasAtLeast(chronicle, "dry_branch", 2)) return new String[]{"FAILED", "The brush wall has thinned, and you have no branches to hand to pack it out again."};
                items.consumeOne(chronicle, "dry_branch", at); items.consumeOne(chronicle, "dry_branch", at);
            }
            jdbc.update("UPDATE construction_project SET integrity_percent=100,last_structural_update=? WHERE object_id=?", ts, existing.get("id"));
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'FENCE_MENDED',jsonb_build_object('projectKind',?))", existing.get("id"), ts, kind);
            return new String[]{"SUCCEEDED", "You work along the line of the fence, packing out the gaps until it stands whole across the approach again."};
        }
        // Raise a new fence: a woven wattle if there are withies and a blade to cut them, else a piled brush wall.
        String withy = null; for (String x : WATTLE_WITHY) if (items.hasAtLeast(chronicle, x, 4)) { withy = x; break; }
        boolean canWattle = !forceBrush && withy != null && items.hasCuttingTool(chronicle);
        if (canWattle) {
            for (int i = 0; i < 4; i++) items.consumeOne(chronicle, withy, at);
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Wattle perimeter fence',?)", id, location);
            jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'WATTLE_FENCE','COMPLETED',100,?)", id, ts);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTED',jsonb_build_object('projectKind','WATTLE_FENCE','withy',?))", id, ts, withy);
            return new String[]{"SUCCEEDED", "You drive a line of stakes and weave the withies between them into a taut wattle wall — a barrier a rushing animal must break before it reaches you."};
        }
        if (forceWattle) return new String[]{"FAILED", "A wattle wall wants cut withies — hazel or willow rods — and a blade to work them, and you are short of one or the other."};
        if (items.hasAtLeast(chronicle, "dry_branch", 4)) {
            String bind = null; for (String b : BRUSH_BIND) if (items.hasAtLeast(chronicle, b, 1)) { bind = b; break; }
            if (bind == null) return new String[]{"FAILED", "You have branches enough, but nothing to lash them with — a length of cordage or fibre must come first."};
            for (int i = 0; i < 4; i++) items.consumeOne(chronicle, "dry_branch", at);
            items.consumeOne(chronicle, bind, at);
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Brush perimeter fence',?)", id, location);
            jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'BRUSH_FENCE','COMPLETED',100,?)", id, ts);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTED',jsonb_build_object('projectKind','BRUSH_FENCE','bind',?))", id, ts, bind);
            return new String[]{"SUCCEEDED", "You pile dead branches into a low barrier and lash them fast — a rough brush fence a rushing animal must break through, not simply cross."};
        }
        return new String[]{"FAILED", "You have nothing to raise a fence from — piled brushwood to lash, or withies and a blade to weave a wattle wall."};
    }

    /**
     * Raise an animal pen (#100/#108): a ring of posts and lashed rails to keep tamed stock. A field structure like a
     * fence — one to a place. While it stands, a beast whose keeper is here recovers its draft-fatigue each turn even
     * as the keeper works (PhysicalItemService.restPennedDraftBeasts), so the pen keeps the draft team fresh.
     */
    @Transactional
    public String[] buildPen(UUID chronicle, UUID location, String text, Instant at) {
        Timestamp ts = Timestamp.from(at);
        Integer existing = jdbc.queryForObject(
            "SELECT COUNT(*) FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "WHERE w.current_location_id=? AND cp.project_kind='ANIMAL_PEN' AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE'",
            Integer.class, location);
        if (existing != null && existing > 0) return new String[]{"FAILED", "A pen already stands here; there is no room to ring another around this ground."};
        if (!items.hasAtLeast(chronicle, "dry_branch", 8))
            return new String[]{"FAILED", "A pen wants a ring of stout posts — eight sound branches at least — and you have not the wood to hand."};
        String bind = null; for (String b : BRUSH_BIND) if (items.hasAtLeast(chronicle, b, 2)) { bind = b; break; }
        if (bind == null) return new String[]{"FAILED", "You have the posts, but nothing to lash the rails with — two lengths of cordage or fibre must come first."};
        for (int i = 0; i < 8; i++) items.consumeOne(chronicle, "dry_branch", at);
        items.consumeOne(chronicle, bind, at); items.consumeOne(chronicle, bind, at);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Animal pen',?)", id, location);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) VALUES (?,'ANIMAL_PEN','COMPLETED',100,?,100)", id, ts);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTED',jsonb_build_object('projectKind','ANIMAL_PEN','bind',?))", id, ts, bind);
        return new String[]{"SUCCEEDED", "You set a ring of posts and lash rails between them into a stout pen — ground your tamed beasts can be kept in, resting easy behind the rails while you are about other work."};
    }

    /** Tall straight poles a lookout stand is raised on, best first (#127). */
    private static final String[] LOOKOUT_POLE = {"hazel_rod", "willow_branch"};

    /**
     * Raise (or mend) a lookout here (#127, EPIC #123): a lashed stand of poles a Chronicle climbs to lift the
     * eye above the near treeline. It is not defence and not shelter — it changes what can be SEEN: {@link
     * com.devosphere.draugr.ecology.WildlifeEncounterService#scoutBoundary} reads a completed lookout at the
     * chunk and, from its height, reports danger a full second chunk out along each way (and always in full
     * detail), so a predator two tiles off is seen while there is still room to plan around it. Wants straight
     * poles, a blade to trim them, and a lashing; mends an existing lookout here rather than raising a second.
     */
    @Transactional
    public String[] buildLookout(UUID chronicle, UUID location, Instant at) {
        Timestamp ts = Timestamp.from(at);
        UUID existing = jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='LOOKOUT' AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE' LIMIT 1 FOR UPDATE", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, location);
        if (existing != null) {
            String pole = null; for (String p : LOOKOUT_POLE) if (items.hasAtLeast(chronicle, p, 1)) { pole = p; break; }
            if (pole == null) return new String[]{"FAILED", "The lookout has weathered loose, and you have no poles to hand to make it fast again."};
            items.consumeOne(chronicle, pole, at);
            jdbc.update("UPDATE construction_project SET integrity_percent=100,last_structural_update=? WHERE object_id=?", ts, existing);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'LOOKOUT_MENDED',jsonb_build_object('projectKind','LOOKOUT'))", existing, ts);
            return new String[]{"SUCCEEDED", "You re-lash the loosened poles until the lookout stands firm enough to climb again."};
        }
        String pole = null; for (String p : LOOKOUT_POLE) if (items.hasAtLeast(chronicle, p, 4)) { pole = p; break; }
        if (pole == null) return new String[]{"FAILED", "A lookout wants tall, straight poles to raise a stand on — hazel or willow rods — and you have too few."};
        if (!items.hasCuttingTool(chronicle)) return new String[]{"FAILED", "You have the poles, but no blade to cut and trim them to a stand that will bear your weight."};
        String bind = null; for (String b : BRUSH_BIND) if (items.hasAtLeast(chronicle, b, 1)) { bind = b; break; }
        if (bind == null) return new String[]{"FAILED", "You have poles enough, but nothing to lash them fast with — a length of cordage or fibre must come first."};
        for (int i = 0; i < 4; i++) items.consumeOne(chronicle, pole, at);
        items.consumeOne(chronicle, bind, at);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Raised lookout',?)", id, location);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'LOOKOUT','COMPLETED',100,?)", id, ts);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTED',jsonb_build_object('projectKind','LOOKOUT','pole',?))", id, ts, pole);
        return new String[]{"SUCCEEDED", "You raise and lash a stand of poles into a lookout you can climb — from its height the near treeline no longer hides what moves beyond it."};
    }

    /** Roofing to keep the rain off a fuel rack, best first (#127). */
    private static final String[] RACK_COVER = {"bark_sheet", "thatch_bundle", "reed_bundle"};

    /**
     * Raise (or mend) a covered fuel rack here (#127, EPIC #123): a low stand that lifts kindling and firewood
     * off the wet ground and roofs it over, so there is dry fuel to start a fire from even when the sky is
     * against you. {@link com.devosphere.draugr.action.ChronicleActionService} reads a completed rack at the
     * chunk and eases the heavy rain/storm penalty on an ignition attempt (dry kindling still takes a spark).
     * It changes nothing in fair weather. Wants poles, a blade, a roof of bark or thatch, and a lashing; mends
     * an existing rack here rather than raising a second.
     */
    @Transactional
    public String[] buildFuelRack(UUID chronicle, UUID location, Instant at) {
        Timestamp ts = Timestamp.from(at);
        UUID existing = jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='FUEL_RACK' AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE' LIMIT 1 FOR UPDATE", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, location);
        if (existing != null) {
            String cover = null; for (String c : RACK_COVER) if (items.hasAtLeast(chronicle, c, 1)) { cover = c; break; }
            if (cover == null) return new String[]{"FAILED", "The rack's roof has blown loose, and you have nothing to re-lay it with — bark, thatch, or reed."};
            items.consumeOne(chronicle, cover, at);
            jdbc.update("UPDATE construction_project SET integrity_percent=100,last_structural_update=? WHERE object_id=?", ts, existing);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'FUEL_RACK_MENDED',jsonb_build_object('projectKind','FUEL_RACK'))", existing, ts);
            return new String[]{"SUCCEEDED", "You re-lay the cover over the fuel rack until the store beneath it is dry and sheltered again."};
        }
        String pole = null; for (String p : LOOKOUT_POLE) if (items.hasAtLeast(chronicle, p, 3)) { pole = p; break; }
        if (pole == null) return new String[]{"FAILED", "A fuel rack wants poles to stand it on off the wet ground — hazel or willow rods — and you have too few."};
        if (!items.hasCuttingTool(chronicle)) return new String[]{"FAILED", "You have the poles, but no blade to cut and fit them into a frame that will stand."};
        String cover = null; for (String c : RACK_COVER) if (items.hasAtLeast(chronicle, c, 2)) { cover = c; break; }
        if (cover == null) return new String[]{"FAILED", "You have the frame, but nothing to roof it with to keep the rain off — bark sheets, thatch, or reed must come first."};
        String bind = null; for (String b : BRUSH_BIND) if (items.hasAtLeast(chronicle, b, 1)) { bind = b; break; }
        if (bind == null) return new String[]{"FAILED", "You have poles and a cover, but nothing to lash them fast with — a length of cordage or fibre must come first."};
        for (int i = 0; i < 3; i++) items.consumeOne(chronicle, pole, at);
        for (int i = 0; i < 2; i++) items.consumeOne(chronicle, cover, at);
        items.consumeOne(chronicle, bind, at);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Covered fuel rack',?)", id, location);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'FUEL_RACK','COMPLETED',100,?)", id, ts);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTED',jsonb_build_object('projectKind','FUEL_RACK','pole',?,'cover',?))", id, ts, pole, cover);
        return new String[]{"SUCCEEDED", "You raise a low rack off the wet ground and roof it over — a dry store of kindling and fuel that will take a spark when the sodden ground will not."};
    }

    /** Screen and cover material for a latrine, best first (#127). */
    private static final String[] LATRINE_SCREEN = {"reed_bundle", "thatch_bundle", "dry_grass_bundle"};

    /**
     * Dig (or freshen) a camp latrine and refuse pit here (#127/#218): a pit dug well away from where a Chronicle
     * eats and sleeps, screened for privacy, that keeps filth out of the living space. {@link
     * com.devosphere.draugr.chronicle.ChroniclePhysiologyService} reads a completed latrine at the chunk and
     * halves the passive hygiene loss while one stands there, which in turn eases the low-hygiene illness
     * pressure — a clean camp is a healthier one. Wants a digging implement and a screen of reed, thatch, or
     * grass; mends an existing latrine here rather than digging a second.
     */
    @Transactional
    public String[] buildLatrine(UUID chronicle, UUID location, Instant at) {
        Timestamp ts = Timestamp.from(at);
        UUID existing = jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='LATRINE' AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE' LIMIT 1 FOR UPDATE", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, location);
        if (existing != null) {
            String screen = null; for (String s : LATRINE_SCREEN) if (items.hasAtLeast(chronicle, s, 1)) { screen = s; break; }
            if (screen == null) return new String[]{"FAILED", "The latrine screen has fallen in, and you have no reed, thatch, or grass to re-set it."};
            items.consumeOne(chronicle, screen, at);
            jdbc.update("UPDATE construction_project SET integrity_percent=100,last_structural_update=? WHERE object_id=?", ts, existing);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'LATRINE_MENDED',jsonb_build_object('projectKind','LATRINE'))", existing, ts);
            return new String[]{"SUCCEEDED", "You clear and re-screen the pit until it is decent and sound again."};
        }
        if (!items.hasAtLeast(chronicle, "digging_stick", 1) && !items.hasAtLeast(chronicle, "wooden_shovel", 1))
            return new String[]{"FAILED", "A latrine wants a pit dug, and you have nothing to dig it with — a digging stick or a shovel must come first."};
        String screen = null; for (String s : LATRINE_SCREEN) if (items.hasAtLeast(chronicle, s, 2)) { screen = s; break; }
        if (screen == null) return new String[]{"FAILED", "You can dig the pit, but have nothing to screen it with — reed, thatch, or dry grass must come first."};
        for (int i = 0; i < 2; i++) items.consumeOne(chronicle, screen, at);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Camp latrine',?)", id, location);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'LATRINE','COMPLETED',100,?)", id, ts);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTED',jsonb_build_object('projectKind','LATRINE','screen',?))", id, ts, screen);
        return new String[]{"SUCCEEDED", "You dig a deep pit well downwind of the camp and screen it round — a place to keep filth away from where you eat and sleep."};
    }

    /**
     * Cut (or re-daub) a roof smoke-vent in an enclosing shelter here (#219 hazard-emission mitigation; extends the
     * #198 enclosed-fire smoke model). Woodsmoke from an unvented fire in an enclosed shelter fouls the air and
     * pushes a Chronicle toward illness; a hole cut through the roof lets it rise and leave. {@link
     * com.devosphere.draugr.chronicle.ChroniclePhysiologyService} reads a completed SMOKE_VENT at the chunk as
     * venting the smoke — the terminal payoff for the build. A smoke-hole only means anything cut into a roof, so it
     * requires an enclosing shelter already standing; a blade to cut the opening; and clay to daub the rim so sparks
     * do not catch the thatch. It weathers and, unmended, fails like the other field structures (#220).
     */
    @Transactional
    public String[] buildSmokeVent(UUID chronicle, UUID location, Instant at) {
        Timestamp ts = Timestamp.from(at);
        boolean enclosed = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "WHERE w.current_location_id=? AND cp.project_kind IN ('LEAN_TO','WATTLE_AND_DAUB_HUT','EARTH_SHELTERED_HUT','LOG_CABIN') " +
            "AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND w.lifecycle_state='ACTIVE')", Boolean.class, location));
        if (!enclosed) return new String[]{"FAILED", "A smoke-vent is a hole cut through a roof, and there is no shelter standing here to cut one in — raise an enclosing shelter first."};
        UUID existing = jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='SMOKE_VENT' AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE' LIMIT 1 FOR UPDATE", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, location);
        if (existing != null) {
            if (!items.hasAtLeast(chronicle, "clay_lump", 1)) return new String[]{"FAILED", "The vent's daubed rim has crumbled and needs re-lining, but you have no clay to daub it with."};
            items.consumeOne(chronicle, "clay_lump", at);
            jdbc.update("UPDATE construction_project SET integrity_percent=100,last_structural_update=? WHERE object_id=?", ts, existing);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'SMOKE_VENT_MENDED',jsonb_build_object('projectKind','SMOKE_VENT'))", existing, ts);
            return new String[]{"SUCCEEDED", "You re-daub the vent's rim with fresh clay until it draws clean and sound again."};
        }
        if (!items.hasCuttingTool(chronicle)) return new String[]{"FAILED", "Cutting a smoke-hole through the roof wants a blade, and you have none to open one with."};
        if (!items.hasAtLeast(chronicle, "clay_lump", 1)) return new String[]{"FAILED", "You can cut the hole, but have no clay to daub its rim so the sparks will not catch the thatch — clay must come first."};
        items.consumeOne(chronicle, "clay_lump", at);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Roof smoke-vent',?)", id, location);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'SMOKE_VENT','COMPLETED',100,?)", id, ts);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTED',jsonb_build_object('projectKind','SMOKE_VENT'))", id, ts);
        return new String[]{"SUCCEEDED", "You cut a hole through the roof over the hearth and daub its rim with clay. The smoke finds it and rises out, and the air beneath clears."};
    }

    /** Wall infill for a tool shed, best first — the same withies a wattle fence is woven from. */
    private static final String[] SHED_WALL = {"hazel_rod", "willow_branch"};

    /**
     * Raise (or re-roof) a camp tool shed here (#207 heritage TOOL_SHED, "reduces preparation time and loss"): a
     * standing frame walled in wattle and roofed over, where tools and made stock are kept to hand and out of the
     * weather instead of being fetched and hunted for at the start of every job. {@link
     * com.devosphere.draugr.action.ChronicleActionService} reads a completed tool shed at the chunk and shortens
     * the setting-up of fabrication and repair while one stands — the terminal payoff for the build. A substantial
     * structure: a pole frame, wattle walls, a roof cover, and cordage to lash it, plus a blade to fit the frame;
     * re-lays the roof on an existing shed here rather than raising a second. It weathers and, unmended, collapses
     * like the other field structures (#220).
     */
    @Transactional
    public String[] buildToolShed(UUID chronicle, UUID location, Instant at) {
        Timestamp ts = Timestamp.from(at);
        UUID existing = jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='TOOL_SHED' AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE' LIMIT 1 FOR UPDATE", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, location);
        if (existing != null) {
            String cover = null; for (String c : RACK_COVER) if (items.hasAtLeast(chronicle, c, 1)) { cover = c; break; }
            if (cover == null) return new String[]{"FAILED", "The shed's roof has weathered through, and you have nothing to re-lay it with — bark, thatch, or reed."};
            items.consumeOne(chronicle, cover, at);
            jdbc.update("UPDATE construction_project SET integrity_percent=100,last_structural_update=? WHERE object_id=?", ts, existing);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'TOOL_SHED_MENDED',jsonb_build_object('projectKind','TOOL_SHED'))", existing, ts);
            return new String[]{"SUCCEEDED", "You re-lay the roof and firm the walls until the tool shed is dry and sound again."};
        }
        String pole = null; for (String p : LOOKOUT_POLE) if (items.hasAtLeast(chronicle, p, 4)) { pole = p; break; }
        if (pole == null) return new String[]{"FAILED", "A tool shed wants stout poles for its frame — hazel or willow rods — and you have too few."};
        if (!items.hasCuttingTool(chronicle)) return new String[]{"FAILED", "You have the poles, but no blade to cut and fit them into a frame that will stand."};
        String wall = null; for (String w : SHED_WALL) if (items.hasAtLeast(chronicle, w, 4)) { wall = w; break; }
        if (wall == null) return new String[]{"FAILED", "You have the frame, but nothing to wall it in with — withies of hazel or willow must come first."};
        String cover = null; for (String c : RACK_COVER) if (items.hasAtLeast(chronicle, c, 2)) { cover = c; break; }
        if (cover == null) return new String[]{"FAILED", "You have the frame and walls, but nothing to roof it with to keep the weather off — bark sheets, thatch, or reed must come first."};
        String bind = null; for (String b : BRUSH_BIND) if (items.hasAtLeast(chronicle, b, 2)) { bind = b; break; }
        if (bind == null) return new String[]{"FAILED", "You have frame, walls, and roof, but nothing to lash it all fast with — cordage or fibre must come first."};
        for (int i = 0; i < 4; i++) items.consumeOne(chronicle, pole, at);
        for (int i = 0; i < 4; i++) items.consumeOne(chronicle, wall, at);
        for (int i = 0; i < 2; i++) items.consumeOne(chronicle, cover, at);
        for (int i = 0; i < 2; i++) items.consumeOne(chronicle, bind, at);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Tool shed',?)", id, location);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'TOOL_SHED','COMPLETED',100,?)", id, ts);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTED',jsonb_build_object('projectKind','TOOL_SHED','pole',?,'wall',?,'cover',?))", id, ts, pole, wall, cover);
        return new String[]{"SUCCEEDED", "You raise a stout little shed of frame, wattle, and roof — a dry place to keep your tools and made stock to hand, so no job begins with hunting for them."};
    }

    /**
     * Raise (or re-roof) a camp resource store here (#207 heritage STORAGE_AREA, "logistics"): a raised, covered
     * stockpile pen where a Chronicle sets down what they bring in instead of carrying it on the body. {@link
     * com.devosphere.draugr.ecology.WildlifeEncounterService} reads a completed store at the chunk and ends the
     * fresh-kill predator draw while a Chronicle stands there — a home camp with a proper larder is somewhere a
     * kill can be brought back to without turning the ground into an ambush. Wants a pole frame, a roof cover, and
     * cordage, plus a blade to fit the frame; re-lays the roof on an existing store here rather than raising a
     * second. Weathers and collapses unmended like the other field structures (#220).
     */
    @Transactional
    public String[] buildStorageArea(UUID chronicle, UUID location, Instant at) {
        Timestamp ts = Timestamp.from(at);
        UUID existing = jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='STORAGE_AREA' AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE' LIMIT 1 FOR UPDATE", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, location);
        if (existing != null) {
            String cover = null; for (String c : RACK_COVER) if (items.hasAtLeast(chronicle, c, 1)) { cover = c; break; }
            if (cover == null) return new String[]{"FAILED", "The store's cover has weathered through, and you have nothing to re-lay it with — bark, thatch, or reed."};
            items.consumeOne(chronicle, cover, at);
            jdbc.update("UPDATE construction_project SET integrity_percent=100,last_structural_update=? WHERE object_id=?", ts, existing);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'STORAGE_AREA_MENDED',jsonb_build_object('projectKind','STORAGE_AREA'))", existing, ts);
            return new String[]{"SUCCEEDED", "You re-lay the cover over the store until what it holds is dry and sheltered again."};
        }
        String pole = null; for (String p : LOOKOUT_POLE) if (items.hasAtLeast(chronicle, p, 4)) { pole = p; break; }
        if (pole == null) return new String[]{"FAILED", "A store wants stout poles to raise it off the wet ground — hazel or willow rods — and you have too few."};
        if (!items.hasCuttingTool(chronicle)) return new String[]{"FAILED", "You have the poles, but no blade to cut and fit them into a frame that will stand."};
        String cover = null; for (String c : RACK_COVER) if (items.hasAtLeast(chronicle, c, 2)) { cover = c; break; }
        if (cover == null) return new String[]{"FAILED", "You have the frame, but nothing to roof it with to keep the weather off the store — bark sheets, thatch, or reed must come first."};
        String bind = null; for (String b : BRUSH_BIND) if (items.hasAtLeast(chronicle, b, 2)) { bind = b; break; }
        if (bind == null) return new String[]{"FAILED", "You have poles and a cover, but nothing to lash them fast with — cordage or fibre must come first."};
        for (int i = 0; i < 4; i++) items.consumeOne(chronicle, pole, at);
        for (int i = 0; i < 2; i++) items.consumeOne(chronicle, cover, at);
        for (int i = 0; i < 2; i++) items.consumeOne(chronicle, bind, at);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Resource store',?)", id, location);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'STORAGE_AREA','COMPLETED',100,?)", id, ts);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTED',jsonb_build_object('projectKind','STORAGE_AREA','pole',?,'cover',?))", id, ts, pole, cover);
        return new String[]{"SUCCEEDED", "You raise a covered store off the wet ground — a place to set down what you bring in, so a fresh kill goes to the larder instead of riding on your back through the woods."};
    }

    /** Bedding material a bed can be laid from, best first — all reachable first-era plant stock (#71 make_bed). */
    private static final String[][] BEDDING = {{"straw_bundle","straw"},{"reed_bundle","reed"},{"thatch_bundle","thatch"},{"plant_fiber","plant fibre"},{"reed_mat","reed matting"},{"cattail_stalk","cattail"},{"water_lily_pad","dry lily pads"},{"dry_grass_bundle","dry grass"},{"big_leaf","leaves"},{"moss_bundle","moss"}};

    /**
     * Lay (or freshen) a bedding pallet here (#71 make_bed): a persistent GROUND_BED construction gathered from
     * carried plant stock. It is not shelter — it does not stop weather — but a body off the cold, wet ground
     * sleeps less broken, and the exposure model reads the bed from its completed form, not from having tried.
     * Fails grounded with no bedding stuff to hand; refreshes an existing bed here rather than stacking a second.
     */
    @Transactional
    public String[] makeBed(UUID chronicle, UUID location, Instant at) {
        String used = null, label = null;
        for (String[] b : BEDDING) if (items.hasAtLeast(chronicle, b[0], 1)) { used = b[0]; label = b[1]; break; }
        if (used == null) return new String[]{"FAILED", "You cast about for something soft and dry to lie on, but you have nothing to make a bed of — no grass, reed, straw, or fibre to hand."};
        UUID existing = jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='GROUND_BED' AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE' LIMIT 1 FOR UPDATE", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, location);
        if (!items.consumeOne(chronicle, used, at)) throw new IllegalStateException("Reachable bedding material changed during the action.");
        Timestamp ts = Timestamp.from(at);
        if (existing != null) {
            jdbc.update("UPDATE construction_project SET integrity_percent=100,last_structural_update=? WHERE object_id=?", ts, existing);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'BEDDING_REFRESHED',jsonb_build_object('material',?))", existing, ts, used);
            return new String[]{"SUCCEEDED", "You work fresh " + label + " into the bedding, and the bed lies dry and springing again."};
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Bedding pallet',?)", id, location);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'GROUND_BED','COMPLETED',100,?)", id, ts);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTED',jsonb_build_object('projectKind','GROUND_BED','material',?))", id, ts, used);
        return new String[]{"SUCCEEDED", "You gather " + label + " into a thick, dry pallet and lay it out — a place to sleep up off the bare cold ground."};
    }

    /** Brush/reed/grass a windbreak can be raised from by hand — all bare-hand stock (#195). */
    private static final String[][] WINDBREAK_STOCK = {{"dry_branch","brush"},{"reed_bundle","reed"},{"dry_grass_bundle","grass"},{"thatch_bundle","thatch"},{"straw_bundle","straw"}};

    /**
     * Raise a windbreak here (#195, bare-hand handwork): a low screen of brush, reed, or grass leant and woven
     * against the wind — no tool, no blade. It is not shelter: it turns no rain and has no roof, but out of the
     * wind a fire warms better and rest eases the body more (see ChroniclePhysiologyService.windbreakAt). Fails
     * grounded with not enough of any one brush stock to hand; refreshes an existing windbreak rather than
     * stacking a second.
     */
    @Transactional
    public String[] placeWindbreak(UUID chronicle, UUID location, Instant at) {
        String used = null, label = null;
        for (String[] b : WINDBREAK_STOCK) if (items.hasAtLeast(chronicle, b[0], 3)) { used = b[0]; label = b[1]; break; }
        if (used == null) return new String[]{"FAILED", "You cast about for enough brush or reed to raise a windbreak, but you have not got enough of any one thing to hand."};
        UUID existing = jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='WINDBREAK' AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE' LIMIT 1 FOR UPDATE", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, location);
        for (int i = 0; i < 3; i++) if (!items.consumeOne(chronicle, used, at)) throw new IllegalStateException("Reachable windbreak material changed during the action.");
        Timestamp ts = Timestamp.from(at);
        if (existing != null) {
            jdbc.update("UPDATE construction_project SET integrity_percent=100,last_structural_update=? WHERE object_id=?", ts, existing);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'WINDBREAK_REFRESHED',jsonb_build_object('material',?))", existing, ts, used);
            return new String[]{"SUCCEEDED", "You work fresh " + label + " into the windbreak, and it stands close against the wind again."};
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Windbreak',?)", id, location);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'WINDBREAK','COMPLETED',100,?)", id, ts);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTED',jsonb_build_object('projectKind','WINDBREAK','material',?))", id, ts, used);
        return new String[]{"SUCCEEDED", "You lean and weave " + label + " into a low screen against the wind — no roof and no wall, but it breaks the worst of the cold off you."};
    }

    /** Bare-hand stock for each partial cover (#195) — all no-blade, no-tool. {itemKey, spokenLabel}. */
    private static final java.util.Map<String, String[][]> COVER_STOCK = java.util.Map.of(
        "SUNSHADE",    new String[][]{{"big_leaf","leaves"},{"thatch_bundle","thatch"},{"dry_grass_bundle","grass"}},
        "RAIN_COVER",  new String[][]{{"bark_sheet","bark"},{"big_leaf","broad leaves"},{"thatch_bundle","thatch"}},
        "GROUNDSHEET", new String[][]{{"bark_sheet","bark"},{"big_leaf","broad leaves"},{"dry_grass_bundle","grass"}},
        "STONE_RING",  new String[][]{{"granite_cobble","cobbles"},{"basalt_cobble","cobbles"}},
        // A perimeter trip-line hung with anything that clatters (#126): strung from cordage, or a fence of dry brush.
        "CAMP_ALARM",  new String[][]{{"fiber_cordage","cordage"},{"dry_branch","dry brush"},{"reed_bundle","reed"}});
    private static final java.util.Map<String, Integer> COVER_QTY = java.util.Map.of(
        "SUNSHADE", 3, "RAIN_COVER", 3, "GROUNDSHEET", 3, "STONE_RING", 4, "CAMP_ALARM", 3);
    private static final java.util.Map<String, String> COVER_NAME = java.util.Map.of(
        "SUNSHADE", "Sunshade", "RAIN_COVER", "Rain cover", "GROUNDSHEET", "Groundsheet", "STONE_RING", "Stone ring", "CAMP_ALARM", "Camp alarm");

    /**
     * Place a bare-hand partial cover here (#195): a leaf sunshade against the sun, a leant rain cover that sheds
     * the worst of a shower, a bark/grass groundsheet off the cold damp ground, or a ring of gathered stones round
     * a fire. No blade, no tool. None of these is secure shelter — a sunshade turns no rain, a rain cover has no
     * walls, a groundsheet turns no weather, a stone ring is not a roof — so each gives only graded, partial relief
     * (see ChroniclePhysiologyService). Fails grounded without enough of any one bare-hand stock to hand; refreshes
     * an existing cover of the same kind rather than stacking a second.
     */
    @Transactional
    public String[] placeCover(UUID chronicle, UUID location, Instant at, String kind) {
        String[][] stock = COVER_STOCK.get(kind);
        if (stock == null) return new String[]{"FAILED", "You are not sure what cover you mean to place."};
        int need = COVER_QTY.get(kind);
        String used = null, label = null;
        for (String[] b : stock) if (items.hasAtLeast(chronicle, b[0], need)) { used = b[0]; label = b[1]; break; }
        if (used == null) return new String[]{"FAILED", "You cast about for enough to raise a " + COVER_NAME.get(kind).toLowerCase() + ", but you have not got enough of any one thing to hand."};
        UUID existing = jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind=? AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE' LIMIT 1 FOR UPDATE", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, location, kind);
        for (int i = 0; i < need; i++) if (!items.consumeOne(chronicle, used, at)) throw new IllegalStateException("Reachable cover material changed during the action.");
        Timestamp ts = Timestamp.from(at);
        String prose = switch (kind) {
            case "SUNSHADE"    -> "You lean and lash " + label + " into a low sunshade overhead — no walls, but it throws a patch of shade off the worst of the sun.";
            case "RAIN_COVER"  -> "You prop " + label + " into a leant cover pitched to shed water — it turns the worst of a shower, though it is no roof and no wall.";
            case "GROUNDSHEET" -> "You spread " + label + " into a groundsheet to lie on — off the cold, wet earth, though it turns no weather above you.";
            case "STONE_RING"  -> "You set " + label + " in a ring where a fire will sit — it holds the embers and throws their heat back, no more.";
            case "CAMP_ALARM"  -> "You string " + label + " across the approaches and hang it with anything that clatters — it will not stop a beast, but nothing crosses it in the dark without warning you first.";
            default            -> "You place the cover.";
        };
        if (existing != null) {
            jdbc.update("UPDATE construction_project SET integrity_percent=100,last_structural_update=? WHERE object_id=?", ts, existing);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'COVER_REFRESHED',jsonb_build_object('projectKind',?,'material',?))", existing, ts, kind, used);
            return new String[]{"SUCCEEDED", "You work fresh " + label + " into the " + COVER_NAME.get(kind).toLowerCase() + ", and it stands as it should again."};
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION',?,?)", id, COVER_NAME.get(kind), location);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,?,'COMPLETED',100,?)", id, kind, ts);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTED',jsonb_build_object('projectKind',?,'material',?))", id, ts, kind, used);
        return new String[]{"SUCCEEDED", prose};
    }

    /**
     * Tend the camp here (#71 maintain_camp): go over what stands, setting decayed constructions a little back
     * toward true and ordering the site around them. Grounded — with nothing built here there is no camp to tend.
     * Its benefit is the real integrity it restores; the small easing of mind is applied by the caller.
     */
    @Transactional
    public String[] maintainCamp(UUID chronicle, UUID location, Instant at) {
        List<UUID> here = jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND w.lifecycle_state='ACTIVE' AND cp.state='COMPLETED' FOR UPDATE", (rs, r) -> rs.getObject(1, UUID.class), location);
        Timestamp ts = Timestamp.from(at);
        // Tidying a camp carries off the filth of living there (#218): clearing refuse is part of the work — the
        // active counter-play beside a latrine's passive disposal — and a good tidy is worth doing even at a bare
        // site that has grown foul. Cleared by hand here; drained slowly (and faster with a latrine) in the tick.
        int refuseBefore = jdbc.queryForObject("SELECT COALESCE((SELECT refuse_level FROM chunk_refuse WHERE chunk_id=?),0)", Integer.class, location);
        int cleared = 0;
        if (refuseBefore > 0) {
            jdbc.update("UPDATE chunk_refuse SET refuse_level=GREATEST(0,refuse_level-25),last_updated_at=? WHERE chunk_id=?", ts, location);
            cleared = Math.min(25, refuseBefore);
        }
        if (here.isEmpty() && cleared == 0) return new String[]{"FAILED", "You look around to set a camp in order, but there is nothing built here to tend and nothing to clear away."};
        int tended = 0;
        for (UUID id : here) {
            Integer integ = jdbc.queryForObject("SELECT integrity_percent FROM construction_project WHERE object_id=?", Integer.class, id);
            if (integ != null && integ < 100) {
                jdbc.update("UPDATE construction_project SET integrity_percent=LEAST(100,integrity_percent+10),last_structural_update=? WHERE object_id=?", ts, id);
                jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CAMP_MAINTAINED','{}'::jsonb)", id, ts);
                tended++;
            }
        }
        String tail = here.isEmpty()
            ? "There is nothing built here to tend, but the ground is the better for the clearing."
            : (tended == 0
                ? "Everything here already stands sound; you set what little is astray back in its place."
                : "You go over what stands here, working " + tended + " thing" + (tended == 1 ? "" : "s") + " back toward true and ordering the camp around them.");
        String filth = cleared > 0 ? " You carry off the refuse that had gathered, and the living space is cleaner for it." : "";
        return new String[]{"SUCCEEDED", "You tidy the camp — stowing what is loose, righting what has shifted. " + tail + filth};
    }

    /**
     * Repair / maintain a reachable standing structure here (#70 repair_structure / maintain_structure): the named
     * one, else the only one. Works on any completed construction — not just the lean-to — restoring integrity
     * from kind-appropriate material actually carried. Grounded throughout: nothing built here, nothing damaged, or
     * no fit material to hand each fails without disclosing the recipe. Distinct from item repair (#69) and REFINE.
     */
    @Transactional
    public String[] repairStructure(UUID chronicle, UUID location, String text, Instant at) {
        String lower = text == null ? "" : text.toLowerCase(java.util.Locale.ROOT);
        java.util.List<java.util.Map<String,Object>> projects = jdbc.queryForList(
            "SELECT cp.object_id, w.display_name, cp.project_kind, cp.integrity_percent FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "WHERE w.current_location_id=? AND w.lifecycle_state='ACTIVE' AND cp.state='COMPLETED' ORDER BY length(w.display_name) DESC", location);
        if (projects.isEmpty()) return new String[]{"FAILED", "You look for something built here to set right, but the ground is bare."};
        java.util.Map<String,Object> proj = projects.stream()
            .filter(p -> lower.contains(((String)p.get("display_name")).toLowerCase(java.util.Locale.ROOT))
                || lower.contains(((String)p.get("project_kind")).toLowerCase(java.util.Locale.ROOT).replace('_', ' ')))
            .findFirst().orElse(projects.size() == 1 ? projects.get(0) : null);
        if (proj == null) return new String[]{"FAILED", "There is more than one thing standing here — name the one to work on."};
        UUID id = (UUID) proj.get("object_id");
        String name = ((String) proj.get("display_name")).toLowerCase(java.util.Locale.ROOT);
        String kind = (String) proj.get("project_kind");
        Integer integ = (Integer) proj.get("integrity_percent");
        if (integ != null && integ >= 100) return new String[]{"SUCCEEDED", "You look the " + name + " over, but it already stands sound — there is nothing here that needs setting right."};
        // Kind-appropriate repair stock, best first — earth and clay for the fired forms, poles and binding for the rest.
        String[] mats = switch (kind) {
            case "CLAY_LINED_HEARTH", "RAINWATER_CATCHMENT" -> new String[]{"clay_lump"};
            case "SPLIT_RAIL_FENCE" -> new String[]{"straight_sapling", "dry_branch"};
            case "WATTLE_AND_DAUB_HUT", "EARTH_SHELTERED_HUT" -> new String[]{"clay_lump", "straw_bundle", "withy_rope"};
            default -> new String[]{"withy_rope", "plant_fiber", "dry_branch", "straight_sapling"};
        };
        String used = null;
        for (String m : mats) if (items.hasAtLeast(chronicle, m, 1)) { used = m; break; }
        if (used == null) return new String[]{"FAILED", "You set your hands to the " + name + ", but you have nothing fit to mend it with to hand."};
        if (!items.consumeOne(chronicle, used, at)) throw new IllegalStateException("Reachable repair material changed during the action.");
        Timestamp ts = Timestamp.from(at);
        jdbc.update("UPDATE construction_project SET integrity_percent=LEAST(100,integrity_percent+25),last_structural_update=? WHERE object_id=?", ts, id);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTION_REPAIRED',jsonb_build_object('material',?))", id, ts, used);
        return new String[]{"SUCCEEDED", "You work over the " + name + ", setting what has weathered and worked loose back toward true. It stands the sounder for it."};
    }

    /**
     * Dismantle / salvage a reachable construction here (#70): the named one, else the only one. It keeps its
     * UUID and full history (marked DESTROYED with cause DISMANTLED, never deleted) and yields only a FRACTION
     * of its materials — some is always lost in the taking-apart, and only what physically comes out is recovered.
     */
    @Transactional
    public String[] dismantle(UUID chronicle, UUID location, String text, Instant at) {
        String lower = text == null ? "" : text.toLowerCase(java.util.Locale.ROOT);
        java.util.List<java.util.Map<String,Object>> projects = jdbc.queryForList(
            "SELECT cp.object_id, w.display_name, cp.project_kind FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "WHERE w.current_location_id=? AND w.lifecycle_state='ACTIVE' AND cp.state<>'DESTROYED' ORDER BY length(w.display_name) DESC", location);
        if (projects.isEmpty()) return new String[]{"FAILED", "There is nothing built here to take apart."};
        java.util.Map<String,Object> proj = projects.stream()
            .filter(p -> lower.contains(((String)p.get("display_name")).toLowerCase(java.util.Locale.ROOT))
                || lower.contains(((String)p.get("project_kind")).toLowerCase(java.util.Locale.ROOT).replace('_', ' ')))
            .findFirst().orElse(projects.size() == 1 ? projects.get(0) : null);
        if (proj == null) return new String[]{"FAILED", "There is more than one thing built here — name the one to take apart."};
        UUID id = (UUID) proj.get("object_id"); String name = ((String) proj.get("display_name")).toLowerCase(java.util.Locale.ROOT); String kind = (String) proj.get("project_kind");
        java.util.List<String[]> salvage = switch (kind) {
            case "LEAN_TO" -> java.util.List.<String[]>of(new String[]{"dry_branch","Dry branch"}, new String[]{"dry_branch","Dry branch"}, new String[]{"plant_fiber","Plant fiber bundle"});
            case "STONE_FIRE_PIT" -> java.util.List.<String[]>of(new String[]{"field_stone","Field stone"}, new String[]{"field_stone","Field stone"}, new String[]{"field_stone","Field stone"});
            default -> java.util.List.<String[]>of(new String[]{"dry_branch","Dry branch"});
        };
        int recovered = 0;
        for (String[] s : salvage) if (items.hasCarryRoomFor(chronicle, s[0])) { items.createCarriedItem(chronicle, s[0], s[1], at, "SALVAGED"); recovered++; }
        Timestamp ts = Timestamp.from(at);
        jdbc.update("UPDATE construction_project SET state='DESTROYED' WHERE object_id=?", id);
        jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED', destroyed_at=?, destroyed_location_id=?, destroyed_cause='DISMANTLED', current_location_id=NULL WHERE id=?", ts, location, id);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'DISMANTLED',jsonb_build_object('recovered',?))", id, ts, recovered);
        String got = recovered == 0 ? "nothing worth keeping — your load is full" : recovered + " usable piece" + (recovered == 1 ? "" : "s") + " of material";
        return new String[]{"SUCCEEDED", "You take the " + name + " apart piece by piece, recovering " + got + ". Where it stood is bare ground again."};
    }
}
