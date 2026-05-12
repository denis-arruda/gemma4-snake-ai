package dev.denisarruda.gemmasnakeai.simulation.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.denisarruda.gemmasnakeai.agent.boundary.SnakeAgent;
import dev.denisarruda.gemmasnakeai.game.boundary.GameSession;
import dev.denisarruda.gemmasnakeai.game.control.GameEngine;
import dev.denisarruda.gemmasnakeai.game.entity.Cell;
import dev.denisarruda.gemmasnakeai.game.entity.Direction;
import dev.denisarruda.gemmasnakeai.game.entity.Food;
import dev.denisarruda.gemmasnakeai.game.entity.GameState;
import dev.denisarruda.gemmasnakeai.game.entity.GameStatus;
import dev.denisarruda.gemmasnakeai.simulation.boundary.GameBroadcaster;
import dev.denisarruda.gemmasnakeai.simulation.entity.SimulationRun;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

@ApplicationScoped
public class SimulationRunner {

    static final System.Logger LOGGER = System.getLogger(SimulationRunner.class.getName());

    static final java.util.concurrent.ExecutorService AGENT_EXECUTOR =
        Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("agent-", 0).factory());

    final GameSession session;
    final SnakeAgent agent;
    final GameBroadcaster broadcaster;
    final ObjectMapper mapper;

    @ConfigProperty(name = "game.grid.size", defaultValue = "30")
    int gridSize;

    final ConcurrentHashMap<String, SimulationRun> runs = new ConcurrentHashMap<>();

    @Inject
    SimulationRunner(GameSession session, SnakeAgent agent,
                     GameBroadcaster broadcaster, ObjectMapper mapper) {
        this.session = session;
        this.agent = agent;
        this.broadcaster = broadcaster;
        this.mapper = mapper;
    }

    void onStart(@Observes StartupEvent ev) {
        startRun(generateId());
        LOGGER.log(System.Logger.Level.INFO, "Auto-started initial simulation");
    }

    private static String generateId() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    public GameState startRun(String runId) {
        session.start(runId);
        var initial = session.snapshot();
        runs.put(runId, SimulationRun.started(runId, initial));
        broadcaster.broadcast(renderStateJson(initial));
        return initial;
    }

    public String currentStateJson() {
        if (session.runId == null) return null;
        return renderStateJson(session.snapshot());
    }

    public Optional<SimulationRun> getRun(String id) {
        return Optional.ofNullable(runs.get(id));
    }

    @Scheduled(every = "${simulation.tick-interval:200ms}")
    void tick() {
        if (!session.running) return;

        var direction = session.latestDirection();
        var state = GameEngine.advance(
            session.snake(), session.foods(), direction,
            gridSize, session.runId, session.step, session.score);

        session.record(state);
        runs.computeIfPresent(session.runId, (k, run) -> run.withSnapshot(state));
        broadcaster.broadcast(renderStateJson(state));

        if (state.status() == GameStatus.GAME_OVER) {
            runs.computeIfPresent(session.runId, (k, run) -> run.completed(state.score()));
            return;
        }

        session.pendingState.set(state);

        if (session.agentBusy.compareAndSet(false, true)) {
            CompletableFuture.runAsync(this::runAgent, AGENT_EXECUTOR);
        }
    }

    void runAgent() {
        try {
            GameState state;
            while ((state = session.pendingState.getAndSet(null)) != null) {
                var dir = callAgent(state);
                session.latestDirection.set(dir);
            }
        } finally {
            session.agentBusy.set(false);
            if (session.pendingState.get() != null
                    && session.agentBusy.compareAndSet(false, true)) {
                CompletableFuture.runAsync(this::runAgent, AGENT_EXECUTOR);
            }
        }
    }

    Direction callAgent(GameState state) {
        try {
            var response = agent.decide(buildPrompt(state)).trim().toUpperCase();
            return Direction.valueOf(response);
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING,
                "Agent call failed at step {0}, keeping direction {1}: {2}",
                state.step(), session.latestDirection(), e.getMessage());
            return session.latestDirection();
        }
    }

    private static String buildPrompt(GameState state) {
        return """
            Head: %s  Body: %s
            Food: %s
            Direction: %s  Score: %d  Step: %d
            """.formatted(
                state.head(),
                state.body(),
                state.food().stream().map(f -> f.position().toString()).toList(),
                state.direction().name(),
                state.score(),
                state.step());
    }

    String renderStateJson(GameState state) {
        var root = mapper.createObjectNode();
        root.put("tick", state.step());
        root.put("status", state.status().name());

        var snakeNode = mapper.createObjectNode();
        snakeNode.put("agentId", "agent-1");
        snakeNode.set("head", cellNode(state.head()));
        snakeNode.set("body", cellArray(state.body()));
        snakeNode.put("direction", state.direction().name());
        snakeNode.put("score", state.score());
        snakeNode.put("alive", state.status() == GameStatus.RUNNING);

        root.set("snakes", mapper.createArrayNode().add(snakeNode));
        root.set("food", foodArray(state.food()));

        return root.toString();
    }

    private ObjectNode cellNode(Cell cell) {
        var node = mapper.createObjectNode();
        node.put("x", cell.x());
        node.put("y", cell.y());
        return node;
    }

    private ArrayNode cellArray(List<Cell> cells) {
        var array = mapper.createArrayNode();
        for (var cell : cells) {
            array.add(cellNode(cell));
        }
        return array;
    }

    private ArrayNode foodArray(List<Food> foods) {
        var array = mapper.createArrayNode();
        for (var food : foods) {
            array.add(cellNode(food.position()));
        }
        return array;
    }
}
