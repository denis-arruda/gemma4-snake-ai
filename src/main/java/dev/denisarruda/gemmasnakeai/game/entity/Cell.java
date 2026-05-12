package dev.denisarruda.gemmasnakeai.game.entity;

public record Cell(int x, int y) {

    public static Cell of(int x, int y) {
        return new Cell(x, y);
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}
