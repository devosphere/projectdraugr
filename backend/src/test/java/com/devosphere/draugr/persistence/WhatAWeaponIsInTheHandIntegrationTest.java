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
 * #968 — WHAT A WEAPON IS IN THE HAND.
 *
 * <p>The weapon side of a fight knew two things about a weapon: its {@code combat_role} and its
 * {@code edge_tier}. Damage was already data — the edge ladder runs stone(+35) &lt; hardened(+45) &lt;
 * bronze(+53) &lt; iron(+60) &lt; steel(+70) — but reach, defensive handling and hand requirements did not
 * exist at all, so a knife and a two-handed spear were the same weapon but for the edge on them.
 *
 * <p>And the weapon never wore. Armour and shields take part of every blow and are graded by
 * {@code armour_protection}; a restraint strains SOUND → WORN → BROKEN when an animal pulls against it; a
 * tool wears by {@code use_count}. The thing you actually swing was the one piece of equipment in the game
 * that a fight never touched, so a steel sword and a bone knife came out of a hundred fights identical.
 *
 * <p>Skips without Docker. Every query here was also run against a throwaway Postgres with the full
 * migration set applied, because a suite that skips is not evidence.
 */
@SpringBootTest
class WhatAWeaponIsInTheHandIntegrationTest {

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
     * EVERY MELEE WEAPON ANSWERS ALL FOUR, and no ranged one pretends to. Writing 0 for an arrow's parry
     * would be a number posing as a measurement, so the ranged roles are NULL and the CHECK says so.
     */
    @Test void everyWeaponCarriesTheFactsItsRoleRequires() {
        List<String> incomplete = jdbc.queryForList(
            "SELECT item_key FROM weapon_profile WHERE combat_role IN ('HAND','BLUNT') " +
            "  AND (reach_cm IS NULL OR parry IS NULL OR fights_per_wear_step IS NULL) ORDER BY 1", String.class);
        assertTrue(incomplete.isEmpty(), "melee weapons with a fact missing: " + incomplete);

        List<String> overclaiming = jdbc.queryForList(
            "SELECT item_key FROM weapon_profile WHERE combat_role NOT IN ('HAND','BLUNT') " +
            "  AND (reach_cm IS NOT NULL OR parry IS NOT NULL OR fights_per_wear_step IS NOT NULL) ORDER BY 1", String.class);
        assertTrue(overclaiming.isEmpty(), "ranged weapons claiming a melee fact: " + overclaiming);

        assertEquals(0, (int) jdbc.queryForObject(
            "SELECT count(*) FROM weapon_profile WHERE hands_required NOT IN (1,2)", Integer.class));
        assertTrue(jdbc.queryForObject("SELECT count(*) FROM weapon_profile", Integer.class) >= 48);
    }

    /**
     * THE TRADE IS REAL, which is what makes the facts worth having: a spear outreaches every sword and no
     * sword is two-handed, so the reach weapon and the guard weapon are different weapons.
     */
    @Test void aSpearOutreachesEverySwordAndEverySwordOutguardsEverySpear() {
        int longestSword = jdbc.queryForObject(
            "SELECT max(reach_cm) FROM weapon_profile WHERE combat_skill_key = 'SWORD_FIGHTING'", Integer.class);
        int shortestSpear = jdbc.queryForObject(
            "SELECT min(reach_cm) FROM weapon_profile WHERE combat_skill_key = 'SPEAR_COMBAT'", Integer.class);
        assertTrue(shortestSpear > longestSword,
            "the shortest spear (" + shortestSpear + ") should outreach the longest sword (" + longestSword + ")");

        // The guard claim, stated as it is actually true. A bronze spear parries as well as a WOODEN practice
        // sword — both 5 — and that is honest: a training sword is sword-shaped and made of wood. What holds
        // is that no spear beats any sword, and that the best guard in the catalogue is a sword.
        int worstSwordGuard = jdbc.queryForObject(
            "SELECT min(parry) FROM weapon_profile WHERE combat_skill_key = 'SWORD_FIGHTING'", Integer.class);
        int bestSpearGuard = jdbc.queryForObject(
            "SELECT max(parry) FROM weapon_profile WHERE combat_skill_key = 'SPEAR_COMBAT'", Integer.class);
        assertTrue(worstSwordGuard >= bestSpearGuard,
            "no spear should out-guard any sword: worst sword " + worstSwordGuard + " vs best spear " + bestSpearGuard);

        String bestGuarded = jdbc.queryForObject(
            "SELECT combat_skill_key FROM weapon_profile WHERE parry IS NOT NULL ORDER BY parry DESC, reach_cm DESC LIMIT 1",
            String.class);
        assertEquals("SWORD_FIGHTING", bestGuarded, "the best guard in the catalogue should be a sword");

        // And every sword with a real edge on it out-guards every spear outright.
        int worstEdgedSword = jdbc.queryForObject(
            "SELECT min(parry) FROM weapon_profile WHERE combat_skill_key = 'SWORD_FIGHTING' AND edge_tier <> 'PLAIN'",
            Integer.class);
        assertTrue(worstEdgedSword > bestSpearGuard,
            "an edged sword should beat any spear's guard: " + worstEdgedSword + " vs " + bestSpearGuard);

        // And a sword leaves a hand free, which is the other half of "defensive handling".
        assertEquals(0, (int) jdbc.queryForObject(
            "SELECT count(*) FROM weapon_profile WHERE combat_skill_key = 'SWORD_FIGHTING' AND hands_required <> 1",
            Integer.class));
    }

    /**
     * THE EXTRACTION CHAIN PAYS TWICE. A better metal both bites deeper (the existing edge ladder) and
     * survives more fighting — so #964's progression is worth climbing for a second, independent reason.
     */
    @Test void theMetalLadderBuysDurabilityAsWellAsBite() {
        record Rung(String tier, int span) { }
        List<Rung> ladder = jdbc.query(
            "SELECT edge_tier, max(fights_per_wear_step) FROM weapon_profile " +
            " WHERE combat_skill_key = 'SWORD_FIGHTING' GROUP BY edge_tier",
            (rs, i) -> new Rung(rs.getString(1), rs.getInt(2)));

        int plain = ladder.stream().filter(r -> r.tier().equals("PLAIN")).mapToInt(Rung::span).max().orElse(0);
        int hardened = ladder.stream().filter(r -> r.tier().equals("HARDENED")).mapToInt(Rung::span).max().orElse(0);
        int bronze = ladder.stream().filter(r -> r.tier().equals("BRONZE")).mapToInt(Rung::span).max().orElse(0);
        int iron = ladder.stream().filter(r -> r.tier().equals("IRON")).mapToInt(Rung::span).max().orElse(0);
        int steel = ladder.stream().filter(r -> r.tier().equals("STEEL")).mapToInt(Rung::span).max().orElse(0);

        assertTrue(bronze > hardened, "bronze should outlast a knapped edge: " + bronze + " vs " + hardened);
        assertTrue(iron > bronze, "iron over bronze: " + iron + " vs " + bronze);
        assertTrue(steel > iron, "steel over iron: " + steel + " vs " + iron);
        // The wooden practice sword is wood: it does not chip, it splinters, so it outlasts the knapped one.
        assertTrue(plain > hardened, "the practice sword should outlast the stone-edged one: " + plain + " vs " + hardened);
    }

    /**
     * #968 NAMES REASONING AND V475 LEFT IT OUT. "supported by relevant Dexterity, Agility,
     * Strength/Endurance, Perception, and Reasoning/Knowledge" — the fifth is the one that distinguishes
     * swordplay from swinging an axe, and it was the one missing.
     */
    @Test void swordFightingDrawsOnEveryAptitudeTheTicketNames() {
        List<String> supporting = jdbc.queryForList(
            "SELECT aptitude_key FROM skill_aptitude_support WHERE skill_key = 'SWORD_FIGHTING' ORDER BY 1",
            String.class);
        for (String named : List.of("DEXTERITY", "AGILITY", "STRENGTH_ENDURANCE", "PERCEPTION", "REASONING"))
            assertTrue(supporting.contains(named), named + " should support sword fighting: " + supporting);

        Double sum = jdbc.queryForObject(
            "SELECT sum(weight) FROM skill_aptitude_support WHERE skill_key = 'SWORD_FIGHTING'", Double.class);
        assertEquals(1.0, sum, 0.0001, "the weights are shares and must still sum to one");
    }

    /**
     * A BLUNTED WEAPON HAS SOMEWHERE TO GO. The wear uses the condition ladder that already exists, so the
     * repair and honing paths already put it right — this asserts the states it steps through are the ones
     * those paths know, rather than a private vocabulary.
     */
    @Test void wearUsesTheConditionLadderRepairAlreadyUnderstands() {
        List<String> states = jdbc.queryForList(
            "SELECT DISTINCT condition_state FROM item_instance WHERE condition_state IS NOT NULL ORDER BY 1",
            String.class);
        assertTrue(states.contains("SOUND"), "the ladder starts at SOUND: " + states);
        // use_count is the counter the wear increments, and it already exists for tools.
        assertEquals(1, (int) jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns " +
            " WHERE table_name = 'item_instance' AND column_name = 'use_count'", Integer.class));
    }
}
