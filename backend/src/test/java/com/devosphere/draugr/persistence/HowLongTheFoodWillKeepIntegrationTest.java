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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How long the food will keep (#37, V395).
 *
 * <p>Spoilage is a finished subsystem — five tiers with their own spans, a {@code safe_until} clock per object, a
 * pest check, and a foodborne illness for eating what has gone over. <b>None of it could be asked about.</b> A
 * Chronicle could carry two months of salted meat and a fish that would be finished by evening and had no way in
 * the language of the game to tell them apart; and the one survey that does walk your own ground counts only
 * structures. So the world punished a mistake it would not let you see coming.
 *
 * <p>The stocktake also turned up a second defect, which is why this class asserts it too: <b>eight made foods had
 * no preservation tier at all and so never spoiled</b> — flours, kernels, a peeled root, a washed root, wild grain,
 * a bait pouch. A washed root is the #60 defect once more: processing a perishable food laundered it into food that
 * never spoiled. It is fixed by letting anything FOOD fall back to the foraged reading instead of to nothing, so
 * the next made food cannot be immortal by omission; and four items are held back from that fallback by name,
 * because for honey and for water a spoilage clock would be the lie.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class HowLongTheFoodWillKeepIntegrationTest {

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
    @Autowired com.devosphere.draugr.item.PhysicalItemService items;
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

    /**
     * Take away every food this Chronicle can reach, so the empty answer is measured rather than assumed.
     *
     * <p>Mirrors {@code retire()} exactly: a destroyed object holds no live location or owner and records how it
     * ended, and the Auditor fails the whole suite over the shortcut. Equipment attachments go first, or the
     * attachment is left pointing at an inactive item — also an Auditor violation.
     */
    private void takeAwayEveryFood(UUID chronicle) {
        UUID where = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        // Scoped to this Chronicle and the ground it stands on. A fixture that claims every object of a kind in
        // the world breaks whichever class runs next — one replay once broke on any database where somebody had
        // gathered firewood, because it claimed every object called "Dry branch".
        String food = "SELECT i.object_id FROM item_instance i JOIN item_definition d ON d.item_key=i.item_key " +
                      "JOIN world_object w ON w.id=i.object_id WHERE d.category='FOOD' AND w.lifecycle_state='ACTIVE' " +
                      // The owner may be a container the Chronicle carries rather than the Chronicle, which is
                      // how STORE re-owns things, so one level of nesting counts as within reach too.
                      "AND (w.current_owner_id=? OR w.current_location_id=? " +
                      "     OR w.current_owner_id IN (SELECT c.id FROM world_object c WHERE c.current_owner_id=? OR c.current_location_id=?))";
        jdbc.update("DELETE FROM equipment_attachment WHERE item_id IN (" + food + ")", chronicle, where, chronicle, where);
        jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED', destroyed_at=(SELECT simulated_at FROM simulation_clock WHERE id=1), " +
            "destroyed_cause='CONSUMED', destroyed_location_id=?, current_owner_id=NULL, current_location_id=NULL " +
            "WHERE id IN (" + food + ")", where, chronicle, where, chronicle, where);
    }

    /** One food in the Chronicle's hands, on a named tier with a named span left in it. */
    private void carry(UUID chronicle, String itemKey, String tier, String interval) {
        UUID id = UUID.randomUUID();
        String name = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, itemKey);
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM',?,?)", id, name, chronicle);
        jdbc.update("INSERT INTO item_instance (object_id,item_key) VALUES (?,?)", id, itemKey);
        if (tier != null)
            // Dated from the simulation clock, never from now(): a row stamped with the wall clock is either
            // already gone over or good for five years, depending on which way the world is pinned.
            jdbc.update("INSERT INTO food_preservation_state (object_id,preparation_kind,safe_until,pest_checked_at) " +
                "VALUES (?,?,(SELECT simulated_at FROM simulation_clock WHERE id=1) + ?::interval, " +
                "(SELECT simulated_at FROM simulation_clock WHERE id=1))", id, tier, interval);
    }

    @Test
    void theWorldWillNowSayWhatYouHaveAndHowLongItKeeps() {
        UUID chronicle = awaken();
        takeAwayEveryFood(chronicle);

        String empty = actions.resolve("check my food stores").perception();
        assertEquals("TAKE_STOCK_OF_FOOD", actions.resolve("check my food stores").intent());
        assertTrue(empty.contains("no food"),
            () -> "with nothing to eat the answer must say so plainly: " + empty);

        carry(chronicle, "salted_meat", "SALTED", "58 days");
        carry(chronicle, "smoked_fish", "SMOKED", "26 days");
        carry(chronicle, "dried_meat", "DRIED", "40 days");
        carry(chronicle, "grain_porridge", "COOKED", "50 hours");
        carry(chronicle, "raw_game_meat", "RAW", "6 hours");
        carry(chronicle, "raw_fish", "RAW", "-3 hours");

        String said = actions.resolve("check my food stores").perception();
        // Every tier named in its own word, so that the five spans the subsystem keeps become visible in play.
        for (String tier : java.util.List.of("salted", "smoked", "dried", "cooked", "raw")) {
            assertTrue(said.contains("(" + tier + ")"), () -> "the " + tier + " tier must be named: " + said);
        }
        // The long keeper and the one that will not see tomorrow must read differently — the asymmetry IS the answer.
        assertTrue(said.contains("58 days") && said.contains("6 hours"),
            () -> "two months of salt meat and six hours of raw must not read alike: " + said);
        // And the one already past eating must be named as such, with the warning the world otherwise never gave.
        assertTrue(said.contains("gone over"), () -> "spoiled food must be named: " + said);
        assertTrue(said.contains("sicken"), () -> "eating it sickens you, and the answer must say so: " + said);
        // Soonest first: what is about to go is the thing the question is really about.
        assertTrue(said.indexOf("gone over") < said.indexOf("58 days"),
            () -> "soonest to spoil must be named first: " + said);
    }

    @Test
    void everyPlainWayOfAskingReachesIt() {
        awaken();
        for (String said : java.util.List.of(
                "check my food stores",
                "how long will the food last",
                "what food do I have",
                "is anything going off",
                "will the meat keep",
                "check my supplies",
                "take stock of the food",
                "how much food have I got")) {
            assertEquals("TAKE_STOCK_OF_FOOD", actions.resolve(said).intent(),
                () -> "\"" + said + "\" is that question: " + actions.resolve(said).intent());
        }
    }

    @Test
    void andAskingAboutTheMeatIsNotAnswerredBySaltingIt() {
        awaken();
        // The question words must not take the acts you perform ON the answer, nor the camp survey they sit beside,
        // nor the animals that share the word "stock".
        record Held(String said, String intent) { }
        for (Held h : java.util.List.of(
                new Held("take stock of the camp", "TAKE_STOCK_OF_CAMP"),
                new Held("smoke the meat", "PROCESS_MATERIAL"),
                new Held("salt the fish", "PROCESS_MATERIAL"),
                new Held("dry the meat", "PROCESS_MATERIAL"),
                new Held("cook the meat", "COOK_MEAT"),
                new Held("water the stock", "FEED_ANIMAL"),
                new Held("feed the stock", "FEED_ANIMAL"))) {
            assertEquals(h.intent(), actions.resolve(h.said()).intent(),
                () -> "\"" + h.said() + "\" must stay " + h.intent() + ": " + actions.resolve(h.said()).intent());
        }
        assertTrue(auditor.inspect().consistent(),
            () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void aMadeFoodMissingFromTheKeepingMapIsNoLongerImmortal() {
        UUID chronicle = awaken();
        // hazelnut_kernel is one of the eight: it came off a real process and carried no clock at all, so a shelled
        // kernel kept for ever. It is not in the named map and never was — the fallback is what tracks it.
        for (int i = 0; i < 4; i++) carry(chronicle, "hazelnut", "DRIED", "45 days");
        ChronicleActionService.ActionResult r = actions.resolve("shell hazelnuts");
        Assumptions.assumeTrue("SUCCEEDED".equals(r.outcome()),
            "this asserts the keeping of what the process makes, so the process must have run: " + r.perception());

        Integer tracked = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "JOIN food_preservation_state f ON f.object_id=i.object_id " +
            "WHERE i.item_key='hazelnut_kernel' AND w.lifecycle_state='ACTIVE'", Integer.class);
        assertTrue(tracked != null && tracked > 0,
            "a shelled kernel must keep on a tier like anything else made of food");

        Integer untracked = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "LEFT JOIN food_preservation_state f ON f.object_id=i.object_id " +
            "WHERE i.item_key='hazelnut_kernel' AND w.lifecycle_state='ACTIVE' AND f.object_id IS NULL", Integer.class);
        assertEquals(0, untracked, "no kernel may be left without a clock on it");
    }

    @Test
    void butHoneyAndWaterKeep() {
        UUID chronicle = awaken();
        // The other direction, and the reason the fallback is not blanket. Not spoiling is a real property of
        // honey; water is filed under FOOD only so that drinking can find it, and a bucket does not go over in
        // four days.
        //
        // Created through the service, deliberately: this path ALREADY had the fallback, so before honey and water
        // were held back by name it gave both of them a four-day clock. Inserting the rows by hand here would
        // assert nothing at all.
        java.time.Instant at = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class).toInstant();
        for (String itemKey : java.util.List.of("raw_honey", "clean_water")) {
            String name = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, itemKey);
            items.createHeldItem(chronicle, itemKey, name, at, "GATHERED");
            Integer clocked = jdbc.queryForObject(
                "SELECT COUNT(*) FROM item_instance i JOIN food_preservation_state f ON f.object_id=i.object_id " +
                "JOIN world_object w ON w.id=i.object_id WHERE i.item_key=? AND w.lifecycle_state='ACTIVE'",
                Integer.class, itemKey);
            assertEquals(0, clocked, itemKey + " does not go over, and a clock on it would be the lie");
        }
        String said = actions.resolve("check my food stores").perception();
        assertTrue(said.contains("which keeps"),
            () -> "food with no clock on it must be named as keeping, not left out: " + said);
        assertFalse(said.contains("null"), () -> "no tier may print as null: " + said);
    }
}
