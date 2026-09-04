package com.devosphere.draugr.simulation;

import com.devosphere.draugr.world.WorldEventRepository;
import com.devosphere.draugr.world.domain.WorldEvent;
import com.devosphere.draugr.chronicle.ChroniclePhysiologyService;
import com.devosphere.draugr.ecology.WildlifeSimulationService;
import com.devosphere.draugr.survival.FoodPreservationService;
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
    private final WildlifeSimulationService wildlife;
    private final WeatherSimulationService weather;
    private final com.devosphere.draugr.construction.FireService fires;
    private final FoodPreservationService food;
    private final com.devosphere.draugr.construction.ConstructionService construction;
    private final com.devosphere.draugr.item.PhysicalItemService items;
    private final Clock clock = Clock.systemUTC();
    public SimulationTickService(SimulationClockRepository clocks, WorldEventRepository events, SimulationAgent simulation, ChroniclePhysiologyService physiology, WildlifeSimulationService wildlife, WeatherSimulationService weather, com.devosphere.draugr.construction.FireService fires, FoodPreservationService food, com.devosphere.draugr.construction.ConstructionService construction, com.devosphere.draugr.item.PhysicalItemService items) { this.clocks = clocks; this.events = events; this.simulation = simulation; this.physiology = physiology; this.wildlife = wildlife; this.weather=weather; this.fires=fires; this.food=food; this.construction=construction; this.items=items; }
    @Transactional
    public SimulationTick advance() {
        SimulationClock simulationClock = clocks.findById((short) 1).orElseThrow();
        // Actions advance simulated time forward by their duration, so simulated
        // time can already be ahead of the wall clock. A real-time heartbeat tick
        // must never rewind the world; clamp to the current simulated instant.
        Instant now = clock.instant();
        if (now.isBefore(simulationClock.getSimulatedAt())) now = simulationClock.getSimulatedAt();
        SimulationAgent.SimulationAssessment assessment = simulation.assess(simulationClock.getTick() + 1, now);
        weather.advanceTo(now); fires.advanceTo(now); food.advanceTo(now); construction.advanceTo(now); physiology.advanceTo(now);
        wildlife.advanceTo(now); items.weatherExposedMetal(now); items.rotExposedOrganics(now); items.slakeExposedGreenware(now); items.advanceCrops(now); items.restPennedDraftBeasts(now); items.advanceDraftHunger(now);
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
        weather.advanceTo(now); fires.advanceTo(now); food.advanceTo(now); construction.advanceTo(now); physiology.advanceTo(now);
        wildlife.advanceTo(now); items.weatherExposedMetal(now); items.rotExposedOrganics(now); items.slakeExposedGreenware(now); items.advanceCrops(now); items.restPennedDraftBeasts(now); items.advanceDraftHunger(now);
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
