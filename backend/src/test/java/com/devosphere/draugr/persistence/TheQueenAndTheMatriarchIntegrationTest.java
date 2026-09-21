package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
import com.devosphere.draugr.ecology.WildlifeSimulationService;
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

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The queen and the matriarch (#122, #121).
 *
 * <p>The Tier II rule is that taking a queen, a matriarch, a nest or a clutch changes population, range and future
 * material availability. A queen is the clearest case: a colony without one is not depleted, it is finished — and
 * only a colony that has a queen can lose one, which is a fact about the creature and not about the act. A herd
 * does not hunt, so it is never in the states a pack is in when it loses its leader; what a herd does is stand, and
 * the animal it stands behind is the one that decides where it goes. Skips without Docker.
 */
@SpringBootTest
class TheQueenAndTheMatriarchIntegrationTest {

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
    @Autowired WildlifeEncounterService encounters;
    @Autowired WildlifeSimulationService wildlife;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void at(Instant when) {
        jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", Timestamp.from(when));
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update=?, hours_without_food=0, hours_without_water=0, sleep_debt_hours=0", Timestamp.from(when));
    }

    private UUID herd(UUID chunk, String species, int count, Instant when) {
        Timestamp ts = Timestamp.from(when);
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        UUID site = UUID.randomUUID(), pop = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Grazing ground',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Grazing ground',40)", site, worldId, chunk);
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
            "VALUES (?,?,?,'HERBIVORE','DIURNAL',?,12,'ALERT',?)", pop, site, species, count, ts);
        return pop;
    }

    @Test
    void takingTheOneAGroupTurnsOnEndsWhatItHeldTogether() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        Instant summerNoon = Instant.parse("2031-07-12T12:00:00Z");
        at(summerNoon);
        jdbc.update("UPDATE world_chunk SET biome='TEMPERATE_FOREST' WHERE id=?", chunk);

        // The queen: only a colony that has one can lose one.
        assertTrue(jdbc.queryForObject("SELECT has_a_queen FROM insect_colony_kind WHERE colony_kind='honeybee_hive'", Boolean.class));
        assertTrue(!jdbc.queryForObject("SELECT has_a_queen FROM insect_colony_kind WHERE colony_kind='earthworm_patch'", Boolean.class));

        var cut = items.raidHive(chronicle, chunk, "cut out the brood comb and the queen from the honeybee hive", summerNoon);
        assertTrue(cut.narration().contains("queen") || cut.narration().contains("colony of equals"), cut::narration);
        Integer health = jdbc.query("SELECT health FROM insect_colony WHERE chunk_id=? AND colony_kind='honeybee_hive'",
            rs -> rs.next() ? rs.getInt(1) : null, chunk);
        if (health != null) {
            assertEquals(0, (int) health, "a colony without its queen is finished, not depleted");
            assertNotNull(jdbc.queryForObject("SELECT queen_taken_at FROM insect_colony WHERE chunk_id=? AND colony_kind='honeybee_hive'",
                Timestamp.class, chunk), "and the world records that it was ended rather than worked out");
            var again = items.raidHive(chronicle, chunk, "raid the honeybee hive", summerNoon.plus(Duration.ofDays(60)));
            assertEquals("FAILED", again.outcome(), () -> "two months on there is still nothing there: " + again.narration());
        }

        // The matriarch: a herd standing its ground is standing behind somebody.
        jdbc.update("UPDATE wildlife_population wp SET population_count=0 FROM ecology_site es WHERE es.id=wp.site_id AND es.chunk_id=?", chunk);
        UUID deer = herd(chunk, "red_deer", 6, summerNoon);
        jdbc.update("UPDATE chronicle_physiology SET energy_level=100, injury_severity=0, pain_level=0 WHERE chronicle_id=?", chronicle);
        var killed = encounters.confront(chronicle, chunk, UUID.randomUUID(), summerNoon, 500);
        assertEquals("SUCCEEDED", killed.outcome(), killed::narration);
        assertEquals("SCATTERED", jdbc.queryForObject("SELECT behavior_state FROM wildlife_population WHERE id=?", String.class, deer),
            "the herd loses the one it stands behind");
        assertNotNull(jdbc.queryForObject("SELECT leader_lost_at FROM wildlife_population WHERE id=?", Timestamp.class, deer));

        // And neither a leaderless herd nor a leaderless pack breeds until another comes forward.
        int after = jdbc.queryForObject("SELECT population_count FROM wildlife_population WHERE id=?", Integer.class, deer);
        at(summerNoon.plus(Duration.ofDays(5)));
        wildlife.advanceTo(summerNoon.plus(Duration.ofDays(5)));
        assertEquals(after, (int) jdbc.queryForObject("SELECT population_count FROM wildlife_population WHERE id=?", Integer.class, deer),
            "what is missing is the thing that makes them a group rather than a crowd");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
