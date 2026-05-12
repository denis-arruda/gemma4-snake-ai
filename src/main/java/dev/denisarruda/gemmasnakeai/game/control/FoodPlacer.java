package dev.denisarruda.gemmasnakeai.game.control;

import dev.denisarruda.gemmasnakeai.game.entity.Cell;
import dev.denisarruda.gemmasnakeai.game.entity.Food;
import dev.denisarruda.gemmasnakeai.game.entity.Snake;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public interface FoodPlacer {

    int MAX_FOOD = 5;

    static List<Food> replenish(List<Food> current, Snake snake, int gridSize) {
        var result = new ArrayList<>(current);
        var occupied = occupiedCells(current, snake);
        while (result.size() < MAX_FOOD) {
            if (occupied.size() >= gridSize * gridSize) break;
            var cell = randomEmptyCell(occupied, gridSize);
            result.add(Food.at(cell));
            occupied.add(cell);
        }
        return result;
    }

    static Cell randomEmptyCell(Set<Cell> occupied, int gridSize) {
        var rng = ThreadLocalRandom.current();
        Cell candidate;
        do {
            candidate = Cell.of(rng.nextInt(gridSize), rng.nextInt(gridSize));
        } while (occupied.contains(candidate));
        return candidate;
    }

    private static Set<Cell> occupiedCells(List<Food> food, Snake snake) {
        var occupied = new HashSet<>(snake.body());
        food.stream().map(Food::position).forEach(occupied::add);
        return occupied;
    }
}
