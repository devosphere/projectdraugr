package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ExaminationService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A monster lair must contain a monster (#86).
 *
 * <p>The world seeds ten MONSTER sites — a bog warden's lair, a mire hydra's nest, an ash hound den — and every
 * one of them was empty. Population seeding filtered on {@code site_category = 'WILDLIFE'}, so no monster
 * population was ever created anywhere, and every piece of code written to handle one was dead by construction:
 * the boundary scout that warns of a lair joins through {@code wildlife_population} and found nothing; the pack
 * hunt keyed on {@code site_category='MONSTER'} could never fire; the disturbance responses for monster ground
 * never ran; and all thirty-nine {@code monster_profile} rows were unreachable as living creatures. The world had
 * monsters in its catalogue, marks for them on its map, and none in it.
 *
 * <p>Proves lairs are inhabited, that what inhabits one belongs to that ground, that its territory is felt across
 * its own sight radius and no further, and that a monster is still never reported as ordinary fauna. Skips
 * without Docker.
 */
@SpringBootTest
class MonsterLairsIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired com.devosphere.draugr.simulation.SimulationTickService ticks;
    @Autowired JdbcTemplate jdbc;

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        assertNotNull(chronicles.awaken());
        // Populations are seeded into sites by the world TICK, and awakening does not run one — every other test
        // that needs wildlife gets a tick incidentally by performing an action. This one asks for it outright.
        ticks.advanceBy(java.time.Duration.ofMinutes(1));
    }

    @Test
    void aLairHoldsSomethingThatBelongsThere() {
        world();

        List<Map<String, Object>> lairs = jdbc.queryForList(
            "SELECT es.site_kind, c.biome, wp.species_key, wp.population_count, mp.biome_affinity, mp.sight_radius " +
            "FROM ecology_site es JOIN world_chunk c ON c.id = es.chunk_id " +
            "LEFT JOIN wildlife_population wp ON wp.site_id = es.id " +
            "LEFT JOIN monster_profile mp ON mp.species_key = wp.species_key " +
            "WHERE es.site_category = 'MONSTER' ORDER BY es.site_kind");
        assertFalse(lairs.isEmpty(), "the world must mark monster ground at all");

        int inhabited = 0;
        for (Map<String, Object> lair : lairs) {
            String biome = (String) lair.get("biome");
            String species = (String) lair.get("species_key");
            // A lair on ground the catalogue has no monster for is deliberately left empty rather than filled
            // with something that does not belong there.
            Integer possible = jdbc.queryForObject(
                "SELECT COUNT(*) FROM monster_profile WHERE biome_affinity ILIKE ?", Integer.class, "%" + biome + "%");
            assertNotNull(possible);
            if (possible == 0) continue;

            assertNotNull(species, "a lair on " + biome + " ground, where the catalogue has " + possible
                + " monsters that could keep it, must not be empty: " + lair.get("site_kind"));
            assertTrue(((String) lair.get("biome_affinity")).contains(biome),
                species + " does not belong on " + biome + " ground — a wyvern must never be seeded in a marsh");
            assertTrue(((Number) lair.get("population_count")).intValue() > 0, "an inhabited lair holds something living");
            assertTrue(((Number) lair.get("population_count")).intValue() <= 2, "a monster is not a herd");
            inhabited++;
        }
        assertTrue(inhabited > 0, "at least one lair in the world must actually be kept by something");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void aTerritoryIsFeltAcrossItsOwnReachAndNoFurther() {
        world();

        Map<String, Object> lair = jdbc.queryForMap(
            "SELECT c.world_id, c.grid_x, c.grid_y, wp.species_key, GREATEST(1, mp.sight_radius) AS reach " +
            "FROM ecology_site es JOIN world_chunk c ON c.id = es.chunk_id " +
            "JOIN wildlife_population wp ON wp.site_id = es.id AND wp.population_count > 0 " +
            "JOIN monster_profile mp ON mp.species_key = wp.species_key " +
            "WHERE es.site_category='MONSTER' ORDER BY mp.sight_radius DESC, c.grid_y, c.grid_x LIMIT 1");
        UUID world = (UUID) lair.get("world_id");
        int lx = ((Number) lair.get("grid_x")).intValue(), ly = ((Number) lair.get("grid_y")).intValue();
        int reach = ((Number) lair.get("reach")).intValue();
        String species = ((String) lair.get("species_key")).replace('_', ' ');

        // On its own ground, a careful reader finds the sign.
        UUID onIt = jdbc.queryForObject("SELECT id FROM world_chunk WHERE world_id=? AND grid_x=? AND grid_y=?", UUID.class, world, lx, ly);
        assertTrue(examination.presentLife(onIt, 1.0).toLowerCase(Locale.ROOT).contains(species),
            "standing on a monster's own ground, a careful eye must read that something keeps it");

        // Far outside its reach there is nothing of it to read, because there is nothing of it there.
        UUID away = jdbc.queryForObject(
            "SELECT id FROM world_chunk WHERE world_id=? AND (abs(grid_x-?) > ? + 2 OR abs(grid_y-?) > ? + 2) " +
            "ORDER BY grid_y, grid_x LIMIT 1", UUID.class, world, lx, reach, ly, reach);
        assertNotNull(away, "the map must be big enough to stand well outside one territory");
        assertFalse(examination.presentLife(away, 1.0).toLowerCase(Locale.ROOT).contains(species),
            "a monster's sign must not appear on ground it has never crossed — its territory is a place, not a biome-wide rumour");
    }

    /** Monsters belong to the sign channel, where the rule against handing the player a hint is enforced. */
    @Test
    void aMonsterIsNeverReportedAsOrdinaryFauna() {
        world();
        UUID lairChunk = jdbc.queryForObject(
            "SELECT c.id FROM ecology_site es JOIN world_chunk c ON c.id = es.chunk_id " +
            "JOIN wildlife_population wp ON wp.site_id = es.id AND wp.population_count > 0 " +
            "WHERE es.site_category='MONSTER' ORDER BY c.grid_y, c.grid_x LIMIT 1", UUID.class);
        assertNotNull(lairChunk);
        String said = examination.presentLife(lairChunk, 1.0);
        assertFalse(said.contains("grazes the open ground") || said.contains("forages nearby"),
            "a monster must never be narrated as a grazing or foraging animal: " + said);
    }
}
