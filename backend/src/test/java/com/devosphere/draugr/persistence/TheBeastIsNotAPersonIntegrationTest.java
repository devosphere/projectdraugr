package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.people.ConductService;
import com.devosphere.draugr.people.PersonWords;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The beast is not a person, and it has a name (#106, V402).
 *
 * <p>Act sixteen swept the husbandry axis — fifty-six sentences a person who keeps animals would say.
 * <b>Thirty-six reached nothing</b>, and the worst of the ones that answered answered like this:
 *
 * <pre>
 *   tie up the goat   ->  CONDUCT_TOWARD_PEOPLE  "You lay hands on Holm, who twists free and shrieks. The rest
 *                                                 are on you at once, and you are driven off the isle bruised
 *                                                 and bleeding."
 *   feed the goat     ->  TAME                   "It lets you come nearer than last time, and holds there."
 *   water the animals ->  FEED_ANIMAL            "None of your draft beasts is hungry."
 *   groom the goat    ->  UNKNOWN                though groomAnimal has been implemented all along
 * </pre>
 *
 * <p>Three separate causes, each a different face of the one defect:
 *
 * <ol>
 *   <li><b>The conduct phrases never read their object.</b> "tie up the", "drag the", "seize the", "steal the"
 *       match a verb and a bare article and take whatever follows. 11 of 31 sentences about a THING were resolved
 *       as acts against the community in reach; the rest were told "you put that to people, and there are none
 *       within reach". ConductService's own class note says <i>"Persons are not livestock"</i>. Now held to
 *       {@link PersonWords} and to the names in {@code native_individual}.</li>
 *   <li><b>Three rules, three species lists.</b> Tending knew nine species, grooming knew none, feeding knew
 *       seven and not the goat. And {@code namesAKeptAnimal}, the data-driven escape that exists to stop exactly
 *       this, asked for the WHOLE compound key — the species are {@code mountain_goat} and {@code bighorn_sheep},
 *       so the word a keeper says twice a day matched nothing. One vocabulary now, and the head noun counts.</li>
 *   <li><b>And the gate that outlived its own fix.</b> The hunger tick was corrected for #122 so that every kept
 *       animal grows hungry — "a milk goat went through her whole life at nought" — and the gate on
 *       {@code feedDraftBeasts} still demanded a species that pulls. So the goat grew hungry by a tick that
 *       included her and could not be fed by the action that excluded her.</li>
 * </ol>
 *
 * <p>Each test resets the animal's wants immediately before it asks, because the world ticks on every action:
 * hunger falls on pasture and sickness heals on clean ground, so a question asked fifth is otherwise answered
 * about a beast the first four cured. The bond is this test's own and is taken out again afterwards.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class TheBeastIsNotAPersonIntegrationTest {

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
    @Autowired ConductService conduct;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private Timestamp clockWas;
    private UUID myBond;
    private UUID keeperWas;
    private UUID keeperStoodAt;

    @BeforeEach
    void pinTheClock() {
        clockWas = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class);
        jdbc.update("UPDATE simulation_clock SET simulated_at = TIMESTAMPTZ '2031-06-10T12:00:00Z' WHERE id=1");
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update = (SELECT simulated_at FROM simulation_clock WHERE id=1), " +
            "hours_without_food=0, hours_without_water=0, sleep_debt_hours=0, injury_severity=0, illness_severity=0, " +
            "blood_loss_ml=0, energy_level=100");
    }

    @AfterEach
    void putItBack() {
        // This test's own bond, taken out again, and the keeper stood back where they were: ~925 tests share one
        // world and one Chronicle, so a kept animal or a moved keeper left behind changes what the next feeding,
        // taming or breeding test sees. A test that passes alone and fails in CI is usually one of those.
        if (myBond != null) { jdbc.update("DELETE FROM wildlife_bond WHERE id=?", myBond); myBond = null; }
        if (keeperWas != null && keeperStoodAt != null)
            jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", keeperStoodAt, keeperWas);
        keeperWas = null; keeperStoodAt = null;
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

    /**
     * Tame one animal of a kind whose catalogue key is COMPOUND — mountain_goat, bighorn_sheep, water_buffalo —
     * because the head noun is the whole point, and returns the word a keeper would use for it.
     *
     * <p>A seeded population is used as it stands; nothing is repointed. The Chronicle is stood on its ground.
     */
    private String keepOneCompoundNamedBeast(UUID chronicle) {
        Map<String, Object> herd = jdbc.query(
            "SELECT wp.id, wp.species_key, es.chunk_id FROM wildlife_population wp " +
            "JOIN ecology_site es ON es.id = wp.site_id " +
            "WHERE wp.species_key LIKE '%\\_%' " +
            "  AND wp.species_key IN (SELECT species_key FROM tamed_yield UNION SELECT species_key FROM draft_species) " +
            "  AND NOT EXISTS (SELECT 1 FROM wildlife_bond wb WHERE wb.population_id = wp.id AND wb.chronicle_id = ?) " +
            "ORDER BY wp.species_key LIMIT 1",
            rs -> rs.next() ? Map.of("pop", rs.getObject(1, UUID.class), "species", rs.getString(2),
                                     "chunk", rs.getObject(3, UUID.class)) : null, chronicle);
        Assumptions.assumeTrue(herd != null,
            "this world seeds no compound-named keepable population, so there is no head noun to hold to account");

        keeperWas = chronicle;
        keeperStoodAt = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", herd.get("chunk"), chronicle);
        myBond = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_bond (id, chronicle_id, population_id, bond_stage, trust_level, " +
            "interaction_count, last_interaction_at, draft_hunger, draft_thirst, draft_fatigue, sickness) " +
            "VALUES (?,?,?,'TAMED',90,12,(SELECT simulated_at FROM simulation_clock WHERE id=1),35,70,30,55)",
            myBond, chronicle, herd.get("pop"));

        String spoken = ((String) herd.get("species")).replace('_', ' ');
        return spoken.substring(spoken.lastIndexOf(' ') + 1);   // the head noun: goat, sheep, buffalo, fowl
    }

    /** The world turns on every action, so the wants are set again before every question asked of them. */
    private void wanting() {
        jdbc.update("UPDATE wildlife_bond SET draft_hunger=35, draft_thirst=70, draft_fatigue=30, sickness=55 WHERE id=?", myBond);
    }

    @Test
    void anOffenceAgainstPeopleNeedsAPersonInIt() {
        awaken();
        // Each of these begins with a phrase from the conduct table and is about a thing. None may be an offence.
        for (String said : List.of("tie up the goat", "tie up the bundle", "tie up the raft", "capture the duck",
                                   "seize the branch", "seize the rope", "drag the log", "drag the sledge",
                                   "leash the goat", "steal the honey", "threaten the wolf", "stab the fish")) {
            ChronicleActionService.ActionResult r = actions.resolve(said);
            assertNotEquals("CONDUCT_TOWARD_PEOPLE", r.intent(),
                () -> "\"" + said + "\" is about a thing, and was answered as an act against people: " + r.perception());
        }
        // And the words alone agree, with no world around them.
        for (String said : List.of("tie up the goat", "drag the log", "seize the rope", "capture the duck"))
            assertNull(ConductService.recognise(said), "\"" + said + "\" names nobody");
    }

    @Test
    void anOffenceThatDoesNameAPersonIsStillAnOffence() {
        awaken();
        // The vocabulary, directly: this is the half that must not have been thrown out with the goat.
        for (String said : List.of("tie up the elder", "tie up the child", "seize the headsperson", "drag the woman",
                                   "capture the reedkin", "threaten them", "burn their village", "steal their nets"))
            assertNotNull(ConductService.recognise(said), () -> "\"" + said + "\" names a person and must still be recognised");
        assertEquals(ConductService.Act.RESTRAINT, ConductService.recognise("tie up the elder"));
        assertEquals(ConductService.Act.DAMAGE, ConductService.recognise("burn their village"));
        assertEquals(ConductService.Act.HARM, ConductService.recognise("attack the reedkin"), "a phrase that names its own object is untouched");

        // Word by word, never as a substring — a vocabulary of words as short as "men" and "kin" is the worst
        // possible place to repeat the mistake that "scutch" contains "cut".
        assertTrue(PersonWords.namesAPerson("tie up the men"));
        assertFalse(PersonWords.namesAPerson("mend the kindling"), "\"kindling\" is not kin");
        assertFalse(PersonWords.namesAPerson("carry the manure"), "\"manure\" is not a man");
        assertFalse(PersonWords.namesAPerson("gather the mushrooms"));

        // And a person the WORLD named, which no vocabulary could know in advance.
        String someone = jdbc.query("SELECT given_name FROM native_individual WHERE given_name IS NOT NULL LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : null);
        if (someone != null) {
            assertTrue(conduct.namesSomebodyLiving("tie up " + someone), "a given name from this world names somebody");
            assertNotNull(conduct.recogniseHere("tie up the " + someone.toLowerCase(Locale.ROOT)),
                "and so the offence against them is recognised, though no list could have held their name");
        }
    }

    @Test
    void oneSpeciesVocabularyForTendingGroomingAndFeeding() {
        UUID me = awaken();
        String beast = keepOneCompoundNamedBeast(me);

        // The head noun — "goat", not "mountain goat" — reaches all three, which is what three separate lists
        // could never manage. The answers differ because the three acts are different; none may be UNKNOWN.
        Map<String, String> work = new LinkedHashMap<>();
        work.put("groom the " + beast, "GROOM_ANIMAL");
        work.put("brush the " + beast, "GROOM_ANIMAL");
        work.put("tend the " + beast, "TEND_ANIMAL");
        work.put("feed the " + beast, "FEED_ANIMAL");
        work.forEach((said, intent) -> {
            wanting();
            ChronicleActionService.ActionResult r = actions.resolve(said);
            assertEquals(intent, r.intent(), () -> "\"" + said + "\" must reach " + intent + ": " + r.perception());
        });
    }

    @Test
    void feedingReachesAnAnimalThatDoesNotPull() {
        UUID me = awaken();
        String beast = keepOneCompoundNamedBeast(me);
        boolean pulls = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM draft_species ds JOIN wildlife_population wp ON wp.species_key=ds.species_key " +
            "JOIN wildlife_bond wb ON wb.population_id=wp.id WHERE wb.id=?)", Boolean.class, myBond));

        wanting();
        ChronicleActionService.ActionResult r = actions.resolve("feed the " + beast);
        assertEquals("FEED_ANIMAL", r.intent());
        // The gate used to require a species that pulls, while the hunger tick had been fixed to include every
        // kept animal. A hungry beast must now be SEEN as hungry whether or not it draws a cart: the refusal may
        // be about fodder, never about there being nothing hungry.
        assertFalse(r.perception().toLowerCase(Locale.ROOT).contains("none of your"),
            () -> "a kept animal at hunger 35 is hungry" + (pulls ? "" : ", and this one does not pull") + ": " + r.perception());
    }

    @Test
    void theStockCanBeAskedAfter() {
        UUID me = awaken();
        String beast = keepOneCompoundNamedBeast(me);

        for (String said : List.of("how is the " + beast, "check on the animals", "is the " + beast + " sick",
                                   "water the animals", "water the " + beast)) {
            wanting();
            ChronicleActionService.ActionResult r = actions.resolve(said);
            assertEquals("CHECK_STOCK", r.intent(), () -> "\"" + said + "\" must ask after the stock: " + r.perception());
            assertTrue(r.perception().toLowerCase(Locale.ROOT).contains(beast),
                () -> "and must say which animal it is about: " + r.perception());
        }

        // Thirst, specifically, is what "water the animals" was answered about wrongly — it got appetite.
        wanting();
        String watered = actions.resolve("water the animals").perception().toLowerCase(Locale.ROOT);
        assertTrue(watered.contains("water"), () -> "watering must be answered about water: " + watered);
        assertFalse(watered.contains("hungry — there is nothing"), () -> "and not about hunger: " + watered);
    }

    @Test
    void andTheRulesThatOwnedThoseWordsKeepThem() {
        UUID me = awaken();
        // Taming is still taming when the animal is not yours — which is every wild one.
        assertEquals("TAME", actions.resolve("tame the goat").intent(), "taming by name is unambiguous");
        assertEquals("TAME", actions.resolve("befriend the deer").intent());

        Map<String, String> held = new LinkedHashMap<>();
        held.put("milk the goat", "TAKE_ANIMAL_YIELD");
        held.put("shear the sheep", "TAKE_ANIMAL_YIELD");
        held.put("bind the wound", "TREAT_WOUND");
        held.put("water the crop", "WATER_CROP");
        held.put("till the ground", "TILL_GROUND");
        held.forEach((said, intent) -> assertEquals(intent, actions.resolve(said).intent(),
            () -> "\"" + said + "\" must stay " + intent + ": " + actions.resolve(said).perception()));

        // A question about a tool is not a question about an animal, though both start "how is my".
        assertEquals("TAKE_STOCK_OF_GEAR", actions.resolve("how is my knife").intent(),
            "the gear stocktake keeps the sentences that are about gear");

        String beast = keepOneCompoundNamedBeast(me);
        assertEquals("TAME", actions.resolve("tame the " + beast).intent(),
            "and a keeper who says they are taming a second, wild one of the same kind means it");

        assertTrue(auditor.inspect().consistent(),
            () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

}
