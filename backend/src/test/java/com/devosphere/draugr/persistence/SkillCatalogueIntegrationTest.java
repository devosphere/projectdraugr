package com.devosphere.draugr.persistence;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #954 — THE SKILL CATALOGUE KEEPS ITS WORD.
 *
 * <p>This project's recurring defect is data that declares a capability no code reaches, so a layer of
 * thirty-eight new numbers is exactly the sort of thing that rots into a card collection. These are the
 * guards that stop it: every skill must be REACHABLE by something a player can actually do, every intent
 * must have DECIDED whether it practises one, and every weapon must name the discipline swinging it
 * rehearses. A skill or an intent added carelessly fails here rather than quietly doing nothing.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class SkillCatalogueIntegrationTest {

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

    @Autowired JdbcTemplate jdbc;

    /**
     * NO SKILL MAY BE A TOKEN. Each one must be reached by an intent's impact card, a catalogued process,
     * or a weapon that exists — otherwise it is a number that can never move, which is the thing this
     * project refuses to ship.
     */
    @Test void everySkillIsReachableBySomethingAPlayerCanDo() {
        List<String> orphans = jdbc.queryForList(
            "SELECT s.skill_key FROM skill_definition s " +
            " WHERE NOT EXISTS (SELECT 1 FROM activity_impact  WHERE skill_key        = s.skill_key) " +
            "   AND NOT EXISTS (SELECT 1 FROM material_process WHERE skill_key        = s.skill_key) " +
            "   AND NOT EXISTS (SELECT 1 FROM weapon_profile   WHERE combat_skill_key = s.skill_key) " +
            " ORDER BY 1", String.class);
        assertTrue(orphans.isEmpty(), "skills nothing can ever practise: " + orphans);
        assertTrue(jdbc.queryForObject("SELECT count(*) FROM skill_definition", Integer.class) >= 38,
            "the catalogue should not have shrunk");
    }

    /**
     * EVERY ACT HAS DECIDED. A card carries a skill or the reason it carries none — the same standard
     * activity_impact already holds itself to for its footprint. The CHECK constraint enforces it, so
     * this asserts the decision was actually made for all of them rather than defaulted.
     */
    @Test void everyIntentDecidesWhetherItPractisesASkill() {
        assertEquals(0, (int) jdbc.queryForObject(
            "SELECT count(*) FROM activity_impact WHERE skill_key IS NULL AND no_skill_reason IS NULL", Integer.class),
            "an intent that has not decided");
        assertEquals(0, (int) jdbc.queryForObject(
            "SELECT count(*) FROM activity_impact WHERE skill_key IS NOT NULL AND no_skill_reason IS NOT NULL", Integer.class),
            "an intent that both practises a skill and gives a reason it does not");
        // The acts with no discipline are a deliberate, small set: resting, warming, eating, relieving
        // oneself, lashing out, and the sentence that was not understood. If this grows, somebody decided
        // an act teaches nothing and should have to say so here too.
        assertTrue(jdbc.queryForObject("SELECT count(*) FROM activity_impact WHERE skill_key IS NULL", Integer.class) <= 20,
            "too many acts are being excused from teaching anything");
    }

    /** A skill with no supporting aptitude would learn in isolation and never assist anything. */
    @Test void everySkillIsSupportedAndTheWeightsAreShares() {
        List<String> unsupported = jdbc.queryForList(
            "SELECT s.skill_key FROM skill_definition s LEFT JOIN skill_aptitude_support a USING (skill_key) " +
            " WHERE a.skill_key IS NULL ORDER BY 1", String.class);
        assertTrue(unsupported.isEmpty(), "skills no aptitude supports: " + unsupported);

        List<String> adrift = jdbc.queryForList(
            "SELECT skill_key || ' sums to ' || round(sum(weight)::numeric, 4) FROM skill_aptitude_support " +
            " GROUP BY skill_key HAVING abs(sum(weight) - 1.0) > 0.0001 ORDER BY 1", String.class);
        assertTrue(adrift.isEmpty(), "support weights must be shares of one: " + adrift);
    }

    /** Each aptitude must point at a column that exists, or competence() would ask for nothing. */
    @Test void everyAptitudeNamesARealAdaptationColumn() {
        List<String> missing = jdbc.queryForList(
            "SELECT a.aptitude_key || ' -> ' || a.adaptation_column FROM aptitude a " +
            " WHERE NOT EXISTS (SELECT 1 FROM information_schema.columns " +
            "                    WHERE table_name = 'chronicle_capability_adaptation' " +
            "                      AND column_name = a.adaptation_column) ORDER BY 1", String.class);
        assertTrue(missing.isEmpty(), "aptitudes pointing at columns that do not exist: " + missing);
    }

    /**
     * SEVEN KNIVES, AND NOW ALL OF THEM WEAPONS.
     *
     * <p>Before #954 not one knife had a weapon_profile row, and the combat query joins that table — so a
     * knapped flint knife, a thing whose whole purpose is an edge, could not be raised against an animal
     * at all. The skill #954 asks for ("knife combat, distinct from carving") was unreachable because the
     * weapon was. This asserts the capability, not just the number.
     */
    @Test void aKnifeIsAWeaponAndNamesItsDiscipline() {
        List<String> unarmed = jdbc.queryForList(
            "SELECT i.item_key FROM item_definition i " +
            " WHERE i.item_key LIKE '%knife%' AND i.item_key NOT LIKE '%sheath%' " +
            "   AND NOT EXISTS (SELECT 1 FROM weapon_profile w WHERE w.item_key = i.item_key) ORDER BY 1", String.class);
        assertTrue(unarmed.isEmpty(), "knives that still cannot be fought with: " + unarmed);
        assertEquals(7, (int) jdbc.queryForObject(
            "SELECT count(*) FROM weapon_profile WHERE combat_skill_key = 'KNIFE_COMBAT'", Integer.class));
    }

    /** A weapon with no discipline would be swung and teach nothing; NOT NULL makes it impossible. */
    @Test void everyWeaponNamesTheDisciplineThatSwingsIt() {
        assertEquals(0, (int) jdbc.queryForObject(
            "SELECT count(*) FROM weapon_profile WHERE combat_skill_key IS NULL", Integer.class));
        // The disciplines must actually be distinct: HAND holds both spears and axes, which are not one
        // skill, and that is why the mapping is per item rather than per combat_role.
        assertTrue(jdbc.queryForObject(
            "SELECT count(DISTINCT combat_skill_key) FROM weapon_profile WHERE combat_role = 'HAND'", Integer.class) >= 3,
            "the hand weapons should not collapse into one discipline");
    }

    /**
     * THE FORGE IS A DISCIPLINE OF ITS OWN. material_process.domain_key calls the smelts and forges
     * 'tools' and 'items' along with three hundred other things, so the forging skill is reached by the
     * process keys themselves. If that mapping stops matching, forging silently becomes generic craft.
     */
    @Test void forgingAndTheOtherNamedCraftsAreReachedByTheirProcesses() {
        assertTrue(jdbc.queryForObject(
            "SELECT count(*) FROM material_process WHERE skill_key = 'FORGING'", Integer.class) >= 20,
            "the smelts, forges, alloys and casts should all be forging");
        // Tempering CLAY is not forging, however much it sounds like it.
        assertEquals(0, (int) jdbc.queryForObject(
            "SELECT count(*) FROM material_process WHERE process_key LIKE 'temper_clay%' AND skill_key = 'FORGING'", Integer.class));
        for (String expected : new String[]{"WEAVING", "KNAPPING", "PRESERVING", "CARVING"})
            assertTrue(jdbc.queryForObject(
                "SELECT count(*) FROM material_process WHERE skill_key = ?", Integer.class, expected) > 0,
                expected + " should be reached by catalogued processes");
    }
}
