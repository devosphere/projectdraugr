package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterEach;
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

import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Taking the stock to water (#106).
 *
 * <p>The model for this already worked and only the player had no way into it. Both ticks read the BOND's
 * {@code chronicle_id}'s chunk rather than the animal's — {@code advanceDraftThirst} relieves on a
 * WETLAND/RIVER_BANK biome, a freshwater site or a watering structure, and {@code advanceDraftHunger} on
 * GRASSLAND — so a kept herd already goes where its keeper goes. Nothing about the following was missing; the
 * saying of it was.
 *
 * <p><b>What this holds that the routing test cannot:</b> that the bearing the act takes is decided by the same
 * predicate the tick relieves on. If the two ever disagree, the game sends a keeper to water that does not water
 * their stock — which is why {@code towardStock} is a separate query from {@code toward} (that one matches
 * biomes only, and would call a neighbouring stream on grassland "no water within a step").
 *
 * <p>And that the prose does not lie about WHEN. The first measurement of this had it claiming the beasts drank
 * on arrival while {@code draft_thirst} had just risen 70 → 74, because the tick runs before the move. Standing
 * on the water it then fell 74 → 34 → 0 over the next two turns. The relief is the turn's, not the arrival's.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class TakingTheStockToWaterIntegrationTest {

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

    private UUID bondRaised;

    @AfterEach
    void takeTheBondBack() {
        // This suite shares one world. A tamed bond left behind is another test's herd, and several tests count
        // what the Chronicle keeps.
        if (bondRaised != null) jdbc.update("DELETE FROM wildlife_bond WHERE id=?", bondRaised);
        bondRaised = null;
    }

    private UUID aLivingChronicle() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.active() != null ? chronicles.active() : chronicles.awaken();
        assertNotNull(summary, "a living Chronicle is needed");
        return summary.id();
    }

    /** Ground whose neighbour relieves thirst by the tick's OWN predicate, so act and model cannot disagree. */
    private UUID standingBeside(String what) {
        String neighbourFits = "water".equals(what)
            ? "(n.biome IN ('WETLAND','RIVER_BANK') OR EXISTS (SELECT 1 FROM ecology_site es "
              + " WHERE es.chunk_id=n.id AND " + com.devosphere.draugr.ecology.FreshWater.sites("es") + "))"
            : "n.biome = 'GRASSLAND'";
        return jdbc.query(
            "SELECT c.id FROM world_chunk c WHERE c.biome <> 'CAVE_INTERIOR' AND c.biome <> 'OCEAN' "
            + "  AND EXISTS (SELECT 1 FROM world_chunk n WHERE n.world_id=c.world_id "
            + "               AND abs(n.grid_x-c.grid_x)+abs(n.grid_y-c.grid_y)=1 AND " + neighbourFits + ") "
            + "ORDER BY c.grid_y, c.grid_x LIMIT 1",
            rs -> rs.next() ? (UUID) rs.getObject(1) : null);
    }

    /** A tamed bond of this Chronicle's, thirsty and hungry, on any population the world holds. */
    private void keepOneBeast(UUID me, int thirst, int hunger) {
        bondRaised = UUID.randomUUID();
        Integer made = jdbc.update(
            "INSERT INTO wildlife_bond (id, chronicle_id, population_id, bond_stage, trust_level, "
            + "  last_interaction_at, draft_thirst, draft_hunger) "
            + "SELECT ?, ?, wp.id, 'TAMED', 90, now(), ?, ? FROM wildlife_population wp LIMIT 1",
            bondRaised, me, thirst, hunger);
        Assumptions.assumeTrue(made != null && made == 1, "the world must hold a population to bond with");
    }

    private String said(String text) {
        var r = actions.resolve(text, UUID.randomUUID());
        assertNotNull(r, () -> "the composer must answer: " + text);
        return (r.perception() == null ? "" : r.perception()).toLowerCase(Locale.ROOT);
    }

    @Test
    void theDriveGoesWhereTheTickWillRelieveThem() {
        UUID me = aLivingChronicle();
        UUID from = standingBeside("water");
        Assumptions.assumeTrue(from != null, "this world has no ground beside water to drive from");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", from, me);
        keepOneBeast(me, 70, 10);

        String prose = said("drive the herd to the stream");
        assertTrue(prose.contains("you travel"), () -> "a drive is a move: " + prose);
        assertTrue(prose.contains("beast you keep") || prose.contains("beasts you keep"),
            () -> "the herd must be accounted for in the telling: " + prose);

        // THE POINT: the ground arrived on must be ground the THIRST TICK relieves on. Asserted with the tick's
        // own predicate rather than a biome list, because that is exactly where the two could drift apart.
        UUID now = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, me);
        assertEquals(Boolean.TRUE, jdbc.queryForObject(
            "SELECT (c.biome IN ('WETLAND','RIVER_BANK') OR EXISTS (SELECT 1 FROM ecology_site es "
            + " WHERE es.chunk_id=c.id AND " + com.devosphere.draugr.ecology.FreshWater.sites("es") + ")) "
            + "FROM world_chunk c WHERE c.id=?", Boolean.class, now),
            "the drive must end on ground that waters them, or the act and the model disagree");
    }

    @Test
    void theProseDoesNotSayTheyDrankBeforeTheyHave() {
        UUID me = aLivingChronicle();
        UUID from = standingBeside("water");
        Assumptions.assumeTrue(from != null, "this world has no ground beside water to drive from");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", from, me);
        keepOneBeast(me, 70, 10);

        // The tick runs BEFORE the move, so on the turn of arrival the thirst has just risen. Claiming they
        // drank would be a lie told in the same even voice as every true line.
        String prose = said("take the stock to water");
        assertTrue(prose.contains("water in front of them"),
            () -> "the telling must report where they are, not what they have done: " + prose);
    }

    @Test
    void aKeeperWithNothingKeptIsToldSo() {
        UUID me = aLivingChronicle();
        UUID from = standingBeside("water");
        Assumptions.assumeTrue(from != null, "this world has no ground beside water to drive from");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", from, me);
        jdbc.update("DELETE FROM wildlife_bond WHERE chronicle_id=? AND bond_stage='TAMED'", me);

        String prose = said("take the stock to water");
        assertTrue(prose.contains("no tamed animals"),
            () -> "a keeper with nothing kept must not read a line about stock they do not have: " + prose);
    }

    @Test
    void withNoWaterWithinAStepItRefusesAndSaysWhy() {
        UUID me = aLivingChronicle();
        UUID dry = jdbc.query(
            "SELECT c.id FROM world_chunk c WHERE NOT EXISTS (SELECT 1 FROM world_chunk n "
            + "  WHERE n.world_id=c.world_id AND abs(n.grid_x-c.grid_x)+abs(n.grid_y-c.grid_y)=1 "
            + "    AND (n.biome IN ('WETLAND','RIVER_BANK') OR EXISTS (SELECT 1 FROM ecology_site es "
            + "          WHERE es.chunk_id=n.id AND " + com.devosphere.draugr.ecology.FreshWater.sites("es") + "))) "
            + "ORDER BY c.grid_y, c.grid_x LIMIT 1",
            rs -> rs.next() ? (UUID) rs.getObject(1) : null);
        Assumptions.assumeTrue(dry != null, "every chunk in this world is beside water");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", dry, me);
        keepOneBeast(me, 70, 10);

        String prose = said("take the stock to water");
        assertTrue(prose.contains("no water within a step"),
            () -> "it must say why rather than wander: " + prose);
        // And it must NOT have moved them on a refusal.
        assertEquals(dry, jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, me),
            "a refused drive leaves the keeper where they stood");
    }
}
