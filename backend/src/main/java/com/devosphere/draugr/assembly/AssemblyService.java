package com.devosphere.draugr.assembly;

import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.quality.QualityGrade;
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
        Map<String, QualityGrade> doneGrade = new HashMap<>();
        if (instanceId != null)
            for (Map<String, Object> c : jdbc.queryForList(
                    "SELECT stage_key, completed_at, quality_grade FROM assembly_stage_completion WHERE instance_id=?", instanceId)) {
                done.add((String) c.get("stage_key"));
                doneAt.put((String) c.get("stage_key"), (Timestamp) c.get("completed_at"));
                doneGrade.put((String) c.get("stage_key"), QualityGrade.of((String) c.get("quality_grade")));
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
        String prereqKey = (String) next.get("prerequisite_stage_key");

        // A flawed prior step gates everything after it (M3b): the build cannot go on
        // until the defective stage is reworked, so a defect can never be carried into a
        // finished piece.
        if (prereqKey != null && doneGrade.get(prereqKey) == QualityGrade.DEFECTIVE)
            return new String[]{"FAILED", "The previous work is flawed — it has to be reworked before you can go on."};

        if (cure > 0) {
            Timestamp since = prereqKey != null ? doneAt.get(prereqKey) : null;
            long elapsedMin = since == null ? Long.MAX_VALUE : (at.toEpochMilli() - since.getTime()) / 60000L;
            if (elapsedMin < cure) {
                long remainingHours = Math.max(1, (cure - elapsedMin + 59) / 60);
                return new String[]{"FAILED",
                    "It is still curing — it needs about " + remainingHours + " more hour" +
                    (remainingHours == 1 ? "" : "s") + " before it can be worked."};
            }
            // A cure is only waiting; it carries the grade of the work it set.
            recordStage(instanceId, stageKey, at, prereqKey != null ? doneGrade.getOrDefault(prereqKey, QualityGrade.SOUND) : QualityGrade.SOUND);
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

            // A defective material is gated, not silently built in: it must be replaced
            // or reworked before it can be consumed.
            List<String> reqKeys = new java.util.ArrayList<>();
            for (Map<String, Object> r : reqs) reqKeys.add((String) r.get("item_key"));
            QualityGrade inputGrade = items.worstGradeAmong(chronicle, reqKeys);
            if (inputGrade == QualityGrade.DEFECTIVE)
                return new String[]{"FAILED", "One of the materials for this step is defective — replace or rework it before you use it."};

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
            // The stage is only as good as its materials and the care of the attempt.
            recordStage(instanceId, stageKey, at, QualityGrade.worst(inputGrade, QualityGrade.attempt(text)));
        }

        if (isLast) {
            Timestamp ts = Timestamp.from(at);
            jdbc.update("UPDATE assembly_instance SET state='COMPLETE', updated_at=? WHERE id=?", ts, instanceId);
            if ("CRAFT".equals(subjectKind)) {
                // The finished piece is only as good as its worst stage (M3b).
                QualityGrade outGrade = worstStageGrade(instanceId);
                String outKey = (String) def.get("produces_item_key");
                String outName = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, outKey);
                UUID made = items.createCarriedItem(chronicle, outKey, outName, at, "ASSEMBLED", outGrade);
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

    private void recordStage(UUID instanceId, String stageKey, Instant at, QualityGrade grade) {
        jdbc.update("INSERT INTO assembly_stage_completion (instance_id, stage_key, completed_at, quality_grade) VALUES (?,?,?,?)",
            instanceId, stageKey, Timestamp.from(at), grade.name());
    }

    /** The worst grade across an instance's completed stages — what its finished piece inherits. */
    private QualityGrade worstStageGrade(UUID instanceId) {
        QualityGrade worst = null;
        for (String g : jdbc.query("SELECT quality_grade FROM assembly_stage_completion WHERE instance_id=?",
                (rs, n) -> rs.getString(1), instanceId)) {
            QualityGrade q = QualityGrade.of(g);
            worst = worst == null ? q : QualityGrade.worst(worst, q);
        }
        return worst == null ? QualityGrade.SOUND : worst;
    }

    /**
     * Report the grade of an in-progress assembly stage by stage, or of the materials
     * carried — witness-stance, no advice (M3b). Returns [outcome, narration].
     */
    @Transactional(readOnly = true)
    public String[] inspect(UUID chronicle, String text) {
        String key = match(text);
        UUID instanceId = key != null ? jdbc.query(
            "SELECT id FROM assembly_instance WHERE chronicle_id=? AND assembly_key=? AND state='IN_PROGRESS' LIMIT 1",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, chronicle, key) : null;
        if (instanceId == null) instanceId = jdbc.query(
            "SELECT id FROM assembly_instance WHERE chronicle_id=? AND state='IN_PROGRESS' ORDER BY updated_at DESC LIMIT 1",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, chronicle);

        if (instanceId != null) {
            String name = jdbc.queryForObject(
                "SELECT d.display_name FROM assembly_instance ai JOIN assembly_definition d ON d.assembly_key=ai.assembly_key WHERE ai.id=?",
                String.class, instanceId);
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT s.name, c.quality_grade FROM assembly_stage_completion c JOIN assembly_stage s ON s.stage_key=c.stage_key " +
                "WHERE c.instance_id=? ORDER BY s.stage_order", instanceId);
            if (rows.isEmpty())
                return new String[]{"SUCCEEDED", "You look over the " + name + ", but no work has been set into it yet."};
            List<String> flawed = new java.util.ArrayList<>();
            for (Map<String, Object> r : rows)
                if (QualityGrade.of((String) r.get("quality_grade")) == QualityGrade.DEFECTIVE)
                    flawed.add((String) r.get("name"));
            if (!flawed.isEmpty())
                return new String[]{"SUCCEEDED", "You look over the " + name + ". The " + String.join(" and the ", flawed).toLowerCase(java.util.Locale.ROOT) + " has come out defective; it will spoil the finished piece if it stands."};
            QualityGrade w = worstStageGrade(instanceId);
            return new String[]{"SUCCEEDED", "You look over the " + name + ". The work so far is " + w.label() + ", with nothing flawed in it."};
        }

        // No assembly in hand: report the materials carried.
        Integer defective = jdbc.queryForObject(
            "WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' " +
            "UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') " +
            "SELECT COUNT(*) FROM reachable r JOIN item_instance i ON i.object_id=r.id WHERE i.quality_grade='DEFECTIVE'", Integer.class, chronicle);
        Integer poor = jdbc.queryForObject(
            "WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' " +
            "UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') " +
            "SELECT COUNT(*) FROM reachable r JOIN item_instance i ON i.object_id=r.id WHERE i.quality_grade='POOR'", Integer.class, chronicle);
        int d = defective == null ? 0 : defective, p = poor == null ? 0 : poor;
        if (d == 0 && p == 0)
            return new String[]{"SUCCEEDED", "You turn your materials over in your hands. What you carry is sound."};
        StringBuilder sb = new StringBuilder("You turn your materials over in your hands. ");
        if (d > 0) sb.append(d).append(d == 1 ? " piece is defective" : " pieces are defective");
        if (d > 0 && p > 0) sb.append(", and ");
        if (p > 0) sb.append(p).append(p == 1 ? " is only poor" : " are only poor");
        sb.append(".");
        return new String[]{"SUCCEEDED", sb.toString()};
    }

    /**
     * Return an in-progress assembly to its earliest defective stage, stripping that
     * stage and everything after it so it can be done again (M3b). The flawed work is
     * undone, not discarded.
     */
    @Transactional
    public String[] rework(UUID chronicle, String text, Instant at) {
        String key = match(text);
        UUID instanceId = key != null ? jdbc.query(
            "SELECT id FROM assembly_instance WHERE chronicle_id=? AND assembly_key=? AND state='IN_PROGRESS' LIMIT 1 FOR UPDATE",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, chronicle, key) : null;
        if (instanceId == null) instanceId = jdbc.query(
            "SELECT id FROM assembly_instance WHERE chronicle_id=? AND state='IN_PROGRESS' ORDER BY updated_at DESC LIMIT 1 FOR UPDATE",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, chronicle);
        if (instanceId == null) return new String[]{"FAILED", "You have nothing half-made to rework."};

        Map<String, Object> flaw = jdbc.query(
            "SELECT s.stage_key, s.stage_order, s.name FROM assembly_stage_completion c JOIN assembly_stage s ON s.stage_key=c.stage_key " +
            "WHERE c.instance_id=? AND c.quality_grade='DEFECTIVE' ORDER BY s.stage_order LIMIT 1",
            rs -> rs.next() ? Map.of("order", rs.getInt("stage_order"), "name", rs.getString("name")) : null, instanceId);
        if (flaw == null) return new String[]{"FAILED", "You look the piece over, but there is nothing flawed to redo."};

        int fromOrder = (int) flaw.get("order");
        final UUID inst = instanceId;
        jdbc.update("DELETE FROM assembly_stage_completion WHERE instance_id=? AND stage_key IN (" +
            "SELECT stage_key FROM assembly_stage WHERE assembly_key=(SELECT assembly_key FROM assembly_instance WHERE id=?) AND stage_order >= ?)",
            inst, inst, fromOrder);
        jdbc.update("UPDATE assembly_instance SET updated_at=? WHERE id=?", Timestamp.from(at), inst);
        return new String[]{"SUCCEEDED", "You strip the " + ((String) flaw.get("name")).toLowerCase(java.util.Locale.ROOT) + " back out and ready the piece to be done again."};
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
