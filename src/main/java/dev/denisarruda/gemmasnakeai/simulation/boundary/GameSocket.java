package dev.denisarruda.gemmasnakeai.simulation.boundary;

import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.inject.Inject;

@WebSocket(path = "/ws/game")
public class GameSocket {

    final GameBroadcaster broadcaster;

    @Inject
    GameSocket(GameBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @OnOpen
    void onOpen(WebSocketConnection connection) {
        broadcaster.register(connection);
    }

    @OnClose
    void onClose(WebSocketConnection connection) {
        broadcaster.unregister(connection);
    }
}
