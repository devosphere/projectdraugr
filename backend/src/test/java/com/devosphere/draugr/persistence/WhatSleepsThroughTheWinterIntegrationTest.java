package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ExaminationService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What sleeps through the winter (#161, V312).
 *
 * <p>Plants have answered to the season since V39 and insects since their colonies were catalogued; animals never
 * did. A brown bear could be seen, tracked and come upon on a January morning. {@code wildlife_species.dormant_months}
 * gives hibernators and brumators their winter, and {@code wildlife_abroad(species)} is the one rule every wild-facing
 * query now calls — reading the world clock itself, so no Java path can disagree with another about what month it is.
 *
 * <p>The test moves the world clock between January and July on the same ground and the same seeded bear, so the only
 * thing that changes is the season. It restores the clock afterwards, because sibling tests share the database.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class WhatSleepsThroughTheWinterIntegrationTest {

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
    @Autowired ExaminationService examination;
    @Autowired WildlifeEncounterService wildlife;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private static final Instant MID_JANUARY = Instant.parse("2026-01-15T12:00:00Z");
    private static final Instant MID_JULY = Instant.parse("2026-07-15T12:00:00Z");

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    private void setClock(Instant at) {
        jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", Timestamp.from(at));
    }

    private boolean abroad(String species) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT wildlife_abroad(?)", Boolean.class, species));
    }

    /** A bear resident on this ground, large enough that no eye misses it when it is out. */
    private UUID seedBear(UUID chunk) {
        Timestamp ts = Timestamp.from(Instant.now());
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        UUID site = UUID.randomUUID(), pop = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Bear den',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Bear den',400)", site, worldId, chunk);
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'brown_bear','OMNIVORE','DIURNAL',50,60,'FORAGING',?)", pop, site, ts);
        return pop;
    }

    /** The rule itself, by month. The whole point is that one definition answers for everything. */
    @Test
    void theRuleAnswersByTheWorldClock() {
        Timestamp original = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class);
        try {
            setClock(MID_JANUARY);
            assertFalse(abroad("brown_bear"), "a bear is denned up in January");
            assertFalse(abroad("common_adder"), "an adder is brumating in January");
            assertFalse(abroad("hedgehog"), "a hedgehog is hibernating in January");
            assertTrue(abroad("gray_wolf"), "a wolf is abroad all winter — it is the season wolves hunt hardest");
            assertTrue(abroad("european_badger"), "a badger goes torpid in hard spells but is not absent, and this does not pretend otherwise");
            assertTrue(abroad("no_such_species"), "an uncatalogued species is never hidden by a rule it has no row for");

            setClock(MID_JULY);
            assertTrue(abroad("brown_bear"), "a bear is abroad in July");
            assertTrue(abroad("common_adder"), "an adder basks in July");
        } finally {
            jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", original);
        }
    }

    /**
     * Same ground, same bear, two months. Looking and tracking are two separate readers of "is the animal here", and
     * the rule is only honest if both obey it: a survey that shows no bear followed by a track that finds one would be
     * the world contradicting itself.
     */
    @Test
    void aDennedBearIsNeitherSeenNorTrackedAndIsBothInSummer() {
        world();
        var summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        Timestamp original = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class);
        UUID bear = seedBear(chunk);
        try {
            setClock(MID_JANUARY);
            String winterLook = examination.presentLife(chunk, 1.0).toLowerCase();
            assertFalse(winterLook.contains("brown bear"), () -> "a denned bear must not be seen: " + winterLook);
            var winterTrack = wildlife.track(chronicle, chunk, UUID.randomUUID(), MID_JANUARY, "HIGH", 1.0);
            assertFalse(winterTrack.narration().toLowerCase().contains("brown bear"),
                () -> "a denned bear leaves no fresh sign to follow: " + winterTrack.narration());
            assertEquals(0, (int) jdbc.queryForObject(
                "SELECT COUNT(*) FROM wildlife_population wp JOIN ecology_site es ON es.id=wp.site_id " +
                "WHERE es.chunk_id=? AND wp.population_count>0 AND wildlife_abroad(wp.species_key) AND wp.id=?",
                Integer.class, chunk, bear), "the seeded bear is exactly what the rule hides");

            setClock(MID_JULY);
            String summerLook = examination.presentLife(chunk, 1.0).toLowerCase();
            assertTrue(summerLook.contains("brown bear"), () -> "the same bear on the same ground is plain to see in July: " + summerLook);
        } finally {
            jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", original);
            jdbc.update("UPDATE wildlife_population SET population_count=0 WHERE id=?", bear);
        }

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** No draft or working species sleeps: a keeper's harnessed animal is never caught by a rule written for the wild. */
    @Test
    void noWorkingAnimalHibernates() {
        Integer drafted = jdbc.queryForObject(
            "SELECT COUNT(*) FROM draft_species ds JOIN wildlife_species ws ON ws.species_key=ds.species_key WHERE ws.dormant_months IS NOT NULL",
            Integer.class);
        assertEquals(0, drafted, "a draft species with a dormancy would go to sleep in harness");
    }
}
