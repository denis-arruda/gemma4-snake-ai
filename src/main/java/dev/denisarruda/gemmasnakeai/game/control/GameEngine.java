package dev.denisarruda.gemmasnakeai.game.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.denisarruda.gemmasnakeai.agent.boundary.SnakeAgent;
import dev.denisarruda.gemmasnakeai.game.entity.Direction;
import dev.denisarruda.gemmasnakeai.game.entity.GameState;
import dev.denisarruda.gemmasnakeai.game.entity.RenderState;
import dev.denisarruda.gemmasnakeai.simulation.boundary.GameBroadcaster;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class GameEngine {

    static final System.Logger LOGGER = System.getLogger(GameEngine.class.getName());

    static final ExecutorService AGENT_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    final GameState gameState;
    final SnakeAgent snakeAgent;
    final GameBroadcaster broadcaster;
    final ObjectMapper objectMapper;

    final AtomicReference<Direction> latestDirection = new AtomicReference<>(null);
    final AtomicBoolean agentBusy = new AtomicBoolean(false);

    @Inject
    GameEngine(GameState gameState, SnakeAgent snakeAgent,
               GameBroadcaster broadcaster, ObjectMapper objectMapper) {
        this.gameState    = gameState;
        this.snakeAgent   = snakeAgent;
        this.broadcaster  = broadcaster;
        this.objectMapper = objectMapper;
    }

    @Scheduled(every = "${game.tick.interval:200ms}")
    @RunOnVirtualThread
    public void tick() {
        try {
            gameState.applyDirection(latestDirection.get());
            gameState.advance();
            var state = gameState.toRenderState();
            broadcaster.broadcast(objectMapper.writeValueAsString(state));

            if (gameState.isAlive() && agentBusy.compareAndSet(false, true)) {
                AGENT_EXECUTOR.execute(() -> decideAsync(state));
            }
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.ERROR, "Tick failed: {0}", e.getMessage());
        }
    }

    public void restart() {
        latestDirection.set(null);
        agentBusy.set(false);
        gameState.restart();
    }

    void decideAsync(RenderState state) {
        try {
            var raw = snakeAgent.decide(buildPrompt(state));
            if (raw != null) latestDirection.set(Direction.parse(raw));
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING, "Agent call failed: {0}", e.getMessage());
        } finally {
            agentBusy.set(false);
        }
    }

    String buildPrompt(RenderState state) {
        var snake = state.snakes().get(0);
        var head  = snake.cells().get(0);
        int g     = gameState.gridSize();

        var moves = new StringBuilder();
        for (var dir : Direction.values()) {
            var next     = step(head, dir);
            var danger   = isOutOfBounds(next, g)        ? "WALL — instant death"
                         : snake.cells().contains(next)   ? "BODY — instant death"
                         : "safe";
            var wallDist = wallDistance(head, dir, g);
            moves.append("  %-5s → (%2d,%2d)  %-22s  (%d step%s to wall)%n"
                    .formatted(dir, next.x(), next.y(), danger,
                               wallDist, wallDist == 1 ? "" : "s"));
        }

        var foodStr = state.foods().isEmpty() ? "none" : state.foods().toString();
        return """
                Grid %dx%d  |  x=0..%d left→right, y=0..%d top→bottom  |  currently moving %s
                Head at (%d,%d).

                Move options:
                %s
                Food: %s

                Never choose WALL or BODY — instant death.
                Move toward food when safe.
                Reply with exactly one word: UP DOWN LEFT RIGHT
                """.formatted(g, g, g - 1, g - 1, snake.direction(),
                              head.x(), head.y(), moves, foodStr);
    }

    RenderState.Position step(RenderState.Position pos, Direction dir) {
        return switch (dir) {
            case UP    -> new RenderState.Position(pos.x(),     pos.y() - 1);
            case DOWN  -> new RenderState.Position(pos.x(),     pos.y() + 1);
            case LEFT  -> new RenderState.Position(pos.x() - 1, pos.y());
            case RIGHT -> new RenderState.Position(pos.x() + 1, pos.y());
        };
    }

    boolean isOutOfBounds(RenderState.Position pos, int g) {
        return pos.x() < 0 || pos.x() >= g || pos.y() < 0 || pos.y() >= g;
    }

    int wallDistance(RenderState.Position head, Direction dir, int g) {
        return switch (dir) {
            case UP    -> head.y();
            case DOWN  -> g - 1 - head.y();
            case LEFT  -> head.x();
            case RIGHT -> g - 1 - head.x();
        };
    }
}
