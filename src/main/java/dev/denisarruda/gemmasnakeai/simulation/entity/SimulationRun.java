package dev.denisarruda.gemmasnakeai.simulation.entity;

import dev.denisarruda.gemmasnakeai.game.entity.GameState;

import java.util.ArrayList;
import java.util.List;

public record SimulationRun(
    String id,
    SimulationStatus status,
    List<GameState> snapshots,
    int finalScore
) {

    public static SimulationRun started(String id, GameState initial) {
        return new SimulationRun(id, SimulationStatus.RUNNING, List.of(initial), 0);
    }

    public SimulationRun withSnapshot(GameState state) {
        var updated = new ArrayList<>(snapshots);
        updated.add(state);
        return new SimulationRun(id, status, List.copyOf(updated), state.score());
    }

    public SimulationRun completed(int score) {
        return new SimulationRun(id, SimulationStatus.COMPLETED, snapshots, score);
    }
}
