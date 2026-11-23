package com.devosphere.draugr.chronicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ILLNESS ONLY EVER ROSE, AND A SICKNESS IS NOT A WOUND (#909, #913).
 *
 * <p>Two rules, both pure over numbers and both the cause of a live defect, so they are read here without a
 * database or a world.
 *
 * <p><b>#909.</b> Every source of illness the simulation holds is answerable — wash, get dry, get warm, vent
 * the hearth, dig a latrine, dress the wound that is feeding it. Answering all of them left the Chronicle
 * exactly as sick as before, because the only arithmetic illness had was addition. Above 80 that is Critical
 * and at 100 it is Systemic Illness, so a Chronicle who had put right everything the world held against them
 * still had nowhere to go but down.
 *
 * <p><b>#913.</b> The one-word health label returned "Injured" for a sickness, so Chronicle 41 — illness 64,
 * injury 0, nothing bleeding — was labelled Injured, and every reader that trusted the label described an open
 * hurt to someone who had none.
 */
class TheBodyMendsTest {

    @Test
    void aBodyHalfLookedAfterHoldsItsGroundAndNoMore() {
        // Two of the five put right is not looking after yourself: no mending at all, however long it goes on.
        assertEquals(0, ChroniclePhysiologyService.illnessMended(24, 0));
        assertEquals(0, ChroniclePhysiologyService.illnessMended(24, 2));
        // Three begins it.
        assertTrue(ChroniclePhysiologyService.illnessMended(10, 3) > 0);
    }

    @Test
    void mendingQuickensWithEveryThingPutRight() {
        int three = ChroniclePhysiologyService.illnessMended(10, 3);
        int four = ChroniclePhysiologyService.illnessMended(10, 4);
        int five = ChroniclePhysiologyService.illnessMended(10, 5);
        assertTrue(three < four && four < five, three + " < " + four + " < " + five);
        // At five it throws off .6 an hour — the rate the worst single pressure in advanceTo RAISES it, so a
        // Chronicle doing everything right exactly outruns the sickest camp they could have lived in.
        assertEquals(6, five, "ten hours of being properly kept must mend six points");
    }

    @Test
    void aChronicleCanClimbOutOfACriticalIllness() {
        // Chronicle 49's state, with every cause answered: 99 illness, and nothing still driving it.
        int illness = 99;
        int hours = 0;
        while (illness > 80 && hours < 200) { illness -= ChroniclePhysiologyService.illnessMended(1, 5); hours++; }
        assertTrue(illness <= 80, "illness must be able to fall out of Critical at all: " + illness);
        assertTrue(hours >= 12 && hours <= 48,
            "and it must take a day or so of being properly kept, not a nap and not a season: " + hours + "h");
    }

    @Test
    void everyThingPutRightIsCounted() {
        // Warm, dry, clean, fed and watered, rested.
        assertEquals(5, ChroniclePhysiologyService.howWellKept(37.0, 10, 80, 10, 2, 1));
        // Soaked and filthy, and two of the five are gone.
        assertEquals(3, ChroniclePhysiologyService.howWellKept(37.0, 90, 5, 10, 2, 1));
        // Cold, soaked, filthy, starving, sleepless: nothing is being done about any of it.
        assertEquals(0, ChroniclePhysiologyService.howWellKept(34.0, 90, 5, 300, 60, 40));
    }

    /** A body with nothing wrong with it, to vary one system at a time from. */
    private static ChroniclePhysiologyService.Vitals well() {
        return new ChroniclePhysiologyService.Vitals(0, 0, 0, 37.0, 0, 0, 100, 0, 0, 100, 0, 0, 0, 0);
    }

    private static ChroniclePhysiologyService.Vitals with(java.util.function.UnaryOperator<ChroniclePhysiologyService.Vitals> change) {
        return change.apply(well());
    }

    /**
     * #957 — HEALTH IS THE WORST SYSTEM, NEVER AN AVERAGE.
     *
     * <p>The old label read three numbers — injury, illness, blood loss — while the death check twenty lines
     * away read temperature, food and water as well. So a Chronicle at 29 °C core, a degree from dying of
     * cold, was "Healthy". Each case below is one of the ones #957 names.
     */
    @Test
    void healthIsTheWorstSystemAndNoSystemCanHideBehindTheRest() {
        assertEquals("Healthy", ChroniclePhysiologyService.health(well()));

        // THE REPORTED DEFECT: freezing to death while reading Healthy.
        ChroniclePhysiologyService.Vitals freezing = new ChroniclePhysiologyService.Vitals(
            0, 0, 0, 29.0, 0, 0, 100, 0, 0, 100, 0, 0, 0, 0);
        assertEquals("Critical", ChroniclePhysiologyService.health(freezing),
            "a body four degrees from hypothermic death is not Healthy");

        // Severe dehydration: dead at 73 hours.
        assertEquals("Critical", ChroniclePhysiologyService.health(new ChroniclePhysiologyService.Vitals(
            0, 0, 0, 37.0, 0, 70, 100, 0, 0, 100, 0, 0, 0, 0)));
        // STARVATION, MEASURED AS DAMAGE RATHER THAN READ OFF THE CLOCK (#959/#960). This case used to be
        // "700 hours since eating", and that is no longer a statement about a body at all: a Chronicle who
        // last ate 700 hours ago may have been living on a full stomach and good reserves since, which is
        // precisely the confusion #959 took apart. Hours are history; starvation_severity is the fact, and a
        // body at 95 of it is dying of starvation whatever the clock says.
        assertEquals("Critical", ChroniclePhysiologyService.health(new ChroniclePhysiologyService.Vitals(
            0, 0, 0, 37.0, 700, 0, 100, 0, 0, 100, 0, 0, 0, 0, 0, 0, 20, 95)));
        // And the converse, which is the whole point: the clock alone says nothing.
        assertEquals("Healthy", ChroniclePhysiologyService.health(new ChroniclePhysiologyService.Vitals(
            0, 0, 0, 37.0, 700, 0, 100, 0, 0, 100, 0, 0, 0, 0, 900, 80, 75, 0)),
            "700 hours since eating, with a full stomach and sound reserves, is not a dying body");
        // And the three roads that were already Critical stay Critical.
        assertEquals("Critical", ChroniclePhysiologyService.health(new ChroniclePhysiologyService.Vitals(
            0, 95, 0, 37.0, 0, 0, 100, 0, 0, 100, 0, 0, 0, 0)));
        assertEquals("Critical", ChroniclePhysiologyService.health(new ChroniclePhysiologyService.Vitals(
            95, 0, 0, 37.0, 0, 0, 100, 0, 0, 100, 0, 0, 0, 0)));
        assertEquals("Critical", ChroniclePhysiologyService.health(new ChroniclePhysiologyService.Vitals(
            0, 0, 3000, 37.0, 0, 0, 100, 0, 0, 100, 0, 0, 0, 0)));

        // NO AVERAGING. One lethal system among nine healthy ones must still read Critical — an average
        // would put this body at a comfortable nine out of a hundred.
        ChroniclePhysiologyService.Vitals oneLethalRestFine = new ChroniclePhysiologyService.Vitals(
            0, 97, 0, 37.0, 0, 0, 100, 0, 0, 100, 0, 0, 0, 0);
        assertEquals("Critical", ChroniclePhysiologyService.health(oneLethalRestFine));
    }

    /**
     * #957 — "A minor deviation need not imply medical crisis." The systems that cannot kill are capped
     * below Critical by construction, so no amount of being tired, wet, filthy and bursting adds up to a
     * body that is about to die.
     */
    @Test
    void theThingsThatCannotKillYouCannotMakeYouCritical() {
        ChroniclePhysiologyService.Vitals wretched = new ChroniclePhysiologyService.Vitals(
            0, 0, 0, 37.0, 0, 0, 0, 72, 100, 0, 100, 100, 100, 100);
        String health = ChroniclePhysiologyService.health(wretched);
        assertNotEquals("Critical", health,
            "exhausted, sleepless, soaked, filthy and desperate is miserable, not dying: " + health);
        assertNotEquals("Healthy", health, "nor is it nothing: " + health);
        // But it IS all reported, which is the other half (#958).
        java.util.List<String> said = ChroniclePhysiologyService.conditions(wretched);
        assertTrue(said.size() >= 4, "every one of those should be named: " + said);
    }

    /**
     * #958 — CONDITION IS A LIST, AND CLEARING ONE LEAVES THE OTHERS.
     *
     * <p>Condition was a single string whose precedence suppressed everything behind it, so a Chronicle who
     * was injured, bleeding and sick was told one of the three and left to guess. The ticket's own example
     * is the first assertion here.
     */
    @Test
    void everyComplaintIsNamedAndTheUrgentOneComesFirst() {
        ChroniclePhysiologyService.Vitals hurtSickBleedingSpent = new ChroniclePhysiologyService.Vitals(
            85, 60, 1800, 37.0, 0, 0, 5, 0, 0, 100, 0, 0, 0, 0);
        java.util.List<String> said = ChroniclePhysiologyService.conditions(hurtSickBleedingSpent);

        assertTrue(said.stream().anyMatch(s -> s.contains("injured")), "the injury: " + said);
        assertTrue(said.stream().anyMatch(s -> s.contains("Bleeding")), "the bleeding: " + said);
        assertTrue(said.stream().anyMatch(s -> s.contains("ick") || s.contains("Unwell")), "the sickness: " + said);
        assertTrue(said.stream().anyMatch(s -> s.contains("xhaust") || s.contains("Collaps")), "the exhaustion: " + said);
        assertEquals("Critical", ChroniclePhysiologyService.health(hurtSickBleedingSpent));
        // Worst first, so a reader who sees only the head of the list sees the thing that matters.
        assertTrue(said.get(0).contains("injured"), "the gravest should lead: " + said);

        // CLEARING ONE LEAVES THE OTHERS. Heal the wound; the sickness and the bleeding stand.
        ChroniclePhysiologyService.Vitals healed = new ChroniclePhysiologyService.Vitals(
            0, 60, 1800, 37.0, 0, 0, 5, 0, 0, 100, 0, 0, 0, 0);
        java.util.List<String> after = ChroniclePhysiologyService.conditions(healed);
        assertTrue(after.stream().noneMatch(s -> s.contains("injured")), "the wound is closed: " + after);
        assertTrue(after.stream().anyMatch(s -> s.contains("Bleeding")), "the bleeding is not: " + after);
        assertTrue(after.stream().anyMatch(s -> s.contains("ick")), "nor the sickness: " + after);
    }

    /** A body with nothing wrong with it says so, rather than listing nothing. */
    @Test
    void aWellBodyHasNoComplaints() {
        assertTrue(ChroniclePhysiologyService.conditions(well()).isEmpty());
        assertEquals("Comfortable", ChroniclePhysiologyService.conditionSummary(well()));
    }

    /** The joined line and the list must say the same thing, since both are shown. */
    @Test
    void theLineAndTheListAgree() {
        ChroniclePhysiologyService.Vitals v = new ChroniclePhysiologyService.Vitals(
            70, 50, 0, 33.0, 0, 0, 20, 0, 0, 100, 0, 0, 0, 0);
        assertEquals(String.join(", ", ChroniclePhysiologyService.conditions(v)),
                     ChroniclePhysiologyService.conditionSummary(v));
        // And the HUD built from that body carries the same entries, not a re-split string.
        assertEquals(ChroniclePhysiologyService.conditions(v),
                     ChroniclePhysiologyService.snapshot(v).conditions());
    }

    /**
     * #957 — THE CONDITION LIST AND THE SELF-CHECK MUST NOT CONTRADICT EACH OTHER.
     *
     * <p>The self-check says "you are losing blood, and it is still coming" from 400 ml. A 900 ml bleed is
     * only a severity of twenty, so the Condition list stayed silent about a wound the prose was reporting —
     * the player was told two different things about their own body in one screen. A bleed is a FACT at a low
     * bar and a DANGER at a high one, and the list now names it from the lower.
     */
    @Test
    void aBleedTheProseReportsIsNamedInTheConditionsToo() {
        ChroniclePhysiologyService.Vitals bleeding = new ChroniclePhysiologyService.Vitals(
            0, 0, 900, 37.0, 0, 0, 100, 0, 0, 100, 0, 0, 0, 0);
        assertTrue(ChroniclePhysiologyService.conditions(bleeding).stream().anyMatch(s -> s.contains("bleeding") || s.contains("Bleeding")),
            "a 900 ml bleed must be named: " + ChroniclePhysiologyService.conditions(bleeding));

        // The same for a wound and a sickness that the self-check speaks of but that are not yet dangerous.
        ChroniclePhysiologyService.Vitals mildlyHurt = new ChroniclePhysiologyService.Vitals(
            20, 0, 0, 37.0, 0, 0, 100, 0, 0, 100, 0, 0, 0, 0);
        assertFalse(ChroniclePhysiologyService.conditions(mildlyHurt).isEmpty(), "a real wound is named");
        ChroniclePhysiologyService.Vitals mildlySick = new ChroniclePhysiologyService.Vitals(
            0, 20, 0, 37.0, 0, 0, 100, 0, 0, 100, 0, 0, 0, 0);
        assertFalse(ChroniclePhysiologyService.conditions(mildlySick).isEmpty(), "a real sickness is named");

        // And naming it is not calling it a crisis: a graze is not Critical.
        assertNotEquals("Critical", ChroniclePhysiologyService.health(bleeding));
        // While a body with genuinely nothing wrong still says nothing.
        assertTrue(ChroniclePhysiologyService.conditions(well()).isEmpty());
    }
}
