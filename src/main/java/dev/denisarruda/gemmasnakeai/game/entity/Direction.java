package dev.denisarruda.gemmasnakeai.game.entity;

public enum Direction {
    UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

    final int dx;
    final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public int dx() { return dx; }
    public int dy() { return dy; }

    public boolean isOpposite(Direction other) {
        return this.dx == -other.dx && this.dy == -other.dy;
    }
}
