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

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A recipe's two halves must agree about the sentence (#37, #99).
 *
 * <p>A process is reached only when its CATEGORY, one of its KEYWORDS and one of its SUBJECT TERMS all appear in
 * the sentence — and then one last guard asks whether the sentence was asking for WORK at all, so that
 * "wash the bowl" cannot reach the carving of one. Two recipes can therefore fail a sentence they both describe
 * perfectly, in two different places:
 *
 * <pre>
 *   taper the billet   ->  nothing at all     `taper` IS a keyword of shape_components
 *                                             `billet` IS a subject term of shape_components
 *   cut a mortise      ->  nothing at all     `cut a mortise` IS a keyword of cut_mortise
 *                                             but its subject term was `mortised`, never `mortise`
 * </pre>
 *
 * <p>The first was the guard asking the wrong half: it looked for "billet" among the KEYWORDS, where a subject
 * term was never going to be. The second was the catalogue disagreeing with itself — the same defect V459 found
 * once in {@code shape sling stones}, and V462 found a hundred and eleven more times.
 *
 * <p>Both are asserted here against a real world, and so is the guard they must not weaken: a sentence about
 * washing a plank is still not a request to shape one.
 *
 * <p>Deterministic. Skips without Docker.
 */
@SpringBootTest
class TheRecipeAgreesWithItselfIntegrationTest {

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

    @Test
    void theWordTheRecipeCallsItsMaterialReachesThatRecipe() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID me = summary.id();
        UUID here = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, me);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, " +
            "maximum_single_lift_grams=100000000 WHERE chronicle_id=?", me);
        Instant now = ticks.current().simulatedAt();

        // A bench to work at and a blade to work with — shape_components and cut_mortise both want a CUTTING
        // tool and a woodworking bench, and neither of those is what is being tested.
        items.createCarriedItem(me, "stone_chisel", "Stone chisel", now, "TEST_FIXTURE");
        items.createCarriedItem(me, "woodworking_bench", "Woodworking bench", now, "TEST_FIXTURE");

        // THE GUARD ASKED THE WRONG HALF. `taper` is one of shape_components' own keywords and `billet` is one
        // of its own subject terms, so both halves of that recipe claim this sentence — and it reached nothing,
        // because the guard looked for the material among the keywords.
        items.createCarriedItem(me, "timber_plank", "Timber plank", now, "TEST_FIXTURE");
        String[] tapered = items.runProcess(me, here, "taper the billet", now);
        assertEquals("SUCCEEDED", tapered[0],
            () -> "`taper` is a keyword of shape_components and `billet` one of its subject terms, so the work "
                + "must happen: " + tapered[1]);

        // And the phrasing that always worked must keep working, because `plank` happens to sit in the keywords
        // as well — that difference is what made this look like a phrasing problem instead of a guard problem.
        //
        // `trim the plank`, NOT `shape the plank` — replayed through the live catalogue's classify() and
        // resolveForPlay(), not assumed. `shape the plank` never worked and is a different defect: `shape` is a
        // keyword of lap_joint_planks too and `plank` one of split_planks, so three recipes tie at equal length,
        // and the matcher settles that tie by whose OUTPUT the sentence names — which keeps the two that MAKE
        // planks (timber_plank, lapped_plank) and drops the one that WORKS a plank (wooden_component) before what
        // is in reach is ever asked. Nor `taper the plank`: `taper` is no category term, so the sentence is
        // matched across the whole catalogue and split_planks' bare `plank` takes it. Both recorded on #37, and
        // both since settled (V463 + ProcessMatcher.resolve's work-outranks-material step); this control stays
        // `trim the plank` because it is the phrasing that worked BEFORE either change.
        items.createCarriedItem(me, "timber_plank", "Timber plank", now, "TEST_FIXTURE");
        String[] planed = items.runProcess(me, here, "trim the plank", now);
        assertEquals("SUCCEEDED", planed[0], () -> "the phrasing that worked before must still work: " + planed[1]);

        // THE CATALOGUE DISAGREEING WITH ITSELF. `cut a mortise` is declared by cut_mortise word for word, and
        // the only subject term anywhere near it was `mortised` — the finished beam, not the cut.
        items.createCarriedItem(me, "structural_timber", "Structural timber", now, "TEST_FIXTURE");
        String[] mortised = items.runProcess(me, here, "cut a mortise in the beam", now);
        assertEquals("SUCCEEDED", mortised[0], () -> "naming the beam has always worked: " + mortised[1]);

        items.createCarriedItem(me, "structural_timber", "Structural timber", now, "TEST_FIXTURE");
        String[] bare = items.runProcess(me, here, "cut a mortise", now);
        assertEquals("SUCCEEDED", bare[0],
            () -> "`cut a mortise` is a phrase the recipe advertises, so it must reach the recipe: " + bare[1]);

        // AND THE GUARD MUST STILL GUARD. Washing a plank is not shaping one, and the whole reason that guard
        // exists is that 1,317 of the catalogue's keywords are bare nouns any sentence can carry.
        items.createCarriedItem(me, "timber_plank", "Timber plank", now, "TEST_FIXTURE");
        String[] washed = items.runProcess(me, here, "wash the plank", now);
        assertEquals("FAILED", washed[0],
            () -> "washing a plank is not a request to shape one — the guard must not have been widened: " + washed[1]);
        assertTrue(washed[1].contains("no way to work it") || washed[1].contains("not a thing your hands"),
            () -> "and it must fail as the honest miss, not as a failed shaping: " + washed[1]);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
