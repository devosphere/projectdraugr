package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.simulation.SimulationTickService;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rack you built does the work (#77/#220).
 *
 * <p>{@code SMOKE_RACK} and {@code DRYING_RACK} are buildable construction kinds with staged assemblies behind
 * them, and they were read by nothing at all — no Java named either, and no process asked for them. A Chronicle
 * could cut the rods, raise a smoke rack, and smoke exactly as well as over a bare fire, while three smoking and
 * five drying processes ran beside it and never noticed it was there. Smoking and drying are two of the four
 * preservation tiers, so these are the structures that make keeping food through a winter worth building for.
 *
 * <p>A station EASES work and never gates it, which is the existing contract and the right one: drying without a
 * rack must still work. This proves both halves — that it still works without, and that the rack is worth
 * raising. Skips without Docker.
 */
@SpringBootTest
class RackStationIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private int carried(UUID chronicle, String key) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key=? AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, key, chronicle);
        return n == null ? 0 : n;
    }

    /** Dry the same mushrooms the same number of times, and count what comes off. */
    private int dryRepeatedly(UUID chronicle, UUID chunk, Instant now, int rounds) {
        int made = 0;
        for (int i = 0; i < rounds; i++) {
            for (int j = 0; j < 4; j++) items.createCarriedItem(chronicle, "oyster_mushroom", "Oyster mushroom", now, "TEST_SEED");
            int before = carried(chronicle, "dried_mushroom");
            String[] r = items.runProcess(chronicle, chunk, "dry the mushrooms", now);
            if ("SUCCEEDED".equals(r[0])) made += carried(chronicle, "dried_mushroom") - before;
        }
        return made;
    }

    /** The data contract: both racks are named as stations, both are buildable, both exist in the registry. */
    @Test
    void bothRacksAreNamedAsStationsAndCanBeRaised() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        List<String> worked = jdbc.queryForList(
            "SELECT process_key FROM material_process WHERE station_kind IN ('SMOKE_RACK','DRYING_RACK') ORDER BY 1", String.class);
        assertTrue(worked.size() >= 7, "the smoking and drying processes must ask for their rack: " + worked);

        List<String> unraisable = jdbc.queryForList(
            "SELECT k FROM unnest(ARRAY['SMOKE_RACK','DRYING_RACK']) k " +
            "WHERE NOT EXISTS (SELECT 1 FROM assembly_definition ad WHERE ad.construction_kind = k) ORDER BY 1", String.class);
        assertTrue(unraisable.isEmpty(), "a station nobody can build is a station nobody can use: " + unraisable);
    }

    @Test
    void dryingWorksWithoutARackAndBetterWithOne() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();

        // Bare ground, no rack standing.
        jdbc.update("DELETE FROM construction_project cp USING world_object w WHERE w.id=cp.object_id AND w.current_location_id=?", chunk);

        // A station eases and never gates: drying on the ground must still succeed. This is the half that would
        // break if a station were ever allowed to become a requirement.
        int withoutRack = dryRepeatedly(chronicle, chunk, now, 40);
        assertTrue(withoutRack > 0, "drying without a rack must still work — a station eases, it never gates");

        // Raise a drying rack on the same ground and dry the same way again.
        UUID rack = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) VALUES (?,'CONSTRUCTION','Drying rack','ACTIVE',?)", rack, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) VALUES (?,'DRYING_RACK','COMPLETED',100,?,100)", rack, Timestamp.from(now));

        int withRack = dryRepeatedly(chronicle, chunk, now, 40);

        assertTrue(withRack > withoutRack,
            () -> "a drying rack must be worth raising — it skews the yield high, so forty rounds on it must beat "
                + "forty on the bare ground (withRack=" + withRack + ", withoutRack=" + withoutRack + ")");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
