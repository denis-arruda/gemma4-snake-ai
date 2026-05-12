package dev.denisarruda.gemmasnakeai.game.entity;

import java.util.List;

public record GameState(
    String runId,
    int step,
    int score,
    GameStatus status,
    Cell head,
    List<Cell> body,
    Direction direction,
    List<Food> food
) {

    public static GameState initial(String runId, Snake snake, List<Food> food) {
        return from(runId, 0, 0, GameStatus.RUNNING, snake, food);
    }

    public static GameState from(String runId, int step, int score,
                                  GameStatus status, Snake snake, List<Food> food) {
        return new GameState(
            runId, step, score, status,
            snake.head(),
            List.copyOf(snake.body()),
            snake.direction(),
            List.copyOf(food)
        );
    }
}
