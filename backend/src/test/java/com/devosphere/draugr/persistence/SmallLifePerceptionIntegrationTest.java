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

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Looking hard must find the small things (#37). Birds and insects were drawn from the same naming budget as deer
 * and boar and lost it every time — anything larger outranked them, and the visibility model put a TINY creature
 * beyond an ordinary eye outright — so a Chronicle could stand in a meadow in summer, look intently, and be told
 * about nothing smaller than a hare. The registry held them all along.
 *
 * <p>Proves that deliberate attention now names birds and invertebrates, that casual attention does not (they are
 * easy to overlook, which is the point), and that a given place keeps its own cast rather than every chunk in a
 * biome reporting the same alphabetically-first creature. Skips without Docker.
 */
@SpringBootTest
class SmallLifePerceptionIntegrationTest {

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

    /** Does the account name any of the creatures the registry says live in this biome, of the given kingdoms? */
    private boolean namesAnyOf(String account, String biome, String kingdoms) {
        List<String> keys = jdbc.queryForList(
            "SELECT species_key FROM wildlife_species WHERE kingdom_class IN (" + kingdoms + ") AND biome_affinity ILIKE ?",
            String.class, "%" + biome + "%");
        assertFalse(keys.isEmpty(), "this test needs a biome that actually holds " + kingdoms + " — " + biome + " holds none");
        String lower = account.toLowerCase(Locale.ROOT);
        return keys.stream().anyMatch(k -> lower.contains(k.replace('_', ' ')));
    }

    @Test
    void deliberateAttentionFindsTheBirdsAndTheInsects() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }

        String biome = "GRASSLAND";
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome=? ORDER BY grid_y, grid_x LIMIT 1", UUID.class, biome);
        assertNotNull(chunk, "the approved world must contain a " + biome + " chunk");

        String looked = examination.presentLife(chunk, 1.0);
        assertNotNull(looked);
        assertTrue(namesAnyOf(looked, biome, "'AVES'"),
            "a Chronicle looking hard at open grassland must be told about the birds over it — got: " + looked);
        assertTrue(namesAnyOf(looked, biome, "'INSECTA','ARACHNIDA','GASTROPODA','ANNELIDA','BIVALVIA'"),
            "and about the small life in it, which is exactly what #37 says is never returned — got: " + looked);

        // Small life is easy to overlook. A glance must not hand it over — attention has to be spent.
        String glanced = examination.presentLife(chunk, 0.3);
        assertFalse(namesAnyOf(glanced, biome, "'INSECTA','ARACHNIDA','GASTROPODA','ANNELIDA','BIVALVIA'"),
            "a passing glance should not pick out insects; noticing them is the reward for looking — got: " + glanced);

        // The same ground reads the same way twice: this is perception, not a dice roll.
        assertEquals(looked, examination.presentLife(chunk, 1.0), "looking twice at unchanged ground must report the same life");

        // And two different places in one biome must not read identically, which is what an alphabetical pick gave.
        List<UUID> others = jdbc.queryForList(
            "SELECT id FROM world_chunk WHERE biome=? AND id<>? ORDER BY grid_y, grid_x LIMIT 12", UUID.class, biome, chunk);
        assertFalse(others.isEmpty(), "this test needs more than one chunk of " + biome);
        assertTrue(others.stream().anyMatch(o -> !examination.presentLife(o, 1.0).equals(looked)),
            "every stretch of " + biome + " reported exactly the same life — the cast must vary by place");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
