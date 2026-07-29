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
    private final Clock clock = Clock.systemUTC();
    public SimulationTickService(SimulationClockRepository clocks, WorldEventRepository events) { this.clocks = clocks; this.events = events; }
    @Transactional
    public SimulationTick advance() {
        SimulationClock simulationClock = clocks.findById((short) 1).orElseThrow();
        Instant now = clock.instant();
        simulationClock.advanceTo(now);
        events.save(new WorldEvent(now, "SIMULATION_TICK_COMPLETED", null, null, Map.of()));
        return new SimulationTick(simulationClock.getTick(), simulationClock.getSimulatedAt());
    }
    @Transactional(readOnly = true)
    public SimulationTick current() {
        SimulationClock simulationClock = clocks.findById((short) 1).orElseThrow();
        return new SimulationTick(simulationClock.getTick(), simulationClock.getSimulatedAt());
    }
    public record SimulationTick(long tick, Instant simulatedAt) { }
}
