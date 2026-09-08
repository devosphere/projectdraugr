package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
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

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rock has an inside, and one way into it (#158).
 *
 * <p>{@code WorldCaveInteriorTest} proves the geography without a database. This proves what the biome is FOR: it
 * is dark at noon, it is out of the weather, it shelters, it holds an ecology of its own, and it cannot be walked
 * into off an open mountainside.
 *
 * <p>That last one is what keeps it from being a differently-worded piece of mountain. The generator only ever
 * makes a chamber where the rock is closed to the sky, so the mouth beside it is the way in — and if a Chronicle
 * could step into it from the summit, the derivation rule would be describing something the world did not enforce.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class CaveInteriorIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    private UUID chronicle() {
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        return summary.id();
    }

    /**
     * A chamber and a neighbour of the given biome beside it, as {from, to} chunk ids plus the direction word that
     * walks from one to the other. Returns null when this world has no such pair.
     */
    private Map<String,Object> approachTo(String fromBiome) {
        return jdbc.query(
            "SELECT f.id AS from_id, t.id AS to_id, t.grid_x - f.grid_x AS dx, t.grid_y - f.grid_y AS dy " +
            "  FROM world_chunk t JOIN world_chunk f ON f.world_id = t.world_id " +
            "   AND abs(t.grid_x - f.grid_x) + abs(t.grid_y - f.grid_y) = 1 " +
            " WHERE t.biome = 'CAVE_INTERIOR' AND f.biome = ? " +
            " ORDER BY t.grid_y, t.grid_x LIMIT 1",
            rs -> rs.next()
                ? Map.of("from", rs.getObject("from_id"), "to", rs.getObject("to_id"),
                         "dir", direction(rs.getInt("dx"), rs.getInt("dy")))
                : null,
            fromBiome);
    }

    private static String direction(int dx, int dy) {
        if (dx == 1) return "east";
        if (dx == -1) return "west";
        return dy == 1 ? "south" : "north";
    }

    private UUID whereIs(UUID chronicle) {
        return jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
    }

    /** The world must actually persist chambers, or everything below is asserted about nothing. */
    @Test
    void theWorldPersistsTheInsideOfTheRock() {
        world();
        Integer chambers = jdbc.queryForObject(
            "SELECT COUNT(*) FROM world_chunk WHERE biome='CAVE_INTERIOR'", Integer.class);
        assertTrue(chambers != null && chambers > 0, "the persisted world must contain cave interiors");

        Integer mouths = jdbc.queryForObject(
            "SELECT COUNT(*) FROM world_chunk WHERE biome='CAVE_MOUTH'", Integer.class);
        assertTrue(mouths != null && mouths >= chambers,
            "there must be at least as many ways in as there are chambers (" + mouths + " mouths, " + chambers + ")");
    }

    /** You go in at the mouth. Off the open mountain, the rock is rock. */
    @Test
    void theRockIsEnteredAtItsMouth() {
        world();

        Map<String,Object> offTheMountain = approachTo("MOUNTAIN");
        Assumptions.assumeTrue(offTheMountain != null, "this world set no chamber against open mountain");

        UUID chronicle = chronicle();
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", offTheMountain.get("from"), chronicle);
        UUID before = whereIs(chronicle);
        actions.resolve("walk " + offTheMountain.get("dir"), UUID.randomUUID());
        assertEquals(before, whereIs(chronicle),
            "a Chronicle must not walk into solid rock from the open mountain — without this the biome is only a "
                + "differently-worded piece of mountain");
    }

    /** And from the doorway, in. */
    @Test
    void fromTheDoorwayTheChamberIsReachable() {
        world();

        Map<String,Object> throughTheMouth = approachTo("CAVE_MOUTH");
        assertNotNull(throughTheMouth,
            "every chamber is generated against a mouth, so the persisted world must contain such a pair");

        UUID chronicle = chronicle();
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", throughTheMouth.get("from"), chronicle);
        UUID before = whereIs(chronicle);
        actions.resolve("walk " + throughTheMouth.get("dir"), UUID.randomUUID());
        assertEquals(throughTheMouth.get("to"), whereIs(chronicle),
            "from the mouth the chamber must be reachable, or the cave is sealed and the ground unreachable "
                + "(was at " + before + ")");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** The dark inside must not be barren — that is the whole reason V288 exists. */
    @Test
    void theDarkHoldsStoneGrowthAndLife() {
        world();

        Integer minerals = jdbc.queryForObject(
            "SELECT COUNT(*) FROM mineral_definition WHERE 'CAVE_INTERIOR' = ANY(string_to_array(biome_affinity, ','))", Integer.class);
        Integer flora = jdbc.queryForObject(
            "SELECT COUNT(*) FROM flora_definition WHERE 'CAVE_INTERIOR' = ANY(string_to_array(biome_affinity, ','))", Integer.class);
        Integer life = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wildlife_species WHERE 'CAVE_INTERIOR' = ANY(string_to_array(biome_affinity, ','))", Integer.class);

        assertTrue(minerals != null && minerals > 0, "a cave is dissolved out of stone; it must hold some");
        assertTrue(flora != null && flora > 0,
            "something must grow in the dark — the ticket gates subterranean fungi until this topology exists, "
                + "and it exists now");
        assertTrue(life != null && life > 0, "bats roost deep; the chamber must hold life");
    }

    /** A cave mushroom must be reachable food, not a catalogue entry that fruits nowhere. */
    @Test
    void whatGrowsInTheDarkCanBeEaten() {
        world();

        Integer fruiting = jdbc.queryForObject(
            "SELECT COUNT(*) FROM flora_drop d JOIN flora_definition f ON f.flora_key = d.flora_key " +
            "WHERE f.flora_key='cave_fungus'", Integer.class);
        assertTrue(fruiting != null && fruiting > 0, "cave fungus must drop something or it cannot be gathered");

        String category = jdbc.queryForObject(
            "SELECT category FROM item_definition WHERE item_key='cave_mushroom'", String.class);
        assertEquals("FOOD", category, "what it drops must be food, or gathering it leads nowhere");

        // A cave holds one temperature while the seasons turn outside, which is why it fruits all year — and the
        // only forageable in the catalogue with no season. Asserted so a later seasonal sweep does not quietly
        // take the cave's winter food away.
        String season = jdbc.query("SELECT season FROM flora_drop WHERE flora_key='cave_fungus' LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : "MISSING");
        assertEquals(null, season, "a cave keeps one temperature, so its fungus fruits in every season");
    }
}
