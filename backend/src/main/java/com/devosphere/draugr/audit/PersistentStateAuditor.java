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
        Integer destroyedNoCause = jdbc.queryForObject("SELECT COUNT(*) FROM world_object WHERE lifecycle_state='DESTROYED' AND destroyed_cause IS NULL", Integer.class);
        if (destroyedNoCause != null && destroyedNoCause > 0) violations.add(destroyedNoCause + " destroyed object(s) do not record how they were destroyed.");
        Integer invalidContainment = jdbc.queryForObject("SELECT COUNT(*) FROM item_containment ic JOIN world_object item ON item.id=ic.item_id JOIN world_object container ON container.id=ic.container_id WHERE item.lifecycle_state<>'ACTIVE' OR container.lifecycle_state<>'ACTIVE'", Integer.class);
        if (invalidContainment != null && invalidContainment > 0) violations.add(invalidContainment + " containment relation(s) reference an inactive object.");
        Integer inactiveEquipment = jdbc.queryForObject("SELECT COUNT(*) FROM equipment_attachment ea JOIN world_object item ON item.id=ea.item_id JOIN chronicle c ON c.id=ea.chronicle_id WHERE item.lifecycle_state<>'ACTIVE' OR c.life_state='DEAD'", Integer.class);
        if (inactiveEquipment != null && inactiveEquipment > 0) violations.add(inactiveEquipment + " equipment attachment(s) reference an inactive item or dead Chronicle.");
        Integer brokenShelters = jdbc.queryForObject("SELECT COUNT(*) FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE cp.state='COMPLETED' AND cp.integrity_percent=0 AND w.lifecycle_state='ACTIVE'", Integer.class);
        if (brokenShelters != null && brokenShelters > 0) violations.add(brokenShelters + " completed construction(s) have zero integrity while still active.");
        Integer orphanFood = jdbc.queryForObject("SELECT COUNT(*) FROM food_preservation_state fps LEFT JOIN item_instance ii ON ii.object_id=fps.object_id WHERE ii.object_id IS NULL", Integer.class);
        if (orphanFood != null && orphanFood > 0) violations.add(orphanFood + " food-preservation record(s) lack a physical item.");
        // Literature: a document is a title bound to a chain of revisions. A document
        // with no current revision holds no words; a current revision belonging to
        // another document, or one that is not the latest, means the chain has been
        // corrupted — the record would render stale or foreign text.
        Integer emptyDocuments = jdbc.queryForObject("SELECT COUNT(*) FROM literature_document WHERE current_revision_id IS NULL", Integer.class);
        if (emptyDocuments != null && emptyDocuments > 0) violations.add(emptyDocuments + " literature document(s) have no current revision.");
        Integer foreignRevision = jdbc.queryForObject("SELECT COUNT(*) FROM literature_document ld JOIN literature_revision lr ON lr.id=ld.current_revision_id WHERE lr.document_id <> ld.object_id", Integer.class);
        if (foreignRevision != null && foreignRevision > 0) violations.add(foreignRevision + " literature document(s) point to a revision from another document.");
        Integer staleRevision = jdbc.queryForObject("SELECT COUNT(*) FROM literature_document ld JOIN literature_revision cur ON cur.id=ld.current_revision_id WHERE cur.revision_number < (SELECT MAX(r.revision_number) FROM literature_revision r WHERE r.document_id=ld.object_id)", Integer.class);
        if (staleRevision != null && staleRevision > 0) violations.add(staleRevision + " literature document(s) do not point to their latest revision.");
        // Fire: a live fire needs fuel, a hearth to sit in, and that hearth intact.
        Integer starvedFire = jdbc.queryForObject("SELECT COUNT(*) FROM fire_state WHERE active=true AND fuel_minutes=0", Integer.class);
        if (starvedFire != null && starvedFire > 0) violations.add(starvedFire + " fire(s) burn with no fuel remaining.");
        Integer displacedFire = jdbc.queryForObject("SELECT COUNT(*) FROM fire_state fs JOIN construction_project cp ON cp.object_id=fs.construction_id JOIN world_object w ON w.id=fs.construction_id WHERE fs.active=true AND (cp.project_kind <> 'STONE_FIRE_PIT' OR w.lifecycle_state='DESTROYED')", Integer.class);
        if (displacedFire != null && displacedFire > 0) violations.add(displacedFire + " fire(s) burn on something other than an intact fire pit.");
        // A carcass emptied of both meat and hide should have been retired, not left
        // standing as an active object the world still offers up.
        Integer spentCarcass = jdbc.queryForObject("SELECT COUNT(*) FROM wildlife_carcass wc JOIN world_object w ON w.id=wc.object_id WHERE w.lifecycle_state='ACTIVE' AND wc.remaining_meat_units=0 AND wc.hide_available=false", Integer.class);
        if (spentCarcass != null && spentCarcass > 0) violations.add(spentCarcass + " exhausted carcass(es) remain active in the world.");
        // Process integrity: a declarative recipe can be wrong in ways that never
        // throw — creating matter, consuming its own output, resting on an input
        // nobody can get. These are caught before a process is allowed to run, so the
        // only thing reported here is drift: a process that went live while carrying
        // an unresolved blocking finding against it.
        Integer liveButFlagged = jdbc.queryForObject(
            "SELECT COUNT(*) FROM material_process mp WHERE mp.review_state='VERIFIED' AND EXISTS (" +
            "  SELECT 1 FROM process_review r WHERE r.process_key=mp.process_key AND r.severity='BLOCKING' AND r.resolved_at IS NULL)", Integer.class);
        if (liveButFlagged != null && liveButFlagged > 0) violations.add(liveButFlagged + " verified process(es) still carry an unresolved blocking finding.");
        Integer massCreating = jdbc.queryForObject(
            "SELECT COUNT(*) FROM process_mass_balance b JOIN material_process mp ON mp.process_key=b.process_key " +
            "WHERE mp.review_state='VERIFIED' AND NOT mp.conservation_exempt " +
            "AND b.min_input_grams > 0 AND b.max_output_grams > b.min_input_grams * 1.05", Integer.class);
        if (massCreating != null && massCreating > 0) violations.add(massCreating + " live process(es) would create matter from nothing.");
        // Routing reachability (V54/V55). A process is matched only when the action text
        // agrees with it on category, keyword and subject, so a process can go silently
        // unreachable in two ways — and unreachable is not a loud failure. Nothing
        // throws; the action simply routes to the Architect, and the world pays for an
        // AI call to invent something it already had.
        //
        // V54 and V55 enforce both of these at migration time, but a migration only
        // guards the rows present when it runs. These are the standing checks.
        Integer subjectlessProcesses = jdbc.queryForObject(
            "SELECT COUNT(*) FROM material_process mp " +
            "WHERE NOT EXISTS (SELECT 1 FROM process_subject s WHERE s.process_key=mp.process_key)", Integer.class);
        if (subjectlessProcesses != null && subjectlessProcesses > 0)
            violations.add(subjectlessProcesses + " process(es) have no subject terms and can never match.");
        // The second way: a process whose own keywords classify to a different category
        // than the one it declares. The category gate then rejects every sentence that
        // would have named it.
        Integer miscategorised = jdbc.queryForObject(
            "SELECT COUNT(*) FROM material_process mp WHERE NOT EXISTS (" +
            "  SELECT 1 FROM unnest(string_to_array(mp.keywords, ',')) k " +
            "  JOIN category_term ct ON ct.category_key = mp.category_key " +
            "  WHERE ' ' || btrim(k) || ' ' LIKE '% ' || ct.term || ' %' " +
            "     OR ' ' || ct.term  || ' ' LIKE '% ' || btrim(k) || ' %')", Integer.class);
        if (miscategorised != null && miscategorised > 0)
            violations.add(miscategorised + " process(es) have no keyword that classifies to their own category.");
        // A term shared by many categories decides nothing and only adds noise to every
        // score it touches. Some sharing is legitimate — 'dress' is PROCESS on a hide
        // and CONSTRUCT on a stone — so this reports genuine over-breadth, not sharing.
        Integer overBroadTerms = jdbc.queryForObject(
            "SELECT COUNT(*) FROM (SELECT term FROM category_term GROUP BY term HAVING COUNT(*) > 3) t", Integer.class);
        if (overBroadTerms != null && overBroadTerms > 0)
            violations.add(overBroadTerms + " routing term(s) belong to more than three categories and cannot discriminate.");
        // Reachability: an item nobody can obtain is scenery wearing the costume of a
        // mechanic. This has already happened twice — the V48 hide garments were
        // insulating but unsewable, and V49 added flint and pyrite as fire kit with
        // nowhere to find them. Both were caught by hand, which is not a system.
        // item_source (V51) declares how each item enters the world; anything with no
        // source and no recorded reason is a definition someone forgot to finish.
        Integer unreachableItems = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_definition d " +
            "WHERE NOT EXISTS (SELECT 1 FROM item_source s WHERE s.item_key=d.item_key) " +
            "AND NOT EXISTS (SELECT 1 FROM item_unreachable_known k WHERE k.item_key=d.item_key)", Integer.class);
        if (unreachableItems != null && unreachableItems > 0) violations.add(unreachableItems + " item definition(s) have no way to be obtained.");
        // The converse: a source pointing at an item that no longer exists.
        Integer orphanSources = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_source s LEFT JOIN item_definition d ON d.item_key=s.item_key WHERE d.item_key IS NULL", Integer.class);
        if (orphanSources != null && orphanSources > 0) violations.add(orphanSources + " item source(s) reference an item that does not exist.");
        // Staged assembly (V58). A definition can be broken in ways that never throw at
        // runtime — a stage depending on itself or a later one (a cycle that never
        // resolves), an assembly with no stages to run, or a stage needing an item
        // nobody can obtain. The migration gates these, but a migration only guards the
        // rows present when it runs; these are the standing checks.
        Integer verifiedButFlaggedAssemblies = jdbc.queryForObject(
            "SELECT COUNT(*) FROM assembly_definition d WHERE d.review_state='VERIFIED' AND EXISTS (" +
            "  SELECT 1 FROM assembly_review r WHERE r.assembly_key=d.assembly_key AND r.severity='BLOCKING' AND r.resolved_at IS NULL)", Integer.class);
        if (verifiedButFlaggedAssemblies != null && verifiedButFlaggedAssemblies > 0)
            violations.add(verifiedButFlaggedAssemblies + " verified assembly(ies) still carry an unresolved blocking finding.");
        Integer cyclicStages = jdbc.queryForObject(
            "SELECT COUNT(*) FROM assembly_stage s JOIN assembly_stage p ON p.stage_key=s.prerequisite_stage_key " +
            "WHERE p.assembly_key <> s.assembly_key OR p.stage_order >= s.stage_order", Integer.class);
        if (cyclicStages != null && cyclicStages > 0)
            violations.add(cyclicStages + " assembly stage(s) depend on a later or foreign stage (a prerequisite cycle).");
        Integer stagelessAssemblies = jdbc.queryForObject(
            "SELECT COUNT(*) FROM assembly_definition d WHERE NOT EXISTS (SELECT 1 FROM assembly_stage s WHERE s.assembly_key=d.assembly_key)", Integer.class);
        if (stagelessAssemblies != null && stagelessAssemblies > 0)
            violations.add(stagelessAssemblies + " assembly(ies) declare no stages and can never advance.");
        Integer unobtainableStageInputs = jdbc.queryForObject(
            "SELECT COUNT(*) FROM assembly_stage_requirement r " +
            "WHERE NOT EXISTS (SELECT 1 FROM item_source src WHERE src.item_key=r.item_key)", Integer.class);
        if (unobtainableStageInputs != null && unobtainableStageInputs > 0)
            violations.add(unobtainableStageInputs + " assembly stage requirement(s) name an item with no acquisition path.");
        // Production quality (V59). A finished assembly must never carry a defective
        // stage: the stage gate refuses to advance past flawed work, so a completed
        // instance holding one means the gate was bypassed. Reworking is the sanctioned
        // way out, not shipping the flaw.
        Integer completedWithDefect = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT ai.id) FROM assembly_instance ai JOIN assembly_stage_completion c ON c.instance_id=ai.id " +
            "WHERE ai.state='COMPLETE' AND c.quality_grade='DEFECTIVE'", Integer.class);
        if (completedWithDefect != null && completedWithDefect > 0)
            violations.add(completedWithDefect + " completed assembly(ies) carry a defective stage that was never reworked.");
        // Navigation memory: a recorded visit means the chronicle has stood there at
        // least once, so a non-positive count is a corrupted route memory.
        Integer emptyVisits = jdbc.queryForObject("SELECT COUNT(*) FROM chronicle_chunk_visit WHERE visit_count <= 0", Integer.class);
        if (emptyVisits != null && emptyVisits > 0) violations.add(emptyVisits + " navigation visit record(s) hold a non-positive count.");
        Integer chunks = jdbc.queryForObject("SELECT COUNT(*) FROM world_chunk", Integer.class);
        if (chunks == null || chunks == 0) violations.add("Canonical geography is missing.");
        // The domain registry records what the world knows how to be. A world with no
        // registered domains has lost the Architect's ledger and cannot know what schema
        // it already carries — the foundation domains must always be present.
        Integer domains = jdbc.queryForObject("SELECT COUNT(*) FROM domain_registry", Integer.class);
        if (domains == null || domains == 0) violations.add("The domain registry is empty.");
        return new AuditReport(violations.isEmpty(), List.copyOf(violations));
    }

    public record AuditReport(boolean consistent, List<String> violations) { }
}
