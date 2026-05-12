package dev.denisarruda.gemmasnakeai.game.control;

import dev.denisarruda.gemmasnakeai.game.entity.Food;
import dev.denisarruda.gemmasnakeai.game.entity.Snake;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FoodPlacerTest {

    static final int GRID = 30;

    @Test
    void replenish_fillsUpToMaxFood() {
        var snake = Snake.start(GRID);
        var result = FoodPlacer.replenish(List.of(), snake, GRID);
        assertThat(result).hasSize(FoodPlacer.MAX_FOOD);
    }

    @Test
    void replenish_avoidsSnakeAndExistingFoodCells() {
        var snake = Snake.start(GRID);
        var result = FoodPlacer.replenish(List.of(), snake, GRID);
        var snakeCells = snake.body();
        for (Food food : result) {
            assertThat(snakeCells).doesNotContain(food.position());
        }
    }

    @Test
    void replenish_keepsExistingFoodItems() {
        var snake = Snake.start(GRID);
        var existing = List.of(Food.at(0, 0));
        var result = FoodPlacer.replenish(existing, snake, GRID);
        assertThat(result).contains(existing.getFirst());
        assertThat(result).hasSize(FoodPlacer.MAX_FOOD);
    }
}
