// Thread-safe registry of connected clients with broadcast helpers.
package network;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ConnectionManager {
    private final List<ClientHandler> activeClients;

    // Creates an empty client registry.
    public ConnectionManager() {
        this.activeClients = new CopyOnWriteArrayList<>();
    }

    // Registers a newly connected client.
    public void addClient(ClientHandler handler) {
        activeClients.add(handler);
    }

    // Removes a client that has disconnected.
    public void removeClient(ClientHandler handler) {
        activeClients.remove(handler);
    }

    // Returns how many clients are currently connected.
    public int getActiveClientCount() {
        return activeClients.size();
    }

    // Returns a snapshot copy of the connected clients.
    public List<ClientHandler> getActiveClients() {
        return activeClients;
    }

    // Sends a message to every connected client.
    public void broadcastToAll(String jsonMessage) {
        for (ClientHandler client : activeClients) {
            if (client.isClientConnected()) {
                client.sendToClient(jsonMessage);
            }
        }
    }

    // Sends a message to every client except the sender.
    public void broadcastToOthers(String jsonMessage, ClientHandler sender) {
        for (ClientHandler client : activeClients) {
            if (client != sender && client.isClientConnected()) {
                client.sendToClient(jsonMessage);
            }
        }
    }

    // Disconnects every client and clears the registry.
    public void closeAll() {
        for (ClientHandler client : activeClients) {
            client.getConnection().close();
        }
        activeClients.clear();
    }
}
