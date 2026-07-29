package com.devosphere.draugr.narration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NarrationPolicyTest {
    private final NarrationPolicy policy = new NarrationPolicy();
    @Test void permitsExternalPerception() { assertDoesNotThrow(() -> policy.validate("Rainwater darkens the leaf litter beneath the trees.")); }
    @Test void permitsFactBackedSensationWithoutDiagnosis() { assertDoesNotThrow(() -> policy.validate("Cold rain runs beneath your collar as the trail narrows.")); }
    @Test void rejectsHudHints() { assertThrows(IllegalArgumentException.class, () -> policy.validate("You should find water before you collapse.")); }
    @Test void rejectsDirectHudDiagnosis() { assertThrows(IllegalArgumentException.class, () -> policy.validate("You are dehydrated.")); }
}
