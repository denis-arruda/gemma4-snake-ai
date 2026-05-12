package dev.denisarruda.gemmasnakeai.simulation.boundary;

import dev.denisarruda.gemmasnakeai.simulation.control.SimulationRunner;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.inject.Inject;

@WebSocket(path = "/ws/game")
public class GameSocket {

    static final System.Logger LOGGER = System.getLogger(GameSocket.class.getName());

    final GameBroadcaster broadcaster;
    final SimulationRunner runner;

    @Inject
    GameSocket(GameBroadcaster broadcaster, SimulationRunner runner) {
        this.broadcaster = broadcaster;
        this.runner = runner;
    }

    @OnOpen
    void onOpen(WebSocketConnection connection) {
        broadcaster.register(connection);
        LOGGER.log(System.Logger.Level.DEBUG, "Client connected: {0}", connection.id());
        var stateJson = runner.currentStateJson();
        if (stateJson != null) {
            connection.sendText(stateJson)
                .subscribe().with(
                    v -> {},
                    err -> LOGGER.log(System.Logger.Level.WARNING, "Initial state send failed: {0}", err.getMessage())
                );
        }
    }

    @OnClose
    void onClose(WebSocketConnection connection) {
        broadcaster.unregister(connection);
        LOGGER.log(System.Logger.Level.DEBUG, "Client disconnected: {0}", connection.id());
    }
}
