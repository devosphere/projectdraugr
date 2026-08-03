package com.devosphere.draugr.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Architect against a stub model — parses a JSON draft, and is inert/empty on off, NONE, or garbage. */
class RuntimeArchitectTest {

    private static final String JSON = """
        {"processKey":"twist_cord","category":"PROCESS","keywords":"twist,ply","subjects":"fiber,cord",
         "toolClass":null,"inputs":[{"itemKey":"plant_fiber","quantity":2}],"outputItemKey":"twisted_cord",
         "outputQty":1,"narration":"You twist the fibers into a cord.",
         "newItems":[{"itemKey":"twisted_cord","displayName":"Twisted cord","category":"MATERIAL","unitMassGrams":180,"unitVolumeMl":120}]}""";

    private AiProperties enabled() { AiProperties p = new AiProperties(); p.setEnabled(true); p.setApiKey("test-key-not-a-real-secret"); return p; }

    @Test void inertWhenDisabled() {
        assertTrue(new RuntimeArchitect((m, s, u) -> Optional.of(JSON), new AiProperties()).draft("twist fibers", List.of()).isEmpty());
    }

    @Test void parsesAJsonDraftEvenInsideCodeFences() {
        var a = new RuntimeArchitect((m, s, u) -> Optional.of("```json\n" + JSON + "\n```"), enabled());
        var d = a.draft("twist the fibers into a cord", List.of("plant_fiber"));
        assertTrue(d.isPresent());
        assertEquals("twist_cord", d.get().processKey());
        assertEquals(1, d.get().inputs().size());
        assertEquals("plant_fiber", d.get().inputs().get(0).itemKey());
        assertEquals("twisted_cord", d.get().outputItemKey());
        assertEquals(180, d.get().newItems().get(0).unitMassGrams());
    }

    @Test void noneOrMalformedYieldsEmpty() {
        assertTrue(new RuntimeArchitect((m, s, u) -> Optional.of("NONE"), enabled()).draft("x", List.of()).isEmpty());
        assertTrue(new RuntimeArchitect((m, s, u) -> Optional.of("sorry, no idea"), enabled()).draft("x", List.of()).isEmpty());
        assertTrue(new RuntimeArchitect((m, s, u) -> Optional.empty(), enabled()).draft("x", List.of()).isEmpty());
    }
}
