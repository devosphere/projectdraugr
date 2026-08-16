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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fire-brand deterrent regression (M1 #126, EPIC #123). The ecology's fire-fear already holds a predator off
 * ground where a fire burns, but a Chronicle carrying a lit brand — a resin torch it can raise and wave —
 * read to the ambush check as unarmed: fire in the hand deterred nothing. Now a carried brand cuts the passive
 * ambush chance, so raising fire against a stalking predator is a real, mechanical deterrent.
 *
 * <p>Proven deterministically over a fixed set of action ids: because the brand only lowers the chance, the
 * ambushes that land WITH a brand are a strict subset of those without one, so across the same ids a brand
 * yields strictly fewer ambushes while the threat itself is unchanged. Skips gracefully without Docker.
 */
@SpringBootTest
class BrandDeterrentIntegrationTest {

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

    /** Count how many of a fixed set of encounters land, resetting the pack to HUNTING before each so every
     *  roll faces the same threat (an ambush otherwise drops the pack to ALERT and skews the rest). The same
     *  id set is used for both cohorts, and passiveEncounter's roll takes the high bits of the id's hash
     *  (action.hashCode() >>> 8), so the ids must vary in their high bits — seeded random longs do. */
    private int ambushes(UUID chronicle, UUID chunk, UUID pack, Instant now, java.util.List<UUID> actionIds) {
        int hits = 0;
        for (UUID action : actionIds) {
            jdbc.update("UPDATE wildlife_population SET behavior_state='HUNTING' WHERE id=?", pack);
            if (wildlife.passiveEncounter(chronicle, chunk, action, now, "LOW") != null) hits++;
        }
        return hits;
    }

    @Test
    void aCarriedFireBrandDetersMoreAmbushesThanNone() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject(
                "SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        Instant now = ticks.current().simulatedAt();
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Timestamp ts = Timestamp.from(now);

        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Dusk prowler territory',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Dusk prowler territory',400)", site, worldId, chunk);
        UUID pack = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'dire_wolf','CARNIVORE','DIURNAL',3,5,'HUNTING',?)", pack, site, ts);

        // A fixed, well-spread set of encounters (seeded, so it is the same every run and its rolls span
        // the whole range), used identically for both cohorts.
        java.util.Random rnd = new java.util.Random(42);
        java.util.List<UUID> actionIds = new java.util.ArrayList<>();
        for (int i = 0; i < 160; i++) actionIds.add(new UUID(rnd.nextLong(), rnd.nextLong()));

        // Unarmed of fire: a baseline of how often the hunt closes across those encounters.
        int withoutBrand = ambushes(chronicle, chunk, pack, now, actionIds);
        assertTrue(withoutBrand > 0, "a HUNTING predator must actually reach an unbranded Chronicle sometimes (else the test proves nothing)");

        // Brand in hand: the same encounters, now with a resin torch to raise against the pack.
        items.createCarriedItem(chronicle, "resin_torch", "Resin torch", now, "TEST_SEED");
        int withBrand = ambushes(chronicle, chunk, pack, now, actionIds);

        assertTrue(withBrand < withoutBrand,
                () -> "a carried fire-brand must deter ambushes the same threat would otherwise land (with=" + withBrand + ", without=" + withoutBrand + ") (#126)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
