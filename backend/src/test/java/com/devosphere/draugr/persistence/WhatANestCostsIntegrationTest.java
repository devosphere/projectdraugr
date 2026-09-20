package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
import com.devosphere.draugr.ecology.WildlifeSimulationService;
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
 * What a nest costs (#122).
 *
 * <p>A bird egg could only be had by killing the bird, which is backwards. Robbing a nest is the other way, and it
 * carries the rules the ticket asks of non-lethal taking: a season, a method, and a consequence. The consequence is
 * that a clutch taken is this year's young taken — the birds stand where they stood, and what would have been added
 * is not added. Skips without Docker.
 */
@SpringBootTest
class WhatANestCostsIntegrationTest {

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
    @Autowired WildlifeEncounterService encounters;
    @Autowired WildlifeSimulationService wildlife;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void at(Instant when) {
        jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", Timestamp.from(when));
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update=?, hours_without_food=0, hours_without_water=0, sleep_debt_hours=0", Timestamp.from(when));
    }

    private UUID birds(UUID chunk, String species, int count) {
        Timestamp ts = Timestamp.from(Instant.now());
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        UUID site = UUID.randomUUID(), pop = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Nesting ground',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Nesting ground',40)", site, worldId, chunk);
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
            "VALUES (?,?,?,'OMNIVORE','DIURNAL',?,12,'FORAGING',?)", pop, site, species, count, ts);
        return pop;
    }

    private int eggs(UUID chronicle) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key='bird_egg'", Integer.class, chronicle);
    }

    private int flock(UUID pop) {
        return jdbc.queryForObject("SELECT population_count FROM wildlife_population WHERE id=?", Integer.class, pop);
    }

    @Test
    void aClutchTakenIsThisYearsYoungTaken() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, " +
            "maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        // Out of season first: in October the nests are old cups of grass.
        Instant autumn = Instant.parse("2031-10-12T10:00:00Z");
        at(autumn);
        UUID fowl = birds(chunk, "marsh_fowl", 4);
        var cold = actions.resolve("rob the nest for eggs");
        assertEquals("RAID_NEST", cold.intent(), cold::perception);
        assertEquals("FAILED", cold.outcome(), cold::perception);
        assertEquals(0, eggs(chronicle), "nothing was taken out of season");

        // In the laying season there is a clutch, and it comes away in the hand.
        Instant spring = Instant.parse("2031-05-12T10:00:00Z");
        at(spring);
        var robbed = actions.resolve("rob the nest for eggs");
        assertEquals("SUCCEEDED", robbed.outcome(), robbed::perception);
        int took = eggs(chronicle);
        assertTrue(took >= 2, () -> "a clutch is more than one egg: " + took);
        assertEquals(4, flock(fowl), "the birds standing there are the birds that were standing there");
        assertNotNull(jdbc.queryForObject("SELECT clutch_taken_at FROM wildlife_population WHERE id=?", Timestamp.class, fowl));

        // The same nest again is the nest you emptied.
        var again = actions.resolve("rob the nest for eggs");
        assertEquals("FAILED", again.outcome(), again::perception);
        assertEquals(took, eggs(chronicle), "and nothing more came out of it");

        // And what it costs: through the breeding season that follows, this flock does not grow.
        at(spring.plus(Duration.ofDays(20)));
        wildlife.advanceTo(spring.plus(Duration.ofDays(20)));
        assertEquals(4, flock(fowl), "the young that would have come off that nest do not come");

        // A month on, they breed again — the loss was a year's young, not the end of them.
        at(spring.plus(Duration.ofDays(WildlifeEncounterService.CLUTCH_COSTS_DAYS + 5)));
        wildlife.advanceTo(spring.plus(Duration.ofDays(WildlifeEncounterService.CLUTCH_COSTS_DAYS + 5)));
        assertTrue(flock(fowl) >= 4, "the flock is not diminished by it either");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
