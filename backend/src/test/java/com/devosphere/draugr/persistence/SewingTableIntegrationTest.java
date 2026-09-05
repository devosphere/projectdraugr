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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A workstation must be gettable, and must be for something (#95/#96).
 *
 * <p>{@code sewing_table} was dead at both ends at once. Unobtainable: its only {@code item_source} row points at
 * {@code technique_definition}, no material process produces it, and it was left out of the CRAFT_WORKSTATION
 * list that raises the other three benches — so there was no way in the world to have one. And useless if you
 * had: fifty-three sewing and leatherwork processes existed and not one declared a station. A 17kg work table
 * that nothing asked for.
 *
 * <p>It slipped past the dead-end guard because that guard exempts FURNITURE — the right call for chairs and
 * shelves, exactly the wrong one for a workstation. Skips without Docker.
 */
@SpringBootTest
class SewingTableIntegrationTest {

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
    @Autowired ChronicleActionService actions;
    @Autowired PhysicalItemService items;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void aSewingTableCanBeBuiltAndIsWhatTheLeatherworkAsksFor() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = Instant.now();

        // The stock a bench is framed and lashed from, and the blade to cut it.
        items.createCarriedItem(chronicle, "flint_knife", "Flint knife", now, "TEST_SEED");
        for (int i = 0; i < 4; i++) items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "TEST_SEED");
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "plant_fiber", "Plant fiber", now, "TEST_SEED");

        ChronicleActionService.ActionResult built = actions.resolve("build a sewing table");
        assertEquals("CRAFT_WORKSTATION", built.intent(),
            "a sewing table is a workstation like the other three benches: " + built.perception());
        assertEquals("SUCCEEDED", built.outcome(), "there must be a way in the world to have one: " + built.perception());

        Integer standing = jdbc.queryForObject(
            "SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "WHERE w.current_location_id=? AND i.item_key='sewing_table' AND w.lifecycle_state='ACTIVE'",
            Integer.class, chunk);
        assertNotNull(standing);
        assertTrue(standing > 0, "the table must actually stand on this ground afterwards");

        // And it is now the station the leatherwork names, rather than a bench nothing asks for.
        Integer asking = jdbc.queryForObject(
            "SELECT COUNT(*) FROM material_process WHERE station_kind='sewing_table'", Integer.class);
        assertNotNull(asking);
        assertTrue(asking >= 40,
            "the sewing and leatherwork run must ask for the table it was built for; only " + asking + " do");

        // Sewing still works without one — a station is a bonus, never a gate, and this must ask nothing new of
        // a Chronicle who works leather on their knee.
        Integer gated = jdbc.queryForObject(
            "SELECT COUNT(*) FROM material_process WHERE station_kind='sewing_table' AND review_state <> 'VERIFIED'", Integer.class);
        assertEquals(0, gated, "no recipe should have been left unverified by naming a station");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
