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
    @Transactional public boolean workLeanTo(UUID chronicle, UUID location, Instant at) { UUID id=jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='LEAN_TO' AND cp.state IN ('PLANNED','IN_PROGRESS') ORDER BY w.created_at LIMIT 1 FOR UPDATE",rs->rs.next()?rs.getObject(1,UUID.class):null,location); if(id==null||!items.consumeOne(chronicle,"dry_branch",at)||!items.consumeOne(chronicle,"plant_fiber",at))return false; Integer prior=jdbc.queryForObject("SELECT progress_percent FROM construction_project WHERE object_id=?",Integer.class,id); int progress=Math.min(100,(prior==null?0:prior)+25); jdbc.update("UPDATE construction_project SET state=?,progress_percent=?,completed_at=? WHERE object_id=?",progress==100?"COMPLETED":"IN_PROGRESS",progress,progress==100?at:null,id); jdbc.update("UPDATE world_object SET display_name=? WHERE id=?",progress==100?"Lean-to":"Lean-to frame",id); return true; }

    /** Returns false for an unsuccessful physical attempt without disclosing hidden prerequisites to narration. */
    @Transactional
    public boolean buildFirePit(UUID chronicleId, UUID locationId, UUID actionId, Instant occurredAt) {
        List<UUID> stones = jdbc.query("WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') SELECT r.id FROM reachable r JOIN item_instance i ON i.object_id=r.id WHERE i.item_key='field_stone' ORDER BY r.id FOR UPDATE", (rs, row) -> rs.getObject(1, UUID.class), chronicleId);
        if (stones.size() < 4) return false;
        UUID projectId = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit',?)", projectId, locationId);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,created_from_action_id,completed_at) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?,?)", projectId, actionId, occurredAt);
        for (UUID stone : stones.subList(0, 4)) {
            jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED', destroyed_at=?, current_owner_id=NULL WHERE id=?", occurredAt, stone);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSUMED_FOR_CONSTRUCTION',jsonb_build_object('projectId',?::text))", stone, occurredAt, projectId.toString());
        }
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTED',jsonb_build_object('projectKind','STONE_FIRE_PIT'))", projectId, occurredAt);
        return true;
    }
}
