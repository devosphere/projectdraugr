package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.construction.ConstructionService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.quality.QualityGrade;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Salvage, asserted for every structure rather than for the four the lifecycle walk raises (#77).
 *
 * <p>{@link AStructureFromRaisingToSalvageIntegrationTest} walks four structures from raising to salvage, and
 * {@link WhatComesOutIsWhatWentInIntegrationTest} holds the conservation rule over the catalogue <em>as data</em>.
 * Neither asks the live dismantling of each of the 108 kinds what it actually hands back. This does: every kind
 * with a bill of materials is stood up, taken apart, and what came out is compared, key by key, with half of
 * what went in.
 *
 * <p><b>The structures are stood up directly, not raised.</b> Raising 108 structures would take their materials,
 * their tools, their cures and their weather, and the walk already proves that raising leads to salvage. What is
 * asked here is narrower: given that this kind stands, does taking it apart return its own materials?
 *
 * <p>It also found a defect on the way. Salvage skipped every piece the load had no room for, so a Chronicle
 * with a full pack dismantled a structure and its timber simply ceased to exist. What does not fit now lies
 * where the structure stood, and {@link #aFullLoadLeavesTheSalvageOnTheGroundRatherThanLosingIt()} holds that.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class SalvageOfEveryStructureIntegrationTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

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
    @Autowired ConstructionService construction;
    @Autowired PhysicalItemService items;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

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

    /** A finished structure of this kind standing on this ground, written as assembly writes one. */
    private UUID standUp(String kind, String displayName, UUID ground, Instant at) {
        UUID obj = UUID.randomUUID();
        Timestamp ts = Timestamp.from(at);
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION',?,?)",
            obj, displayName, ground);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,?,'COMPLETED',100,?)",
            obj, kind, ts);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTED',jsonb_build_object('projectKind',?::text))",
            obj, ts, kind);
        return obj;
    }

    /**
     * What salvage should return for each kind, worked out here and not by calling the service: half of each
     * material rounded down, and one of the largest material when that would come to nothing at all.
     */
    private Map<String, Map<String, Integer>> expectedSalvage() {
        Map<String, Map<String, Integer>> bills = new TreeMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT d.construction_kind, r.item_key, SUM(r.quantity)::int AS q FROM assembly_definition d"
              + " JOIN assembly_stage s ON s.assembly_key=d.assembly_key"
              + " JOIN assembly_stage_requirement r ON r.stage_key=s.stage_key"
              + " WHERE d.construction_kind IS NOT NULL GROUP BY d.construction_kind, r.item_key ORDER BY 1, 3 DESC, 2")) {
            bills.computeIfAbsent((String) row.get("construction_kind"), k -> new LinkedHashMap<>())
                 .put((String) row.get("item_key"), ((Number) row.get("q")).intValue());
        }
        Map<String, Map<String, Integer>> expected = new TreeMap<>();
        bills.forEach((kind, bill) -> {
            Map<String, Integer> out = new TreeMap<>();
            bill.forEach((key, q) -> { if (q / 2 > 0) out.put(key, q / 2); });
            if (out.isEmpty()) out.put(bill.keySet().iterator().next(), 1);
            expected.put(kind, out);
        });
        return expected;
    }

    /** Every item a dismantling at this instant produced, carried or lying on this ground, counted by key. */
    private Map<String, Integer> salvaged(UUID chronicle, UUID ground, Instant at) {
        Map<String, Integer> got = new TreeMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT i.item_key, count(*)::int AS n FROM object_transition t"
              + " JOIN item_instance i ON i.object_id=t.object_id JOIN world_object w ON w.id=t.object_id"
              + " WHERE t.transition_type='SALVAGED' AND t.occurred_at=? AND w.lifecycle_state='ACTIVE'"
              + " AND (w.current_owner_id=? OR (w.current_owner_id IS NULL AND w.current_location_id=?))"
              + " GROUP BY i.item_key", Timestamp.from(at), chronicle, ground)) {
            got.put((String) row.get("item_key"), ((Number) row.get("n")).intValue());
        }
        return got;
    }

    /** Put down what the Chronicle carries from a dismantling, so the next one starts with the same load. */
    private void setDownWhatWasCarried(UUID chronicle, UUID ground, Instant at) {
        for (UUID item : jdbc.queryForList(
                "SELECT t.object_id FROM object_transition t JOIN world_object w ON w.id=t.object_id"
              + " WHERE t.transition_type='SALVAGED' AND t.occurred_at=? AND w.current_owner_id=?",
                UUID.class, Timestamp.from(at), chronicle)) {
            items.drop(item, ground, at);
        }
    }

    @Test
    void everyStructureGivesBackHalfOfItsOwnMaterialsAndNothingElse() {
        UUID chronicle = chronicle();
        UUID ground = emptyGround();
        Map<String, Map<String, Integer>> expected = expectedSalvage();
        assertTrue(expected.size() >= 100, () -> "the catalogue names the materials of " + expected.size() + " kinds");

        Map<String, String> names = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT construction_kind, display_name FROM assembly_definition WHERE construction_kind IS NOT NULL")) {
            names.put((String) row.get("construction_kind"), (String) row.get("display_name"));
        }

        // Whole seconds apart, so each dismantling's pieces are told apart by the instant they were made.
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        List<String> wrong = new ArrayList<>();
        int i = 0;
        for (var e : expected.entrySet()) {
            String kind = e.getKey();
            Instant at = base.plusSeconds(++i);
            UUID structure = standUp(kind, names.get(kind), ground, at);

            String[] r = construction.dismantle(chronicle, ground, "take apart the " + names.get(kind).toLowerCase(), at);
            if (!"SUCCEEDED".equals(r[0])) { wrong.add(kind + ": " + r[0] + " — " + r[1]); continue; }

            Map<String, Integer> got = salvaged(chronicle, ground, at);
            if (!got.equals(e.getValue())) wrong.add(kind + ": expected " + e.getValue() + ", got " + got);
            String state = jdbc.queryForObject("SELECT lifecycle_state FROM world_object WHERE id=?", String.class, structure);
            if (!"DESTROYED".equals(state)) wrong.add(kind + ": still " + state + " after it was taken apart");

            setDownWhatWasCarried(chronicle, ground, at);
        }
        assertTrue(wrong.isEmpty(), () -> wrong.size() + " of " + expected.size() + " structures salvage wrongly:\n  "
            + String.join("\n  ", wrong));
    }

    @Test
    void aFullLoadLeavesTheSalvageOnTheGroundRatherThanLosingIt() {
        UUID chronicle = chronicle();
        UUID ground = emptyGround();
        String kind = jdbc.queryForObject(
            "SELECT construction_kind FROM assembly_definition WHERE assembly_key='quarry_safety_screen'", String.class);
        Map<String, Integer> expected = expectedSalvage().get(kind);
        assertNotNull(expected, "the quarry safety screen must have a bill of materials");

        // Fill the load with each of the screen's own materials in turn until the next one will not fit, so no
        // salvaged piece can find room: what is more full of one thing is never less full of another.
        List<UUID> filler = new ArrayList<>();
        Instant fill = Instant.now();
        for (String key : expected.keySet()) {
            String name = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, key);
            for (int n = 0; n < 500; n++) {
                UUID piece = UUID.randomUUID();
                filler.add(piece);
                if (!items.createCraftedItem(chronicle, ground, piece, key, name, fill, "TEST_SEED", QualityGrade.SOUND)) break;
            }
            assertFalse(items.hasCarryRoomFor(chronicle, key), () -> "the load must have no room for " + key);
        }

        try {
            Instant at = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(100_000);
            UUID screen = standUp(kind, "Quarry safety screen", ground, at);
            String[] r = construction.dismantle(chronicle, ground, "take apart the quarry safety screen", at);
            assertEquals("SUCCEEDED", r[0], () -> "dismantling must succeed with a full load: " + r[1]);
            assertEquals(expected, salvaged(chronicle, ground, at),
                () -> "every salvaged piece must exist, carried or on the ground: " + r[1]);
            assertTrue(r[1].contains("lie where it stood"), () -> "the player must be told where the pieces went: " + r[1]);
            assertEquals("DESTROYED", jdbc.queryForObject("SELECT lifecycle_state FROM world_object WHERE id=?", String.class, screen));
        } finally {
            // Leave the Chronicle as it was found: the suite shares one world, and a full load breaks the next test.
            for (UUID piece : filler) {
                UUID owner = jdbc.queryForObject("SELECT current_owner_id FROM world_object WHERE id=?", UUID.class, piece);
                if (chronicle.equals(owner)) items.drop(piece, ground, Instant.now());
            }
        }
        assertTrue(auditor.inspect().consistent(), () -> "the world must stay consistent: " + auditor.inspect().violations());
    }
}
