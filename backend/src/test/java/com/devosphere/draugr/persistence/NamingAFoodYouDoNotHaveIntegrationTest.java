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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Naming a food you do not have must not feed you a different one (#37).
 *
 * <p>Measured on a booted backend: {@code eat the fish} and {@code eat the trout}, with no fish in the pack,
 * both answered <em>"The mushroom is earthy and dense on the tongue, and you chew it down slowly."</em> The
 * Chronicle asked for a fish and the world fed them a mushroom — and said so plainly, in the same even voice as
 * every true line. That is the worst shape of defect this project has: not a missing answer but a confident
 * wrong one.
 *
 * <p>The cause: {@code namedFoodInReach} returns null for two different situations — the player named nothing,
 * and the player named something they do not have — and the eating rule could not tell them apart, so it fell
 * through to "any food".
 *
 * <p><b>Three things this test holds that a routing test cannot:</b>
 *
 * <ol>
 *   <li>the refusal happens at all, and <b>names what the player said</b> rather than the catalogue's longest
 *       match on it (the first cut answered "eat the fish" with "You go looking for SALTED FISH", naming a thing
 *       the Chronicle never mentioned);</li>
 *   <li>it is checked <b>ahead of the two meat attempts</b> — my first cut sat below them, so a keeper carrying
 *       cooked meat who asked for a trout would have been fed the meat: the same defect one food along, and
 *       invisible unless you happen to be carrying meat;</li>
 *   <li><b>the family word still reaches the pack.</b> {@code eat the mushroom} with an OYSTER MUSHROOM in hand
 *       found nothing, because the reach side matched full names only — so the new refusal turned a wrong answer
 *       into a wrong refusal, telling the Chronicle there was no mushroom while they carried three. A family
 *       word has to be fixed on BOTH sides or the defect only moves.</li>
 * </ol>
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class NamingAFoodYouDoNotHaveIntegrationTest {

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

    private final List<UUID> given = new ArrayList<>();

    @AfterEach
    void takeBackWhatThisTestGave() {
        // One world, ~345 tests, and several of them count what the Chronicle carries or go looking for food.
        for (UUID id : given) {
            jdbc.update("DELETE FROM item_instance WHERE object_id=?", id);
            jdbc.update("DELETE FROM world_object WHERE id=?", id);
        }
        given.clear();
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

    /** Put an item in the Chronicle's own hands. An OWNED object has no location — world_object_check1. */
    private void carry(UUID me, String itemKey) {
        UUID id = UUID.randomUUID();
        Integer made = jdbc.update(
            "INSERT INTO world_object (id, object_type, display_name, lifecycle_state, current_owner_id, created_at) "
            + "SELECT ?, 'ITEM', d.display_name, 'ACTIVE', ?, now() FROM item_definition d WHERE d.item_key=?",
            id, me, itemKey);
        Assumptions.assumeTrue(made != null && made == 1, () -> "this catalogue has no " + itemKey);
        jdbc.update("INSERT INTO item_instance (object_id, item_key, condition_state) VALUES (?,?,'SOUND')",
            id, itemKey);
        given.add(id);
    }

    /** Strip every food the Chronicle can reach, so "what is to hand" is known rather than assumed. */
    private void emptyThePack(UUID me) {
        jdbc.update("UPDATE world_object SET lifecycle_state='ARCHIVED' WHERE current_owner_id=? AND id IN ("
            + "  SELECT i.object_id FROM item_instance i JOIN item_definition d ON d.item_key=i.item_key "
            + "  WHERE d.category='FOOD')", me);
    }

    private String said(String text) {
        var r = actions.resolve(text, UUID.randomUUID());
        assertNotNull(r, () -> "the composer must answer: " + text);
        return (r.perception() == null ? "" : r.perception()).toLowerCase(Locale.ROOT);
    }

    @Test
    void askingForAFishYouDoNotHaveIsRefusedAndNotSubstituted() {
        UUID me = aLivingChronicle();
        emptyThePack(me);
        carry(me, "oyster_mushroom");

        String prose = said("eat the fish");
        // THE DEFECT, in the words the player read: a mushroom, eaten, described.
        assertFalse(prose.contains("mushroom is earthy"),
            () -> "asking for a fish must not feed the Chronicle a mushroom: " + prose);
        assertTrue(prose.contains("looking for fish"),
            () -> "and the refusal must name what the player asked for: " + prose);
        // ...and the mushroom must still be there, uneaten.
        Integer left = jdbc.queryForObject(
            "SELECT count(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id "
            + "WHERE w.current_owner_id=? AND i.item_key='oyster_mushroom' AND w.lifecycle_state='ACTIVE'",
            Integer.class, me);
        assertEquals(1, left, "a refused meal consumes nothing");
    }

    @Test
    void theRefusalNamesWhatThePlayerSaidAndNotTheCataloguesLongestMatch() {
        UUID me = aLivingChronicle();
        emptyThePack(me);
        String plain = said("eat the fish");
        assertTrue(plain.contains("looking for fish"),
            () -> "the player said \"fish\", so the answer says fish: " + plain);
        assertFalse(plain.contains("salted fish") || plain.contains("smoked fish"),
            () -> "naming a thing the Chronicle never mentioned is its own small lie: " + plain);

        // ...but a player who names the preserved kind gets that named back, because a full-name match outranks
        // a head-noun one.
        String precise = said("eat the dried fish");
        assertTrue(precise.contains("dried fish"),
            () -> "a named preserve must be echoed as named: " + precise);
    }

    @Test
    void theCheckSitsAheadOfTheMeatAttempts() {
        // MY FIRST CUT SAT BELOW THEM. A keeper carrying cooked meat who asked for a trout would have been fed
        // the meat — the same defect one food along, and invisible unless you happen to be carrying meat.
        UUID me = aLivingChronicle();
        emptyThePack(me);
        carry(me, "cooked_game_meat");

        String prose = said("eat the fish");
        assertFalse(prose.contains("cooked meat is warm"),
            () -> "asking for a fish must not be answered with the meat in the pack: " + prose);
        assertTrue(prose.contains("looking for fish"), () -> "it must refuse, naming the fish: " + prose);
    }

    @Test
    void theFamilyWordStillReachesTheFoodInThePack() {
        // THE FIX CARRYING THE DEFECT, caught by testing the positive case. `eat the mushroom` with an oyster
        // mushroom in hand found nothing, because the reach side matched full names only — so the refusal above
        // told the Chronicle there was no mushroom while they carried one.
        UUID me = aLivingChronicle();
        emptyThePack(me);
        carry(me, "oyster_mushroom");

        String prose = said("eat the mushroom");
        assertFalse(prose.contains("looking for mushroom"),
            () -> "there IS a mushroom in the pack; refusing it is the fix carrying the bug: " + prose);
        assertTrue(prose.contains("mushroom"), () -> "the family word must reach it: " + prose);
    }

    @Test
    void aBareEatStillTakesWhateverIsToHand() {
        // The fallback is right and this is why it exists. A rule that made the Chronicle name their food before
        // eating would be a worse game than one that occasionally guessed.
        UUID me = aLivingChronicle();
        emptyThePack(me);
        carry(me, "oyster_mushroom");

        String prose = said("eat");
        assertFalse(prose.contains("looking for"),
            () -> "a bare \"eat\" names nothing, so there is nothing to refuse: " + prose);
        assertTrue(prose.contains("mushroom"), () -> "it takes what is to hand: " + prose);
    }
}
