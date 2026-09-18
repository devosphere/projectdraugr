package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A carcass is more than meat (#155/#74, V343).
 *
 * <p>Twelve mammals from a goat's size to a buffalo's had no drops, so once the meat was off they gave only the
 * generic hide harvest() keeps for uncatalogued species: no sinew, no bone, no fat. This takes five ox carcasses apart
 * through the real harvest boundary. Each part is a roll (sinew and bone at 0.85), so five carcasses put the chance of
 * seeing neither of either below one in six thousand; one carcass would be a coin toss worth of flake. Skips without
 * Docker.
 */
@SpringBootTest
class ACarcassIsMoreThanMeatIntegrationTest {

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
    @Autowired WildlifeEncounterService encounters;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private static final int CARCASSES = 5;

    private int owned(UUID chronicle, String key) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key=? AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, key, chronicle);
        return n == null ? 0 : n;
    }

    @Test
    void anOxComesApartIntoHideSinewAndBone() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, " +
            "maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();
        Timestamp ts = Timestamp.from(now);

        // No other carcass here to be worked first: harvest() takes the oldest on the ground.
        jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED', destroyed_at=?, destroyed_location_id=current_location_id, " +
            "destroyed_cause='ROTTED', current_location_id=NULL WHERE object_type='CARCASS' AND current_location_id=?", ts, chunk);

        // A kept herd for the carcasses to have come from, made here rather than borrowed from the seeded world.
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Pasture',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Pasture',50)", site, worldId, chunk);
        UUID herd = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
            "VALUES (?,?,'ox','HERBIVORE','DIURNAL',6,8,'FORAGING',?)", herd, site, ts);

        // The meat already off each, so every harvest goes straight to what the carcass is besides meat.
        for (int i = 0; i < CARCASSES; i++) {
            UUID carcass = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CARCASS','Ox carcass',?)", carcass, chunk);
            jdbc.update("INSERT INTO wildlife_carcass (object_id,source_population_id,species_key,remaining_meat_units,hide_available,killed_by_action_id,died_at) " +
                "VALUES (?,?,'ox',0,true,?,?)", carcass, herd, UUID.randomUUID(), Timestamp.from(now.minusSeconds(CARCASSES - i)));
        }

        for (int i = 0; i < CARCASSES; i++) {
            var taken = encounters.harvest(chronicle, chunk, UUID.randomUUID(), now);
            assertEquals("SUCCEEDED", taken.outcome(), () -> "each ox must be worked: " + taken.narration());
        }

        assertTrue(owned(chronicle, "animal_hide") >= CARCASSES, "every ox gives its hide");
        assertTrue(owned(chronicle, "animal_sinew") > 0, "an ox must give sinew — a bowstring, a sewing thread");
        assertTrue(owned(chronicle, "animal_bone") > 0, "an ox must give bone — an awl, a needle, a fish hook");

        // The data contract, for every animal this covers: a goat's size or more comes apart into more than meat.
        List<String> meatOnly = jdbc.queryForList(
            "SELECT species_key FROM wildlife_species s WHERE kingdom_class='MAMMALIA' AND size_tier IN ('MEDIUM','LARGE','HUGE') " +
            "AND NOT EXISTS (SELECT 1 FROM wildlife_drop d WHERE d.species_key=s.species_key) ORDER BY 1", String.class);
        assertTrue(meatOnly.isEmpty(), () -> "these carcasses still give nothing but meat: " + meatOnly);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
