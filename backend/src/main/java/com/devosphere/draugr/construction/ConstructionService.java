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

    /** Bedding material a bed can be laid from, best first — all reachable first-era plant stock (#71 make_bed). */
    private static final String[][] BEDDING = {{"straw_bundle","straw"},{"reed_bundle","reed"},{"thatch_bundle","thatch"},{"plant_fiber","plant fibre"}};

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

    /**
     * Tend the camp here (#71 maintain_camp): go over what stands, setting decayed constructions a little back
     * toward true and ordering the site around them. Grounded — with nothing built here there is no camp to tend.
     * Its benefit is the real integrity it restores; the small easing of mind is applied by the caller.
     */
    @Transactional
    public String[] maintainCamp(UUID chronicle, UUID location, Instant at) {
        List<UUID> here = jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND w.lifecycle_state='ACTIVE' AND cp.state='COMPLETED' FOR UPDATE", (rs, r) -> rs.getObject(1, UUID.class), location);
        if (here.isEmpty()) return new String[]{"FAILED", "You look around to set a camp in order, but there is nothing built here to tend."};
        Timestamp ts = Timestamp.from(at);
        int tended = 0;
        for (UUID id : here) {
            Integer integ = jdbc.queryForObject("SELECT integrity_percent FROM construction_project WHERE object_id=?", Integer.class, id);
            if (integ != null && integ < 100) {
                jdbc.update("UPDATE construction_project SET integrity_percent=LEAST(100,integrity_percent+10),last_structural_update=? WHERE object_id=?", ts, id);
                jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CAMP_MAINTAINED','{}'::jsonb)", id, ts);
                tended++;
            }
        }
        String tail = tended == 0
            ? "Everything here already stands sound; you set what little is astray back in its place."
            : "You go over what stands here, working " + tended + " thing" + (tended == 1 ? "" : "s") + " back toward true and ordering the camp around them.";
        return new String[]{"SUCCEEDED", "You tidy the camp — stowing what is loose, righting what has shifted. " + tail};
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
