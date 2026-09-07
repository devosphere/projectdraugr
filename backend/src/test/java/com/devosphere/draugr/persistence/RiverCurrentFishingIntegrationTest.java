package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
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

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the water runs decides what works in it (#156).
 *
 * <p>The rule under test is the fast/slow distinction the ticket asks for as topology rather than labels. A fixed
 * trap or a net in fast water does not have to find the fish — the current delivers them into it, which is the
 * principle a weir is built on. A hand-line is the reverse: swept off the hold in a fast stream, sitting where the
 * fish lie in a slow reach.
 *
 * <p>{@link #aRiverHoldsFish} guards the ground the rule stands on. {@code fish()} picks what a stretch holds by
 * matching {@code biome_affinity} against the chunk's biome, so if the running-water species V269 placed in
 * RIVER_BANK were ever narrowed back to the marsh, this whole comparison would quietly become a comparison
 * between two empty rivers — both cohorts zero, and the reason invisible.
 *
 * <p>Both are asserted over cohorts of many attempts, because a single cast proves nothing about odds, and every
 * cohort is checked to contain both catches and failures — a comparison between two saturated cohorts would be
 * green and worthless whatever the rule underneath said. Skips without Docker.
 */
@SpringBootTest
class RiverCurrentFishingIntegrationTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final int CASTS = 160;

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
    @Autowired WildlifeEncounterService wildlife;
    @Autowired PhysicalItemService items;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    private UUID sited(String kind) {
        return jdbc.query("SELECT chunk_id FROM ecology_site WHERE site_kind=? LIMIT 1",
            rs -> rs.next() ? (UUID) rs.getObject(1) : null, kind);
    }

    private UUID fisher(Instant now) {
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, " +
            "maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        return chronicle;
    }

    /**
     * Fish a stretch many times with one method. The stock is cleared before each cast, because finite water is a
     * different rule with its own test and a cohort this size would otherwise fish the stretch out and measure
     * depletion instead of current.
     */
    private int catchesOver(UUID chronicle, UUID water, String actionText, Instant now) {
        int caught = 0;
        for (int i = 0; i < CASTS; i++) {
            jdbc.update("DELETE FROM fish_stock WHERE chunk_id=?", water);
            var result = wildlife.fish(chronicle, water, UUID.nameUUIDFromBytes((actionText + ":" + i).getBytes()), now, actionText);
            if ("SUCCEEDED".equals(result.outcome())) caught++;
        }
        return caught;
    }

    /** The river must hold fish at all — everything else here is a comparison between two empty rivers otherwise. */
    @Test
    void aRiverHoldsFish() {
        world();
        Instant now = Instant.now();

        Integer inRivers = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wildlife_species WHERE movement_class='AQUATIC' AND biome_affinity ILIKE '%RIVER_BANK%'",
            Integer.class);
        assertTrue(inRivers != null && inRivers >= 10,
            "running water must keep the cast V269 gave it — trout, chub, dace, minnow, perch, pike, eel, lamprey, "
                + "sturgeon, bream, catfish, crayfish — or the current rule below compares two empty rivers");

        UUID water = sited("Slow river reach");
        assertNotNull(water, "the world must place a slow river reach");
        UUID chronicle = fisher(now);
        items.createCarriedItem(chronicle, "fish_trap", "Woven fish trap", now, "CRAFTED");
        jdbc.update("DELETE FROM fish_stock WHERE chunk_id=?", water);

        var result = wildlife.fish(chronicle, water, UUID.randomUUID(), now, "set the trap in the river");
        assertNotEquals("You watch the ground a while. There is no water here that holds anything worth taking.",
            result.narration(),
            "a river with nothing in it is the defect this asserts against — a Chronicle may fail to catch, but "
                + "must not be told the river holds nothing");
    }

    /** A trap works with the current; a line works against it. Same gear, two waters, opposite results. */
    @Test
    void aTrapWantsFastWaterAndALineWantsSlow() {
        world();
        Instant now = Instant.now();

        UUID fast = sited("Fast stream");
        UUID slow = sited("Slow river reach");
        assertNotNull(fast, "the world must place a fast stream");
        assertNotNull(slow, "the world must place a slow river reach");
        assertNotEquals(fast, slow, "the two waters must be two stretches, or this compares a chunk with itself");

        UUID chronicle = fisher(now);
        items.createCarriedItem(chronicle, "fish_trap", "Woven fish trap", now, "CRAFTED");
        int trapFast = catchesOver(chronicle, fast, "set the trap in the water", now);
        int trapSlow = catchesOver(chronicle, slow, "set the trap in the water", now);

        // A separate body, so the trap is not still to hand and picked ahead of the line.
        jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED' WHERE current_owner_id=? " +
            "AND id IN (SELECT object_id FROM item_instance WHERE item_key='fish_trap')", chronicle);
        items.createCarriedItem(chronicle, "bone_fish_hook", "Bone fish hook", now, "CRAFTED");
        int lineFast = catchesOver(chronicle, fast, "fish the water with hook and line", now);
        int lineSlow = catchesOver(chronicle, slow, "fish the water with hook and line", now);

        for (int cohort : new int[]{ trapFast, trapSlow, lineFast, lineSlow })
            assertTrue(cohort > 0 && cohort < CASTS,
                "every cohort must both catch and fail, or this compares two certainties and proves nothing "
                    + "(trapFast=" + trapFast + ", trapSlow=" + trapSlow + ", lineFast=" + lineFast
                    + ", lineSlow=" + lineSlow + " of " + CASTS + ")");

        assertTrue(trapFast > trapSlow,
            () -> "a fixed trap is what fast water is for — the current carries the fish into it (fast=" + trapFast
                + ", slow=" + trapSlow + ")");
        assertTrue(lineSlow > lineFast,
            () -> "a hand-line wants slack water; in a fast stream the bait is swept off the hold (slow=" + lineSlow
                + ", fast=" + lineFast + ")");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** Ordinary water, named neither way, must go on fishing exactly as it did. */
    @Test
    void waterTheWorldSaysNothingAboutIsUnchanged() {
        world();
        Instant now = Instant.now();
        UUID plain = jdbc.query(
            "SELECT c.id FROM world_chunk c WHERE c.biome='WETLAND' " +
            "AND NOT EXISTS (SELECT 1 FROM ecology_site es WHERE es.chunk_id=c.id " +
            "  AND (es.site_kind ILIKE '%fast stream%' OR es.site_kind ILIKE '%slow river%')) " +
            "ORDER BY c.grid_y, c.grid_x LIMIT 1", rs -> rs.next() ? (UUID) rs.getObject(1) : null);
        Assumptions.assumeTrue(plain != null, "this world laid down no unnamed wetland to contrast with");

        UUID chronicle = fisher(now);
        items.createCarriedItem(chronicle, "fish_trap", "Woven fish trap", now, "CRAFTED");
        int caught = catchesOver(chronicle, plain, "set the trap in the water", now);
        assertTrue(caught > 0 && caught < CASTS,
            "unnamed water still fishes as it always did — neither made certain nor made hopeless (" + caught + ")");
    }
}
