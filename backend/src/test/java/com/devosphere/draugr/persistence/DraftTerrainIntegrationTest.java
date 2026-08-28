package com.devosphere.draugr.persistence;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Draft terrain (EPIC #100 / #103). Hauling a loaded team over rough ground strains it half again as hard as over
 * open country — the reason a keeper routes a loaded team over pasture and plain. Proven on the haul: the same work
 * done crossing forest tires the beast more, and it hauls less afterward, than the same work over grassland. Skips
 * without Docker.
 */
@SpringBootTest
class DraftTerrainIntegrationTest {

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

    private void tameAnAurochs(UUID chronicle, Instant now) {
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Range',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Range',20)", site, world, chunk);
        UUID pop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'aurochs','HERBIVORE','DIURNAL',2,4,'FORAGING',?)", pop, site, Timestamp.from(now));
        jdbc.update("INSERT INTO wildlife_bond (chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at) " +
                "VALUES (?,?,'TAMED',100,10,?)", chronicle, pop, Timestamp.from(now));
    }

    private int haulAfterWorkOn(UUID chronicle, String biome, int bouts) {
        jdbc.update("UPDATE world_chunk SET biome=? WHERE id=(SELECT current_location_id FROM world_object WHERE id=?)", biome, chronicle);
        jdbc.update("UPDATE wildlife_bond SET draft_fatigue=0, draft_conditioning=0, draft_hunger=0 WHERE chronicle_id=?", chronicle);
        for (int i = 0; i < bouts; i++) items.workDraftBeasts(chronicle);
        jdbc.update("UPDATE wildlife_bond SET draft_conditioning=0 WHERE chronicle_id=?", chronicle);
        return items.sustainedMassCapacity(chronicle);
    }

    @Test
    void haulingOverRoughGroundTiresATeamMoreThanOverOpenCountry() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=500000, direct_bulk_ml=500000, maximum_single_lift_grams=500000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();

        items.createCarriedItem(chronicle, "travois", "Travois", now, "TEST");
        tameAnAurochs(chronicle, now);

        // Two bouts over open grassland tire it 40 (20 each) — it hauls 60% of 250 kg.
        int easy = haulAfterWorkOn(chronicle, "GRASSLAND", 2);
        assertEquals(500000 + 150000, easy, "over open ground a bout tires the team 20");

        // The same two bouts crossing forest tire it 60 (30 each, half again as hard) — it hauls less.
        int rough = haulAfterWorkOn(chronicle, "TEMPERATE_FOREST", 2);
        assertEquals(500000 + 100000, rough, "over rough ground a bout tires the team 30");
        assertTrue(rough < easy, "rough terrain tires a hauling team more than open country");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
