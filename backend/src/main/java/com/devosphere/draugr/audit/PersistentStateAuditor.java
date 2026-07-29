package com.devosphere.draugr.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Read-only consistency checks. This service deliberately contains no mutation methods. */
@Service
public class PersistentStateAuditor {
    private final JdbcTemplate jdbc;
    public PersistentStateAuditor(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public AuditReport inspect() {
        List<String> violations = new ArrayList<>();
        Integer activeChronicles = jdbc.queryForObject("SELECT COUNT(*) FROM chronicle WHERE life_state = 'LIVING'", Integer.class);
        if (activeChronicles != null && activeChronicles > 1) violations.add("More than one living Chronicle exists.");
        Integer unlocatedObjects = jdbc.queryForObject("SELECT COUNT(*) FROM world_object WHERE lifecycle_state <> 'DESTROYED' AND current_location_id IS NULL AND current_owner_id IS NULL", Integer.class);
        if (unlocatedObjects != null && unlocatedObjects > 0) violations.add(unlocatedObjects + " active object(s) lack a location or owner.");
        Integer destroyedLocated = jdbc.queryForObject("SELECT COUNT(*) FROM world_object WHERE lifecycle_state='DESTROYED' AND (current_location_id IS NOT NULL OR current_owner_id IS NOT NULL)", Integer.class);
        if (destroyedLocated != null && destroyedLocated > 0) violations.add(destroyedLocated + " destroyed object(s) still have an active location or owner.");
        Integer invalidContainment = jdbc.queryForObject("SELECT COUNT(*) FROM item_containment ic JOIN world_object item ON item.id=ic.item_id JOIN world_object container ON container.id=ic.container_id WHERE item.lifecycle_state<>'ACTIVE' OR container.lifecycle_state<>'ACTIVE'", Integer.class);
        if (invalidContainment != null && invalidContainment > 0) violations.add(invalidContainment + " containment relation(s) reference an inactive object.");
        Integer inactiveEquipment = jdbc.queryForObject("SELECT COUNT(*) FROM equipment_attachment ea JOIN world_object item ON item.id=ea.item_id JOIN chronicle c ON c.id=ea.chronicle_id WHERE item.lifecycle_state<>'ACTIVE' OR c.life_state='DEAD'", Integer.class);
        if (inactiveEquipment != null && inactiveEquipment > 0) violations.add(inactiveEquipment + " equipment attachment(s) reference an inactive item or dead Chronicle.");
        Integer brokenShelters = jdbc.queryForObject("SELECT COUNT(*) FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE cp.state='COMPLETED' AND cp.integrity_percent=0 AND w.lifecycle_state='ACTIVE'", Integer.class);
        if (brokenShelters != null && brokenShelters > 0) violations.add(brokenShelters + " completed construction(s) have zero integrity while still active.");
        Integer orphanFood = jdbc.queryForObject("SELECT COUNT(*) FROM food_preservation_state fps LEFT JOIN item_instance ii ON ii.object_id=fps.object_id WHERE ii.object_id IS NULL", Integer.class);
        if (orphanFood != null && orphanFood > 0) violations.add(orphanFood + " food-preservation record(s) lack a physical item.");
        Integer chunks = jdbc.queryForObject("SELECT COUNT(*) FROM world_chunk", Integer.class);
        if (chunks == null || chunks == 0) violations.add("Canonical geography is missing.");
        return new AuditReport(violations.isEmpty(), List.copyOf(violations));
    }

    public record AuditReport(boolean consistent, List<String> violations) { }
}
