package com.devosphere.draugr.chronicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where an animal's attack reaches, read from the catalogue rather than invented (#37/#99).
 *
 * <p>#37 complained that {@code how is my leg} and {@code how are my feet} reached nothing, and the diagnosis
 * standing on the scenario matrix was that nothing in this world recorded WHERE a wound was — so answering a
 * leg question out of whole-body condition "would be inventing exactly that".
 *
 * <p>This is the rule that makes it not an invention. A species' {@code size_tier} already sits in the
 * catalogue for every one of the 215 of them, and {@code base_resistance} beside it is already authoritative
 * for how badly a wound hurts — the answer about WHERE was in the same row all along. A thing that lives at
 * ankle height strikes the ankle; a thing that meets you at the waist takes the arm you put up; a thing that
 * stands taller than you reaches your chest. A beast you had your hands on takes the hand, whatever its size,
 * because that is where your hands were.
 *
 * <p>Pure, so it needs no database and runs everywhere — which matters, because the integration tests that
 * exercise the reading skip wherever Testcontainers cannot reach Docker.
 */
class WhereAWoundLandsTest {

    @Test
    void aSmallThingStrikesLowAndALargeThingReachesTheBody() {
        assertEquals("ANKLE", ChroniclePhysiologyService.whereAnAnimalReaches("TINY", false));
        assertEquals("ANKLE", ChroniclePhysiologyService.whereAnAnimalReaches("SMALL", false));
        assertEquals("ARM", ChroniclePhysiologyService.whereAnAnimalReaches("MEDIUM", false));
        assertEquals("TORSO", ChroniclePhysiologyService.whereAnAnimalReaches("LARGE", false));
        assertEquals("TORSO", ChroniclePhysiologyService.whereAnAnimalReaches("HUGE", false));
    }

    @Test
    void theTierIsReadWhateverCaseTheCatalogueSpellsItIn() {
        assertEquals("ANKLE", ChroniclePhysiologyService.whereAnAnimalReaches("tiny", false));
        assertEquals("TORSO", ChroniclePhysiologyService.whereAnAnimalReaches("Large", false));
    }

    /** Handling beats size: an adder and an aurochs both take the hand that was on them. */
    @Test
    void aBeastYouHadYourHandsOnTakesTheHand() {
        assertEquals("HAND", ChroniclePhysiologyService.whereAnAnimalReaches("TINY", true));
        assertEquals("HAND", ChroniclePhysiologyService.whereAnAnimalReaches("HUGE", true));
        assertEquals("HAND", ChroniclePhysiologyService.whereAnAnimalReaches(null, true));
    }

    /**
     * AND AN UNCATALOGUED SPECIES RECORDS NO PART AT ALL, which is the half worth defending.
     *
     * <p>A null site means the body is hurt without a place, and the reading then says you could not tell where
     * it sits. That is true. Choosing a limb so the answer sounds definite would be the invention the matrix
     * warned against, and this test is what stops a later tidy-up from supplying a "sensible default".
     */
    @Test
    void anUnknownSizeNamesNoPartRatherThanGuessingOne() {
        assertNull(ChroniclePhysiologyService.whereAnAnimalReaches(null, false));
        assertNull(ChroniclePhysiologyService.whereAnAnimalReaches("", false));
        assertNull(ChroniclePhysiologyService.whereAnAnimalReaches("GIGANTIC", false));
    }

    /** Every site the rule can produce has to be one the reading knows how to say. */
    @Test
    void everySiteThisRuleProducesIsAPartTheWorldKnows() {
        for (String tier : new String[]{"TINY", "SMALL", "MEDIUM", "LARGE", "HUGE"}) {
            String part = ChroniclePhysiologyService.whereAnAnimalReaches(tier, false);
            assertTrue(ChroniclePhysiologyService.BODY_PARTS.contains(part),
                () -> tier + " reached " + part + ", which is not one of the body's parts");
        }
        assertTrue(ChroniclePhysiologyService.BODY_PARTS.contains(
            ChroniclePhysiologyService.whereAnAnimalReaches("MEDIUM", true)));
    }

    /**
     * NO SIDES, DELIBERATELY. Nothing in this simulation distinguishes a Chronicle's left from their right, so
     * a recorded LEFT_LEG would claim knowledge the world has not got — and the prose would then have to keep
     * that promise every time it answered.
     */
    @Test
    void theBodyHasPartsAndNotSides() {
        for (String part : ChroniclePhysiologyService.BODY_PARTS) {
            assertTrue(!part.contains("LEFT") && !part.contains("RIGHT"),
                () -> part + " names a side, and this world does not know which side anything is on");
        }
    }
}
