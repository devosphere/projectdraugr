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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Soil fertility (EPIC #162 / story #164). A harvest takes from the soil, and ground cropped without rest gives
 * thinner stands until it is left fallow to recover — the reason a farmer rotates and rests fields. Pristine ground
 * reads full, so a first crop is unchanged; the cost falls on the field worked without rest, and fallow time wins it
 * back.
 *
 * <p>Proven: a fresh field yields full and its fertility falls after the harvest; a worn field yields less; and a
 * field left fallow long enough has recovered to yield full again. Skips gracefully without Docker.
 */
@SpringBootTest
class SoilFertilityIntegrationTest {

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

    private int heads(UUID chronicle) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key='wild_grain_head' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, chronicle);
        return n == null ? 0 : n;
    }

    private void ripeCrop(UUID chunk, Instant reapAt) {
        jdbc.update("INSERT INTO crop_stand (id, chunk_id, crop_key, sown_at, maturity_days, harvested, tilled) VALUES (?,?,?,?,?,false,false)",
            UUID.randomUUID(), chunk, "wild_grain", Timestamp.from(reapAt.minus(Duration.ofDays(35))), 30);
    }

    @Test
    void aWornFieldYieldsLessAndFallowRestoresIt() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        List<UUID> chunks = jdbc.queryForList("SELECT id FROM world_chunk ORDER BY grid_y, grid_x LIMIT 3", UUID.class);
        UUID fresh = chunks.get(0), worn = chunks.get(1), rested = chunks.get(2);
        Instant now = ticks.current().simulatedAt();

        // A pristine field; a worn field (fertility low, just cropped); a worn field left fallow forty days.
        jdbc.update("INSERT INTO field_soil (chunk_id, fertility, last_updated_at) VALUES (?,30,?)", worn, Timestamp.from(now));
        jdbc.update("INSERT INTO field_soil (chunk_id, fertility, last_updated_at) VALUES (?,30,?)", rested, Timestamp.from(now.minus(Duration.ofDays(40))));
        ripeCrop(fresh, now);
        ripeCrop(worn, now);
        ripeCrop(rested, now);

        // Fresh, pristine ground: the full stand, and the harvest draws its fertility down.
        assertEquals("SUCCEEDED", items.harvestCrop(chronicle, fresh, now)[0], "reaping the fresh field must succeed");
        int freshYield = heads(chronicle);
        assertEquals(4, freshYield, "pristine ground yields the full untilled stand");
        assertEquals(70, (int) jdbc.queryForObject("SELECT fertility FROM field_soil WHERE chunk_id=?", Integer.class, fresh),
            "the harvest must draw the field's fertility down");

        // Worn ground: a thinner stand.
        assertEquals("SUCCEEDED", items.harvestCrop(chronicle, worn, now)[0], "reaping the worn field must succeed");
        int wornYield = heads(chronicle) - freshYield;
        assertEquals(2, wornYield, "worn soil gives a thinner stand");

        // Rested ground (fallow recovered its fertility): the full stand again.
        assertEquals("SUCCEEDED", items.harvestCrop(chronicle, rested, now)[0], "reaping the rested field must succeed");
        int restedYield = heads(chronicle) - freshYield - wornYield;
        assertEquals(4, restedYield, "a field left fallow long enough recovers to yield full again");

        assertTrue(restedYield > wornYield, "fallow rest must restore what continuous cropping wore away (#164)");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
