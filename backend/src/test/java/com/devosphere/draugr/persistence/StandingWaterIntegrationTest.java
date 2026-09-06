package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.ecology.FreshWater;
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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Water that simply sits (#156).
 *
 * <p>The world had running water and springs and nothing standing: no pond, no lake margin, no headwater. This
 * places all three, and the point is that they are water to everything at once rather than to whichever call
 * sites somebody remembered — {@link FreshWater} became the single definition first, precisely so that adding
 * them could not produce ground that is water to the drinking check and dry to the process gate.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class StandingWaterIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    @Test
    void theWorldHoldsWaterThatSimplySits() {
        world();

        List<String> placed = jdbc.queryForList(
            "SELECT DISTINCT site_kind FROM ecology_site " +
            "WHERE site_kind IN ('Still pond','Lake margin','Headwater spring') ORDER BY 1", String.class);
        assertTrue(placed.size() == 3,
            "the world must hold a pond, a lake margin and a headwater — found " + placed);
    }

    /**
     * The whole reason the definition was consolidated first: a pond must be water to the process gate, which is
     * the caller that would previously have been left out. Before {@link FreshWater} it named four site words of
     * its own and a pond was not among them.
     */
    @Test
    void aPondIsWaterToTheProcessGateAndNotOnlyToWhoeverRememberedIt() {
        world();

        // The generator places its ponds on wetland, which is ALREADY water by biome — so finding one there would
        // prove nothing about the site being read. A pond is put on dry ground here deliberately, so the site is
        // the only thing that can make the answer true.
        UUID dry = jdbc.queryForObject(
            "SELECT id FROM world_chunk WHERE biome='GRASSLAND' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(dry, "the world must have dry grassland to put a pond on");
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, dry);

        jdbc.update("DELETE FROM ecology_site WHERE chunk_id=? AND " + FreshWater.sites(), dry);
        assertTrue(!items.waterToWorkWith(dry), "dry grassland with no water site on it must read as dry first");

        UUID pond = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Still pond',?)", pond, dry);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'RESOURCE','Still pond',20)",
            pond, worldId, dry);

        assertTrue(items.waterToWorkWith(dry),
            "a pond is fresh water, and the process gate is the caller that would previously have been left "
          + "behind — before the definition was consolidated it named four site words of its own, and a pond "
          + "was not among them");
    }

    /** Every standing-water kind the generator places must be covered by the one definition. */
    @Test
    void everyWaterSiteThePlanPlacesIsCoveredByTheDefinition() {
        world();

        List<String> uncovered = jdbc.queryForList(
            "SELECT DISTINCT site_kind FROM ecology_site WHERE site_kind IN ('Still pond','Lake margin','Headwater spring') " +
            "AND NOT " + FreshWater.sites() + " ORDER BY 1", String.class);
        assertTrue(uncovered.isEmpty(),
            "these are water the world places and the definition does not recognise, which is ground that is wet "
          + "to look at and dry to work with: " + uncovered);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
