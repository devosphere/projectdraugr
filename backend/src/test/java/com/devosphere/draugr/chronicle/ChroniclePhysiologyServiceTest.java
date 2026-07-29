package com.devosphere.draugr.chronicle;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ChroniclePhysiologyServiceTest {
    @Test void bodyHudUsesQualitativePhysiologyThresholds() {
        var body = ChroniclePhysiologyService.snapshot("Healthy", "Unsteady", 72, 24, 70, 37, 25, 45, 45, 55);
        assertEquals("Hungry", body.hunger());
        assertEquals("Thirsty", body.thirst());
        assertEquals("Rested", body.energy());
        assertEquals("Comfortable", body.temperature());
        assertEquals("Damp", body.wetness());
        assertEquals("Need to Urinate", body.bladder());
        assertEquals("Need Relief", body.bowel());
        assertEquals("Normal", body.hygiene());
    }
}
