package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Saying what a thing in your camp is doing (#37).
 *
 * <p>The same shape as a statement about one's own body, one level out — and two of these were not misses but
 * acts in the wrong direction:
 *
 * <pre>
 *   the trap is sprung    -&gt; SNARE — it tried to BUILD a snare
 *   my waterskin is empty -&gt; EMPTY_CONTAINER — it offered to EMPTY it
 *   the roof is leaking   -&gt; the timber recipe, offering to rive roof shakes
 *   the wall is crumbling -&gt; the wattle recipe
 *   the fire is dying / has gone out / is low -&gt; nothing at all
 *   the shelter is leaking / falling apart    -&gt; nothing at all
 *   the fence is broken                       -&gt; nothing at all
 *   repair the shelter                        -&gt; nothing at all
 * </pre>
 *
 * <p>Saying a thing is empty and being offered the chance to empty it is the plainest inversion in the composer.
 * And {@code repair the shelter} reached nothing while {@code mend the fence} and {@code fix the roof} both
 * worked, because "shelter" was not among the structure nouns — a lean-to being the first thing most Chronicles
 * build and the first thing the weather takes apart.
 *
 * <p><b>Each family goes where the camp's own nearest verb already goes</b>, so nothing here invents a routing
 * decision: {@code add wood to the fire} is FEED_FIRE, {@code relight the fire} is LIGHT_FIRE,
 * {@code mend the fence} is REPAIR_STRUCTURE, {@code check the trap} is CHECK_TRAP, {@code fill my waterskin} is
 * COLLECT_WATER. Each answers a bare statement honestly when there is nothing to be done — "you look for
 * something built here to set right, but the ground is bare".
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class SayingWhatTheCampIsDoingIntegrationTest {

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

    private void aLivingChronicle() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        assertNotNull(chronicles.active() != null ? chronicles.active() : chronicles.awaken(),
            "a living Chronicle is needed");
    }

    private String intentOf(String text) {
        var r = actions.resolve(text, UUID.randomUUID());
        assertNotNull(r, () -> "the composer must answer: " + text);
        return r.intent();
    }

    @Test
    void sayingItGoesWhereTheCampsOwnVerbAlreadyGoes() {
        aLivingChronicle();
        Map<String, String> expected = Map.ofEntries(
            Map.entry("the fire is dying", "FEED_FIRE"),
            Map.entry("the fire is low", "FEED_FIRE"),
            Map.entry("the fire is going out", "FEED_FIRE"),
            Map.entry("the fire has gone out", "LIGHT_FIRE"),
            Map.entry("the fire is dead", "LIGHT_FIRE"),
            Map.entry("the shelter is leaking", "REPAIR_STRUCTURE"),
            Map.entry("the shelter is falling apart", "REPAIR_STRUCTURE"),
            Map.entry("the roof is leaking", "REPAIR_STRUCTURE"),
            Map.entry("the fence is broken", "REPAIR_STRUCTURE"),
            Map.entry("the wall is crumbling", "REPAIR_STRUCTURE"),
            Map.entry("the trap is sprung", "CHECK_TRAP"),
            Map.entry("my waterskin is empty", "COLLECT_WATER"),
            Map.entry("the bucket is dry", "COLLECT_WATER"));
        expected.forEach((said, want) -> {
            String got = intentOf(said);
            assertTrue(want.equals(got), () -> "'" + said + "' must reach " + want + ", not " + got);
        });
    }

    @Test
    void sayingATrapWentOffDoesNotBuildOne() {
        // THE WORST OF THESE. A statement that a trap had sprung reached SNARE, which is the hand-worked
        // snaring — "you crouch over the run and find you have nothing to build a snare from". It failed only
        // for want of cordage; with fibre in hand it would have BUILT a trap in answer to a report that one had
        // gone off.
        aLivingChronicle();
        for (String said : List.of("the trap is sprung", "the snare has gone off", "the deadfall is tripped")) {
            String got = intentOf(said);
            assertFalse("SNARE".equals(got) || "SET_TRAP".equals(got),
                () -> "a report that a trap went off must not build one: '" + said + "' -> " + got);
        }
    }

    @Test
    void sayingAVesselIsEmptyIsNotAskingToEmptyIt() {
        // The plainest inversion in the composer: "my waterskin is empty" matched EMPTY_CONTAINER's own word
        // and was answered "There is no container within reach to empty" — offered the chance to empty the very
        // thing whose emptiness was being reported. The statement is a request to FILL.
        aLivingChronicle();
        assertTrue("COLLECT_WATER".equals(intentOf("my waterskin is empty")),
            () -> "an empty skin wants filling: -> " + intentOf("my waterskin is empty"));
        // And the act keeps its own words. Both sites read one pair of lists, so they cannot drift into
        // disagreeing about what counts as a vessel.
        assertTrue("EMPTY_CONTAINER".equals(intentOf("empty the waterskin")), "emptying one is still emptying it");
        assertTrue("EMPTY_CONTAINER".equals(intentOf("tip out the basket")), "and so is tipping one out");
    }

    @Test
    void repairingAShelterIsRepairingAStructure() {
        aLivingChronicle();
        assertTrue("REPAIR_STRUCTURE".equals(intentOf("repair the shelter")),
            () -> "a shelter is a structure: -> " + intentOf("repair the shelter"));
        // The lean-to keeps its own dedicated intent, which is more specific than the general repair.
        assertTrue("REPAIR_LEAN_TO".equals(intentOf("repair the lean-to")),
            () -> "the lean-to has its own work: -> " + intentOf("repair the lean-to"));
    }

    @Test
    void theRecipesThatSHAREThoseNounsKeepTheirPhrases() {
        // `the roof is leaking` used to reach `rive roof shakes` and `the wall is crumbling` the wattle panel.
        // Those recipes must still answer to their own names — the statement was taken from them, not the work.
        aLivingChronicle();
        assertTrue("PROCESS_MATERIAL".equals(intentOf("rive roof shakes")), "riving shakes is still the timber work");
        assertTrue("PROCESS_MATERIAL".equals(intentOf("make a wattle panel")), "and weaving a panel still the wattle");
    }

    @Test
    void theActsThemselvesAreUntouched() {
        aLivingChronicle();
        assertTrue("FEED_FIRE".equals(intentOf("add wood to the fire")), "feeding a fire keeps its words");
        assertTrue("LIGHT_FIRE".equals(intentOf("relight the fire")), "and relighting one");
        assertTrue("REPAIR_STRUCTURE".equals(intentOf("mend the fence")), "and mending a fence");
        assertTrue("CHECK_TRAP".equals(intentOf("check the trap")), "and checking a trap");
        assertTrue("SET_TRAP".equals(intentOf("set a snare")), "and setting one");
        assertTrue("COLLECT_WATER".equals(intentOf("fill my waterskin")), "and filling a skin");
    }
}
