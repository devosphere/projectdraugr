package com.devosphere.draugr.simulation;

import org.springframework.stereotype.Service;
import java.time.Instant;

/** MVP implementation; a future AI-backed agent must keep this non-persistent contract. */
@Service
public class DeterministicSimulationAgent implements SimulationAgent {
    @Override public SimulationAssessment assess(long nextTick, Instant simulatedAt) {
        return new SimulationAssessment("SIMULATION_TICK_COMPLETED");
    }
}
