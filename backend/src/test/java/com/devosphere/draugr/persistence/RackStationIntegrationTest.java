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

    /**
     * How many times each cohort dries, and why it is this many.
     *
     * <p>The yield is a roll over 2..4: unassisted it is one roll (mean 3.0), at a station the better of two (mean
     * 3.44). Forty rounds separated those means by only about two and a half standard deviations — roughly a one in
     * two hundred failure, which duly happened twice in CI and read as a flake rather than as a test that was asking
     * too little. A hundred and fifty rounds puts the separation past five, which is a margin a coin toss does not
     * reach. Kept as a named constant because it is a statistical decision, not a magic number.
     */
    private static final int ROUNDS = 150;

    private int carried(UUID chronicle, String key) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key=? AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, key, chronicle);
        return n == null ? 0 : n;
    }

    /**
     * Dry the same mushrooms the same number of times, and count what comes off — and how many of the rounds
     * actually dried anything. The yield per SUCCESSFUL round is the thing this test reasons about, so a round that
     * failed for some unrelated reason must not be counted as a round that yielded nothing: that would drag both
     * averages down and make the bands below lie about what the roll did.
     */
    private Drying dryRepeatedly(UUID chronicle, UUID chunk, Instant now, int rounds) {
        int made = 0, dried = 0;
        for (int i = 0; i < rounds; i++) {
            for (int j = 0; j < 4; j++) items.createCarriedItem(chronicle, "oyster_mushroom", "Oyster mushroom", now, "TEST_SEED");
            int before = carried(chronicle, "dried_mushroom");
            String[] r = items.runProcess(chronicle, chunk, "dry the mushrooms", now);
            if ("SUCCEEDED".equals(r[0])) { made += carried(chronicle, "dried_mushroom") - before; dried++; }
        }
        return new Drying(made, dried);
    }

    /** What a batch of drying produced, and over how many rounds that actually dried. */
    private record Drying(int made, int rounds) {
        double perRound() { return rounds == 0 ? 0 : made / (double) rounds; }
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

        // Control the OTHER thing that skews this same yield. A drying mat in reach biases `dry_mushrooms` exactly
        // as a rack does (`atStation || toolAssist`), so one lying here — carried or set down — would give the bare
        // ground the rack's own advantage and this test would compare two identical cohorts and pass or fail on a
        // coin toss. Nothing in the fixture makes one; that is the point of saying so out loud and clearing it.
        UUID elsewhere = jdbc.queryForObject(
            "SELECT id FROM world_chunk WHERE id <> ? ORDER BY grid_y DESC, grid_x DESC LIMIT 1", UUID.class, chunk);
        jdbc.update("UPDATE world_object w SET current_owner_id=NULL, current_location_id=? " +
            "FROM item_instance i WHERE i.object_id=w.id AND i.item_key='drying_mat' " +
            "  AND (w.current_owner_id=? OR w.current_location_id=?)", elsewhere, chronicle, chunk);

        // A station eases and never gates: drying on the ground must still succeed. This is the half that would
        // break if a station were ever allowed to become a requirement.
        Drying bareGround = dryRepeatedly(chronicle, chunk, now, ROUNDS);
        int withoutRack = bareGround.made();
        assertTrue(withoutRack > 0, "drying without a rack must still work — a station eases, it never gates");
        assertTrue(bareGround.rounds() >= ROUNDS * 9 / 10,
            () -> "the bare-ground cohort must actually dry: only " + bareGround.rounds() + " of " + ROUNDS
                + " rounds succeeded, so the averages below are measuring something other than the roll");

        // Raise a drying rack on the same ground and dry the same way again.
        UUID rack = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) VALUES (?,'CONSTRUCTION','Drying rack','ACTIVE',?)", rack, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) VALUES (?,'DRYING_RACK','COMPLETED',100,?,100)", rack, Timestamp.from(now));

        Drying onTheRack = dryRepeatedly(chronicle, chunk, now, ROUNDS);
        int withRack = onTheRack.made();

        // Each cohort is also checked against the yield it should HAVE, not only against the other. A comparison
        // alone cannot tell "the rack did nothing" from "both cohorts had the rack's advantage" — which is exactly
        // how this test failed twice in CI with the two totals a hair apart, and it named neither cause. The bare
        // ground rolls once over 2..4 (mean 3.0); at a station it takes the better of two rolls (mean 3.44).
        double bare = bareGround.perRound(), racked = onTheRack.perRound();
        assertTrue(bare < 3.25,
            () -> "the bare ground is yielding as though a station stood on it (" + bare + " a round against an "
                + "unassisted 3.0) — something else in reach is biasing this roll, and the comparison below proves "
                + "nothing until it is found");
        assertTrue(racked > 3.2,
            () -> "a drying rack must actually bias the roll high (" + racked + " a round against an assisted 3.44)");
        assertTrue(racked > bare,
            () -> "a drying rack must be worth raising — it skews the yield high, so a round on it must out-yield a "
                + "round on the bare ground (" + racked + " against " + bare + "; totals " + withRack + " and "
                + withoutRack + " over " + onTheRack.rounds() + " and " + bareGround.rounds() + " rounds)");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
