package com.devosphere.draugr.people;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Which act of conduct toward a people a Chronicle's words name (#114). No database. */
class ConductRecognitionTest {

    @Test
    void offencesAndAmendsAreEachNamedInPlainWords() {
        assertEquals(ConductService.Act.THEFT, ConductService.recognise("steal from their store"));
        assertEquals(ConductService.Act.THREAT, ConductService.recognise("threaten them with my spear"));
        assertEquals(ConductService.Act.HARM, ConductService.recognise("attack the reedkin"));
        assertEquals(ConductService.Act.RESTRAINT, ConductService.recognise("tie up the elder"));
        assertEquals(ConductService.Act.RESTRAINT, ConductService.recognise("tame the reedkin"),
            "a person offered the livestock word is an attempt at seizure, not taming");
        assertEquals(ConductService.Act.APOLOGY, ConductService.recognise("apologise to them"));
        assertEquals(ConductService.Act.RESTITUTION, ConductService.recognise("make restitution"));
        assertEquals(ConductService.Act.SHARED_LABOUR, ConductService.recognise("help them fish at the weirs"));
    }

    @Test
    void ordinaryWorkIsNoOffence() {
        for (String text : new String[]{"fish the channel", "gather reeds", "look around", "call out to them"})
            assertNull(ConductService.recognise(text), text);
    }
}
