package com.devosphere.draugr.assembly;

import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.routing.ActivityClassifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runs staged assemblies (V58): multi-stage structures and crafts advanced one stage
 * at a time. Unlike a material process, which turns inputs into an output in a single
 * act, an assembly holds an ordered list of stages -- each consuming inputs, or, for a
 * cure stage, waiting out a span of world time before it can complete.
 *
 * <p>State of a build lives in {@code assembly_instance} and {@code
 * assembly_stage_completion}, not in the definition, so the same blueprint serves
 * every chronicle. A cure measures elapsed simulated time against the prerequisite
 * stage's completion, which is the same tick the body and food already run on -- the
 * player passes it by doing other things and coming back.
 */
@Service
public class AssemblyService {

    private final JdbcTemplate jdbc;
    private final PhysicalItemService items;

    public AssemblyService(JdbcTemplate jdbc, PhysicalItemService items) {
        this.jdbc = jdbc; this.items = items;
    }

    /**
     * The verified assembly whose keyword best matches the text, or null when the text
     * names no assembly. Longest matching keyword wins, so "build a hunting bow" beats a
     * bare "build a bow" elsewhere and the same text always resolves the same way.
     */
    @Transactional(readOnly = true)
    public String match(String text) {
        String v = ActivityClassifier.normalise(text);
        String best = null; int bestLen = -1;
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT assembly_key, keywords FROM assembly_definition WHERE review_state='VERIFIED'")) {
            String key = (String) row.get("assembly_key");
            for (String kw : ((String) row.get("keywords")).split(",")) {
                String k = kw.trim();
                if (!k.isEmpty() && ActivityClassifier.containsTerm(v, k) && k.length() > bestLen) {
                    best = key; bestLen = k.length();
                }
            }
        }
        return best;
    }

    /**
     * Advance the named assembly by one stage. Returns [outcome, narration], or null
     * when the text names no assembly at all (the caller then tries a material process).
     *
     * <p>Resolves the next stage whose prerequisite is complete; a cure stage completes
     * only once enough world time has passed, a work stage only once its tool, fire and
     * inputs are present. The final stage produces the craft item or raises the
     * construction.
     */
    @Transactional
    public String[] advance(UUID chronicle, UUID location, String text, Instant at) {
        String key = match(text);
        if (key == null) return null;

        Map<String, Object> def = jdbc.queryForMap(
            "SELECT subject_kind, produces_item_key, construction_kind, display_name, narration " +
            "FROM assembly_definition WHERE assembly_key=?", key);
        String subjectKind = (String) def.get("subject_kind");

        UUID instanceId = jdbc.query(
            "SELECT id FROM assembly_instance WHERE chronicle_id=? AND assembly_key=? AND state='IN_PROGRESS' LIMIT 1 FOR UPDATE",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, chronicle, key);

        Set<String> done = new HashSet<>();
        Map<String, Timestamp> doneAt = new HashMap<>();
        if (instanceId != null)
            for (Map<String, Object> c : jdbc.queryForList(
                    "SELECT stage_key, completed_at FROM assembly_stage_completion WHERE instance_id=?", instanceId)) {
                done.add((String) c.get("stage_key"));
                doneAt.put((String) c.get("stage_key"), (Timestamp) c.get("completed_at"));
            }

        List<Map<String, Object>> stages = jdbc.queryForList(
            "SELECT stage_key, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration " +
            "FROM assembly_stage WHERE assembly_key=? ORDER BY stage_order", key);

        Map<String, Object> next = null;
        for (Map<String, Object> s : stages) {
            if (done.contains((String) s.get("stage_key"))) continue;
            String prereq = (String) s.get("prerequisite_stage_key");
            if (prereq == null || done.contains(prereq)) { next = s; break; }
        }
        if (next == null) return new String[]{"FAILED", "It is already finished."};

        String stageKey = (String) next.get("stage_key");
        int cure = ((Number) next.get("cure_minutes")).intValue();
        boolean isLast = stages.get(stages.size() - 1).get("stage_key").equals(stageKey);

        if (cure > 0) {
            String prereq = (String) next.get("prerequisite_stage_key");
            Timestamp since = prereq != null ? doneAt.get(prereq) : null;
            long elapsedMin = since == null ? Long.MAX_VALUE : (at.toEpochMilli() - since.getTime()) / 60000L;
            if (elapsedMin < cure) {
                long remainingHours = Math.max(1, (cure - elapsedMin + 59) / 60);
                return new String[]{"FAILED",
                    "It is still curing — it needs about " + remainingHours + " more hour" +
                    (remainingHours == 1 ? "" : "s") + " before it can be worked."};
            }
            recordStage(instanceId, stageKey, at);
        } else {
            String tool = (String) next.get("tool_class");
            if (tool != null && !hasTool(chronicle, tool))
                return new String[]{"FAILED", "The work needs a tool you are not carrying."};
            if (Boolean.TRUE.equals(next.get("requires_fire")) && !fireHere(location))
                return new String[]{"FAILED", "It needs heat, and there is no fire burning here."};

            List<Map<String, Object>> reqs = jdbc.queryForList(
                "SELECT item_key, quantity FROM assembly_stage_requirement WHERE stage_key=?", stageKey);
            for (Map<String, Object> r : reqs)
                if (!items.hasAtLeast(chronicle, (String) r.get("item_key"), ((Number) r.get("quantity")).intValue()))
                    return new String[]{"FAILED", "You have not got enough to hand for that step."};
            if (isLast && "CRAFT".equals(subjectKind) && !items.hasCarryRoomFor(chronicle, (String) def.get("produces_item_key")))
                return new String[]{"FAILED", "You could finish it, but you could not carry what it would make."};

            if (instanceId == null) {
                instanceId = UUID.randomUUID();
                jdbc.update("INSERT INTO assembly_instance (id, assembly_key, chronicle_id, location_id) VALUES (?,?,?,?)",
                    instanceId, key, chronicle, "STRUCTURE".equals(subjectKind) ? location : null);
            }
            for (Map<String, Object> r : reqs) {
                int q = ((Number) r.get("quantity")).intValue();
                for (int i = 0; i < q; i++) items.consumeOne(chronicle, (String) r.get("item_key"), at);
            }
            recordStage(instanceId, stageKey, at);
        }

        if (isLast) {
            Timestamp ts = Timestamp.from(at);
            jdbc.update("UPDATE assembly_instance SET state='COMPLETE', updated_at=? WHERE id=?", ts, instanceId);
            if ("CRAFT".equals(subjectKind)) {
                String outKey = (String) def.get("produces_item_key");
                String outName = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, outKey);
                UUID made = items.createCarriedItem(chronicle, outKey, outName, at, "ASSEMBLED");
                jdbc.update("UPDATE assembly_instance SET object_id=? WHERE id=?", made, instanceId);
            } else {
                UUID obj = UUID.randomUUID();
                jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION',?,?)",
                    obj, def.get("display_name"), location);
                jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,?,'COMPLETED',100,?)",
                    obj, def.get("construction_kind"), ts);
                jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTED',jsonb_build_object('projectKind',?::text,'viaAssembly',?::text))",
                    obj, ts, def.get("construction_kind"), key);
                jdbc.update("UPDATE assembly_instance SET object_id=? WHERE id=?", obj, instanceId);
            }
            return new String[]{"SUCCEEDED", (String) def.get("narration")};
        }
        jdbc.update("UPDATE assembly_instance SET updated_at=? WHERE id=?", Timestamp.from(at), instanceId);
        return new String[]{"SUCCEEDED", (String) next.get("narration")};
    }

    private void recordStage(UUID instanceId, String stageKey, Instant at) {
        jdbc.update("INSERT INTO assembly_stage_completion (instance_id, stage_key, completed_at) VALUES (?,?,?)",
            instanceId, stageKey, Timestamp.from(at));
    }

    private boolean hasTool(UUID chronicle, String toolClass) {
        return switch (toolClass) {
            case "CUTTING"  -> items.hasCuttingTool(chronicle);
            case "STRIKING" -> items.hasAtLeast(chronicle, "stone_hammer", 1) || items.hasAtLeast(chronicle, "primitive_pickaxe", 1) || items.hasAtLeast(chronicle, "field_stone", 1);
            case "AXE"      -> items.hasAtLeast(chronicle, "stone_axe", 1) || items.hasAtLeast(chronicle, "stone_hatchet", 1);
            default -> true;
        };
    }

    private boolean fireHere(UUID location) {
        Boolean f = jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM fire_state fs JOIN world_object w ON w.id=fs.construction_id WHERE w.current_location_id=? AND fs.active=true)",
            Boolean.class, location);
        return Boolean.TRUE.equals(f);
    }
}
