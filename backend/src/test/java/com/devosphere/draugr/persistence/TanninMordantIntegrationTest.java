package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
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

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mordant (EPIC #171 / #177 textile finishing). A tannin mordant leached from bark fixes the dye colourfast into
 * cloth. Proven through the public action pipeline: leach the bark to a mordant, then dye the cloth fast with it — the
 * mordant is consumed, proving the mordant path ran (not the plain dyeing). Skips without Docker.
 */
@SpringBootTest
class TanninMordantIntegrationTest {

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
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private int owned(UUID chronicle, String key) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key=? AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, key, chronicle);
        return n == null ? 0 : n;
    }

    @Test
    void barkLeachesToAMordantThatDyesClothColourfast() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();

        // Leach a mordant from bark.
        items.createCarriedItem(chronicle, "bark_sheet", "Bark sheet", now, "TEST");
        items.createCarriedItem(chronicle, "bark_sheet", "Bark sheet", now, "TEST");
        ChronicleActionService.ActionResult leach = actions.resolve("leach a tannin mordant from the bark");
        assertEquals("SUCCEEDED", leach.outcome(), () -> "leaching a mordant must succeed: " + leach.perception());
        assertTrue(owned(chronicle, "tannin_mordant") >= 1, () -> "leaching yields a tannin mordant, got " + owned(chronicle, "tannin_mordant"));

        // Dye cloth fast with the mordant — pigment + mordant + a length of cloth.
        items.createCarriedItem(chronicle, "pigment", "Ground pigment", now, "TEST");
        items.createCarriedItem(chronicle, "linen_cloth", "Linen cloth", now, "TEST");
        int mordantBefore = owned(chronicle, "tannin_mordant");
        // "mordant the cloth" is unambiguous — only mordant_dye_cloth carries that keyword (plain dye_cloth does not).
        ChronicleActionService.ActionResult dye = actions.resolve("mordant the cloth with the pigment");
        assertEquals("SUCCEEDED", dye.outcome(), () -> "mordant-dyeing must succeed: " + dye.perception());
        assertTrue(owned(chronicle, "dyed_cloth") >= 1, () -> "mordant-dyeing yields dyed cloth, got " + owned(chronicle, "dyed_cloth"));
        assertEquals(mordantBefore - 1, owned(chronicle, "tannin_mordant"), "the mordant is consumed — proving the mordant path ran, not the plain dyeing");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
