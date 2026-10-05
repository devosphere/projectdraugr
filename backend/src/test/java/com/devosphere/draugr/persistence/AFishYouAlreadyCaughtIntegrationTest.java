package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A fish you already caught (#37, V396).
 *
 * <p>Two defects that met on the same sentence, with the plainest survival act there is sitting between them.
 *
 * <p><b>The fire cooked exactly one food.</b> {@code cookGameMeat} was written against two string literals —
 * {@code raw_game_meat} in, {@code cooked_game_meat} out — so of the raw foods the catalogue holds (game meat, a
 * whole fish, a gutted fish, fowl off a snare) only game meat could ever be cooked. Every one of them could
 * already be smoked, dried, salted, brined, split and cured by a named process; only one could be put over a
 * fire and eaten. There was no cooked fish and no cooked fowl in the catalogue at all.
 *
 * <p><b>And asking to cook a fish never reached the fire anyway.</b> The FISH intent was gated on the NOUN: any
 * sentence containing "fish" that was not a process meant <i>go fishing</i>. So a Chronicle standing over the
 * fish they had just landed was sent back to the water for every sentence about it:
 *
 * <pre>
 *   eat the fish    -> FISH      cook the fish      -> FISH
 *   carry the fish  -> FISH      count the fish     -> FISH
 *   look at it      -> FISH      bring the fish in  -> FISH
 * </pre>
 *
 * <p>Fishing is now an ACT — fishing as a verb, a line, or a taking verb against a fish or a named species —
 * and what the fire turns into what is a <b>table</b>, so the next raw food is a row rather than another pair of
 * string literals. Each pair must lose mass, which the migration enforces, because cooking drives off water and
 * a cooked output heavier than its raw input would be matter from nothing.
 *
 * <p>Two further things the sweep turned up and this class pins:
 * <ul>
 *   <li>the spoken words live in the row, because nobody says "cook the raw fowl meat" — a first cut derived
 *       them from the item key, matched nothing, and <b>cooked game meat when asked for fowl</b>;</li>
 *   <li>a <b>past participle names a food, and the verb asks for work</b>. The rule asked
 *       {@code contains("cook")}, and "cooked" contains "cook", so "eat the cooked fish" put another fish on
 *       the fire. That was waiting in the rule before V396 widened it: "eat the cooked meat" has always cooked
 *       more meat.</li>
 * </ul>
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class AFishYouAlreadyCaughtIntegrationTest {

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

    private Timestamp clockWas;

    @BeforeEach
    void pinTheClock() {
        clockWas = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class);
        jdbc.update("UPDATE simulation_clock SET simulated_at = TIMESTAMPTZ '2031-06-10T12:00:00Z' WHERE id=1");
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update = (SELECT simulated_at FROM simulation_clock WHERE id=1), " +
            "hours_without_food=0, hours_without_water=0, sleep_debt_hours=0, injury_severity=0, illness_severity=0, " +
            "blood_loss_ml=0, energy_level=100");
    }

    @AfterEach
    void unpinTheClock() {
        if (clockWas != null) jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", clockWas);
    }

    private UUID awaken() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        return summary.id();
    }

    /** A live fire on this ground, dated from the simulation clock so it is not already burnt out or unborn. */
    private void lightAFire(UUID chronicle) {
        UUID where = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        String kind = jdbc.queryForObject("SELECT project_kind FROM construction_kind WHERE holds_fire LIMIT 1", String.class);
        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Hearth',?)", pit, where);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,integrity_percent," +
            "last_structural_update,completed_at) VALUES (?,?,'COMPLETED',100,100," +
            "(SELECT simulated_at FROM simulation_clock WHERE id=1),(SELECT simulated_at FROM simulation_clock WHERE id=1))", pit, kind);
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,600," +
            "(SELECT simulated_at FROM simulation_clock WHERE id=1))", pit);
    }

    private void carry(UUID chronicle, String itemKey) {
        UUID id = UUID.randomUUID();
        String name = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, itemKey);
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM',?,?)", id, name, chronicle);
        jdbc.update("INSERT INTO item_instance (object_id,item_key) VALUES (?,?)", id, itemKey);
    }

    private int owned(UUID chronicle, String itemKey) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key=? AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, itemKey, chronicle);
        return n == null ? 0 : n;
    }

    @Test
    void aFishYouAlreadyHaveIsNotAReasonToGoFishing() {
        awaken();
        for (String said : List.of(
                "eat the fish", "cook the fish", "carry the fish", "drop the fish",
                "count the fish", "look at the fish", "bring the fish in",
                "store the fish", "put the fish away", "smoke the fish",
                "dry the fish", "salt the fish", "gut the fish", "fillet the fish")) {
            ChronicleActionService.ActionResult r = actions.resolve(said);
            assertNotEquals("FISH", r.intent(),
                () -> "\"" + said + "\" is about a fish you have, not a reason to go after one: "
                    + r.intent() + " / " + r.perception());
        }
    }

    @Test
    void andGoingFishingStillMeansGoingFishing() {
        awaken();
        for (String said : List.of(
                "fish", "go fishing", "catch a fish", "fish with the net", "fish for trout",
                "try to catch a fish", "spear a pike", "hook an eel", "cast a line",
                "fish the river", "net some fish", "land a trout")) {
            assertEquals("FISH", actions.resolve(said).intent(),
                () -> "\"" + said + "\" is going fishing: " + actions.resolve(said).intent());
        }
    }

    @Test
    void theFireCooksWhatTheTableSaysItCooks() {
        UUID chronicle = awaken();
        lightAFire(chronicle);
        // One of each, so the choice between them is the thing under test rather than what happened to be left.
        for (String raw : List.of("raw_fish", "raw_fowl_meat", "raw_game_meat")) carry(chronicle, raw);

        // Each asked for by the word a person uses. Before V396 only the third of these could cook at all, and
        // the first cut of the service cooked GAME MEAT when asked for fowl, because it derived the spoken form
        // from the item key and "fowl meat" is in no sentence anybody types.
        assertEquals("SUCCEEDED", actions.resolve("cook the fish").outcome(), "a fish must cook");
        assertEquals(1, owned(chronicle, "cooked_fish"), "cooking the fish must make a cooked fish");

        assertEquals("SUCCEEDED", actions.resolve("cook the fowl").outcome(), "fowl must cook");
        assertEquals(1, owned(chronicle, "cooked_fowl_meat"), "cooking the fowl must make cooked fowl, not meat");

        assertEquals("SUCCEEDED", actions.resolve("cook the meat").outcome(), "game meat must still cook");
        assertEquals(1, owned(chronicle, "cooked_game_meat"), "cooking the meat must make cooked game meat");

        // And each keeps on the cooked tier, so a cooked fish spoils like any other cooked dish rather than
        // being a new food that nothing tracks.
        for (String cooked : List.of("cooked_fish", "cooked_fowl_meat", "cooked_game_meat")) {
            String tier = jdbc.queryForObject(
                "SELECT f.preparation_kind FROM item_instance i JOIN food_preservation_state f ON f.object_id=i.object_id " +
                "JOIN world_object w ON w.id=i.object_id WHERE i.item_key=? AND w.current_owner_id=? LIMIT 1",
                String.class, cooked, chronicle);
            assertEquals("COOKED", tier, cooked + " must keep on the cooked tier");
        }

        assertTrue(auditor.inspect().consistent(),
            () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void andCookingLosesMassRatherThanMakingIt() {
        awaken();
        // The conservation rule, asked of the new table directly. A better process means better RECOVERY, never
        // more matter, and one catalogue row that breaks mass balance fails some three hundred tests.
        Integer makesMatter = jdbc.queryForObject(
            "SELECT COUNT(*) FROM fire_cooking fc JOIN item_definition r ON r.item_key=fc.raw_item_key " +
            "JOIN item_definition c ON c.item_key=fc.cooked_item_key WHERE c.unit_mass_grams >= r.unit_mass_grams",
            Integer.class);
        assertEquals(0, makesMatter, "no cooking pair may weigh more out than in");

        // And every raw food the catalogue holds is either cookable or deliberately not — raw water and raw
        // honey are not cooked, and the two fish cuts exist to be dried and salted.
        List<String> stranded = jdbc.queryForList(
            "SELECT d.item_key FROM item_definition d WHERE d.category='FOOD' AND d.item_key LIKE 'raw_%' " +
            "AND d.item_key NOT IN ('raw_water','raw_honey') " +
            "AND NOT EXISTS (SELECT 1 FROM fire_cooking fc WHERE fc.raw_item_key=d.item_key)", String.class);
        assertTrue(stranded.isEmpty(), () -> "these raw foods can still never be cooked: " + stranded);
    }

    @Test
    void aPastParticipleNamesAFoodAndTheVerbAsksForWork() {
        UUID chronicle = awaken();
        lightAFire(chronicle);
        carry(chronicle, "raw_fish");
        // "cooked" contains "cook", so this rule answered a request to EAT by cooking another one.
        for (String said : List.of("eat the cooked fish", "eat the cooked meat", "eat the roasted fish")) {
            ChronicleActionService.ActionResult r = actions.resolve(said);
            assertNotEquals("COOK_MEAT", r.intent(),
                () -> "\"" + said + "\" names a food and asks to eat it: " + r.intent() + " / " + r.perception());
        }
        // The other direction, including the inflections.
        for (String said : List.of("cook the fish", "cooking the fish", "grill the fish", "roast the meat", "stew the meat")) {
            assertEquals("COOK_MEAT", actions.resolve(said).intent(),
                () -> "\"" + said + "\" asks for cooking: " + actions.resolve(said).intent());
        }
    }

    @Test
    void andARefusalNamesWhyRatherThanSayingTheSameThingTwice() {
        UUID chronicle = awaken();
        // No fire: the old prose said "You prepare the meat for a moment, then set it aside unchanged" whether
        // the camp was cold or the pack was empty. Assert the ASYMMETRY, not that something was said.
        String noFire = actions.resolve("cook the meat").perception();
        lightAFire(chronicle);
        String noFood = actions.resolve("cook the meat").perception();
        assertNotEquals(noFire, noFood, "a cold camp and an empty pack are different refusals");
        assertTrue(noFire.contains("no fire"), () -> "with no fire the answer must say so: " + noFire);
        assertTrue(noFood.contains("nothing raw"), () -> "with nothing raw the answer must say so: " + noFood);
    }
}
