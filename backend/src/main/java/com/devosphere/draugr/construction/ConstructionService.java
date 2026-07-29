package com.devosphere.draugr.construction;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.devosphere.draugr.item.PhysicalItemService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ConstructionService {
    private final JdbcTemplate jdbc; private final PhysicalItemService items;
    public ConstructionService(JdbcTemplate jdbc, PhysicalItemService items) { this.jdbc = jdbc; this.items=items; }

    @Transactional public boolean startLeanTo(UUID chronicle, UUID location, UUID action, Instant at) { Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='LEAN_TO' AND cp.state IN ('PLANNED','IN_PROGRESS')",Integer.class,location); if(count!=null&&count>0)return false; UUID id=UUID.randomUUID(); jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Lean-to frame',?)",id,location); jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,created_from_action_id) VALUES (?,'LEAN_TO','PLANNED',0,?)",id,action); return true; }
    @Transactional public boolean abandonLeanTo(UUID location, Instant at) { UUID id=jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='LEAN_TO' AND cp.state IN ('PLANNED','IN_PROGRESS') ORDER BY w.created_at LIMIT 1 FOR UPDATE",rs->rs.next()?rs.getObject(1,UUID.class):null,location); if(id==null)return false; jdbc.update("UPDATE construction_project SET state='ABANDONED' WHERE object_id=?",id); jdbc.update("UPDATE world_object SET display_name='Abandoned lean-to frame',updated_at=? WHERE id=?",at,id); jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTION_ABANDONED','{}'::jsonb)",id,at); return true; }
    @Transactional public boolean resumeLeanTo(UUID location, Instant at) { UUID id=jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='LEAN_TO' AND cp.state='ABANDONED' ORDER BY w.created_at LIMIT 1 FOR UPDATE",rs->rs.next()?rs.getObject(1,UUID.class):null,location); if(id==null)return false; jdbc.update("UPDATE construction_project SET state='IN_PROGRESS' WHERE object_id=?",id); jdbc.update("UPDATE world_object SET display_name='Lean-to frame',updated_at=? WHERE id=?",at,id); jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTION_RESUMED','{}'::jsonb)",id,at); return true; }
    @Transactional public boolean workLeanTo(UUID chronicle, UUID location, Instant at) { UUID id=jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='LEAN_TO' AND cp.state IN ('PLANNED','IN_PROGRESS') ORDER BY w.created_at LIMIT 1 FOR UPDATE",rs->rs.next()?rs.getObject(1,UUID.class):null,location); if(id==null || !items.hasAtLeast(chronicle,"dry_branch",1) || !items.hasAtLeast(chronicle,"plant_fiber",1))return false; if(!items.consumeOne(chronicle,"dry_branch",at) || !items.consumeOne(chronicle,"plant_fiber",at)) throw new IllegalStateException("Reachable construction materials changed during the action."); Integer prior=jdbc.queryForObject("SELECT progress_percent FROM construction_project WHERE object_id=?",Integer.class,id); int progress=Math.min(100,(prior==null?0:prior)+25); jdbc.update("UPDATE construction_project SET state=?,progress_percent=?,completed_at=? WHERE object_id=?",progress==100?"COMPLETED":"IN_PROGRESS",progress,progress==100?at:null,id); jdbc.update("UPDATE world_object SET display_name=? WHERE id=?",progress==100?"Lean-to":"Lean-to frame",id); return true; }
    @Transactional public boolean repairLeanTo(UUID chronicle, UUID location, Instant at) { UUID id=jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='LEAN_TO' AND cp.state='COMPLETED' AND cp.integrity_percent<100 LIMIT 1 FOR UPDATE",rs->rs.next()?rs.getObject(1,UUID.class):null,location); if(id==null||!items.hasAtLeast(chronicle,"dry_branch",1)||!items.hasAtLeast(chronicle,"plant_fiber",1))return false; if(!items.consumeOne(chronicle,"dry_branch",at)||!items.consumeOne(chronicle,"plant_fiber",at))throw new IllegalStateException("Reachable repair materials changed during the action."); jdbc.update("UPDATE construction_project SET integrity_percent=LEAST(100,integrity_percent+25),last_structural_update=? WHERE object_id=?",at,id); jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTION_REPAIRED','{}'::jsonb)",id,at); return true; }
    @Transactional public void advanceTo(Instant now) {
        jdbc.update("UPDATE construction_project cp SET integrity_percent=GREATEST(0,cp.integrity_percent-FLOOR(EXTRACT(EPOCH FROM (? - cp.last_structural_update))/86400)::int*4),last_structural_update=? FROM world_object w JOIN world_chunk c ON c.id=w.current_location_id JOIN world_weather ww ON ww.world_id=c.world_id WHERE cp.object_id=w.id AND cp.project_kind='LEAN_TO' AND cp.state='COMPLETED' AND ww.weather_kind='STORM' AND EXTRACT(EPOCH FROM (? - cp.last_structural_update))>=86400",now,now,now);
        List<UUID> collapsed = jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE cp.project_kind='LEAN_TO' AND cp.state='COMPLETED' AND cp.integrity_percent=0 AND w.lifecycle_state='ACTIVE' FOR UPDATE", (rs, row) -> rs.getObject(1, UUID.class));
        for (UUID objectId : collapsed) {
            jdbc.update("UPDATE construction_project SET state='DESTROYED' WHERE object_id=?", objectId);
            jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED',destroyed_at=?,current_location_id=NULL,updated_at=? WHERE id=?", now, now, objectId);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTION_COLLAPSED',jsonb_build_object('cause','storm_decay'))", objectId, now);
        }
    }

    /** Returns false for an unsuccessful physical attempt without disclosing hidden prerequisites to narration. */
    @Transactional
    public boolean buildFirePit(UUID chronicleId, UUID locationId, UUID actionId, Instant occurredAt) {
        List<UUID> stones = jdbc.query("WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') SELECT r.id FROM reachable r JOIN item_instance i ON i.object_id=r.id WHERE i.item_key='field_stone' ORDER BY r.id FOR UPDATE", (rs, row) -> rs.getObject(1, UUID.class), chronicleId);
        if (stones.size() < 4) return false;
        UUID projectId = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit',?)", projectId, locationId);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,created_from_action_id,completed_at) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?,?)", projectId, actionId, occurredAt);
        for (UUID stone : stones.subList(0, 4)) items.retire(stone, occurredAt, "CONSUMED_FOR_CONSTRUCTION", "field_stone");
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTED',jsonb_build_object('projectKind','STONE_FIRE_PIT'))", projectId, occurredAt);
        return true;
    }
}
