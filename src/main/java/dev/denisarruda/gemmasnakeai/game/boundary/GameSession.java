package dev.denisarruda.gemmasnakeai.game.boundary;

import dev.denisarruda.gemmasnakeai.game.control.FoodPlacer;
import dev.denisarruda.gemmasnakeai.game.entity.Direction;
import dev.denisarruda.gemmasnakeai.game.entity.Food;
import dev.denisarruda.gemmasnakeai.game.entity.GameState;
import dev.denisarruda.gemmasnakeai.game.entity.GameStatus;
import dev.denisarruda.gemmasnakeai.game.entity.Snake;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class GameSession {

    static final System.Logger LOGGER = System.getLogger(GameSession.class.getName());

    @ConfigProperty(name = "game.grid.size", defaultValue = "30")
    int gridSize;

    Snake snake;
    List<Food> foods;
    public int score;
    public int step;
    public String runId;
    public volatile boolean running;

    public AtomicReference<Direction> latestDirection;
    public AtomicReference<GameState> pendingState;
    public AtomicBoolean agentBusy;

    @PostConstruct
    void init() {
        latestDirection = new AtomicReference<>(Direction.RIGHT);
        pendingState = new AtomicReference<>(null);
        agentBusy = new AtomicBoolean(false);
        running = false;
    }

    public void start(String newRunId) {
        this.runId = newRunId;
        this.snake = Snake.start(gridSize);
        this.foods = new ArrayList<>(FoodPlacer.replenish(List.of(), snake, gridSize));
        this.score = 0;
        this.step = 0;
        this.running = true;
        latestDirection.set(Direction.RIGHT);
        pendingState.set(null);
        agentBusy.set(false);
        LOGGER.log(System.Logger.Level.DEBUG, "Started run {0}", newRunId);
    }

    public GameState snapshot() {
        return GameState.from(runId, step, score,
            running ? GameStatus.RUNNING : GameStatus.GAME_OVER,
            snake, foods);
    }

    public void record(GameState state) {
        this.step = state.step();
        this.score = state.score();
        if (state.status() == GameStatus.GAME_OVER) {
            running = false;
            LOGGER.log(System.Logger.Level.DEBUG, "Run {0} ended at step {1}, score {2}",
                state.runId(), state.step(), state.score());
        }
    }

    public Direction latestDirection() {
        return latestDirection.get();
    }

    public Snake snake() {
        return snake;
    }

    public List<Food> foods() {
        return foods;
    }
}
