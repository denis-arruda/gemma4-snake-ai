package dev.denisarruda.gemmasnakeai.simulation.boundary;

import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class GameBroadcaster {

    static final System.Logger LOGGER = System.getLogger(GameBroadcaster.class.getName());

    final Set<WebSocketConnection> connections = ConcurrentHashMap.newKeySet();

    public void register(WebSocketConnection connection) {
        connections.add(connection);
    }

    public void unregister(WebSocketConnection connection) {
        connections.remove(connection);
    }

    public void broadcast(String json) {
        connections.forEach(conn -> sendSafely(conn, json));
    }

    void sendSafely(WebSocketConnection conn, String json) {
        try {
            conn.sendText(json).await().indefinitely();
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING, "WS send failed, removing connection: {0}", e.getMessage());
            connections.remove(conn);
        }
    }
}
