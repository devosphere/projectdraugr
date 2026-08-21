package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChroniclePhysiologyService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Garment waterproofing regression (EPIC #171 textiles / #177 finishing — closing the water_resistance dead-read).
 * Worn garments were read for INSULATION (warmth) but never for shedding rain, so a waxed hide cloak soaked as fast
 * as bare skin — waterproofing, oiling, and hardening (tar / cuir bouilli) bought nothing against the wet. Now each
 * garment carries a water_resistance; ChroniclePhysiologyService sums it across what is worn and slows how fast rain
 * wets the body, capped so no outfit stays bone-dry in a storm.
 *
 * <p>Proven: the same Chronicle stood out in the same rain for the same span gains markedly LESS wetness wearing a
 * fur cloak (water_resistance 12) than bare-skinned. Skips gracefully without Docker.
 */
@SpringBootTest
class GarmentWaterproofingIntegrationTest {

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
    @Autowired ChroniclePhysiologyService physiology;
    @Autowired com.devosphere.draugr.simulation.WeatherSimulationService weather;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /** Dry the body, stand it in the rain for {@code hours} from {@code base}, and report the wetness gained. */
    private int wetnessAfterRain(UUID chronicle, Instant base, int hours) {
        jdbc.update("UPDATE chronicle_physiology SET wetness_level=0, last_metabolic_update=? WHERE chronicle_id=?",
                Timestamp.from(base), chronicle);
        physiology.advanceTo(base.plus(Duration.ofHours(hours)));
        return jdbc.queryForObject("SELECT wetness_level FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, chronicle);
    }

    @Test
    void aRainSheddingGarmentKeepsTheBodyDrierInRain() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);

        Instant base = ticks.current().simulatedAt();
        // Ensure the world has a weather row (in play the weather tick creates it), then force a steady rain over the
        // whole world (no fire, no shelter at the chunk, so the body is out in it).
        weather.advanceTo(base);
        jdbc.update("UPDATE world_weather SET weather_kind='RAIN', intensity=60, ambient_temperature_c=15 " +
                "WHERE world_id=(SELECT world_id FROM chronicle WHERE id=?)", chronicle);

        // Bare-skinned: the rain soaks in at the open-air rate.
        int bare = wetnessAfterRain(chronicle, base, 6);
        assertTrue(bare > 0, "standing in rain must actually wet a bare body");

        // Wear a fur cloak (water_resistance 12): the same rain, the same span, but the wet is shed.
        UUID cloak = items.createCarriedItem(chronicle, "fur_cloak", "Fur cloak", base, "TEST_SEED");
        jdbc.update("INSERT INTO equipment_attachment(item_id, chronicle_id, body_position, layer) VALUES (?,?,'TORSO','OUTER')", cloak, chronicle);
        int cloaked = wetnessAfterRain(chronicle, base, 6);

        assertTrue(cloaked < bare,
                () -> "a rain-shedding garment must keep the body drier: cloaked=" + cloaked + " vs bare=" + bare + " (#177)");
        assertTrue(bare - cloaked >= 5,
                () -> "the shedding must be a real margin, not a rounding wobble: bare=" + bare + " cloaked=" + cloaked);

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
