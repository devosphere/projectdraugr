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
}
