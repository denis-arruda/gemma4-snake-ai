package dev.denisarruda.gemmasnakeai.game.entity;

import java.util.List;

public record RenderState(
        int tick,
        List<SnakeRender> snakes,
        List<Position> foods) {

    public record SnakeRender(String agentId, List<Position> cells, Direction direction, boolean alive) {}
    public record Position(int x, int y) {}
}
