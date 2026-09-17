package com.devosphere.draugr.world;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The backdrop resolver: table-driven, deterministic, and impossible to influence with an encounter (#234).
 *
 * <p>Needs no database, because the resolver is a pure function of the visual context — which is the property
 * the ticket asks for and the reason these run in a second rather than forty-seven minutes.
 */
class BackdropResolverTest {

    private VisualContextService.VisualContext context(String biome, List<VisualContextService.Feature> features) {
        return new VisualContextService.VisualContext(
            VisualContextService.VERSION, biome, features, "DAY", "SUMMER", "CLEAR", 14.0, true, "fp");
    }

    private VisualContextService.Feature site(String name) { return new VisualContextService.Feature("SITE:RESOURCE", name); }
    private VisualContextService.Feature built(String kind) { return new VisualContextService.Feature("BUILT:" + kind, kind); }

    /** Most specific wins, and the order is the documented one. */
    @Test void precedenceRunsFromTheMostSpecificOutward() {
        // A site outranks the biome.
        assertEquals("site.still-pond",
            BackdropResolver.resolve(context("WETLAND", List.of(site("Still pond")))).key());
        // A build outranks the biome but not a natural site — a lean-to on a riverbank is still a riverbank.
        assertEquals("site.still-pond",
            BackdropResolver.resolve(context("WETLAND", List.of(built("LEAN_TO"), site("Still pond")))).key());
        assertEquals("built.lean-to",
            BackdropResolver.resolve(context("GRASSLAND", List.of(built("LEAN_TO")))).key());
        // Bare ground falls to the biome.
        assertEquals("biome.grassland", BackdropResolver.resolve(context("GRASSLAND", List.of())).key());
    }

    /** Being inside outranks everything about the country outside, because you cannot see the country. */
    @Test void beingInsideTheRockOutranksTheWeatherAndTheHour() {
        var dark = new VisualContextService.VisualContext(
            VisualContextService.VERSION, "CAVE_INTERIOR", List.of(site("Bat roost")), "DAY", "SUMMER", "STORM", 9.0, false, "fp");
        assertEquals("interior.cave.dark", BackdropResolver.resolve(dark).key(),
            "a site in the cave does not outrank the fact of being in a cave");

        var lit = new VisualContextService.VisualContext(
            VisualContextService.VERSION, "CAVE_INTERIOR", List.of(), "NIGHT", "WINTER", "SNOW", -4.0, true, "fp");
        assertEquals("interior.cave.lit", BackdropResolver.resolve(lit).key(),
            "light in the chamber changes what it looks like; the storm outside does not");
    }

    /** Ties break by name, so two sites on one chunk never toss a coin. */
    @Test void tiesBreakStablyRatherThanArbitrarily() {
        var a = context("COAST", List.of(site("Shell bed"), site("Salt marsh")));
        var b = context("COAST", List.of(site("Salt marsh"), site("Shell bed")));
        assertEquals(BackdropResolver.resolve(a).key(), BackdropResolver.resolve(b).key(),
            "the order the features arrived in must not change the backdrop");
        assertEquals("site.salt-marsh", BackdropResolver.resolve(a).key());
    }

    /** Same context, same answer, every time — asserted rather than assumed. */
    @Test void resolutionIsDeterministic() {
        var c = context("TEMPERATE_FOREST", List.of(built("LEAN_TO"), site("Wild herb grove")));
        String first = BackdropResolver.resolve(c).key();
        for (int i = 0; i < 50; i++)
            assertEquals(first, BackdropResolver.resolve(c).key(), "resolution must not drift across calls");
    }

    /** It can never return nothing — the last tier always matches. */
    @Test void thereIsAlwaysAKey() {
        assertEquals(BackdropResolver.FALLBACK_KEY, BackdropResolver.resolve(null).key());
        assertEquals(BackdropResolver.FALLBACK_KEY, BackdropResolver.resolve(context(null, List.of())).key());
        assertEquals(BackdropResolver.FALLBACK_KEY, BackdropResolver.resolve(context("  ", List.of())).key());
        for (String biome : List.of("OCEAN","WETLAND","TEMPERATE_FOREST","GRASSLAND","HIGHLAND","MOUNTAIN",
                                    "RIVER_BANK","COAST","CAVE_MOUTH","CAVE_INTERIOR")) {
            var choice = BackdropResolver.resolve(context(biome, List.of()));
            assertNotNull(choice.key());
            assertFalse(choice.key().isBlank(), biome + " resolved to nothing");
            assertNotNull(choice.reason(), biome + " gave no reason");
        }
    }

    /**
     * Encounter-independence, which the ticket asks for as a rule and which is stronger than a rule here: the
     * context the resolver is handed carries no encounter at all, so there is nothing to leak. This asserts the
     * shape rather than a behaviour, because a behaviour test could only prove it for the cases it thought of.
     */
    @Test void thereIsNoEncounterForTheResolverToSee() {
        List<String> fields = java.util.Arrays.stream(
                VisualContextService.VisualContext.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName).toList();
        for (String leaky : List.of("encounter", "monster", "predator", "threat", "species"))
            assertFalse(fields.contains(leaky),
                "the visual context must carry no encounter state — what is hunting you is not scenery: " + fields);
    }

    private VisualContextService.VisualContext outside(String biome, String weather, double tempC, boolean lit) {
        return new VisualContextService.VisualContext(
            VisualContextService.VERSION, biome, List.of(), "DAY", "WINTER", weather, tempC, lit, "fp");
    }

    /**
     * #226's complaint, made concrete: a sunlit summer field must not be shown at midnight in a blizzard. The
     * chain offers the honest version first and degrades to one that is merely not a lie.
     */
    @Test void darkAndSnowAreOfferedBeforeTheBareKey() {
        assertEquals(List.of("biome.grassland.snow.night", "biome.grassland.night", "biome.grassland.snow", "biome.grassland"),
            BackdropResolver.resolve(outside("GRASSLAND", "SNOW", -6.0, false)).candidates(),
            "a snowy night offers both, then each alone, then the bare ground");

        assertEquals(List.of("biome.grassland.night", "biome.grassland"),
            BackdropResolver.resolve(outside("GRASSLAND", "CLEAR", 11.0, false)).candidates());

        assertEquals(List.of("biome.grassland.snow", "biome.grassland"),
            BackdropResolver.resolve(outside("GRASSLAND", "SNOW", 1.0, true)).candidates(),
            "falling snow counts even above freezing");

        assertEquals(List.of("biome.grassland.snow", "biome.grassland"),
            BackdropResolver.resolve(outside("GRASSLAND", "CLEAR", -3.0, true)).candidates(),
            "and what fell yesterday is still lying when it is cold enough");

        assertEquals(List.of("biome.grassland"),
            BackdropResolver.resolve(outside("GRASSLAND", "CLEAR", 14.0, true)).candidates(),
            "an ordinary day needs no variant at all");
    }

    /** The dark is the worse lie, so it outranks the snow when both cannot be had. */
    @Test void nightOutranksSnowWhenOnlyOneCanBeHad() {
        List<String> chain = BackdropResolver.resolve(outside("HIGHLAND", "SNOW", -9.0, false)).candidates();
        assertTrue(chain.indexOf("biome.highland.night") < chain.indexOf("biome.highland.snow"),
            "a daylit snow image at midnight is a worse lie than a snowless night one: " + chain);
    }

    /**
     * Eligibility must never be able to promote one place over another. It decorates the key precedence already
     * chose; it does not choose.
     */
    @Test void theHourCannotChangeWhichPlaceThisIs() {
        var day   = new VisualContextService.VisualContext(
            VisualContextService.VERSION, "WETLAND", List.of(site("Still pond"), built("LEAN_TO")), "DAY", "SUMMER", "CLEAR", 14.0, true, "fp");
        var night = new VisualContextService.VisualContext(
            VisualContextService.VERSION, "WETLAND", List.of(site("Still pond"), built("LEAN_TO")), "NIGHT", "WINTER", "SNOW", -8.0, false, "fp");
        assertEquals(BackdropResolver.resolve(day).key(), BackdropResolver.resolve(night).key(),
            "the same ground is the same place after dark");
        assertEquals("site.still-pond", BackdropResolver.resolve(night).key());
    }

    /**
     * The chain is what keeps eligibility from ever naming something absent: it is bounded, it always ends in the
     * key the tiers guaranteed, and every link is a slug.
     */
    @Test void everyChainEndsSomewhereThatExists() {
        for (String biome : List.of("OCEAN","WETLAND","TEMPERATE_FOREST","GRASSLAND","HIGHLAND","MOUNTAIN",
                                    "RIVER_BANK","COAST","CAVE_MOUTH","CAVE_INTERIOR"))
            for (String weather : List.of("CLEAR","SNOW","STORM","RAIN"))
                for (double t : new double[]{-12.0, 0.0, 17.0})
                    for (boolean lit : new boolean[]{true, false}) {
                        var choice = BackdropResolver.resolve(outside(biome, weather, t, lit));
                        assertFalse(choice.candidates().isEmpty(), biome + " offered nothing");
                        assertTrue(choice.candidates().size() <= 4, "the chain must stay bounded: " + choice.candidates());
                        assertEquals(choice.key(), choice.candidates().get(choice.candidates().size() - 1),
                            "the last link must be the key the tiers guaranteed: " + choice.candidates());
                        assertEquals(choice.candidates().stream().distinct().toList(), choice.candidates(),
                            "no link may be offered twice: " + choice.candidates());
                        for (String candidate : choice.candidates())
                            assertTrue(candidate.matches("[a-z0-9.-]+"), "a candidate must be safe to use as a name: " + candidate);
                    }
    }

    /** Inside the rock the sky is not a fact, so nothing decorates the key. */
    @Test void theWeatherDoesNotReachInsideARock() {
        assertEquals(List.of("interior.cave.dark"),
            BackdropResolver.resolve(outside("CAVE_INTERIOR", "SNOW", -14.0, false)).candidates(),
            "a blizzard outside changes nothing about a dark chamber");
        assertEquals(List.of("interior.cave.lit"),
            BackdropResolver.resolve(outside("CAVE_INTERIOR", "STORM", -14.0, true)).candidates());
    }

    /** Keys are slugs: readable, stable, and free of the punctuation a file name would choke on. */
    @Test void keysAreCleanSlugs() {
        assertEquals("site.wild-herb-grove",
            BackdropResolver.resolve(context("GRASSLAND", List.of(site("Wild herb grove")))).key());
        assertEquals("site.slow-river-reach",
            BackdropResolver.resolve(context("RIVER_BANK", List.of(site("Slow river reach")))).key());
        assertEquals("built.wattle-and-daub-hut",
            BackdropResolver.resolve(context("GRASSLAND", List.of(built("WATTLE_AND_DAUB_HUT")))).key());
        for (String biome : List.of("CAVE_MOUTH", "TEMPERATE_FOREST", "RIVER_BANK")) {
            String key = BackdropResolver.resolve(context(biome, List.of())).key();
            assertTrue(key.matches("[a-z0-9.-]+"), "a key must be safe to use as a name: " + key);
        }
    }

    private VisualContextService.VisualContext standing(String biome, List<String> around, String weather, double tempC, boolean lit) {
        return new VisualContextService.VisualContext(
            VisualContextService.VERSION, biome, List.of(), "DAY", "SUMMER", weather, tempC, lit, around, "fp");
    }

    /**
     * #232/#234: what can be seen of the next ground refines the picture of this ground — a dominant landform
     * beside it (adjacent), a different kind of country at its edge (ecotone), or the same country all round
     * (regional). It never changes which place this is: the key and the reason are still the ground's own.
     */
    @Test void theSettingRefinesTheGroundButNeverReplacesIt() {
        var beside = BackdropResolver.resolve(standing("GRASSLAND", List.of("GRASSLAND", "MOUNTAIN", "TEMPERATE_FOREST"), "CLEAR", 14.0, true));
        assertEquals("biome.grassland", beside.key(), "the ground is still grassland");
        assertEquals("BIOME", beside.reason());
        assertEquals(List.of("biome.grassland.beside-mountain", "biome.grassland"), beside.candidates(),
            "a mountain in view dominates the picture over a wood at the edge");

        assertEquals(List.of("biome.grassland.edge-temperate-forest", "biome.grassland"),
            BackdropResolver.resolve(standing("GRASSLAND", List.of("GRASSLAND", "TEMPERATE_FOREST"), "CLEAR", 14.0, true)).candidates(),
            "a different country at the edge of this one is an ecotone");

        assertEquals(List.of("biome.grassland.deep", "biome.grassland"),
            BackdropResolver.resolve(standing("GRASSLAND", List.of("GRASSLAND"), "CLEAR", 14.0, true)).candidates(),
            "the same country in every direction is deep in it");

        assertEquals(List.of("biome.grassland"),
            BackdropResolver.resolve(standing("GRASSLAND", List.of(), "CLEAR", 14.0, true)).candidates(),
            "nothing seen beyond this ground refines nothing");
    }

    /** Ties among neighbours break by name, and snow can lie on a setting; the chain stays within its bound. */
    @Test void theSettingIsDeterministicAndStaysBounded() {
        var a = standing("HIGHLAND", List.of("WETLAND", "GRASSLAND", "HIGHLAND"), "SNOW", -4.0, true);
        var b = standing("HIGHLAND", List.of("HIGHLAND", "GRASSLAND", "WETLAND"), "SNOW", -4.0, true);
        assertEquals(BackdropResolver.resolve(a).candidates(), BackdropResolver.resolve(b).candidates(),
            "the order neighbours arrive in must not change the picture");
        assertEquals(List.of("biome.highland.edge-grassland.snow", "biome.highland.edge-grassland", "biome.highland.snow", "biome.highland"),
            BackdropResolver.resolve(a).candidates());

        assertEquals(List.of("biome.grassland.beside-mountain", "biome.grassland"),
            BackdropResolver.resolve(standing("GRASSLAND", List.of("OCEAN", "MOUNTAIN"), "CLEAR", 12.0, true)).candidates(),
            "the sea and a mountain both dominate; the tie breaks by name, and MOUNTAIN sorts first");

        for (String weather : List.of("CLEAR", "SNOW"))
            for (boolean lit : new boolean[]{true, false}) {
                var choice = BackdropResolver.resolve(standing("TEMPERATE_FOREST", List.of("MOUNTAIN", "WETLAND"), weather, -2.0, lit));
                assertTrue(choice.candidates().size() <= 4, "bounded: " + choice.candidates());
                assertEquals(choice.key(), choice.candidates().get(choice.candidates().size() - 1), "ends in the key: " + choice.candidates());
            }
    }

    /** A site or a build is already the most specific picture; the setting only refines bare ground. */
    @Test void theSettingDoesNotDecorateASiteOrABuild() {
        var onSite = new VisualContextService.VisualContext(VisualContextService.VERSION, "WETLAND", List.of(site("Still pond")),
            "DAY", "SUMMER", "CLEAR", 14.0, true, List.of("MOUNTAIN"), "fp");
        assertEquals(List.of("site.still-pond"), BackdropResolver.resolve(onSite).candidates());
    }

    private VisualContextService.Feature growing(String key) { return new VisualContextService.Feature("FLORA:" + key, key); }

    /**
     * A stand REFINES the ground; it does not replace it (#224).
     *
     * <p>Unlike a site or a build, something is growing on almost every piece of ground there is. If this tier
     * replaced the key outright, every meadow whose particular plant has no image of its own would fall past its
     * own ground to the registry root — the exact failure this file already settled for the setting tier. So the
     * chain is offered stand-first and degrades to the biome underneath it.
     */
    @Test void aStandIsOfferedBeforeTheGroundAndFallsBackToIt() {
        var brake = context("GRASSLAND", List.of(growing("blackberry")));
        var choice = BackdropResolver.resolve(brake);

        assertEquals("flora.blackberry", choice.key(), "the fullest stand is what this place looks like");
        assertEquals("FLORA_HERE", choice.reason());
        assertEquals("flora.blackberry", choice.candidates().get(0), "the stand is offered first");
        assertEquals("biome.grassland", choice.candidates().get(choice.candidates().size() - 1),
            "and the chain ends on the ground it grows on, which always answers: " + choice.candidates());
    }

    /** Ranked under the things it grows around: a hut on a heath is a hut. */
    @Test void aSiteOrABuildOutranksWhatIsGrowingThere() {
        assertEquals("site.clay-beds",
            BackdropResolver.resolve(context("GRASSLAND", List.of(growing("nettle"), site("Clay beds")))).key());
        assertEquals("built.pit-house",
            BackdropResolver.resolve(context("GRASSLAND", List.of(growing("nettle"), built("PIT_HOUSE")))).key());
    }

    /** The fullest stand decides — the context reports them fullest first, and the resolver trusts that order. */
    @Test void theFullestStandIsTheOneThatDecides() {
        var many = context("TEMPERATE_FOREST", List.of(growing("bramble_berry"), growing("nettle")));
        assertEquals("flora.bramble-berry", BackdropResolver.resolve(many).key());
    }
}
