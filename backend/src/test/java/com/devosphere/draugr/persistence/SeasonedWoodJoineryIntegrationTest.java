package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.quality.QualityGrade;
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
 * Seasoned-wood joinery regression (EPIC #200 forestry / #203 timber — closing the seasoned-wood dead-read). The
 * season processes turn green stock into seasoned_plank / seasoned_timber, but nothing ever read the result — a
 * wood a Chronicle seasoned earned no better work than green. Now seasoned wood holds true where green warps and
 * checks as it dries, so joinery worked with seasoned stock to hand comes out truer: it lifts the workmanship of a
 * fit-and-assemble one grade, the same minor, bounded assist a workstation gives, still capped by the stock.
 *
 * <p>Proven: the same mortise cut from the same FINE timber comes out SOUND worked plainly, but FINE with seasoned
 * wood to hand. Skips gracefully without Docker.
 */
@SpringBootTest
class SeasonedWoodJoineryIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private String newestBeamGrade(UUID chronicle) {
        return jdbc.queryForObject(
                "SELECT i.quality_grade FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key='mortised_beam' AND (w.current_owner_id=? OR w.current_location_id IS NOT NULL) " +
                "AND w.lifecycle_state='ACTIVE' ORDER BY w.created_at DESC LIMIT 1", String.class, chronicle);
    }

    @Test
    void seasonedWoodLiftsTheWorkmanshipOfJoinery() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();

        // A blade to cut the joint (cut_mortise is CUTTING-gated) and FINE timber, so the workmanship — not the
        // stock — is what caps the result.
        items.createCarriedItem(chronicle, "stone_knife", "Stone knife", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "structural_timber", "Structural timber", now, "TEST_SEED", QualityGrade.FINE);

        // Cut plainly from FINE green timber: the workmanship is only SOUND, so the FINE stock is capped to SOUND.
        String[] plain = items.executeProcess(chronicle, chunk, "cut_mortise", "cut a mortise", now);
        assertEquals("SUCCEEDED", plain[0], () -> "cutting a mortise must succeed: " + plain[1]);
        assertEquals("SOUND", newestBeamGrade(chronicle), "a plain cut of FINE green timber yields a SOUND beam");

        // The same cut and the same FINE timber, but seasoned wood to hand: the joinery holds true and lifts to FINE.
        items.createCarriedItem(chronicle, "structural_timber", "Structural timber", now, "TEST_SEED", QualityGrade.FINE);
        items.createCarriedItem(chronicle, "seasoned_plank", "Seasoned plank", now, "TEST_SEED");
        String[] seasoned = items.executeProcess(chronicle, chunk, "cut_mortise", "cut a mortise", now);
        assertEquals("SUCCEEDED", seasoned[0], () -> "cutting a mortise with seasoned wood must succeed: " + seasoned[1]);
        assertEquals("FINE", newestBeamGrade(chronicle), "seasoned wood to hand lifts the same FINE timber to a FINE beam (#200/#203)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
