package com.devosphere.draugr.persistence;

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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The world must contain a way into the rock (#158). A cave bear, a cave troll, a cave screecher and a giant bat
 * swarm were all in the catalogue and every one of them denned on a bare, open mountaintop, because rock was the
 * closest thing to a cave the world could offer.
 *
 * <p>Proves the cave mouth is placed where a cave mouth can physically be — in rock, opening onto ground a
 * Chronicle can walk in from — that it is not so common it replaces the open mountain the ore needs, that it is
 * furnished rather than barren, and that it does the one thing everybody already knows a cave does: it gets you
 * out of the weather without your having built anything. Skips without Docker.
 */
@SpringBootTest
class CaveMouthTopologyIntegrationTest {

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
    @Autowired com.devosphere.draugr.action.ChronicleActionService actions;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    @Test
    void theRockOpensSomewhereAndOnlyWhereItCould() {
        world();

        Integer caves = jdbc.queryForObject("SELECT COUNT(*) FROM world_chunk WHERE biome='CAVE_MOUTH'", Integer.class);
        assertNotNull(caves);
        assertTrue(caves > 0, "the world must contain a way into the rock — the cave animals had nowhere to den");

        // The open mountain must survive: obsidian, pumice and lens crystal are found there and nowhere else, so
        // a cave mouth that ate the range would quietly take those chains with it.
        Integer mountains = jdbc.queryForObject("SELECT COUNT(*) FROM world_chunk WHERE biome='MOUNTAIN'", Integer.class);
        assertNotNull(mountains);
        assertTrue(mountains > caves,
            "most rock is solid — open mountain (" + mountains + ") must still outnumber cave mouths (" + caves + ")");

        // Physical validity: a cave mouth must touch ground that can be walked in from. Sea and more mountain
        // do not count — a hole reachable only from open water or sheer rock is not a way in.
        Integer unreachable = jdbc.queryForObject(
            "SELECT COUNT(*) FROM world_chunk c WHERE c.biome='CAVE_MOUTH' AND NOT EXISTS (" +
            "  SELECT 1 FROM world_chunk n WHERE n.world_id=c.world_id " +
            "  AND ABS(n.grid_x-c.grid_x)<=1 AND ABS(n.grid_y-c.grid_y)<=1 AND NOT (n.grid_x=c.grid_x AND n.grid_y=c.grid_y) " +
            "  AND n.biome NOT IN ('OCEAN','MOUNTAIN','CAVE_MOUTH'))", Integer.class);
        assertNotNull(unreachable);
        assertTrue(unreachable == 0,
            unreachable + " cave mouths open onto nothing a Chronicle could walk in from");
    }

    @Test
    void theCaveIsFurnished() {
        world();

        // Not barren: the animals it was named for, stone to work, and something growing in the twilight.
        List<String> beasts = jdbc.queryForList(
            "SELECT species_key FROM wildlife_species WHERE biome_affinity LIKE '%CAVE_MOUTH%' ORDER BY 1", String.class);
        assertTrue(beasts.containsAll(List.of("cave_bear", "cave_screecher", "cave_troll")),
            "the animals named for caves must live in one: " + beasts);

        Integer stone = jdbc.queryForObject(
            "SELECT COUNT(*) FROM mineral_definition WHERE biome_affinity LIKE '%CAVE_MOUTH%'", Integer.class);
        Integer growing = jdbc.queryForObject(
            "SELECT COUNT(*) FROM flora_definition WHERE biome_affinity LIKE '%CAVE_MOUTH%'", Integer.class);
        assertNotNull(stone);
        assertNotNull(growing);
        assertTrue(stone >= 5, "a cave mouth must carry stone worth stopping for, found " + stone);
        assertTrue(growing >= 2, "the twilight zone must grow something, found " + growing);

        // Limestone above all: a cave is what limestone dissolves into, so it is the one that must be there.
        Boolean karst = jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM mineral_definition WHERE mineral_key='limestone_chunk' AND biome_affinity LIKE '%CAVE_MOUTH%')",
            Boolean.class);
        assertTrue(Boolean.TRUE.equals(karst), "a cave is dissolved out of limestone; limestone must be findable there");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /**
     * The oldest roof there is. Standing in a cave mouth must get you out of the weather without your having
     * built anything — that is the first reason to walk into one, and until now shelter came only from a
     * completed construction, so the rock over your head counted for nothing.
     */
    @Test
    void theRockOverYourHeadCountsAsShelter() throws Exception {
        world();

        java.util.UUID cave = jdbc.queryForObject(
            "SELECT id FROM world_chunk WHERE biome='CAVE_MOUTH' ORDER BY grid_y, grid_x LIMIT 1", java.util.UUID.class);
        java.util.UUID openGround = jdbc.queryForObject(
            "SELECT id FROM world_chunk WHERE biome='GRASSLAND' ORDER BY grid_y, grid_x LIMIT 1", java.util.UUID.class);
        assertNotNull(cave, "the world must contain a cave mouth to stand in");
        assertNotNull(openGround, "the world must contain open ground to compare against");

        // shelterInReach is private and reads the chunk directly; go through the target, not the Spring proxy,
        // or the bean's own jdbc field is null.
        java.lang.reflect.Method shelterInReach =
            com.devosphere.draugr.action.ChronicleActionService.class.getDeclaredMethod("shelterInReach", java.util.UUID.class);
        shelterInReach.setAccessible(true);
        Object target = org.springframework.test.util.AopTestUtils.getTargetObject(actions);

        assertTrue((Boolean) shelterInReach.invoke(target, cave),
            "a cave mouth must shelter you from the weather with nothing built");
        assertTrue(!(Boolean) shelterInReach.invoke(target, openGround),
            "bare grassland must NOT shelter you — otherwise the cave has bought nothing and every build is pointless");
    }
}
