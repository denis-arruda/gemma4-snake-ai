package dev.denisarruda.gemmasnakeai.game.control;

import dev.denisarruda.gemmasnakeai.game.entity.Cell;
import dev.denisarruda.gemmasnakeai.game.entity.Direction;
import dev.denisarruda.gemmasnakeai.game.entity.Food;
import dev.denisarruda.gemmasnakeai.game.entity.GameState;
import dev.denisarruda.gemmasnakeai.game.entity.GameStatus;
import dev.denisarruda.gemmasnakeai.game.entity.Snake;

import java.util.List;

public interface GameEngine {

    static GameState advance(Snake snake, List<Food> foods,
                              Direction requested, int gridSize,
                              String runId, int step, int score) {
        var newDir = requested.isOpposite(snake.direction()) ? snake.direction() : requested;
        var nextHead = Cell.of(snake.head().x() + newDir.dx(), snake.head().y() + newDir.dy());

        if (isOutOfBounds(nextHead, gridSize)) {
            snake.direction = newDir;
            return GameState.from(runId, step + 1, score, GameStatus.GAME_OVER, snake, foods);
        }

        if (isSelfCollision(nextHead, snake)) {
            snake.direction = newDir;
            return GameState.from(runId, step + 1, score, GameStatus.GAME_OVER, snake, foods);
        }

        var eaten = foods.stream().filter(f -> f.position().equals(nextHead)).findFirst();
        if (eaten.isPresent()) {
            snake.grow(nextHead);
            foods.remove(eaten.get());
            score++;
        } else {
            snake.moveHead(nextHead);
        }
        snake.direction = newDir;

        var replenished = FoodPlacer.replenish(foods, snake, gridSize);
        foods.clear();
        foods.addAll(replenished);

        return GameState.from(runId, step + 1, score, GameStatus.RUNNING, snake, foods);
    }

    static boolean isOutOfBounds(Cell cell, int gridSize) {
        return cell.x() < 0 || cell.x() >= gridSize
            || cell.y() < 0 || cell.y() >= gridSize;
    }

    static boolean isSelfCollision(Cell next, Snake snake) {
        var body = snake.body();
        // Exclude the tail — it vacates on a non-growth move
        var checkRange = body.subList(0, body.size() - 1);
        return checkRange.contains(next);
    }
}
