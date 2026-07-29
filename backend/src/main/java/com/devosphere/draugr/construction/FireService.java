package com.devosphere.draugr.construction;

import com.devosphere.draugr.item.PhysicalItemService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

@Service
public class FireService {
    private final JdbcTemplate jdbc; private final PhysicalItemService items;
    public FireService(JdbcTemplate jdbc, PhysicalItemService items) { this.jdbc=jdbc; this.items=items; }
    @Transactional
    public boolean light(UUID chronicle, UUID location, Instant now) {
        UUID pit=jdbc.query("SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='STONE_FIRE_PIT' AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE' LIMIT 1",rs->rs.next()?rs.getObject(1,UUID.class):null,location);
        if(pit==null || !items.consumeOne(chronicle,"dry_branch",now)) return false;
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,45,?) ON CONFLICT (construction_id) DO UPDATE SET active=true,fuel_minutes=fire_state.fuel_minutes+45,last_updated_at=EXCLUDED.last_updated_at",pit,now);
        return true;
    }
    @Transactional
    public void advanceTo(Instant now) {
        jdbc.query("SELECT construction_id,last_updated_at,fuel_minutes FROM fire_state WHERE active=true FOR UPDATE",rs->{while(rs.next()){UUID id=rs.getObject(1,UUID.class);Instant last=rs.getTimestamp(2).toInstant();int remaining=Math.max(0,rs.getInt(3)-(int)Duration.between(last,now).toMinutes());jdbc.update("UPDATE fire_state SET fuel_minutes=?,active=?,last_updated_at=? WHERE construction_id=?",remaining,remaining>0,now,id);}return null;});
    }
}
