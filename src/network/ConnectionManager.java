package network;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ConnectionManager {
    private final List<ClientHandler> activeClients;

    public ConnectionManager() {
        this.activeClients = new CopyOnWriteArrayList<>();
    }

    public void addClient(ClientHandler handler) {
        activeClients.add(handler);
    }

    public void removeClient(ClientHandler handler) {
        activeClients.remove(handler);
    }

    public int getActiveClientCount() {
        return activeClients.size();
    }

    public List<ClientHandler> getActiveClients() {
        return activeClients;
    }

    public void broadcastToAll(String jsonMessage) {
        for (ClientHandler client : activeClients) {
            if (client.isClientConnected()) {
                client.sendToClient(jsonMessage);
            }
        }
    }

    public void broadcastToOthers(String jsonMessage, ClientHandler sender) {
        for (ClientHandler client : activeClients) {
            if (client != sender && client.isClientConnected()) {
                client.sendToClient(jsonMessage);
            }
        }
    }

    public void closeAll() {
        for (ClientHandler client : activeClients) {
            client.getConnection().close();
        }
        activeClients.clear();
    }
}
