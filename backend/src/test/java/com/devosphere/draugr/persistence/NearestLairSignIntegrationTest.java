package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ExaminationService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What is underfoot cannot be masked by what is further off (#86).
 *
 * <p>The monster-sign query ordered {@code TREMOR_WARNING} ahead of distance. That reads well until two
 * territories overlap: a thunder lizard — the one creature in the catalogue that carries that mechanic — then
 * drowned out the thing whose ground the Chronicle was actually standing on, and what they were told about was
 * the further creature. Standing on a lair and being told about something two chunks away is not a perception
 * of this ground.
 *
 * <p>A thing the ground announces still comes before anything at the same remove — that is what being felt
 * through the ground means — it simply cannot mask what is underfoot. Skips without Docker.
 */
@SpringBootTest
class NearestLairSignIntegrationTest {

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
    @Autowired ExaminationService examination;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /** Put a monster lair of the given species on the chunk at these coordinates. */
    private void lair(UUID world, int x, int y, String species, Instant now) {
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE world_id=? AND grid_x=? AND grid_y=?", UUID.class, world, x, y);
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE',?,?)", site, species + " ground", chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'MONSTER',?,20)",
            site, world, chunk, species + " ground");
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
            "VALUES (?,?,?,'CARNIVORE','NOCTURNAL',1,2,'RESTING',?)", UUID.randomUUID(), site, species, Timestamp.from(now));
    }

    @Test
    void aThunderLizardTwoChunksOffDoesNotMaskWhatIsUnderfoot() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        Instant now = Instant.now();

        // Two grassland chunks two apart on the same row, with nothing else of ours between them.
        var row = jdbc.queryForMap(
            "SELECT a.world_id, a.grid_x, a.grid_y FROM world_chunk a JOIN world_chunk b " +
            "  ON b.world_id=a.world_id AND b.grid_y=a.grid_y AND b.grid_x=a.grid_x+2 AND b.biome='GRASSLAND' " +
            "WHERE a.biome='GRASSLAND' ORDER BY a.grid_y, a.grid_x LIMIT 1");
        UUID world = (UUID) row.get("world_id");
        int x = ((Number) row.get("grid_x")).intValue(), y = ((Number) row.get("grid_y")).intValue();

        // Clear any lair the generated world already put within reach, so only the two below are in play.
        jdbc.update("DELETE FROM wildlife_population wp USING ecology_site es, world_chunk c " +
            "WHERE wp.site_id=es.id AND es.chunk_id=c.id AND es.site_category='MONSTER' AND c.world_id=? " +
            "AND abs(c.grid_x-?)<=4 AND abs(c.grid_y-?)<=4", world, x, y);

        // Underfoot: a jackal pack. Two chunks off: the thunder lizard, whose reach covers this ground.
        lair(world, x, y, "cinder_jackal_pack", now);
        lair(world, x + 2, y, "thunder_lizard", now);

        UUID here = jdbc.queryForObject("SELECT id FROM world_chunk WHERE world_id=? AND grid_x=? AND grid_y=?", UUID.class, world, x, y);
        assertNotNull(here);
        String read = examination.presentLife(here, 1.0).toLowerCase(Locale.ROOT);

        assertTrue(read.contains("cinder jackal pack"),
            () -> "standing on the jackals' own ground, that is what a careful eye must read: " + read);
        assertTrue(!read.contains("thunder lizard"),
            () -> "the lizard two chunks off must not be what this ground is reported as holding: " + read);

        // And it is still felt where nothing is nearer: on its own ground it is what you read.
        UUID there = jdbc.queryForObject("SELECT id FROM world_chunk WHERE world_id=? AND grid_x=? AND grid_y=?", UUID.class, world, x + 2, y);
        String far = examination.presentLife(there, 1.0).toLowerCase(Locale.ROOT);
        assertTrue(far.contains("thunder lizard"),
            () -> "on its own ground the lizard must still be felt — the fix is about masking, not about silencing it: " + far);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
