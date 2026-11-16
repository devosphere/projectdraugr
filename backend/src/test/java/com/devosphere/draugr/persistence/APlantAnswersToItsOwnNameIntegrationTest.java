package com.devosphere.draugr.persistence;

import com.devosphere.draugr.item.PhysicalItemService;
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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #877 — A PLANT IS NAMED BY ITS NAME, NOT BY ITS CATALOGUE KEY'S HEAD NOUN.
 *
 * <p>Standing in front of a woundwort plant that {@code look around} had just listed, {@code gather woundwort}
 * answered <i>"You have not got enough woundwort leaf within reach. What is missing is not here — it lies
 * wherever you last set it down"</i>. The catalogue calls the plant {@code woundwort_plant}, so the set of
 * phrases that name something growing held "woundwort plant" and "woundwort leaf" and never "woundwort" —
 * the classifier did not recognise the bare word, and the sentence fell past the gather to the poultice
 * process, which consumes the leaf the player had not got yet.
 *
 * <p>This is the project's generic-word shape: the specific entries exist and the plain family word reaches
 * nothing. The fix strips only the head nouns that say nothing — plant, bush, shrub, herb — because the
 * others carry the meaning: {@code meadow_grass} must not become "meadow", and {@code climbing_vine} must not
 * become "climbing".
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class APlantAnswersToItsOwnNameIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired JdbcTemplate jdbc;

    private void aWorld() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    /** The defect, in the words it was reported in. */
    @Test
    void theBareNameOfAPlantNamesSomethingThatGrows() {
        aWorld();
        assertTrue(items.namesSomethingThatGrows("gather woundwort"),
            "woundwort is a plant this world grows, and `gather woundwort` must say so");
        assertTrue(items.namesSomethingThatGrows("gather the woundwort plant"),
            "and the longer way of saying it still does");
    }

    /**
     * EVERY plant whose key carries one of those head nouns, not just the one that was reported. A fix that
     * only reaches woundwort would leave the same sentence failing for twenty-six other plants.
     */
    @Test
    void soDoesEveryOtherPlantWhoseKeyCarriesOne() {
        aWorld();
        List<String> keys = jdbc.queryForList(
            "SELECT flora_key FROM flora_definition WHERE organism_type <> 'TREE' " +
            "AND flora_key ~ '_(plant|bush|shrub|herb)$' ORDER BY flora_key", String.class);
        assertTrue(keys.size() >= 20, () -> "this world should carry a good many such plants, found " + keys.size());

        List<String> unreachable = new ArrayList<>();
        for (String key : keys) {
            String bare = key.replace('_', ' ').replaceAll(" (plant|bush|shrub|herb)$", "");
            if (!items.namesSomethingThatGrows("gather " + bare)) unreachable.add(bare);
        }
        assertEquals(List.of(), unreachable,
            () -> "these plants cannot be named by their own name: " + unreachable);
    }

    /**
     * AND THE HEAD NOUNS THAT CARRY MEANING ARE LEFT ALONE. "meadow" is not a plant and neither is
     * "climbing"; stripping those would hand two ordinary words to the plant gatherer.
     */
    @Test
    void aHeadNounThatCarriesTheMeaningIsNotStripped() {
        aWorld();
        for (String notAPlant : new String[]{"gather meadow", "gather climbing"})
            assertTrue(!items.namesSomethingThatGrows(notAPlant),
                () -> "`" + notAPlant + "` names no plant, and must not be read as one");
        // While the whole names still do.
        assertTrue(items.namesSomethingThatGrows("gather meadow grass"), "meadow grass is a plant");
    }
}
