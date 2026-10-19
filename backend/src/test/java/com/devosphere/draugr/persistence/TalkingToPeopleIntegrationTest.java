package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.people.ContactService;
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

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Talking to people (#109, #37).
 *
 * <p>Swept standing on a reedkin isle, which is the only place these sentences mean anything. The acts that
 * named their object worked; the plainest forms did not, and <b>three of them carried out something else
 * entirely</b>:
 *
 * <pre>
 *   ask about the fishing        -&gt; FISH — "You stand in the cold water with your hands
 *                                   open, and whatever moves past is gone before you close them."
 *   ask what they call this place -&gt; DESIGNATE — "You mean to give this place a name"
 *   teach them to make rope      -&gt; the ROPE RECIPE, and tried to twist cordage
 *
 *   ask about the land      -&gt; nothing   |  ask them about the land -&gt; worked
 *   tell them about myself  -&gt; nothing   |  tell them why           -&gt; worked
 *   show respect            -&gt; nothing   |  respect the boundary    -&gt; worked
 *   learn their language    -&gt; nothing   |  listen to them          -&gt; worked
 *   thank them              -&gt; nothing
 * </pre>
 *
 * <p>Asking about the fishing and being made to fish is the plainest of these: a question addressed to somebody
 * else, carried out as the act its noun belongs to.
 *
 * <p><b>Teaching has no mechanic in this game</b>, and that is recorded rather than papered over. Attempting
 * shared words is the nearest thing that is true — the reedkin's speech "runs in clicks and low calls, and you
 * cannot yet part it into words", so showing somebody how to do a thing across that gap IS an attempt to be
 * understood. It is a true answer to a near question rather than a false answer to the exact one, and it stops
 * the game making rope when asked to teach.
 *
 * <p><b>The gates are held in the composer as well as in the people vocabulary</b>, because those phrases only
 * fire within reach of an isle — and asking about the fishing is no more an act of fishing on empty country than
 * it is beside a village. Both are asserted here.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class TalkingToPeopleIntegrationTest {

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
    @Autowired ContactService contact;
    @Autowired JdbcTemplate jdbc;

    private UUID aLivingChronicle() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.active() != null ? chronicles.active() : chronicles.awaken();
        assertNotNull(summary, "a living Chronicle is needed");
        return summary.id();
    }

    private String intentOf(String text) {
        var r = actions.resolve(text, UUID.randomUUID());
        assertNotNull(r, () -> "the composer must answer: " + text);
        return r.intent();
    }

    private String said(String text) {
        var r = actions.resolve(text, UUID.randomUUID());
        assertNotNull(r, () -> "the composer must answer: " + text);
        return (r.perception() == null ? "" : r.perception()).toLowerCase(Locale.ROOT);
    }

    /** A community's own ground, so these sentences have somebody to be addressed to. */
    private boolean standOnAnIsle(UUID me) {
        UUID home = jdbc.query(
            "SELECT n.home_chunk_id FROM native_community n JOIN cognition_profile cp ON cp.species_key=n.species_key "
            + "WHERE n.lifecycle <> 'DISPERSED' AND cp.cognition_class IN ('PEOPLE','SOVEREIGN') LIMIT 1",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null);
        if (home == null) return false;
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", home, me);
        return contact.communityInReach(home) != null;
    }

    /** Ground out of reach of any community, where the composer's own gates must still hold. */
    private void standWhereNobodyIs(UUID me) {
        UUID empty = jdbc.query(
            "SELECT c.id FROM world_chunk c WHERE NOT EXISTS ("
            + "  SELECT 1 FROM native_community n JOIN world_chunk home ON home.id=n.home_chunk_id "
            + "   WHERE n.lifecycle <> 'DISPERSED' AND abs(home.grid_x-c.grid_x)<=1 AND abs(home.grid_y-c.grid_y)<=1) "
            + "LIMIT 1", rs -> rs.next() ? rs.getObject(1, UUID.class) : null);
        Assumptions.assumeTrue(empty != null, "this world is all within hail of somebody");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", empty, me);
    }

    @Test
    void thePlainestFormsReachThePeople() {
        UUID me = aLivingChronicle();
        Assumptions.assumeTrue(standOnAnIsle(me), "this world generated no people to speak to");
        for (String phrase : List.of("ask about the land", "tell them about myself", "show respect",
                                     "learn their language", "thank them", "ask about the fishing",
                                     "ask what they call this place", "teach them to make rope")) {
            String got = intentOf(phrase);
            assertEquals("CONTACT_PEOPLE", got,
                () -> "'" + phrase + "' is said to people: -> " + got);
        }
    }

    @Test
    void askingAboutTheFishingDoesNotPutYouInTheWater() {
        // The plainest of the three: a question addressed to somebody else, carried out as the act its noun
        // belongs to. Asserted BOTH on an isle and on empty country, because the people vocabulary only fires
        // within reach of one and the sentence is no more an act of fishing away from it.
        UUID me = aLivingChronicle();
        standWhereNobodyIs(me);
        String got = intentOf("ask about the fishing");
        assertFalse("FISH".equals(got), () -> "asking about the fishing is not fishing: -> " + got);
        assertTrue(said("ask about the fishing").contains("no isle") || said("ask about the fishing").contains("within hail"),
            "and with nobody there it says so");

        Assumptions.assumeTrue(standOnAnIsle(me), "this world generated no people to speak to");
        assertEquals("CONTACT_PEOPLE", intentOf("ask about the fishing"), "and beside an isle it is put to them");
    }

    @Test
    void askingWhatTheyCallItDoesNotRenameTheGround() {
        UUID me = aLivingChronicle();
        standWhereNobodyIs(me);
        String got = intentOf("ask what they call this place");
        assertFalse("DESIGNATE".equals(got),
            () -> "asking what THEY call a place must not set a name on it: -> " + got);
    }

    @Test
    void askingToTeachDoesNotMakeTheThing() {
        // Teaching has no mechanic, so the honest outcome is a people answer — never the recipe for the thing
        // being taught.
        UUID me = aLivingChronicle();
        standWhereNobodyIs(me);
        String got = intentOf("teach them to make rope");
        assertFalse("PROCESS_MATERIAL".equals(got),
            () -> "asked to teach, the game must not make the thing: -> " + got);
    }

    @Test
    void fishingItselfStillFishes() {
        // The hazard of gating a noun-driven act on a question word: if it reached too far, nobody could fish.
        UUID me = aLivingChronicle();
        standWhereNobodyIs(me);
        for (String phrase : List.of("go fishing", "fish", "catch a fish", "fish the river")) {
            assertEquals("FISH", intentOf(phrase),
                () -> "'" + phrase + "' is still fishing: -> " + intentOf(phrase));
        }
    }

    @Test
    void namingAPlaceStillNamesIt() {
        UUID me = aLivingChronicle();
        standWhereNobodyIs(me);
        for (String phrase : List.of("name this place", "name this place the Long Meadow",
                                     "call this place the green hollow")) {
            assertEquals("DESIGNATE", intentOf(phrase),
                () -> "naming is still naming: '" + phrase + "' -> " + intentOf(phrase));
        }
    }

    @Test
    void theRecipesAndTheOtherFishWorkAreUntouched() {
        UUID me = aLivingChronicle();
        standWhereNobodyIs(me);
        // "ask leave to fish their waters" is a PEOPLE phrase carrying the word fish, and it must stay theirs.
        String leave = said("ask leave to fish their waters");
        assertTrue(leave.contains("no isle") || leave.contains("within hail"),
            () -> "asking leave to fish is put to people: " + leave);
        // And the work that happens to name a fish keeps its own recipes.
        assertEquals("PROCESS_MATERIAL", intentOf("gut the fish"), "gutting is still the process");
        assertEquals("PROCESS_MATERIAL", intentOf("carve a fish weir"), "and the weir is still carved");
        assertEquals("PROCESS_MATERIAL", intentOf("make cordage"), "and cordage is still twisted");
    }
}
