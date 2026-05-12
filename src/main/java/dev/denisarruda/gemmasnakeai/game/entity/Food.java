package dev.denisarruda.gemmasnakeai.game.entity;

public record Food(Cell position) {

    public static Food at(Cell position) {
        return new Food(position);
    }

    public static Food at(int x, int y) {
        return new Food(Cell.of(x, y));
    }
}
