package dev.denisarruda.gemmasnakeai.agent.boundary;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService
@ApplicationScoped
public interface SnakeAgent {

    @SystemMessage("""
            You are controlling a snake on a 30x30 grid.
            Grid origin (0,0) is top-left. X increases right, Y increases down.
            Respond with exactly one word: UP DOWN LEFT RIGHT.
            """)
    String decide(@UserMessage String stateDescription);
}
