package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
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
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Toxic wildlife — some animals are not food (real-world simulation; the creature half of the poisonous-flora rule).
 * A fire salamander, toad, or great crested newt is genuinely toxic; harvesting one yields no edible meat — a
 * Chronicle recognises the flesh for poison and leaves it, exactly as eating a death-cap sickens. A non-toxic animal
 * still gives its meat.
 *
 * <p>Proven: harvesting a toxic carcass produces no raw game meat and reads as toxic; harvesting an ordinary carcass
 * gives meat as before. Skips gracefully without Docker.
 */
@SpringBootTest
class ToxicWildlifeIntegrationTest {

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
    @Autowired WildlifeEncounterService wildlife;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private int meat(UUID chronicle) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key='raw_game_meat' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, chronicle);
        return n == null ? 0 : n;
    }

    private void placeCarcass(UUID chunk, UUID pop, String species, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CARCASS',?,?)", id, species + " carcass", chunk);
        jdbc.update("INSERT INTO wildlife_carcass (object_id,source_population_id,species_key,remaining_meat_units,hide_available,killed_by_action_id,died_at) " +
                "VALUES (?,?,?,1,false,?,?)", id, pop, species, UUID.randomUUID(), Timestamp.from(now));
    }

    @Test
    void aToxicAnimalYieldsNoEdibleMeatButAnOrdinaryOneDoes() {
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
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Timestamp ts = Timestamp.from(now);

        // A population to hang the carcasses' provenance on (the carcass's own species is what matters).
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Ground',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Ground',100)", site, worldId, chunk);
        UUID pop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'fire_salamander','CARNIVORE','NOCTURNAL',1,3,'RESTING',?)", pop, site, ts);

        // A toxic fire salamander: worked over, but the flesh is poison and taken as nothing.
        placeCarcass(chunk, pop, "fire_salamander", now);
        WildlifeEncounterService.HarvestResult toxic = wildlife.harvest(chronicle, chunk, UUID.randomUUID(), now);
        assertEquals("SUCCEEDED", toxic.outcome(), () -> "harvesting must still resolve: " + toxic.narration());
        assertTrue(toxic.narration().toLowerCase(Locale.ROOT).contains("toxic"),
                () -> "a toxic animal's remains must read as toxic: " + toxic.narration());
        assertEquals(0, meat(chronicle), "a toxic animal must yield no edible meat");

        // An ordinary red deer: its meat is taken as before.
        placeCarcass(chunk, pop, "red_deer", now);
        WildlifeEncounterService.HarvestResult ordinary = wildlife.harvest(chronicle, chunk, UUID.randomUUID(), now);
        assertEquals("SUCCEEDED", ordinary.outcome(), () -> "harvesting the deer must succeed: " + ordinary.narration());
        assertEquals(1, meat(chronicle), "an ordinary animal must give its meat");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
