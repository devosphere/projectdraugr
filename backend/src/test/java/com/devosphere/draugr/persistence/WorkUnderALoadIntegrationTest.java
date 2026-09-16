package com.devosphere.draugr.persistence;

import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.chronicle.ChroniclePhysiologyService;
import com.devosphere.draugr.item.PhysicalItemService;
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

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The load on your back costs something (#77).
 *
 * <p>The carry limit refused an impossible load and permitted everything under it for nothing. A Chronicle hauling
 * ninety kilos of stone tired exactly as much as one walking empty-handed, which is the one thing carrying is not.
 *
 * <p>The same body does the same work three times: empty-handed, laden, and laden with the load hung on a travois
 * behind a tamed ox. The third is the interesting one — the beast bears the load, so the body should not pay for
 * it, and that must fall out of reading the capacity the world actually grants rather than being written as a
 * special case.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class WorkUnderALoadIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void thePackOnYourBackIsPaidForInEnergy() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        Instant now = Instant.now();

        // A body nowhere near its limit — the awakening gear it stands up in is nothing against this capacity, so
        // this measures the work itself with no load worth speaking of.
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, " +
            "maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        int emptyHanded = costOfLabour(chronicle);
        assertTrue(emptyHanded > 0, "the labour must cost something at all, or nothing below is measurable");

        // Now load it to what it can shoulder, as WaterMustBeCrossed does: carry the ballast with every limit out
        // of the way, then fit the capacity to the load, so the fraction is what the fixture says rather than
        // whatever the catalogue's masses happen to make it.
        String heaviest = jdbc.queryForObject(
            "SELECT item_key FROM item_definition WHERE unit_mass_grams BETWEEN 300 AND 6000 " +
            "ORDER BY unit_mass_grams DESC, item_key LIMIT 1", String.class);
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, heaviest, heaviest, now, "TEST_FIXTURE");
        int carried = items.currentLoad(chronicle).massGrams();
        assertTrue(carried > 0, "the ballast must actually weigh something");
        double scale = items.currentLoad(chronicle).sustainedMassCapacityGrams() / 100000000.0;
        if (scale <= 0) scale = 1;
        int column = (int) Math.max(1, Math.round(carried / scale));   // capacity = the load, so the body is full
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=? WHERE chronicle_id=?", column, chronicle);

        int laden = costOfLabour(chronicle);
        assertTrue(laden > emptyHanded,
            () -> "the same work under a full load must cost more than the same work empty-handed — this is the "
                + "defect: the pack was free (empty " + emptyHanded + ", laden " + laden + ")");
        assertTrue(laden >= emptyHanded * 3 / 2,
            () -> "and meaningfully more, not a rounding: empty " + emptyHanded + ", laden " + laden);

        // The same load, shouldered by something else. Capacity is what the body is judged against, so raising it
        // is the same question as handing the weight to a beast — and the cost must fall back towards the empty
        // walk rather than staying where it was.
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=? WHERE chronicle_id=?", column * 10, chronicle);
        int shouldered = costOfLabour(chronicle);
        assertTrue(shouldered < laden,
            () -> "a load that something else is bearing must cost the body less (borne " + shouldered + ", carried " + laden + ")");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** One fixed piece of work, and the energy it actually took out of the body. */
    private int costOfLabour(UUID chronicle) {
        jdbc.update("UPDATE chronicle_physiology SET energy_level=100 WHERE chronicle_id=?", chronicle);
        physiology.applyLabor(chronicle, items, 8, 2);
        Integer left = jdbc.queryForObject("SELECT energy_level FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, chronicle);
        return 100 - left;
    }
}
