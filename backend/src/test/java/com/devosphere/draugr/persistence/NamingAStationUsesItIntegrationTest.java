package com.devosphere.draugr.persistence;

import com.devosphere.draugr.assembly.AssemblyService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.simulation.SimulationTickService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Naming a structure that already stands is using it, not building another (#77).
 *
 * <p>The assembly matcher runs before the material processes, and every structure answers to its bare name so that
 * a half-built one can be picked up again. So once a drying rack was finished, "dry the mushrooms on the drying
 * rack" matched the rack's assembly, found nothing under way, and began raising a second rack — the mushrooms never
 * reached the process that dries them, and every station built for #220 was one sentence from being unusable.
 *
 * <p>This drives the real assembly boundary the action service calls first. Skips without Docker.
 */
@SpringBootTest
class NamingAStationUsesItIntegrationTest {

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
    @Autowired AssemblyService assembly;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private int started(UUID chronicle) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM assembly_instance WHERE chronicle_id=? AND assembly_key='drying_rack'", Integer.class, chronicle);
        return n == null ? 0 : n;
    }

    @Test
    void aStandingRackIsUsedWhenNamedAndOnlyBuiltWhenAskedFor() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        Instant now = ticks.current().simulatedAt();

        // A finished drying rack standing on this ground.
        UUID rack = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) VALUES (?,'CONSTRUCTION','Drying rack','ACTIVE',?)", rack, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) VALUES (?,'DRYING_RACK','COMPLETED',100,?,100)", rack, Timestamp.from(now));

        // The whole complaint: naming the rack in a sentence about using it must fall through to the process.
        assertNull(assembly.advance(chronicle, chunk, "dry the mushrooms on the drying rack", now),
            "with a rack standing here, naming it is using it — the assembly must step aside for the drying process");
        assertEquals(0, started(chronicle), "no second rack may be begun by naming the first");

        // Asking for one still builds one: by a verb, or by asking for another.
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        for (int i = 0; i < 4; i++) items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "TEST_SEED");
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "fiber_cordage", "Fibre cordage", now, "TEST_SEED");
        String[] posts = assembly.advance(chronicle, chunk, "put up another drying rack", now);
        assertNotNull(posts, "asking for another must still reach the assembly even where one stands");
        assertEquals(1, started(chronicle), () -> "the posts of a second rack must be set: " + posts[1]);

        // A rack under way is advanced by its bare name, whatever else stands here.
        String[] bars = assembly.advance(chronicle, chunk, "work on the drying rack", now);
        assertNotNull(bars, "a rack under way must be advanced by its name");
        assertEquals(1, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "WHERE w.current_location_id=? AND cp.project_kind='DRYING_RACK' AND cp.state='COMPLETED' AND w.id<>?",
            Integer.class, chunk, rack), () -> "the second rack must stand finished: " + bars[1]);

        // And a verb still asks for a build: with nothing to hand it fails for want of materials, not by stepping aside.
        assertNotNull(assembly.advance(chronicle, chunk, "build a drying rack", now),
            "a build phrase must reach the assembly even where racks stand");

        assertEquals(true, auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
