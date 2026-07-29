package com.devosphere.draugr.simulation;

import java.time.Instant;

/** Proposes world evolution; persistence is intentionally delegated to the state layer. */
public interface SimulationAgent {
    SimulationAssessment assess(long nextTick, Instant simulatedAt);
    record SimulationAssessment(String eventType) { }
}
