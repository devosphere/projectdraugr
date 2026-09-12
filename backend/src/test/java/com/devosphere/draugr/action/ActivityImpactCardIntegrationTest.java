package com.devosphere.draugr.action;

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

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The impact-card gate (#216, EPIC #215, V307).
 *
 * <p>The ticket's acceptance criterion is that "no procedure can be activated without a complete impact card".
 * Before V307 what a procedure did to the land was decided in four places in Java — a switch over five intents
 * and three recordings written inline into the felling, coppicing and clearing dispatch lines — and the other
 * hundred and twenty-one intents left nothing, not because anybody had decided that but because nobody had ever
 * been asked. Silence was the default and nothing could see it.
 *
 * <p>{@code activity_impact} now holds a card for every intent, including the ones that mark nothing, which must
 * carry a written reason the table's own CHECK enforces. This test is the gate: it asserts the enum and the
 * table are the same set in BOTH directions, so an intent added without a card fails here instead of joining the
 * silent majority, and a card for an intent that no longer exists is caught rather than sitting unread.
 *
 * <p>And it proves the card is what actually drives the world, because a card that only described the Java would
 * be a fresh instance of exactly the declared-but-ignored defect it was written to end.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class ActivityImpactCardIntegrationTest {

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

    /** The Intent enum is private to the service, which is right — it is not part of any contract but this one. */
    private static Set<String> everyIntent() {
        Class<?> intent = Arrays.stream(ChronicleActionService.class.getDeclaredClasses())
            .filter(c -> c.isEnum() && "Intent".equals(c.getSimpleName()))
            .findFirst().orElseThrow(() -> new AssertionError("ChronicleActionService no longer declares an Intent enum"));
        Set<String> names = new TreeSet<>();
        for (Object constant : intent.getEnumConstants()) names.add(((Enum<?>) constant).name());
        return names;
    }

    private int disturbanceEventsAt(UUID chunk) {
        return jdbc.queryForObject("SELECT count(*) FROM chunk_disturbance_event WHERE chunk_id=?", Integer.class, chunk);
    }

    /**
     * The gate. Both directions, because each catches a different mistake: a new intent shipped without anybody
     * deciding what it does to the world, and a card left behind for an intent that has been removed.
     */
    @Test
    void everyIntentHasACardAndEveryCardHasAnIntent() {
        Set<String> intents = everyIntent();
        Set<String> carded = new TreeSet<>(jdbc.queryForList("SELECT intent_key FROM activity_impact", String.class));

        Set<String> uncarded = new LinkedHashSet<>(intents);
        uncarded.removeAll(carded);
        assertTrue(uncarded.isEmpty(),
            "these procedures can be performed and nobody has said what they do to the ground they are done on — "
          + "add a row to activity_impact, and if the answer is 'nothing' the notes must say why: " + uncarded);

        Set<String> orphaned = new LinkedHashSet<>(carded);
        orphaned.removeAll(intents);
        assertTrue(orphaned.isEmpty(),
            "these cards describe procedures that no longer exist, so nothing reads them: " + orphaned);
    }

    /** A card that marks nothing is a decision, and a decision has to be written down. */
    @Test
    void everySilentCardSaysWhyItIsSilent() {
        List<String> mute = jdbc.queryForList(
            "SELECT intent_key FROM activity_impact WHERE footprint_kind IS NULL AND length(btrim(notes)) < 40 ORDER BY 1",
            String.class);
        assertTrue(mute.isEmpty(), "leaving the land unmarked is a claim about the act, and these make it without argument: " + mute);
    }

    /**
     * The card drives the world. Two acts on the same ground, one that the table says marks it and one that the
     * table says does not, both through the real router — so this fails if the reading is wrong, if the wiring
     * is wrong, or if the row is wrong.
     *
     * <p>Clay is the act chosen because it is the one whose success does not depend on a roll: gatherClay yields
     * two lumps on a river bank, every time, so a missing disturbance row means a missing disturbance and not an
     * unlucky afternoon.
     */
    @Test
    void theCardIsWhatActuallyMarksTheGround() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID bank = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='RIVER_BANK' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(bank, "the world must hold a river bank");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", bank, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        // The card for GATHER_CLAY says EXCAVATION 15, felt where it happens and not beyond.
        int before = disturbanceEventsAt(bank);
        ChronicleActionService.ActionResult dug = actions.resolve("dig clay from the bank");
        assertEquals("GATHER_CLAY", dug.intent(), () -> "the phrase must reach the clay dig: " + dug.perception());
        assertEquals("SUCCEEDED", dug.outcome(), () -> "a river bank yields clay every time: " + dug.perception());
        assertEquals(before + 1, disturbanceEventsAt(bank), "digging clay must mark the ground its card says it marks");
        assertEquals("EXCAVATION", jdbc.queryForObject(
            "SELECT source_kind FROM chunk_disturbance_event WHERE chunk_id=? ORDER BY occurred_at DESC LIMIT 1", String.class, bank),
            "and it must mark it as the card says, not as something else");
        assertEquals(15, (int) jdbc.queryForObject(
            "SELECT amount FROM chunk_disturbance_event WHERE chunk_id=? ORDER BY occurred_at DESC LIMIT 1", Integer.class, bank),
            "at the amount the card gives");

        // And a card that says the ground is left as it was found must mean it. Cutting a blaze into bark is a
        // mark a person reads; the land does not feel it.
        int marked = disturbanceEventsAt(bank);
        ChronicleActionService.ActionResult blaze = actions.resolve("carve a blaze into the tree");
        assertEquals("MARK", blaze.intent(), () -> "the phrase must reach marking: " + blaze.perception());
        assertEquals(marked, disturbanceEventsAt(bank), "a blaze leaves no footprint, and its card says as much");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
