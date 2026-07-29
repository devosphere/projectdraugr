package com.devosphere.draugr.simulation;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "simulation_clock")
public class SimulationClock {
    @Id private short id = 1;
    @Column(name = "simulated_at", nullable = false) private Instant simulatedAt;
    @Column(nullable = false) private long tick;
    protected SimulationClock() { }
    public long getTick() { return tick; }
    public Instant getSimulatedAt() { return simulatedAt; }
    public void advanceTo(Instant next) { if (next.isBefore(simulatedAt)) throw new IllegalArgumentException("Simulation time cannot move backward"); simulatedAt = next; tick++; }
}
