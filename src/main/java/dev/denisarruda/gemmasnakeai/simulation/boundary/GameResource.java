package dev.denisarruda.gemmasnakeai.simulation.boundary;

import dev.denisarruda.gemmasnakeai.game.control.GameEngine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@Path("/game")
@ApplicationScoped
public class GameResource {

    final GameEngine gameEngine;

    @Inject
    GameResource(GameEngine gameEngine) {
        this.gameEngine = gameEngine;
    }

    @POST
    @Path("/restart")
    public Response restart() {
        gameEngine.restart();
        return Response.noContent().build();
    }
}
