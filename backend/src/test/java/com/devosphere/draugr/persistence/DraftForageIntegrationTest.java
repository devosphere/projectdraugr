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

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Draft forage (EPIC #100 / #104). A kept beast must be fed: left hungry it hauls poorly, just as a spent one does.
 * On grassland it grazes and stays fed; on bare or wooded ground its keeper must bring it cut fodder. Proven end to
 * end: a starving beast hauls nothing; a feed of fodder restores it; and grazing on pasture eases its hunger over a
 * turn of the world. Skips without Docker.
 */
@SpringBootTest
class DraftForageIntegrationTest {

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

    private void tameAnAurochs(UUID chronicle, Instant now) {
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
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

    private int hunger(UUID chronicle) {
        Integer h = jdbc.queryForObject("SELECT draft_hunger FROM wildlife_bond WHERE chronicle_id=?", Integer.class, chronicle);
        return h == null ? 0 : h;
    }

    private void setBiome(UUID chronicle, String biome) {
        jdbc.update("UPDATE world_chunk SET biome=? WHERE id=(SELECT current_location_id FROM world_object WHERE id=?)", biome, chronicle);
    }

    @Test
    void aStarvingBeastHaulsNothingFodderRestoresItAndPastureGrazesItFed() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=500000, direct_bulk_ml=500000, maximum_single_lift_grams=500000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();
        int base = 500000;
        setBiome(chronicle, "TEMPERATE_FOREST"); // wooded ground — the beast will not graze here

        items.createCarriedItem(chronicle, "travois", "Travois", now, "TEST");
        tameAnAurochs(chronicle, now);

        // Starve it: a hungry beast hauls as poorly as a spent one.
        jdbc.update("UPDATE wildlife_bond SET draft_hunger=100 WHERE chronicle_id=?", chronicle);
        assertEquals(base, items.sustainedMassCapacity(chronicle), "a starving beast hauls nothing");

        // Feed it a bundle of fodder — the hunger's edge comes off and it hauls again.
        items.createCarriedItem(chronicle, "dry_grass_bundle", "Dry grass bundle", now, "TEST");
        ChronicleActionService.ActionResult feed = actions.resolve("feed the animals");
        assertEquals("SUCCEEDED", feed.outcome(), () -> "feeding fodder must succeed: " + feed.perception());
        assertEquals(base + 150000, items.sustainedMassCapacity(chronicle), "a fed beast hauls again (hunger 100 - 60 feed = 40)");
        Integer fodderLeft = jdbc.queryForObject("SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id WHERE i.item_key='dry_grass_bundle' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, chronicle);
        assertEquals(0, fodderLeft, "the fodder is consumed by the feed");

        // Put it on pasture: grazing eases its hunger over a turn of the world, no fodder needed.
        setBiome(chronicle, "GRASSLAND");
        int before = hunger(chronicle);
        ticks.advanceBy(Duration.ofHours(1));
        assertTrue(hunger(chronicle) < before, () -> "grazing on pasture must ease hunger (before=" + before + ", after=" + hunger(chronicle) + ")");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
