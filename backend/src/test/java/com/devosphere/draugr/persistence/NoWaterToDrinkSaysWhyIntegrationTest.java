package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.FreshWater;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No water fit to drink says why (#30).
 *
 * <p>The failure used to be one line for everywhere, "only dry ground that gives nothing back". On a shore that is
 * false, because the reason there is nothing to drink is that the water is the sea's, and in a downpour it is false
 * as well. This stands a Chronicle with nothing carried on a shore with no fresh water, then on dry open ground in
 * the rain, and reads what each is told. Skips without Docker.
 */
@SpringBootTest
class NoWaterToDrinkSaysWhyIntegrationTest {

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
    @Autowired JdbcTemplate jdbc;

    /** A chunk of this biome with no fresh water on it, or null if the world made none. */
    private UUID dryChunk(UUID worldId, String biome) {
        List<UUID> found = jdbc.queryForList(
            "SELECT c.id FROM world_chunk c WHERE c.world_id=? AND c.biome=? " +
            "AND NOT EXISTS (SELECT 1 FROM ecology_site s WHERE s.chunk_id=c.id AND (" + FreshWater.sites("s") + ")) " +
            "ORDER BY c.grid_y, c.grid_x LIMIT 1", UUID.class, worldId, biome);
        return found.isEmpty() ? null : found.get(0);
    }

    /** Stand the Chronicle here with nothing to drink carried and nothing built to catch rain. */
    private void standAt(UUID chronicle, UUID chunk) {
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE world_object w SET lifecycle_state='DESTROYED', destroyed_at=now(), destroyed_location_id=w.current_location_id, " +
            "destroyed_cause='ROTTED', current_location_id=NULL, current_owner_id=NULL FROM item_instance i " +
            "WHERE i.object_id=w.id AND i.item_key IN ('clean_water','filtered_water','raw_water') " +
            "AND (w.current_owner_id=? OR w.current_location_id=?)", chronicle, chunk);
        jdbc.update("DELETE FROM construction_project cp USING world_object w WHERE w.id=cp.object_id AND w.current_location_id=?", chunk);
    }

    private void weather(UUID worldId, String kind) {
        jdbc.update("UPDATE world_weather SET weather_kind=? WHERE world_id=?", kind, worldId);
    }

    @Test
    void theShoreSaysItIsTheSeaAndTheRainSaysItIsRain() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID worldId = worldGenesis.current().worldId();

        UUID shore = dryChunk(worldId, "COAST");
        Assumptions.assumeTrue(shore != null, "this world made no shore without fresh water on it");
        standAt(chronicle, shore);
        weather(worldId, "CLEAR");
        var atTheSea = actions.resolve("drink water");
        assertEquals("FAILED", atTheSea.outcome(), () -> "there is nothing fresh to drink on this shore: " + atTheSea.perception());
        assertTrue(atTheSea.perception().contains("sea"),
            () -> "on a shore the reason is the sea, not dry ground: " + atTheSea.perception());
        assertFalse(atTheSea.perception().contains("dry ground"),
            () -> "a shore is not dry ground: " + atTheSea.perception());

        UUID plain = dryChunk(worldId, "GRASSLAND");
        Assumptions.assumeTrue(plain != null, "this world made no dry grassland");
        standAt(chronicle, plain);

        // THE FRONT IS NOT THE SKY HERE (#28/#37), AND THE FRONT CANNOT BE FORCED ANYWAY.
        //
        // This used to write RAIN into world_weather and assert the drink refusal said "Rain wets your face".
        // Two things were wrong with that, and the second only showed itself in CI:
        //
        //   1. The reason moved onto the LOCAL reading, so on ground where that same front falls as snow the
        //      line correctly says snow. The expectation was describing world_weather; what the player is told
        //      describes the fall where they stand, which is the whole point of BiomeClimate.
        //   2. THE FORCED FRONT DOES NOT SURVIVE AN ACTION. Every resolve advances the clock, and the weather
        //      simulation recomputes the front from (season, seed, day) — so a written-in RAIN is overwritten
        //      by whatever the model says for that day. My first repair spent one action reading the sky and
        //      then asserted about rain, by which time the sky had been recomputed to CLEAR.
        //
        // So nothing is forced. ONE action is spent, and its own frame carries the local sky it was computed
        // from — that is what #778 put there — which means the reason and the sky cannot drift apart between
        // two calls. Every weather is a real assertion, so this cannot pass by finding a sky it has nothing to
        // say about.
        var here = actions.resolve("drink water");
        assertEquals("FAILED", here.outcome(), () -> "there is nothing fresh to drink on dry ground: " + here.perception());
        assertNotNull(here.frame(), "the frame carries the sky the reason was built from");
        assertNotNull(here.frame().weather(), "the frame must report a sky");

        // The one case worth stepping around: if the sky genuinely turned DURING this action, the reason was
        // built from the sky before and the frame reports the sky after, and the two are honestly different.
        boolean skyTurned = here.frame().sinceLastFrame().stream().anyMatch(c -> "weather".equals(c.aspect()));
        Assumptions.assumeFalse(skyTurned, "the sky turned during this very action");

        String skyHere = here.frame().weather().kind();
        switch (skyHere) {
            case "RAIN", "STORM" -> {
                assertTrue(here.perception().contains("Rain wets your face"),
                    () -> "in rain the line must say so: " + here.perception());
                assertFalse(here.perception().contains("dry ground"),
                    () -> "and must not call ground the sky is wetting dry: " + here.perception());
            }
            case "SNOW" -> {
                assertTrue(here.perception().contains("Snow lies about"),
                    () -> "under snow the line must say so: " + here.perception());
                assertFalse(here.perception().contains("dry ground"),
                    () -> "and must not call snow-covered ground dry: " + here.perception());
            }
            // A dry sky is the one case where "dry ground" is the true answer, and it must still be given.
            default -> assertTrue(here.perception().contains("dry ground"),
                () -> "with a dry sky the ground is dry, and the line should say it (" + skyHere + "): " + here.perception());
        }
    }
}
