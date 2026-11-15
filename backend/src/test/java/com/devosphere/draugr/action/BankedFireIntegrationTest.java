package com.devosphere.draugr.action;

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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BANKING A FIRE IS THE OPPOSITE OF LETTING IT ROAR, AND ONE NUMBER SAID BOTH (#890).
 *
 * <p>{@code fire_state.fuel_minutes} is burning time remaining. It was also, because nothing else existed,
 * what the ember hazard read as FEROCITY: {@code scorchNearbyFlammables} bites above 120 fuel-minutes and
 * {@code fireReading} warns at exactly the same figure. Banking RAISES fuel_minutes — that is the whole point
 * of raking the coals tight and covering them — so a keeper who did the careful thing on a low fire was told
 * immediately that their hearth was throwing embers and would char the hut and the smoke vent by the hour.
 *
 * <p>Live, Chronicle 17: check the fire at about 50 minutes, bank it for the night, check it again, and the
 * answer contradicts the act that produced it.
 *
 * <p>Also proves #871 in the same fixture, because it is the same kit: a bow-drill refusal must NAME what the
 * method is short of. A Chronicle with the board, the spindle and the bow, missing only the bearing block, was
 * told their hands "raise no heat from bare wood alone" — a sentence describing someone with no kit at all.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class BankedFireIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired FireService fire;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /** A hearth of this test's own, on ground no other test in the suite stands on. */
    private UUID ownGroundWithAPit(UUID chronicle, Instant now) {
        UUID chunk = jdbc.queryForObject(
            "SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y DESC, grid_x DESC LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        for (int i = 0; i < 6; i++) items.createCarriedItem(chronicle, "field_stone", "Field stone", now, "TEST_SEED");
        ChronicleActionService.ActionResult built = actions.resolve("build a fire pit");
        assertEquals("SUCCEEDED", built.outcome(), built::perception);
        return chunk;
    }

    private int fuelAt(UUID chunk) {
        return jdbc.queryForObject(
            "SELECT fs.fuel_minutes FROM fire_state fs JOIN world_object w ON w.id=fs.construction_id WHERE w.current_location_id=?",
            Integer.class, chunk);
    }

    private boolean bankedAt(UUID chunk) {
        return jdbc.queryForObject(
            "SELECT fs.banked FROM fire_state fs JOIN world_object w ON w.id=fs.construction_id WHERE w.current_location_id=?",
            Boolean.class, chunk);
    }

    @Test
    void bankedCoalsHoldLongAndThrowNothing() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        Instant now = ticks.current().simulatedAt();
        UUID chunk = ownGroundWithAPit(chronicle, now);

        // #871 FIRST, while the pack is still empty of a bearing block: the board, the spindle and the bow,
        // and nothing to hold the spindle's top. The refusal must name the part, not describe bare hands.
        items.createCarriedItem(chronicle, "hearth_board", "Hearth board", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "fire_spindle", "Fire spindle", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "fire_bow", "Fire bow", now, "TEST_SEED");
        ChronicleActionService.ActionResult short0 = actions.resolve("light the fire with the bow drill");
        assertEquals("FAILED", short0.outcome(), short0::perception);
        assertTrue(short0.perception().contains("bearing block"),
            () -> "a refusal must name what the method is short of, not describe empty hands: " + short0.perception());

        // Something that burns, standing beside it — the hazard needs a victim to be observable at all.
        // Raised BEFORE the fire is lit: building it is a resolved action, which moves the clock and may spend
        // dry branches, so raised afterwards it could burn a 45-minute fire down or leave the feedings below
        // nothing to put on (CI: "two feedings ... past the ember threshold" failed). The test owns its fuel.
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "hazel_rod", "Hazel rod", now, "TEST_SEED");
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "bark_sheet", "Bark sheet", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "fiber_cordage", "Fiber cordage", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "stone_knife", "Stone knife", now, "TEST_SEED");
        ChronicleActionService.ActionResult rack = actions.resolve("I build a fuel rack.");
        assertEquals("SUCCEEDED", rack.outcome(), rack::perception);
        now = ticks.current().simulatedAt();

        // Now the fire itself, lit through FireService so the test owns the ember rather than rolling for it.
        items.createCarriedItem(chronicle, "fire_socket", "Bearing block", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "tinder_nest", "Tinder nest", now, "TEST_SEED");
        for (int i = 0; i < 6; i++) items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "TEST_SEED");
        assertEquals(FireService.LightResult.LIT, fire.light(chronicle, chunk, now, true, "bow_drill"));
        assertFalse(bankedAt(chunk), "a fire just lit is an open flame, not banked");

        // The sky decides whether embers catch at all, and it is the world's, not this chunk's — put it back
        // afterwards so no other test in this shared world inherits it.
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        String skyWas = jdbc.queryForObject("SELECT weather_kind FROM world_weather WHERE world_id=?", String.class, world);
        try {
            jdbc.update("UPDATE world_weather SET weather_kind='CLEAR' WHERE world_id=?", world);

            // AN UNBANKED FIRE ABOVE THE THRESHOLD STILL BITES. The fix must not disarm the hazard, only stop
            // it reading duration as ferocity.
            items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "TEST_SEED");
            items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "TEST_SEED");
            assertTrue(fire.feed(chronicle, chunk, now), "a lit fire and a branch in hand: the first feeding must take");
            assertTrue(fire.feed(chronicle, chunk, now), "a lit fire and a branch in hand: the second feeding must take");
            assertTrue(fuelAt(chunk) >= 120, "two feedings on a fresh fire must carry it past the ember threshold");
            int before = integrityOfTheRack(chunk);
            fire.advanceTo(now.plus(Duration.ofHours(2)));
            assertTrue(integrityOfTheRack(chunk) < before,
                "a roaring, unbanked fire in dry weather must still char what stands beside it (#219)");

            // AND A BANKED ONE DOES NOT, at the same fuel or higher. This is the whole of #890: the careful
            // act, which RAISES the number, used to be the thing that set the camp alight.
            jdbc.update("UPDATE construction_project SET integrity_percent=100 WHERE object_id IN " +
                        "(SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
                        " WHERE w.current_location_id=? AND cp.project_kind='FUEL_RACK')", chunk);
            Instant banked = now.plus(Duration.ofHours(2));
            // The two hours above burned the fire down to whatever the feedings left past 120 — fifteen minutes
            // on a 135-minute fire — and bare-handed banking adds 90, which is 105: UNDER the threshold, so the
            // "banked coals throw nothing" check below would have passed for the wrong reason (CI: 105 < 120).
            // A poker rakes the coals tighter (+130), which carries a banked fire past the threshold whatever
            // the feedings left, so what is measured is the flag and not a fire too small to bite.
            items.createCarriedItem(chronicle, "fire_poker", "Fire poker", banked, "TEST_SEED");
            int fuelBeforeBanking = fuelAt(chunk);
            assertTrue(fire.bank(chronicle, chunk, banked), "there is a fire here to bank");
            assertTrue(bankedAt(chunk), "banking must be recorded, not merely add minutes");
            assertTrue(fuelAt(chunk) > fuelBeforeBanking, "banking raises the burn — that is what it is for");
            assertTrue(fuelAt(chunk) >= 120,
                "and past the ember threshold, which is why fuel alone could never say whether a fire bites: " + fuelAt(chunk));

            int intact = integrityOfTheRack(chunk);
            // A SPAN THE BANKED FIRE CAN LIVE THROUGH, taken from its own fuel rather than picked. Three
            // fixed hours is 180 minutes, and banking left it something over 120 — so the coals went OUT
            // partway and the hearth was cold when the reading was taken: "The stone fire pit is cold and
            // empty" (CI, #890). Both claims below were then measuring a dead fire, one of them passing for
            // that reason. Half the remaining fuel keeps it alight and banked for the whole window, which is
            // the state whose hazard is in question.
            Duration underAsh = Duration.ofMinutes(Math.max(30, fuelAt(chunk) / 2));
            Instant raked = banked.plus(underAsh);
            fire.advanceTo(raked);
            assertTrue(fuelAt(chunk) > 0,
                () -> "this measures a banked fire, so it must still be burning at the end of the span: fuel "
                      + fuelAt(chunk) + " after " + underAsh.toMinutes() + " minutes");
            assertEquals(intact, integrityOfTheRack(chunk),
                "coals raked tight under ash throw nothing: a banked hearth must not char what stands beside it");

            // The reading agrees with the act that produced it — the contradiction the player actually saw.
            String read = fire.fireReading(chronicle, chunk, raked, 0);
            assertTrue(read.contains("banked"), () -> "the reading must say the fire is banked: " + read);
            assertFalse(read.contains("throwing embers"),
                () -> "a banked fire must never be reported as throwing embers: " + read);

            // PUTTING A BRANCH ON OPENS THE FLAME AGAIN. A fire you have just fed is not banked, whatever it
            // was a minute ago, and the hazard must come back with the flame.
            fire.feed(chronicle, chunk, raked);
            assertFalse(bankedAt(chunk), "feeding a banked fire opens the flame again");
        } finally {
            jdbc.update("UPDATE world_weather SET weather_kind=? WHERE world_id=?", skyWas, world);
        }

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    private int integrityOfTheRack(UUID chunk) {
        return jdbc.queryForObject(
            "SELECT cp.integrity_percent FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "WHERE w.current_location_id=? AND cp.project_kind='FUEL_RACK'", Integer.class, chunk);
    }
}
