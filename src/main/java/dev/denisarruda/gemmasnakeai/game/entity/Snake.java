package dev.denisarruda.gemmasnakeai.game.entity;

import java.util.ArrayList;
import java.util.List;

public class Snake {

    final String agentId;
    List<RenderState.Position> cells;
    Direction direction;
    boolean alive;
    int score;

    public Snake(String agentId, List<RenderState.Position> cells, Direction direction) {
        this.agentId   = agentId;
        this.cells     = new ArrayList<>(cells);
        this.direction = direction;
        this.alive     = true;
        this.score     = 0;
    }

    public void move(RenderState.Position newHead) {
        cells.add(0, newHead);
        cells.remove(cells.size() - 1);
    }

    public void grow(RenderState.Position newHead) {
        cells.add(0, newHead);
        score++;
    }

    public void kill() { alive = false; }

    public RenderState.Position head()          { return cells.get(0); }
    public List<RenderState.Position> cells()   { return cells; }
    public Direction direction()                { return direction; }
    public boolean alive()                      { return alive; }
    public String agentId()                     { return agentId; }
    public int score()                          { return score; }

    public RenderState.SnakeRender toRender() {
        return new RenderState.SnakeRender(agentId, List.copyOf(cells), direction, alive);
    }
}
