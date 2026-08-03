package com.devosphere.draugr.routing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The routing regression fixture: every collision that has ever been recorded stays
 * blocked, and every process stays reachable.
 *
 * <p>Both halves matter and they pull against each other. Tightening the rule until
 * nothing collides is easy and useless — it makes every action an Architect call.
 * Loosening it until everything resolves is how the collisions got here. A change to
 * the vocabulary or to a process's category has to keep both columns green.
 */
class ProcessRoutingTest {

    /**
     * The eight collisions from {@code docs/architecture/action-routing-hardening.md}.
     * Four survived the whole-word fix in d0e238c and were found only by simulating
     * procedures by hand: split, shape, cordage and ash are legitimate whole words in
     * two processes at once, so only category and subject can separate them.
     */
    @Test
    @DisplayName("recorded collisions do not resolve to the process they wrongly matched")
    void collisionsBlocked() {
        // Substring collisions: 'ore' inside "forest", 'ash' inside "flashing".
        assertNull(RoutingFixture.resolve("gather mushrooms from the forest floor"));
        assertNull(RoutingFixture.resolve("install doors and windows with flashing"));
        // 'weather' inside "weatherproof", 'beam' against timber reinforcement.
        assertNull(RoutingFixture.resolve("weatherproof the shell with cladding"));
        assertNull(RoutingFixture.resolve("build the floor frame with beams"));
        // Whole-word collisions. These are the ones word boundaries could not fix.
        assertNull(RoutingFixture.resolve("split the fish into thin strips"));
        assertNull(RoutingFixture.resolve("shape the bow blank from a branch"));
        assertNull(RoutingFixture.resolve("assemble the bow with cordage and sinew"));

        // Collision 8 named the right domain by the wrong route: it reached gather_ash
        // because the sentence mentions ash. Rendering pitch from resin is exactly what
        // it describes, so the correct outcome here is a match, just not that one.
        assertEquals("render_pitch", RoutingFixture.resolve("process resin into binding compound with ash"));
        assertNotEquals("gather_ash", RoutingFixture.resolve("process resin into binding compound with ash"));
    }

    /**
     * One ordinary phrasing per process. A process no sentence can reach is as broken
     * as one that matches the wrong sentence — it just fails in the direction that
     * costs an Architect call instead of a wrong result, so nothing complains.
     */
    @Test
    @DisplayName("every process is reachable by ordinary phrasing")
    void everyProcessReachable() {
        assertEquals("timber_from_log",    RoutingFixture.resolve("square the log into a timber baulk"));
        assertEquals("split_planks",       RoutingFixture.resolve("split the log into planks with an axe"));
        assertEquals("shape_components",   RoutingFixture.resolve("shape the plank into a component"));
        assertEquals("reinforce_timber",   RoutingFixture.resolve("reinforce the timber beam with cordage"));
        assertEquals("reinforce_timber",   RoutingFixture.resolve("lash the planks into a structural beam"));
        assertEquals("dress_foundation",   RoutingFixture.resolve("dress the foundation stone"));
        assertEquals("dress_construction", RoutingFixture.resolve("dress the construction stone into rubble"));
        assertEquals("knap_tool_stone",    RoutingFixture.resolve("knap the flint core into a tool stone"));
        assertEquals("twist_cordage",      RoutingFixture.resolve("twist the plant fiber into cordage"));
        assertEquals("weave_textile",      RoutingFixture.resolve("weave the fiber into cloth"));
        assertEquals("ret_nettle",         RoutingFixture.resolve("ret the nettle stalks in water"));
        assertEquals("tan_hide",           RoutingFixture.resolve("tan the deer hide with oak bark"));
        assertEquals("cut_leather_cord",   RoutingFixture.resolve("cut a leather cord from the tanned leather"));
        assertEquals("gather_ash",         RoutingFixture.resolve("gather ash from the hearth"));
        assertEquals("leach_lye",          RoutingFixture.resolve("leach the wood ash into lye"));
        assertEquals("render_pitch",       RoutingFixture.resolve("render the pine resin into pitch"));
        assertEquals("carve_needle",       RoutingFixture.resolve("carve a needle from bone"));
        assertEquals("form_vessel",        RoutingFixture.resolve("form a vessel from the clay"));
        assertEquals("fire_vessel",        RoutingFixture.resolve("fire the clay pot in the kiln"));
        assertEquals("weave_large_basket", RoutingFixture.resolve("weave a large basket from plant fiber"));
        assertEquals("haft_stone_axe",     RoutingFixture.resolve("haft the stone axe head onto a wooden handle"));
    }

    /**
     * Weaving cloth and weaving a basket share a verb and a material, and differ only
     * in the object named. This is the pair V54 got wrong — the basket resolved to
     * cloth — so it is worth asserting on its own rather than leaving it buried in the
     * reachability sweep.
     */
    @Test
    @DisplayName("the object named decides between two processes that share a verb")
    void objectSeparatesSharedVerb() {
        assertEquals("weave_textile",      RoutingFixture.resolve("weave the fiber into cloth"));
        assertEquals("weave_large_basket", RoutingFixture.resolve("weave a basket from plant fiber"));
        // Cutting leather and splitting a log both answer to "cut"; only the subject
        // tells them apart, which is why the subject axis exists.
        assertEquals("cut_leather_cord",   RoutingFixture.resolve("cut a leather cord from the tanned leather"));
        assertEquals("split_planks",       RoutingFixture.resolve("cut the log into boards"));
    }

    /**
     * An unrecognised verb drops the category condition rather than matching nothing.
     * Failing closed here would be the expensive direction: an Architect call spent on
     * something the foundation already knows how to do.
     */
    @Test
    @DisplayName("text the vocabulary does not recognise still resolves on keyword and subject")
    void unclassifiableTextStillResolves() {
        assertNull(ActivityClassifier.classify("baulk the timber", RoutingFixture.VOCABULARY, RoutingFixture.PRECEDENCE));
        assertEquals("timber_from_log", RoutingFixture.resolve("baulk the timber"));
    }

    /**
     * A miss has to say which kind of miss it was, because vocabulary gaps and mechanic
     * gaps have completely different fixes and wildly different costs. This is what
     * V56's backlog turns into VOCABULARY / MECHANIC / KEYWORD / SUBJECT.
     */
    @Test
    @DisplayName("a miss reports how far the nearest candidate got")
    void missesAreDiagnosed() {
        // No process is HUNT at all, so nothing even shares the category. The world is
        // missing a mechanic — there is no way to process a fish — and no amount of
        // vocabulary would change that.
        ProcessMatcher.Result mechanic = RoutingFixture.diagnose("split the fish into thin strips");
        assertEquals("NONE", mechanic.furthestGate());
        assertNull(mechanic.nearProcessKey());

        // A construction process exists and the category is right, but it answers to
        // none of these words. That is a keyword gap, and cheap to close.
        ProcessMatcher.Result keyword = RoutingFixture.diagnose("weatherproof the shell with cladding");
        assertEquals("CATEGORY", keyword.furthestGate());
        assertEquals("reinforce_timber", keyword.nearProcessKey());

        // The right process was reached by the right verb and turned the material down.
        // Either a subject term is missing or the player meant something else entirely.
        ProcessMatcher.Result subject = RoutingFixture.diagnose("split the bone into strips");
        assertEquals("KEYWORD", subject.furthestGate());
        assertEquals("split_planks", subject.nearProcessKey());
    }

    /** Nothing at all should come back for text that names no work and no material. */
    @Test
    @DisplayName("empty and irrelevant text resolves to nothing")
    void nothingMatchesNothing() {
        assertNull(RoutingFixture.resolve(""));
        assertNull(RoutingFixture.resolve("   "));
        assertNull(RoutingFixture.resolve("i sit down and think about home"));
    }

    /**
     * GitHub #21: a natural plural on the subject must still resolve. "split the logs into planks"
     * used to return null because the subjects are singular ('log', 'plank') and matching was
     * whole-word — so a correct, common phrasing did nothing. Plural tolerance is on the SUBJECT
     * only; the verb/collision guards below must stay intact.
     */
    @Test
    @DisplayName("a plural subject still resolves (#21)")
    void pluralSubjectsResolve() {
        assertEquals("split_planks", RoutingFixture.resolve("split the logs into planks"));
        assertEquals("split_planks", RoutingFixture.resolve("split logs into planks"));
        assertEquals("split_planks", RoutingFixture.resolve("saw the logs into planks"));
        // Pluralising the subject must not defeat the collision guard: fish is still not timber.
        assertNull(RoutingFixture.resolve("split the fish into thin strips"));
    }
}
