package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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

import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The world knew it was poison and would not say (#37).
 *
 * <p>The poison model is finished. {@code flora_definition.is_poisonous} marks the death cap and the fly agaric,
 * {@code wildlife_species.toxic} marks the fire salamander, the toad and the great crested newt, and both are
 * acted on — eating the mushroom sickens, a toxic carcass is stripped and the flesh left, a toxic catch is left
 * in the snare. The eating rule states the design in its own comment: <i>"The world applies physics, not a
 * warning: the player had to know which mushroom before they ate it."</i>
 *
 * <p>That is the right design, and the game never held up its end. There was no way to know. Measured live,
 * twelve ways of asking reached nothing, and <b>five were answered by eating it</b>.
 *
 * <p><b>What this test holds that the routing test cannot:</b> that the answer is read from the same data the
 * poisoning is read from, and over the same growth the gathering rule picks from. A judgement that read a
 * narrower list than the gatherer can reach would call a mushroom unknown and then let you pick it.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class TheWorldKnewItWasPoisonIntegrationTest {

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
    @Autowired JdbcTemplate jdbc;

    private UUID mushroomGiven;

    @AfterEach
    void takeBackWhatThisTestMade() {
        // One world, ~340 tests. A death cap left in the pack is another test's forage, and at least one of them
        // counts what the Chronicle carries.
        if (mushroomGiven != null) {
            jdbc.update("DELETE FROM item_instance WHERE object_id=?", mushroomGiven);
            jdbc.update("DELETE FROM world_object WHERE id=?", mushroomGiven);
            mushroomGiven = null;
        }
    }

    private UUID aLivingChronicle() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.active() != null ? chronicles.active() : chronicles.awaken();
        assertNotNull(summary, "a living Chronicle is needed");
        return summary.id();
    }

    /** Ground where a poisonous flora actually grows, found by the SAME reach the gathering rule uses. */
    private UUID whereThePoisonGrows() {
        return jdbc.query(
            "SELECT c.id FROM world_chunk c WHERE EXISTS ("
            + "  SELECT 1 FROM flora_definition fd WHERE fd.is_poisonous AND fd.organism_type <> 'TREE' AND ("
            + "    EXISTS (SELECT 1 FROM chunk_flora cf WHERE cf.chunk_id=c.id AND cf.flora_key=fd.flora_key AND cf.quantity > 0)"
            + "    OR fd.biome_affinity ILIKE '%' || c.biome || '%')) "
            + "ORDER BY c.grid_y, c.grid_x LIMIT 1",
            rs -> rs.next() ? (UUID) rs.getObject(1) : null);
    }

    /** One poisonous forage item, carried. An OWNED object has no location — world_object_check1 forbids both. */
    private void carryADeathCap(UUID me) {
        mushroomGiven = UUID.randomUUID();
        Integer made = jdbc.update(
            "INSERT INTO world_object (id, object_type, display_name, lifecycle_state, current_owner_id, created_at) "
            + "SELECT ?, 'ITEM', d.display_name, 'ACTIVE', ?, now() FROM item_definition d WHERE d.item_key='death_cap'",
            mushroomGiven, me);
        Assumptions.assumeTrue(made != null && made == 1, "this catalogue has no death cap");
        jdbc.update("INSERT INTO item_instance (object_id, item_key, condition_state) VALUES (?,'death_cap','SOUND')",
            mushroomGiven);
    }

    private String said(String text) {
        var r = actions.resolve(text, UUID.randomUUID());
        assertNotNull(r, () -> "the composer must answer: " + text);
        return (r.perception() == null ? "" : r.perception()).toLowerCase(Locale.ROOT);
    }

    /** The prose a sentence gets when nothing routed it — what every one of these used to read. */
    private static boolean readsAsAMiss(String prose) {
        return prose.contains("nothing here answers to the attempt")
            || prose.contains("whatever you meant by that")
            || prose.contains("it does not come to anything")
            || prose.contains("get as far as beginning");
    }

    @Test
    void theJudgementNamesThePoisonGrowingWithinReach() {
        UUID me = aLivingChronicle();
        UUID here = whereThePoisonGrows();
        Assumptions.assumeTrue(here != null, "no ground in this world grows a poisonous flora");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", here, me);

        String prose = said("is this poisonous");
        assertFalse(readsAsAMiss(prose), () -> "the question must reach the model that already knows: " + prose);
        // THE POINT: it is read from is_poisonous, over the gatherer's own reach. Asserted by naming what that
        // query says grows here rather than by hard-coding "death cap", so the two cannot drift.
        String named = jdbc.query(
            "SELECT fd.flora_key FROM flora_definition fd WHERE fd.is_poisonous AND fd.organism_type <> 'TREE' AND ("
            + "  EXISTS (SELECT 1 FROM chunk_flora cf WHERE cf.chunk_id=? AND cf.flora_key=fd.flora_key AND cf.quantity > 0)"
            + "  OR fd.biome_affinity ILIKE '%' || (SELECT biome FROM world_chunk WHERE id=?) || '%') LIMIT 1",
            rs -> rs.next() ? rs.getString(1).replace('_', ' ') : null, here, here);
        assertNotNull(named, "the fixture promised poison on this ground");
        assertTrue(prose.contains(named),
            () -> "the one to know must be named, or the answer is no use: expected " + named + " in: " + prose);
        assertTrue(prose.contains("no amount of cooking"),
            () -> "and it must say that cooking does not fix it, which is the thing people get wrong: " + prose);
    }

    @Test
    void aPoisonInThePackIsNamedBecauseGatheringComesBeforeEating() {
        UUID me = aLivingChronicle();
        carryADeathCap(me);

        // The dangerous moment is not always the one you are standing in: forage is picked, carried, and eaten
        // hours later, possibly on ground where nothing poisonous grows at all.
        String prose = said("is this poisonous");
        assertTrue(prose.contains("carrying") && prose.contains("death cap"),
            () -> "what is already in the pack must be named: " + prose);
    }

    @Test
    void theAnswerEndsWhereKnowingEnds() {
        UUID me = aLivingChronicle();
        String prose = said("what here is edible");
        assertFalse(readsAsAMiss(prose), () -> "and this one was answered by EATING: " + prose);
        // Exactly as the water judgement ends at boiling. A mushroom nobody has named is not a safe mushroom,
        // and a judgement that implied otherwise would be worse than the silence it replaced.
        assertTrue(prose.contains("cannot put a name to"),
            () -> "the limit of what looking can do must be said plainly: " + prose);
    }

    @Test
    void askingIsNotEating() {
        UUID me = aLivingChronicle();
        carryADeathCap(me);
        Integer before = jdbc.queryForObject(
            "SELECT count(*) FROM item_instance WHERE object_id=?", Integer.class, mushroomGiven);

        said("is this safe to eat");
        said("can i eat this");

        // THE WHOLE DEFECT IN ONE ASSERTION. Both of these used to be answered by eating it, which is the one
        // outcome the asker was trying to avoid. The death cap must still be in the pack afterwards.
        Integer after = jdbc.queryForObject(
            "SELECT count(*) FROM item_instance WHERE object_id=?", Integer.class, mushroomGiven);
        assertTrue(before != null && before.equals(after),
            () -> "asking whether a thing is safe must not consume it: " + before + " -> " + after);
    }
}
