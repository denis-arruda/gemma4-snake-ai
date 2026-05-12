package dev.denisarruda.gemmasnakeai.game.entity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class Snake {

    Deque<Cell> segments;
    public Direction direction;

    public static Snake start(int gridSize) {
        int cx = gridSize / 2;
        int cy = gridSize / 2;
        var deque = new ArrayDeque<Cell>();
        deque.addLast(Cell.of(cx, cy));
        deque.addLast(Cell.of(cx - 1, cy));
        deque.addLast(Cell.of(cx - 2, cy));
        return new Snake(deque, Direction.RIGHT);
    }

    Snake(Deque<Cell> segments, Direction direction) {
        this.segments = segments;
        this.direction = direction;
    }

    public Cell head() {
        return segments.peekFirst();
    }

    public List<Cell> body() {
        return List.copyOf(segments);
    }

    public Direction direction() {
        return direction;
    }

    public void moveHead(Cell newHead) {
        segments.addFirst(newHead);
        segments.pollLast();
    }

    public void grow(Cell newHead) {
        segments.addFirst(newHead);
    }

    public boolean occupies(Cell cell) {
        return segments.contains(cell);
    }

    public int size() {
        return segments.size();
    }
}
