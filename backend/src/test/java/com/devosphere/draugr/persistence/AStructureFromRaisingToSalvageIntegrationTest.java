package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One whole life of a structure, per category (#77, #108).
 *
 * <p>#77 asks for "one full project lifecycle regression per category, including weather damage and recovery",
 * and #108 for "one build/use/repair lifecycle per species family". The pieces have been built and measured one
 * at a time — salvage and repair reading each structure's own bill of materials (#795), the conservation hole in
 * the curing branch (#801), the weather gate (#802) — but nothing walked a structure from raising to salvage in
 * one go and asked the Auditor afterwards. This does.
 *
 * <p><b>Four structures, chosen so the walk proves something rather than repeating itself:</b>
 *
 * <ul>
 *   <li>{@code door_hanging} — domain <i>construction</i>, {@code is_shelter}. One stage, no tool: the simplest
 *       possible life.</li>
 *   <li>{@code woodworking_stump} — domain <i>woodworking</i>, {@code is_workstation}. Needs an AXE, so the
 *       tool gate is on the path.</li>
 *   <li>{@code quarry_safety_screen} — domain <i>stoneworking</i>, {@code is_barrier}. Eight materials across
 *       two kinds, so salvage has something to halve.</li>
 *   <li>{@code wheel_repair_stand} — <b>two stages</b> with different tool classes, so stage chaining and the
 *       prerequisite are exercised too.</li>
 * </ul>
 *
 * <p>Those are every domain that has a verified structure (construction 104, stoneworking 2, woodworking 2), and
 * between them {@code is_shelter}, {@code is_workstation} and {@code is_barrier} — three capabilities with
 * three different live readers.
 *
 * <p><b>Why these four and not a shelter with a thatched roof.</b> Every one is free of cure stages and of
 * weather limits, so the walk is deterministic: a cure would make the test wait hours of simulated time, and a
 * weather limit would make it pass or fail with the sky, which is the trap
 * {@link WorkThatCannotBeDoneInTheWetIntegrationTest} had to be written around. Those two gates have their own
 * tests; this one is about the shape of a whole life.
 *
 * <p>Each structure gets <b>ground of its own</b> — a grassland chunk with nothing built on it — because
 * {@code repair} and {@code take apart} choose among the structures at the Chronicle's feet by display name,
 * and a second one of the same kind is indistinguishable from the first. That cost a CI round once already.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class AStructureFromRaisingToSalvageIntegrationTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** A structure to walk, and what it takes. */
    private record Subject(String assemblyKey, String buildPhrase, String spokenName,
                           String capabilityFlag, Map<String, String> materials, List<String> tools, int stages) { }

    private static final List<Subject> SUBJECTS = List.of(
        new Subject("door_hanging", "build a door hanging", "door hanging", "is_shelter",
            Map.of("tanned_leather", "Tanned leather"), List.of(), 1),
        new Subject("woodworking_stump", "build a woodworking stump", "woodworking stump", "is_workstation",
            Map.of("dry_branch", "Dry branch"), List.of("stone_axe"), 1),
        new Subject("quarry_safety_screen", "build a quarry safety screen", "quarry safety screen", "is_barrier",
            Map.of("dry_branch", "Dry branch", "fiber_cordage", "Processed fiber cordage"), List.of("stone_axe"), 1),
        new Subject("wheel_repair_stand", "build a wheel repair stand", "wheel repair stand", "is_workstation",
            Map.of("timber_plank", "Timber plank", "field_stone", "Field stone"), List.of("stone_axe"), 2));

    @BeforeAll
    static void startDatabase() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for this integration test");
        System.setProperty("java.awt.headless", "true");
        postgres.start();
    }

    @AfterAll
    static void stopDatabase() { if (postgres.isRunning()) postgres.stop(); }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired WorldGenesisService worldGenesis;
    @Autowired WorldEcologyGenesisService ecology;
    @Autowired ChronicleService chronicles;
    @Autowired ChronicleActionService actions;
    @Autowired PhysicalItemService items;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /** A living Chronicle, awakening a fresh one if the last walk spent the previous. */
    private UUID chronicle() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var active = chronicles.active() != null ? chronicles.active() : chronicles.awaken();
        assertNotNull(active, "a living Chronicle is needed");
        return active.id();
    }

    private UUID emptyGround() {
        UUID ground = jdbc.queryForObject(
            "SELECT c.id FROM world_chunk c WHERE c.biome='GRASSLAND'"
          + " AND NOT EXISTS (SELECT 1 FROM world_object w JOIN construction_project cp ON cp.object_id=w.id"
          + "                  WHERE w.current_location_id=c.id AND w.lifecycle_state='ACTIVE')"
          + " ORDER BY c.grid_y, c.grid_x LIMIT 1", UUID.class);
        assertNotNull(ground, "the world must have open ground with nothing built on it");
        return ground;
    }

    private void give(UUID chronicle, String key, String name, int n) {
        for (int i = 0; i < n; i++) items.createCarriedItem(chronicle, key, name, Instant.now(), "TEST_SEED");
    }

    private int owned(UUID chronicle, String key) {
        Integer n = jdbc.queryForObject(
            "SELECT count(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id"
          + " WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key=?", Integer.class, chronicle, key);
        return n == null ? 0 : n;
    }

    /** Everything the whole assembly asks for, generously, so no stage stalls for want of one piece. */
    private void stockUp(UUID chronicle, Subject s) {
        for (Map.Entry<String, String> m : s.materials().entrySet()) {
            Integer wants = jdbc.queryForObject(
                "SELECT COALESCE(SUM(r.quantity),0) FROM assembly_stage st"
              + " JOIN assembly_stage_requirement r ON r.stage_key=st.stage_key"
              + " WHERE st.assembly_key=? AND r.item_key=?", Integer.class, s.assemblyKey(), m.getKey());
            give(chronicle, m.getKey(), m.getValue(), (wants == null ? 1 : wants) + 2);
        }
        for (String tool : s.tools()) give(chronicle, tool, tool.replace('_', ' '), 1);
    }

    @Test
    void everyCategoryWalksFromRaisingToSalvageWithItsHistoryIntact() {
        for (Subject s : SUBJECTS) {
            UUID chronicle = chronicle();
            UUID ground = emptyGround();
            jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", ground, chronicle);
            stockUp(chronicle, s);

            // ---- RAISING. One resolve per stage; the prerequisite chain decides the order, not the test.
            for (int stage = 0; stage < s.stages(); stage++) {
                var built = actions.resolve(s.buildPhrase(), UUID.randomUUID());
                assertNotNull(built);
                assertEquals("SUCCEEDED", built.outcome(),
                    () -> s.assemblyKey() + " stage: " + built.perception());
            }

            UUID object = jdbc.queryForObject(
                "SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id"
              + " WHERE w.current_location_id=? AND w.lifecycle_state='ACTIVE' AND cp.state='COMPLETED'",
                UUID.class, ground);
            assertNotNull(object, () -> s.assemblyKey() + " must stand on the ground when its stages are done");
            assertEquals(100, (int) jdbc.queryForObject(
                "SELECT integrity_percent FROM construction_project WHERE object_id=?", Integer.class, object),
                () -> s.assemblyKey() + " is newly raised and should be whole");

            // ---- IT IS NOT A TOKEN. The capability it was built for is declared on a flag live code reads.
            assertTrue(jdbc.queryForObject(
                "SELECT " + s.capabilityFlag() + " FROM construction_kind ck JOIN construction_project cp"
              + " ON cp.project_kind=ck.project_kind WHERE cp.object_id=?", Boolean.class, object),
                () -> s.assemblyKey() + " must declare " + s.capabilityFlag() + ", or raising it changed nothing");

            // ---- DAMAGE. Set directly rather than waiting out the weathering pass, which has its own test
            // (ConstructionWeatheringIntegrationTest) and would cost days of simulated time here.
            jdbc.update("UPDATE construction_project SET integrity_percent=50 WHERE object_id=?", object);

            // ---- REPAIR, with the structure's own material. Since #795 the repair stock is read from the
            // assembly's own bill, so one of the things it was built from must mend it.
            String ownMaterial = s.materials().keySet().iterator().next();
            int before = owned(chronicle, ownMaterial);
            assertTrue(before > 0, () -> "the walk should still hold " + ownMaterial + " to mend with");
            var mended = actions.resolve("repair the " + s.spokenName(), UUID.randomUUID());
            assertNotNull(mended);
            assertEquals("SUCCEEDED", mended.outcome(),
                () -> "mending " + s.assemblyKey() + " with its own material: " + mended.perception());
            assertEquals(75, (int) jdbc.queryForObject(
                "SELECT integrity_percent FROM construction_project WHERE object_id=?", Integer.class, object),
                () -> "a mending puts 25 back on " + s.assemblyKey());
            assertEquals(1, (int) jdbc.queryForObject(
                "SELECT count(*) FROM object_transition WHERE object_id=? AND transition_type='CONSTRUCTION_REPAIRED'",
                Integer.class, object), () -> "the mending of " + s.assemblyKey() + " is recorded");

            // ---- SALVAGE. Half of each material, rounded down, from its own bill.
            int branchesBefore = owned(chronicle, ownMaterial);
            var taken = actions.resolve("take apart the " + s.spokenName(), UUID.randomUUID());
            assertNotNull(taken);
            assertEquals("SUCCEEDED", taken.outcome(),
                () -> "taking " + s.assemblyKey() + " apart: " + taken.perception());
            assertTrue(owned(chronicle, ownMaterial) > branchesBefore,
                () -> s.assemblyKey() + " must give back some of what it was made of: " + taken.perception());

            // ---- HISTORY. Destroyed, never deleted, and the reason recorded.
            assertEquals("DESTROYED", jdbc.queryForObject(
                "SELECT lifecycle_state FROM world_object WHERE id=?", String.class, object),
                () -> s.assemblyKey() + " keeps its identity and is marked DESTROYED");
            assertEquals("DISMANTLED", jdbc.queryForObject(
                "SELECT destroyed_cause FROM world_object WHERE id=?", String.class, object),
                () -> s.assemblyKey() + " says why it is gone");
            assertEquals(1, (int) jdbc.queryForObject(
                "SELECT count(*) FROM object_transition WHERE object_id=? AND transition_type='DISMANTLED'",
                Integer.class, object), () -> "the dismantling of " + s.assemblyKey() + " is recorded");
            assertTrue(jdbc.queryForObject(
                "SELECT count(*) FROM object_transition WHERE object_id=? AND transition_type='CONSTRUCTED'",
                Integer.class, object) > 0,
                () -> s.assemblyKey() + " still carries the record of being built, after being taken apart");

            // ---- And the world is still sound after every one of those steps moved matter about.
            assertTrue(auditor.inspect().consistent(),
                () -> "the Auditor must find the world sound after " + s.assemblyKey() + "'s whole life: "
                    + auditor.inspect().violations());
        }
    }

    @Test
    void thatWalkCoveredEveryDomainThatHasAStructure() {
        // A guard on the test's own reach, so it cannot quietly stop being a per-category regression. If a new
        // domain gains a structure, this fails and asks for a subject rather than silently skipping it.
        chronicle();
        List<String> domains = jdbc.queryForList(
            "SELECT DISTINCT d.domain_key FROM assembly_definition d"
          + " WHERE d.subject_kind='STRUCTURE' AND d.review_state='VERIFIED' AND d.construction_kind IS NOT NULL"
          + " ORDER BY 1", String.class);
        List<String> walked = jdbc.queryForList(
            "SELECT DISTINCT d.domain_key FROM assembly_definition d WHERE d.assembly_key IN"
          + " ('door_hanging','woodworking_stump','quarry_safety_screen','wheel_repair_stand') ORDER BY 1",
            String.class);
        assertEquals(domains, walked,
            "the lifecycle walk must cover every domain that has a structure; add a subject for the new one");
    }
}
