package com.devosphere.draugr.chronicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void theLabelTellsASicknessFromAWound() {
        // Chronicle 41, word for word: illness 64, injury 0, no blood lost.
        assertEquals("Sick", ChroniclePhysiologyService.health(0, 64, 0));
        assertEquals("Injured", ChroniclePhysiologyService.health(50, 0, 0));
        assertEquals("Ailing", ChroniclePhysiologyService.health(50, 64, 0));
        assertEquals("Healthy", ChroniclePhysiologyService.health(0, 0, 0));
        assertEquals("Healthy", ChroniclePhysiologyService.health(30, 30, 100));

        // Critical is still Critical, by any of its three roads — the distinction is below that line, where
        // there is still something to be done about it.
        assertEquals("Critical", ChroniclePhysiologyService.health(0, 95, 0));
        assertEquals("Critical", ChroniclePhysiologyService.health(95, 0, 0));
        assertEquals("Critical", ChroniclePhysiologyService.health(0, 0, 3000));
    }
}
