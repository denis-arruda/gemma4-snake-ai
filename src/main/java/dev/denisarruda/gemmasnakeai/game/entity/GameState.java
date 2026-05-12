package dev.denisarruda.gemmasnakeai.game.entity;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

@ApplicationScoped
public class GameState {

    @ConfigProperty(name = "game.grid.size", defaultValue = "30")
    int gridSize;

    @ConfigProperty(name = "game.food.max", defaultValue = "5")
    int maxFood;

    @ConfigProperty(name = "game.snake.initial-length", defaultValue = "3")
    int initialSnakeLength;

    int tick;
    Snake snake;
    List<RenderState.Position> foods;

    @PostConstruct
    public synchronized void restart() {
        tick  = 0;
        snake = randomSnake("agent-1");
        foods = new ArrayList<>(randomPositions(maxFood, snake.cells()));
    }

    public synchronized void applyDirection(Direction direction) {
        if (direction == null || !snake.alive()) return;
        if (isReversal(direction, snake.direction())) return;
        snake.direction = direction;
    }

    boolean isReversal(Direction requested, Direction current) {
        return switch (requested) {
            case UP    -> current == Direction.DOWN;
            case DOWN  -> current == Direction.UP;
            case LEFT  -> current == Direction.RIGHT;
            case RIGHT -> current == Direction.LEFT;
        };
    }

    public synchronized void advance() {
        if (!snake.alive()) return;
        tick++;
        var newHead = nextHead(snake);
        if (isOutOfBounds(newHead) || snake.cells().contains(newHead)) {
            snake.kill();
        } else if (foods.remove(newHead)) {
            snake.grow(newHead);
            spawnFood();
        } else {
            snake.move(newHead);
        }
    }

    void spawnFood() {
        var occupied = Stream.concat(snake.cells().stream(), foods.stream()).toList();
        foods.addAll(randomPositions(1, occupied));
    }

    RenderState.Position nextHead(Snake s) {
        var head = s.head();
        return switch (s.direction()) {
            case RIGHT -> new RenderState.Position(head.x() + 1, head.y());
            case LEFT  -> new RenderState.Position(head.x() - 1, head.y());
            case DOWN  -> new RenderState.Position(head.x(),     head.y() + 1);
            case UP    -> new RenderState.Position(head.x(),     head.y() - 1);
        };
    }

    boolean isOutOfBounds(RenderState.Position p) {
        return p.x() < 0 || p.x() >= gridSize || p.y() < 0 || p.y() >= gridSize;
    }

    public synchronized RenderState toRenderState() {
        return new RenderState(tick, List.of(snake.toRender()), List.copyOf(foods));
    }

    public synchronized boolean isAlive() { return snake.alive(); }

    public int gridSize() { return gridSize; }

    Snake randomSnake(String agentId) {
        var rng    = ThreadLocalRandom.current();
        var dirs   = Direction.values();
        var dir    = dirs[rng.nextInt(dirs.length)];
        var margin = initialSnakeLength - 1;
        var head   = switch (dir) {
            case RIGHT -> new RenderState.Position(rng.nextInt(margin, gridSize),          rng.nextInt(gridSize));
            case LEFT  -> new RenderState.Position(rng.nextInt(0, gridSize - margin),      rng.nextInt(gridSize));
            case DOWN  -> new RenderState.Position(rng.nextInt(gridSize),                  rng.nextInt(margin, gridSize));
            case UP    -> new RenderState.Position(rng.nextInt(gridSize),                  rng.nextInt(0, gridSize - margin));
        };
        var cells = Stream.iterate(head, p -> behindOf(p, dir)).limit(initialSnakeLength).toList();
        return new Snake(agentId, cells, dir);
    }

    RenderState.Position behindOf(RenderState.Position p, Direction dir) {
        return switch (dir) {
            case RIGHT -> new RenderState.Position(p.x() - 1, p.y());
            case LEFT  -> new RenderState.Position(p.x() + 1, p.y());
            case DOWN  -> new RenderState.Position(p.x(),     p.y() - 1);
            case UP    -> new RenderState.Position(p.x(),     p.y() + 1);
        };
    }

    List<RenderState.Position> randomPositions(int count, List<RenderState.Position> excluded) {
        var rng = ThreadLocalRandom.current();
        return Stream.generate(() -> new RenderState.Position(rng.nextInt(gridSize), rng.nextInt(gridSize)))
                .filter(p -> !excluded.contains(p))
                .distinct()
                .limit(count)
                .toList();
    }
}
