package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.construction.FireService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A fire poker must bank a fire better than bare hands (#75).
 *
 * <p>The catalogue calls the {@code fire_poker} "a poker to tend a fire and reach hot coals", it is craftable and
 * held in the right hand, and nothing read it — so a Chronicle could cut one and bank exactly as well without it.
 * Banking is raking the coals into a tight heap and covering them, which is the one job the tool is named for.
 *
 * <p>Same fire, same starting fuel, banked twice: once bare-handed and once with the poker in reach. Skips
 * without Docker.
 */
@SpringBootTest
class FirePokerIntegrationTest {

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
    @Autowired FireService fire;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private int fuel(UUID pit) {
        return jdbc.queryForObject("SELECT fuel_minutes FROM fire_state WHERE construction_id=?", Integer.class, pit);
    }

    @Test
    void aPokerBanksAFireTighterThanBareHands() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        Instant now = ticks.current().simulatedAt();
        Timestamp ts = Timestamp.from(now);

        // A fire pit with a fire in it, on the Chronicle's own ground.
        jdbc.update("DELETE FROM construction_project cp USING world_object w WHERE w.id=cp.object_id AND w.current_location_id=?", chunk);
        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit','ACTIVE',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?,100)", pit, ts);
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,30,?)", pit, ts);

        // Make sure no poker is to hand for the first banking.
        jdbc.update("UPDATE world_object SET current_owner_id=NULL WHERE current_owner_id=? AND id IN (" +
                "SELECT object_id FROM item_instance WHERE item_key='fire_poker')", chronicle);

        assertTrue(fire.bank(chronicle, chunk, now), "there is a fire here, so banking it must succeed");
        int bareHanded = fuel(pit);
        assertTrue(bareHanded > 30, "banking must hold the embers longer than leaving them open");

        // Same fire, same starting fuel, this time with a poker in reach.
        jdbc.update("UPDATE fire_state SET fuel_minutes=30, last_updated_at=? WHERE construction_id=?", ts, pit);
        items.createCarriedItem(chronicle, "fire_poker", "Fire poker", now, "TEST_SEED");

        assertTrue(fire.bank(chronicle, chunk, now), "banking with a poker must still succeed");
        int withPoker = fuel(pit);

        assertTrue(withPoker > bareHanded,
            () -> "a poker is the tool for raking coals, so it must bank the fire tighter than bare hands "
                + "(withPoker=" + withPoker + ", bareHanded=" + bareHanded + ")");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
