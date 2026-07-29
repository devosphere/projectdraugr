package com.devosphere.draugr.simulation;

import com.devosphere.draugr.world.WorldEventRepository;
import com.devosphere.draugr.world.domain.WorldEvent;
import com.devosphere.draugr.chronicle.ChroniclePhysiologyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.Map;

@Service
public class SimulationTickService {
    private final SimulationClockRepository clocks;
    private final WorldEventRepository events;
    private final SimulationAgent simulation;
    private final ChroniclePhysiologyService physiology;
    private final Clock clock = Clock.systemUTC();
    public SimulationTickService(SimulationClockRepository clocks, WorldEventRepository events, SimulationAgent simulation, ChroniclePhysiologyService physiology) { this.clocks = clocks; this.events = events; this.simulation = simulation; this.physiology = physiology; }
    @Transactional
    public SimulationTick advance() {
        SimulationClock simulationClock = clocks.findById((short) 1).orElseThrow();
        Instant now = clock.instant();
        SimulationAgent.SimulationAssessment assessment = simulation.assess(simulationClock.getTick() + 1, now);
        physiology.advanceTo(now);
        simulationClock.advanceTo(now);
        events.save(new WorldEvent(now, assessment.eventType(), null, null, Map.of()));
        return new SimulationTick(simulationClock.getTick(), simulationClock.getSimulatedAt());
    }
    @Transactional
    public SimulationTick advanceBy(Duration duration) {
        SimulationClock simulationClock = clocks.findById((short) 1).orElseThrow();
        simulationClock.advanceBy(duration);
        Instant now = simulationClock.getSimulatedAt();
        SimulationAgent.SimulationAssessment assessment = simulation.assess(simulationClock.getTick(), now);
        physiology.advanceTo(now);
        events.save(new WorldEvent(now, assessment.eventType(), null, null, Map.of("durationMinutes", duration.toMinutes())));
        return new SimulationTick(simulationClock.getTick(), now);
    }
    @Transactional(readOnly = true)
    public SimulationTick current() {
        SimulationClock simulationClock = clocks.findById((short) 1).orElseThrow();
        return new SimulationTick(simulationClock.getTick(), simulationClock.getSimulatedAt());
    }
    public record SimulationTick(long tick, Instant simulatedAt) { }
}
