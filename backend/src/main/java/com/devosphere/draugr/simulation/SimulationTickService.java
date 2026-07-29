package com.devosphere.draugr.simulation;

import com.devosphere.draugr.world.WorldEventRepository;
import com.devosphere.draugr.world.domain.WorldEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Service
public class SimulationTickService {
    private final SimulationClockRepository clocks;
    private final WorldEventRepository events;
    private final SimulationAgent simulation;
    private final Clock clock = Clock.systemUTC();
    public SimulationTickService(SimulationClockRepository clocks, WorldEventRepository events, SimulationAgent simulation) { this.clocks = clocks; this.events = events; this.simulation = simulation; }
    @Transactional
    public SimulationTick advance() {
        SimulationClock simulationClock = clocks.findById((short) 1).orElseThrow();
        Instant now = clock.instant();
        SimulationAgent.SimulationAssessment assessment = simulation.assess(simulationClock.getTick() + 1, now);
        simulationClock.advanceTo(now);
        events.save(new WorldEvent(now, assessment.eventType(), null, null, Map.of()));
        return new SimulationTick(simulationClock.getTick(), simulationClock.getSimulatedAt());
    }
    @Transactional(readOnly = true)
    public SimulationTick current() {
        SimulationClock simulationClock = clocks.findById((short) 1).orElseThrow();
        return new SimulationTick(simulationClock.getTick(), simulationClock.getSimulatedAt());
    }
    public record SimulationTick(long tick, Instant simulatedAt) { }
}
