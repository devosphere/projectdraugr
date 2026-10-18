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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rest of the camp statements (#37).
 *
 * <p>Three members the first cut of this family left out, each reaching nothing while its own nearest verb
 * worked:
 *
 * <pre>
 *   the crop is dry        -&gt; nothing   |  the crop needs water -&gt; WATER_CROP
 *   the crop needs weeding -&gt; nothing   |  weed the crop        -&gt; WEED_CROP
 *   the gate is open       -&gt; nothing   |  mend the fence       -&gt; REPAIR_STRUCTURE
 *   the fence is down      -&gt; nothing
 * </pre>
 *
 * <p>The repair rule had been given the gate, pen and fence NOUNS without the conditions a person actually
 * reports them in. A fence that is down and a pen standing open are the two failures a keeper notices first, and
 * they are precisely what lets the stock out.
 *
 * <p><b>And the frame itself was missing a verb.</b> It knew the copulas and not "needs" or "wants", so
 * {@code the crop needs weeding} was the one form of the whole family that still fell through — while
 * {@code the crop needs water} only worked because an older rule happened to catch that exact phrase. A report
 * and a request are the same sentence here: "the crop needs weeding" says a thing about the crop exactly as
 * "the crop is dry" does.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class TheRestOfTheCampStatementsIntegrationTest {

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
    void theThreeThatWereLeftOut() {
        aLivingChronicle();
        Map<String, String> expected = Map.ofEntries(
            Map.entry("the crop is dry", "WATER_CROP"),
            Map.entry("the crop is wilting", "WATER_CROP"),
            Map.entry("the crop is parched", "WATER_CROP"),
            Map.entry("the crop needs weeding", "WEED_CROP"),
            Map.entry("the crop wants weeding", "WEED_CROP"),
            Map.entry("the field is overgrown", "WEED_CROP"),
            Map.entry("the gate is open", "REPAIR_STRUCTURE"),
            Map.entry("the pen is open", "REPAIR_STRUCTURE"),
            Map.entry("the fence is down", "REPAIR_STRUCTURE"));
        expected.forEach((said, want) -> {
            String got = intentOf(said);
            assertTrue(want.equals(got), () -> "'" + said + "' must reach " + want + ", not " + got);
        });
    }

    @Test
    void needsAndWantsAreCopulasHere() {
        // The frame knew is/are/has/have and not needs/wants, so this was the one form of the family that still
        // fell through — while "the crop needs water" worked only because an older rule caught that exact
        // phrase. Held on BOTH verbs so neither can be dropped without the test saying so.
        aLivingChronicle();
        assertTrue("WEED_CROP".equals(intentOf("the crop needs weeding")), "needs is a copula here");
        assertTrue("WEED_CROP".equals(intentOf("the crop wants weeding")), "and so is wants");
        assertTrue("WATER_CROP".equals(intentOf("the crop needs water")), "and the phrase that already worked still does");
    }

    @Test
    void theFamilyAlreadyShippedIsUntouched() {
        // The frame is shared, so widening its verbs could have changed any of these. Each is held here because
        // a regression in one would otherwise only show up by playing.
        aLivingChronicle();
        Map<String, String> already = Map.ofEntries(
            Map.entry("the fire is dying", "FEED_FIRE"),
            Map.entry("the fire has gone out", "LIGHT_FIRE"),
            Map.entry("the roof is leaking", "REPAIR_STRUCTURE"),
            Map.entry("the trap is sprung", "CHECK_TRAP"),
            Map.entry("my waterskin is empty", "COLLECT_WATER"));
        already.forEach((said, want) -> {
            String got = intentOf(said);
            assertTrue(want.equals(got), () -> "'" + said + "' must keep " + want + ", not " + got);
        });
    }

    @Test
    void theActsThemselvesAreUntouched() {
        aLivingChronicle();
        for (String phrase : List.of("weed the crop", "water the crop", "harvest the barley", "sow the field")) {
            String got = intentOf(phrase);
            assertTrue(List.of("WEED_CROP", "WATER_CROP", "HARVEST_CROP", "SOW").contains(got),
                () -> "'" + phrase + "' keeps its own work: -> " + got);
        }
        assertTrue("CHECK_CROP".equals(intentOf("is the crop ready")), "and asking after the crop still reports");
    }

    @Test
    void openingAGateIsNotRepairingOne() {
        // "the gate is open" is a report of a failure; "open the gate" is a deliberate act. The frame only
        // claims the statement form, so the imperative is left alone rather than being read as damage.
        aLivingChronicle();
        assertTrue(!"REPAIR_STRUCTURE".equals(intentOf("open the gate")),
            () -> "opening a gate on purpose is not a fault to mend: -> " + intentOf("open the gate"));
    }
}
