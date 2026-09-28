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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A dry spell and the birds (#37/#165) — the two things a farmer spends a season on that the field did not reckon.
 *
 * <p>{@code harvestCrop} already read six things: the tilled seedbed, the soil's fertility, whether the animals got
 * in, whether the stand was weeded, what the bees and worms did for it, and how late it was cut. Watering was
 * nowhere in it, and the birds were only ever an excuse the prose gave for a late harvest — "the birds have been at
 * it" — with nothing a Chronicle could do about them. Act nine put both plainly and got UNKNOWN for each.
 *
 * <p>Neither is invented. Drought reads {@code world_chunk.moisture}, the same column that decides how warm the
 * body is where it stands (#709) and whether a well reaches the water table (#726). Birds read the lateness rule
 * that already blamed them. And what driving them off buys is <b>time, not grain</b>: the clean window widens
 * while somebody keeps walking the plot, and grain already shattered onto the ground is gone whatever is shouted.
 *
 * <p>Every number here is asserted as a DIFFERENCE with one variable moved, because the first measurement of this
 * looked like nothing had changed — three reaps in a row each deplete the field's fertility, so the runs were not
 * comparable. Fertility is pinned in every case below for that reason. Skips without Docker.
 */
@SpringBootTest
class ADrySpellAndTheBirdsIntegrationTest {

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

    private UUID awaken() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        return summary.id();
    }

    private UUID where(UUID chronicle) {
        return jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
    }

    /** A ripe, tilled stand on ground of the given dampness, on a field of pinned fertility. */
    private void sow(UUID chronicle, int moisture, int daysOld, Integer wateredDaysAgo, Integer birdsScaredDaysAgo) {
        UUID chunk = where(chronicle);
        jdbc.update("DELETE FROM crop_stand WHERE chunk_id=?", chunk);
        jdbc.update("INSERT INTO field_soil (chunk_id, fertility, last_updated_at) VALUES (?,100,now()) " +
            "ON CONFLICT (chunk_id) DO UPDATE SET fertility=100, last_updated_at=now()", chunk);
        jdbc.update("UPDATE world_chunk SET moisture=?, biome='GRASSLAND' WHERE id=?", moisture, chunk);
        // Inserted without the two nullable timestamps and then set only where they are wanted, so no untyped SQL
        // NULL is ever handed to the driver for an int parameter — a thing that reads fine and fails at runtime.
        jdbc.update("INSERT INTO crop_stand (id, chunk_id, crop_key, sown_at, maturity_days, tilled) " +
            "VALUES (gen_random_uuid(), ?, 'emmer_wheat', (SELECT simulated_at FROM simulation_clock WHERE id=1) - make_interval(days => ?), 20, TRUE)",
            chunk, daysOld);
        if (wateredDaysAgo != null)
            jdbc.update("UPDATE crop_stand SET watered_at = (SELECT simulated_at FROM simulation_clock WHERE id=1) - make_interval(days => ?) " +
                "WHERE chunk_id=? AND harvested=false", wateredDaysAgo.intValue(), chunk);
        if (birdsScaredDaysAgo != null)
            jdbc.update("UPDATE crop_stand SET birds_scared_at = (SELECT simulated_at FROM simulation_clock WHERE id=1) - make_interval(days => ?) " +
                "WHERE chunk_id=? AND harvested=false", birdsScaredDaysAgo.intValue(), chunk);
    }

    private int heads(UUID chronicle) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key='wild_grain_head' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, chronicle);
        return n == null ? 0 : n;
    }

    private int reap(UUID chronicle) {
        int before = heads(chronicle);
        ChronicleActionService.ActionResult cut = actions.resolve("reap the crop");
        assertEquals("SUCCEEDED", cut.outcome(), () -> "the ripe stand must be reapable: " + cut.perception());
        return heads(chronicle) - before;
    }

    @Test
    void wateringIsRefusedWhereItWouldDoNothingAndWhereThereIsNoWater() {
        UUID chronicle = awaken();
        UUID chunk = where(chronicle);

        // Damp ground of its own. There is nothing for a bucket to add, and the refusal says so rather than
        // quietly banking a bonus nobody earned.
        sow(chronicle, 700, 25, null, null);
        ChronicleActionService.ActionResult damp = actions.resolve("water the seedlings");
        assertEquals("FAILED", damp.outcome(), () -> "damp ground needs no watering: " + damp.perception());
        assertTrue(damp.perception().contains("holds its own water"),
            () -> "and is told why, not merely refused: " + damp.perception());

        // Dry ground with nothing to carry water from. This is hauling water, not wishing it.
        jdbc.update("DELETE FROM ecology_site WHERE chunk_id=?", chunk);
        sow(chronicle, 250, 25, null, null);
        ChronicleActionService.ActionResult nothing = actions.resolve("water the seedlings");
        assertEquals("FAILED", nothing.outcome(), () -> "with no water in reach there is none to give: " + nothing.perception());
        assertTrue(nothing.perception().contains("no well"),
            () -> "and a well is named among what would answer, since sinking one is the point (#726): " + nothing.perception());
        assertNull(jdbc.queryForObject("SELECT watered_at FROM crop_stand WHERE chunk_id=? AND harvested=false", java.sql.Timestamp.class, chunk),
            "a refused watering waters nothing");

        // Put a spring on the ground and the same words work.
        UUID site = UUID.randomUUID();
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Spring',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'RESOURCE','Spring',20)", site, world, chunk);
        ChronicleActionService.ActionResult carried = actions.resolve("water the seedlings");
        assertEquals("SUCCEEDED", carried.outcome(), () -> "with a spring here the rows can be watered: " + carried.perception());
        assertNotNull(jdbc.queryForObject("SELECT watered_at FROM crop_stand WHERE chunk_id=? AND harvested=false", java.sql.Timestamp.class, chunk),
            "and the stand records that it was watered");
    }

    @Test
    void aStandGrownDryAndNeverWateredYieldsLess() {
        UUID chronicle = awaken();

        sow(chronicle, 250, 25, null, null);
        int dry = reap(chronicle);

        sow(chronicle, 250, 25, 10, null);
        int watered = reap(chronicle);

        sow(chronicle, 700, 25, null, null);
        int damp = reap(chronicle);

        assertTrue(dry < watered, () -> "a stand grown dry and never watered gives less: " + dry + " vs " + watered);
        assertEquals(watered, damp, "and watering a dry plot brings it back to what damp ground gives of itself: "
            + watered + " vs " + damp);
    }

    @Test
    void drivingTheBirdsOffBuysBackTimeAndNotGrain() {
        UUID chronicle = awaken();

        sow(chronicle, 700, 22, null, null);
        int onTime = reap(chronicle);

        // Sixteen days past ripe: the heads shatter and the birds work at them.
        sow(chronicle, 700, 36, null, null);
        int late = reap(chronicle);

        // The same sixteen days, with somebody walking the plot two days ago.
        sow(chronicle, 700, 36, null, 2);
        int lateButKept = reap(chronicle);

        // Twenty days past ripe is beyond what shouting buys: that grain is on the ground.
        sow(chronicle, 700, 40, null, 2);
        int farTooLate = reap(chronicle);

        assertTrue(late < onTime, () -> "a late stand loses grain: " + late + " vs " + onTime);
        assertEquals(onTime, lateButKept, "keeping the birds off buys back the clean window: " + lateButKept);
        assertEquals(late, farTooLate, "but not for ever — grain already shattered is gone: " + farTooLate);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void birdsAreOnlyWorthDrivingOffAStandThatHasSomethingOnIt() {
        UUID chronicle = awaken();

        sow(chronicle, 700, 2, null, null);   // sown two days ago: still green
        ChronicleActionService.ActionResult green = actions.resolve("scare the birds off the crop");
        assertEquals("FAILED", green.outcome(), () -> "nothing in a green stand is worth a bird's journey: " + green.perception());
        assertTrue(green.perception().contains("still green"), () -> "and the reason is the stand, not the bird: " + green.perception());

        sow(chronicle, 700, 25, null, null);  // ripe
        ChronicleActionService.ActionResult ripe = actions.resolve("scare the birds off the crop");
        assertEquals("SUCCEEDED", ripe.outcome(), () -> "a ripe stand is worth walking: " + ripe.perception());
        assertNotNull(jdbc.queryForObject("SELECT birds_scared_at FROM crop_stand WHERE chunk_id=? AND harvested=false",
            java.sql.Timestamp.class, where(chronicle)), "and the stand remembers when they were last driven off");
    }
}
