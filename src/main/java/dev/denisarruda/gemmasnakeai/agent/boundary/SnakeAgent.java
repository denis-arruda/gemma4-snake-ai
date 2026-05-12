package dev.denisarruda.gemmasnakeai.agent.boundary;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService
@ApplicationScoped
public interface SnakeAgent {

    @SystemMessage("""
            You are controlling a snake in a grid-based game.
            Each turn you receive the game state with every move pre-evaluated as safe or lethal.
            Never choose a move marked WALL or BODY — the snake dies immediately.
            Prefer moves with more steps to the nearest wall.
            Move toward visible food when it is safe to do so.
            Respond with exactly one word: UP DOWN LEFT RIGHT.
            """)
    String decide(@UserMessage String stateDescription);
}
