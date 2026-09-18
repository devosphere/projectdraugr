package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.people.NativeCommunityService;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reedkin, the first native people (#115, DR-0024, V346).
 *
 * <p>A world made fresh has their isles in it: on marsh, apart from each other, never on a monster's ground, each
 * with a village, a store house holding real food, two households and eight named people. Running the placement
 * again — which every boot does through reconcile — founds nothing more. And none of the systems that find wildlife
 * by biome can ever find them: a people lives where its isles are. Skips without Docker.
 */
@SpringBootTest
class TheReedkinIntegrationTest {

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
    @Autowired NativeCommunityService natives;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void aFreshWorldHasTheReedkinOnItsMarshesAndNowhereElse() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        UUID worldId = worldGenesis.current().worldId();

        List<Map<String, Object>> isles = jdbc.queryForList(
            "SELECT n.id, n.name, c.biome, c.grid_x, c.grid_y, n.trade_policy, n.security_posture FROM native_community n " +
            "JOIN world_chunk c ON c.id=n.home_chunk_id WHERE n.world_id=? AND n.species_key='reedkin' ORDER BY n.name", worldId);
        assertTrue(!isles.isEmpty() && isles.size() <= 2, () -> "one or two reedkin isles, never a population in every marsh: " + isles);

        for (Map<String, Object> isle : isles) {
            UUID id = (UUID) isle.get("id");
            assertEquals("WETLAND", isle.get("biome"), () -> "the reedkin live on marsh: " + isle);
            assertEquals("SELECTIVE", isle.get("trade_policy"), "wary of strangers, selective about trade");
            assertEquals(0, (int) jdbc.queryForObject(
                "SELECT COUNT(*) FROM ecology_site s WHERE s.chunk_id=(SELECT home_chunk_id FROM native_community WHERE id=?) AND s.site_category='MONSTER'",
                Integer.class, id), "never on a monster's ground");

            assertEquals(8, (int) jdbc.queryForObject(
                "SELECT COUNT(*) FROM native_individual n JOIN world_object w ON w.id=n.object_id " +
                "WHERE n.community_id=? AND w.lifecycle_state='ACTIVE' AND w.current_location_id=(SELECT home_chunk_id FROM native_community WHERE id=?)",
                Integer.class, id, id), "eight people, each a body standing on the isle");
            assertEquals(2, (int) jdbc.queryForObject("SELECT COUNT(*) FROM native_kin_group WHERE community_id=?", Integer.class, id), "two households");
            assertEquals(1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM native_individual WHERE community_id=? AND role='HEADSPERSON'", Integer.class, id),
                "one who speaks for the isle to outsiders");
            int stored = jdbc.queryForObject(
                "SELECT COUNT(*) FROM native_settlement_site s JOIN world_object w ON w.current_owner_id=s.object_id " +
                "JOIN item_instance i ON i.object_id=w.id WHERE s.community_id=? AND s.holds_stores AND w.lifecycle_state='ACTIVE' AND i.item_key='dried_fish'",
                Integer.class, id);
            assertTrue(stored > 0, "the store house holds real food");
            assertEquals(List.of("FOUNDED"), jdbc.queryForList(
                "SELECT event_kind FROM native_event WHERE community_id=? ORDER BY id LIMIT 1", String.class, id), "their history begins with their founding");
        }
        if (isles.size() == 2) {
            int dx = Math.abs(((Number) isles.get(0).get("grid_x")).intValue() - ((Number) isles.get(1).get("grid_x")).intValue());
            int dy = Math.abs(((Number) isles.get(0).get("grid_y")).intValue() - ((Number) isles.get(1).get("grid_y")).intValue());
            assertTrue(Math.max(dx, dy) >= 4, "each isle keeps its own fishing water");
        }

        // Every boot reconciles: a world that has its isles gains no more.
        assertEquals(0, natives.seedPeoples(worldId), "placement is idempotent");

        // And nothing that finds wildlife by the ground can find a person.
        for (String biome : List.of("WETLAND", "RIVER_BANK", "GRASSLAND", "TEMPERATE_FOREST")) {
            List<String> byGround = jdbc.queryForList(
                "SELECT species_key FROM wildlife_species WHERE biome_affinity ILIKE ? AND species_key='reedkin'", String.class, "%" + biome + "%");
            assertTrue(byGround.isEmpty(), () -> "the reedkin must never be ambient wildlife of " + biome);
        }
        assertEquals(0, (int) jdbc.queryForObject("SELECT tamability FROM wildlife_species WHERE species_key='reedkin'", Integer.class),
            "a people is never offered for taming");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
