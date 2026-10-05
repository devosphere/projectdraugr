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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Putting things away and taking them out (#37, V397).
 *
 * <p>The storage mechanism is finished: storing re-owns an item so a cached perishable stops drawing predators,
 * a container has capacity and an access state, and a named thing can be put in or drawn out. <b>Three ways of
 * asking for it reached nothing.</b>
 *
 * <p><b>One — no way to EMPTY a container.</b> It could be opened, closed, sealed, filled by name and drawn from
 * by name. "empty the pot" reached nothing at all, so a Chronicle who could not remember what they had put by
 * had to name each thing in turn to get it back.
 *
 * <p><b>Two — most of how a person says "put this by" reached nothing.</b> Measured over five things worth
 * keeping, <b>26 of 75 phrasing/thing pairs were dead</b>. `store`, `stow`, `stash`, `cache`, `put it away`,
 * `put it in the basket` and `put it in storage` all worked; `bring the X in`, `put the X in the store`, `take
 * the X inside`, `set the X by`, `put the X by for later`, `get the X under cover` and `lay the X up` reached
 * nothing. 26 → 2, and the two that remain are `keep the X`, left alone deliberately: keeping a thing is
 * retaining it, not storing it, and the word is already claimed by the carcass yield and by grain processes.
 *
 * <p><b>Three — you had to say the catalogue's name back to it.</b> Every container act asked whether the
 * sentence contained the whole display name, so <b>"empty the basket" could not find a "Primitive backpack
 * basket"</b> and "open the pot" could not find a "Fired clay cooking pot". One shared matcher now answers for
 * opening, closing, storing and emptying alike, on the HEAD noun — what the thing actually is.
 *
 * <p>The widenings are held back from the phrasings other rules claim, <b>by name and with the rule that claims
 * them</b>: "bring in more wood" is going out to get some and "bring in the grain" is reaping a standing crop,
 * while "bring the firewood in" and "bring the grain in" are putting away what you already have. The local suite
 * caught the grain one, which is what that regression test is for.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class PuttingThingsAwayAndTakingThemOutIntegrationTest {

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

    private void eachReaches(Map<String, String> expected) {
        expected.forEach((said, intent) -> {
            ChronicleActionService.ActionResult r = actions.resolve(said);
            assertEquals(intent, r.intent(),
                () -> "\"" + said + "\" must reach " + intent + ", not " + r.intent() + " / " + r.perception());
        });
    }

    /**
     * A small pouch the Chronicle carries, holding three light things.
     *
     * <p>Deliberately a LEATHER POUCH rather than a pack basket: a carried container's own volume counts against
     * direct bulk, so a backpack basket puts the Chronicle over capacity on its own and the take is then refused
     * for a reason that has nothing to do with emptying. A pouch leaves room, which is what lets this class
     * measure the emptying itself.
     *
     * @return the pouch's object id
     */
    private UUID aPouchWithThreeThingsIn(UUID chronicle) {
        // THE POUCH MUST BE THE ONLY ONE WITHIN REACH. Other classes leave leather pouches on this ground, the
        // matcher orders by name length and they all have the same name, so the emptying found a stranger's
        // empty pouch and reported it already empty. Clear every container this Chronicle can reach first.
        UUID here = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        jdbc.update("DELETE FROM item_containment WHERE container_id IN (SELECT cp.object_id FROM container_properties cp " +
            "JOIN world_object w ON w.id=cp.object_id WHERE w.current_owner_id=? OR w.current_location_id=?)", chronicle, here);
        jdbc.update("DELETE FROM container_properties WHERE object_id IN (SELECT w.id FROM world_object w " +
            "WHERE w.current_owner_id=? OR w.current_location_id=?)", chronicle, here);

        UUID pouch = UUID.randomUUID();
        String name = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key='leather_pouch'", String.class);
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM',?,?)", pouch, name, chronicle);
        jdbc.update("INSERT INTO item_instance (object_id,item_key) VALUES (?,'leather_pouch')", pouch);
        jdbc.update("INSERT INTO container_properties (object_id,max_mass_grams,max_volume_ml,access_state) VALUES (?,12000,20000,'OPEN')", pouch);
        for (String itemKey : List.of("hazelnut", "hazelnut", "beech_mast")) {
            UUID id = UUID.randomUUID();
            String n = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, itemKey);
            jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM',?,?)", id, n, pouch);
            jdbc.update("INSERT INTO item_instance (object_id,item_key) VALUES (?,?)", id, itemKey);
            jdbc.update("INSERT INTO item_containment (container_id,item_id) VALUES (?,?)", pouch, id);
        }
        return pouch;
    }

    private int inside(UUID container) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM item_containment WHERE container_id=?", Integer.class, container);
        return n == null ? 0 : n;
    }

    @Test
    void aContainerCanBeTurnedOut() {
        UUID chronicle = awaken();
        UUID pouch = aPouchWithThreeThingsIn(chronicle);

        // Sealed: refused, by its reason, and nothing moves. The same rule the single take already honoured.
        jdbc.update("UPDATE container_properties SET access_state='SEALED' WHERE object_id=?", pouch);
        ChronicleActionService.ActionResult sealed = actions.resolve("empty the pouch");
        assertEquals("EMPTY_CONTAINER", sealed.intent(), () -> "emptying is its own act: " + sealed.intent());
        assertEquals("FAILED", sealed.outcome(), () -> "a sealed pouch cannot be emptied: " + sealed.perception());
        assertTrue(sealed.perception().contains("sealed"), () -> "and the refusal must say why: " + sealed.perception());
        assertEquals(3, inside(pouch), "a refused emptying moves nothing");

        // Open: emptied, and what was in it is now carried.
        //
        // Counted as a DELTA, not a total. The total is the suite's: the sibling test in this class empties a
        // pouch of its own, and any other class may leave a hazelnut on this Chronicle — so an absolute count
        // read 6 where it expected 3. What this test owns is the three it put in, and the change in the count
        // is the only honest way to measure them.
        jdbc.update("UPDATE container_properties SET access_state='OPEN' WHERE object_id=?", pouch);
        String carriedNuts = "SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id "
                           + "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' "
                           + "AND i.item_key IN ('hazelnut','beech_mast')";
        Integer before = jdbc.queryForObject(carriedNuts, Integer.class, chronicle);
        ChronicleActionService.ActionResult emptied = actions.resolve("empty the pouch");
        assertEquals("SUCCEEDED", emptied.outcome(), () -> "an open pouch empties: " + emptied.perception());
        assertEquals(0, inside(pouch), "everything must come out");
        Integer after = jdbc.queryForObject(carriedNuts, Integer.class, chronicle);
        assertEquals(3, after - before, "and the three that were in it must be carried afterwards");

        // And again: already empty, said so rather than succeeding at nothing. The asymmetry is the point.
        ChronicleActionService.ActionResult again = actions.resolve("empty the pouch");
        assertTrue(again.perception().contains("already empty"),
            () -> "an empty pouch must be named as empty: " + again.perception());

        assertTrue(auditor.inspect().consistent(),
            () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void andAContainerAnswersToWhatItIs() {
        UUID chronicle = awaken();
        aPouchWithThreeThingsIn(chronicle);
        // "Leather pouch" was reachable only by that whole name. The head noun is what the thing IS.
        Map<String, String> byHeadNoun = new LinkedHashMap<>();
        byHeadNoun.put("empty the pouch", "EMPTY_CONTAINER");
        byHeadNoun.put("take everything out of the pouch", "EMPTY_CONTAINER");
        byHeadNoun.put("open the pouch", "OPEN_CONTAINER");
        byHeadNoun.put("close the pouch", "CLOSE_CONTAINER");
        eachReaches(byHeadNoun);
        // And the act must actually find it, not merely be classified.
        ChronicleActionService.ActionResult opened = actions.resolve("open the pouch");
        assertEquals("SUCCEEDED", opened.outcome(), () -> "and it must find the pouch: " + opened.perception());
    }

    @Test
    void everyPlainWayOfPuttingAThingByReachesTheStore() {
        awaken();
        Map<String, String> plain = new LinkedHashMap<>();
        for (String thing : List.of("meat", "fish", "grain", "hide")) {
            plain.put("bring the " + thing + " in", "STORE");
            plain.put("put the " + thing + " in the store", "STORE");
            plain.put("take the " + thing + " inside", "STORE");
            plain.put("set the " + thing + " by", "STORE");
            plain.put("put the " + thing + " by for later", "STORE");
            plain.put("get the " + thing + " under cover", "STORE");
            plain.put("lay the " + thing + " up", "STORE");
        }
        // Fuel is included, because firewood is a thing you lay up.
        plain.put("bring the firewood in", "STORE");
        plain.put("lay the firewood up", "STORE");
        eachReaches(plain);
    }

    @Test
    void andNothingWithABetterClaimOnThosePhrasingsLosesIt() {
        awaken();
        Map<String, String> held = new LinkedHashMap<>();
        // The phrasings that already worked.
        held.put("store the meat", "STORE");
        held.put("put the meat away", "STORE");
        held.put("cache the meat", "STORE");
        held.put("put the meat in storage", "STORE");
        // Going out to GET fuel is the gather's, not the store's.
        held.put("bring in more wood", "GATHER_BRANCHES");
        held.put("lay in more wood", "GATHER_BRANCHES");
        held.put("stock up on firewood", "GATHER_BRANCHES");
        held.put("gather firewood", "GATHER_BRANCHES");
        // Bringing in a standing crop is reaping it. This exact phrase is in the classifier's own regression
        // suite, and the first cut of this widening broke it.
        held.put("bring in the grain", "HARVEST_CROP");
        held.put("reap the grain", "HARVEST_CROP");
        held.put("harvest the crop", "HARVEST_CROP");
        // "bring in the harvest" is the plainest word for reaping and reached nothing before; it is the crop's.
        held.put("bring in the harvest", "HARVEST_CROP");
        // And the animals keep the word "stock".
        held.put("water the stock", "FEED_ANIMAL");
        held.put("feed the stock", "FEED_ANIMAL");
        eachReaches(held);
    }
}
