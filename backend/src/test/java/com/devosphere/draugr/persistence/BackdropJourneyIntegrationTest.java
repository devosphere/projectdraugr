package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.simulation.SimulationTickService;
import com.devosphere.draugr.world.BackdropResolver;
import com.devosphere.draugr.world.VisualContextController;
import com.devosphere.draugr.world.VisualContextService;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #242 — a journey through the scenery, on real PostgreSQL.
 *
 * <p>The endpoint tests prove one place at a time. A journey proves the thing a player actually does: stand on one
 * kind of ground after another and get the right scene every time. At every stop the chain database → visual
 * context → resolver → served backdrop must agree, a refresh must answer identically, and nothing standing on other
 * ground may leak into what this ground shows. Every failure names the stop, the ground and both answers, so a red
 * run says where the chain broke rather than only that it did.
 *
 * <p>Save and resume are covered by the same property: the server caches nothing about a place, so a Chronicle
 * reloaded onto the same unchanged ground reads exactly what they read before, fingerprint and all.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class BackdropJourneyIntegrationTest {

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
    @Autowired VisualContextController controller;
    @Autowired VisualContextService visual;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    private UUID living() {
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        return summary.id();
    }

    private void standOn(UUID chronicle, UUID chunk) {
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
    }

    /** One stop on every kind of ground the world places, and at each the whole chain agrees with itself. */
    @Test
    void aJourneyAcrossEveryKindOfGroundKeepsTheChainInStep() {
        world();
        UUID chronicle = living();

        List<Map<String, Object>> stops = jdbc.queryForList(
            "SELECT DISTINCT ON (biome) id, biome FROM world_chunk ORDER BY biome, grid_y, grid_x");
        assertTrue(stops.size() >= 6, () -> "a journey needs varied ground; the world placed only " + stops.size() + " kinds");

        Set<String> keysSeen = new HashSet<>();
        for (Map<String, Object> stop : stops) {
            UUID chunk = (UUID) stop.get("id");
            String biome = (String) stop.get("biome");
            String where = "stop on " + biome + " (" + chunk + ")";
            standOn(chronicle, chunk);

            VisualContextService.VisualContext here = visual.active(ticks.current().simulatedAt());
            assertNotNull(here, () -> where + ": a living Chronicle standing somewhere must see it");
            assertEquals(biome, here.biome(), () -> where + ": the context must report the ground the database holds");

            // Hidden knowledge: every site this place shows stands on this ground, and no other.
            Set<String> sitesHere = new HashSet<>(jdbc.queryForList(
                "SELECT site_kind FROM ecology_site WHERE chunk_id=?", String.class, chunk));
            for (VisualContextService.Feature feature : here.features()) {
                if (feature.kind() != null && feature.kind().startsWith("SITE:")) {
                    assertTrue(sitesHere.contains(feature.name()),
                        () -> where + ": a site from other ground leaked into this place: " + feature.name());
                }
            }

            // Hidden knowledge, the other half (#233): what the place says about the country around it must be
            // country that is actually there. A claim about ground two chunks away, or about a kind of country that
            // is nowhere next door, would be the Overseer's map leaking through the horizon.
            Set<String> neighbours = new HashSet<>(jdbc.queryForList(
                "SELECT DISTINCT n.biome FROM world_chunk c JOIN world_chunk n ON n.world_id=c.world_id " +
                "AND abs(n.grid_x-c.grid_x)+abs(n.grid_y-c.grid_y)=1 WHERE c.id=?", String.class, chunk));
            for (String around : here.surroundings()) {
                assertTrue(neighbours.contains(around),
                    () -> where + ": the place claims " + around + " lies next door, and it does not: " + neighbours);
            }
            assertEquals(here.surroundings().stream().distinct().toList(), here.surroundings(),
                () -> where + ": the same country must not be claimed twice: " + here.surroundings());
            assertTrue(here.surroundings().size() <= 4, () -> where + ": a chunk has four neighbours: " + here.surroundings());
            if (!here.lit()) assertTrue(here.surroundings().isEmpty(),
                () -> where + ": nothing beyond this ground can be seen in the dark: " + here.surroundings());

            BackdropResolver.Choice direct = BackdropResolver.resolve(here);
            VisualContextController.Backdrop served = controller.backdrop();
            assertEquals(direct.key(), served.key(),
                () -> where + ": database → context → resolver → served key drifted (resolver " + direct.key()
                    + ", served " + served.key() + ")");
            assertEquals(direct.candidates(), served.candidates(), () -> where + ": the eligibility chain drifted on the wire");
            assertEquals(here.fingerprint(), served.fingerprint(), () -> where + ": the served fingerprint is not this place's");
            // The chain must END somewhere that always answers. That used to be the chosen key itself, because
            // every tier replaced the key outright; since #224 a stand REFINES the ground rather than replacing it
            // (a brake of blackberry with no image of its own must fall back to its own meadow, not to the
            // registry root), so the last link is the ground's own key — or the world's default where there is no
            // ground to name. The chosen key must still be the FIRST thing offered: the chain is a ladder down.
            String last = served.candidates().get(served.candidates().size() - 1);
            assertTrue(last.startsWith("biome.") || last.equals(BackdropResolver.FALLBACK_KEY)
                       || last.equals(served.key()),
                () -> where + ": the chain must end somewhere that always answers: " + served.candidates());
            assertEquals(served.key(), served.candidates().get(0),
                () -> where + ": the chain must offer the chosen key first: " + served.candidates());

            // A refresh — or a reload onto the same unchanged ground after save and resume — answers identically.
            VisualContextController.Backdrop again = controller.backdrop();
            assertEquals(served, again, () -> where + ": reading an unchanged place twice gave two answers: " + served + " / " + again);

            keysSeen.add(served.key());
        }

        assertTrue(keysSeen.size() >= 3,
            () -> "a journey over different ground must not show one scene everywhere: " + keysSeen);
        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** A site that leaves this ground stops deciding the scene at once, and its return restores the same scene. */
    @Test
    void aSiteThatLeavesStopsDecidingTheScene() {
        world();
        UUID chronicle = living();

        Map<String, Object> site = jdbc.queryForMap(
            "SELECT es.id, es.chunk_id, es.site_kind FROM ecology_site es " +
            "WHERE (SELECT COUNT(*) FROM ecology_site other WHERE other.chunk_id = es.chunk_id) = 1 " +
            "  AND NOT EXISTS (SELECT 1 FROM construction_project cp JOIN world_object w ON w.id = cp.object_id " +
            "                   WHERE w.current_location_id = es.chunk_id AND cp.state = 'COMPLETED' AND w.lifecycle_state = 'ACTIVE') " +
            "ORDER BY es.site_kind LIMIT 1");
        UUID siteId = (UUID) site.get("id");
        UUID home = (UUID) site.get("chunk_id");
        String where = "site " + site.get("site_kind") + " on " + home;
        standOn(chronicle, home);

        VisualContextController.Backdrop withSite = controller.backdrop();
        assertEquals("SITE_HERE", withSite.reason(), () -> where + ": a site underfoot must decide the scene: " + withSite);

        UUID elsewhere = jdbc.queryForObject(
            "SELECT id FROM world_chunk WHERE id <> ? ORDER BY grid_y DESC, grid_x DESC LIMIT 1", UUID.class, home);
        jdbc.update("UPDATE ecology_site SET chunk_id=? WHERE id=?", elsewhere, siteId);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", elsewhere, siteId);
        try {
            VisualContextController.Backdrop without = controller.backdrop();
            assertNotEquals("SITE_HERE", without.reason(), () -> where + ": a site that has gone must stop deciding the scene: " + without);
            assertNotEquals(withSite.key(), without.key(), () -> where + ": the scene must change when its site goes");
            assertNotEquals(withSite.fingerprint(), without.fingerprint(), () -> where + ": the caching fingerprint must move with the site");
        } finally {
            jdbc.update("UPDATE ecology_site SET chunk_id=? WHERE id=?", home, siteId);
            jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", home, siteId);
        }

        assertEquals(withSite, controller.backdrop(), () -> where + ": the site's return must restore exactly the scene it had");
        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** With nobody alive to see anything, the scene is the world's default — an answer, never an error. */
    @Test
    void aJourneyThatEndsInDeathEndsInTheDefault() {
        world();
        UUID chronicle = living();
        UUID ground = jdbc.queryForObject("SELECT id FROM world_chunk ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        standOn(chronicle, ground);
        assertNotEquals("NO_CONTEXT", controller.backdrop().reason(), "a living Chronicle has a place to see");

        jdbc.update("UPDATE chronicle SET life_state='DEAD', died_at=?, death_cause='Test journey end' WHERE id=?",
            Timestamp.from(ticks.current().simulatedAt()), chronicle);
        try {
            VisualContextController.Backdrop after = controller.backdrop();
            assertEquals(BackdropResolver.FALLBACK_KEY, after.key(), "with no one alive the scene is the world's default");
            assertEquals("NO_CONTEXT", after.reason(), "and it says why");
            assertEquals(List.of(BackdropResolver.FALLBACK_KEY), after.candidates(), "and offers nothing that could name a place");
            assertNull(after.fingerprint(), "and fingerprints no place, because it reads none");
        } finally {
            // The other journeys in this class share the database and run in any order: a dead Chronicle left with
            // its starting gear attached is an Auditor violation none of them caused. Put the life back.
            jdbc.update("UPDATE chronicle SET life_state='LIVING', died_at=NULL, death_cause=NULL WHERE id=?", chronicle);
        }
    }
}
