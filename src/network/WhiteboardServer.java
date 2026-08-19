package network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class WhiteboardServer {

    private static final int SERVER_PORT = 12345;

    private ServerSocket serverSocket;

    private List<ClientHandler> activeClients;

    private int clientCounter;

    private volatile boolean running;

    public WhiteboardServer() {
        this.activeClients = new CopyOnWriteArrayList<>();
        this.clientCounter = 0;
        this.running = false;
    }

    public void start() {
        try {

            serverSocket = new ServerSocket(SERVER_PORT);
            running = true;

            System.out.println("============================================");
            System.out.println("  AI-Augmented Collaborative Whiteboard");
            System.out.println("  Server Started Successfully");
            System.out.println("============================================");
            System.out.println("[Server] Listening on port: " + SERVER_PORT);
            System.out.println("[Server] Waiting for clients to connect...");
            System.out.println();

            while (running) {

                System.out.println("[Server] Waiting for a new client connection...");
                Socket clientSocket = serverSocket.accept();

                clientCounter++;
                String clientId = "Client-" + clientCounter;

                System.out.println("[Server] *** New client connected! ***");
                System.out.println("[Server] Client ID: " + clientId);
                System.out.println("[Server] Client Address: "
                    + clientSocket.getInetAddress().getHostAddress()
                    + ":" + clientSocket.getPort());

                try {

                    ClientHandler handler = new ClientHandler(
                        clientSocket, this, clientId
                    );

                    activeClients.add(handler);

                    handler.start();

                    System.out.println("[Server] ClientHandler thread started for "
                        + clientId);
                    System.out.println("[Server] Total active clients: "
                        + activeClients.size());
                    System.out.println();

                    broadcastMessage("USER_JOINED:" + clientId, handler);

                } catch (IOException e) {

                    System.err.println("[Server] Error creating handler for "
                        + clientId + ": " + e.getMessage());
                    clientSocket.close();
                }
            }

        } catch (IOException e) {

            if (running) {
                System.err.println("[Server] Server error: " + e.getMessage());
                e.printStackTrace();
            } else {
                System.out.println("[Server] Server stopped.");
            }

        } finally {

            stop();
        }
    }

    public void broadcastMessage(String message, ClientHandler sender) {
        System.out.println("[Server] Broadcasting: " + message);

        for (ClientHandler client : activeClients) {

            if (client != sender && client.isClientConnected()) {
                client.sendToClient(message);
            }
        }
    }

    public void broadcastToAll(String message) {
        System.out.println("[Server] Broadcasting to all: " + message);

        for (ClientHandler client : activeClients) {
            if (client.isClientConnected()) {
                client.sendToClient(message);
            }
        }
    }

    public void removeClient(ClientHandler handler) {
        activeClients.remove(handler);

        System.out.println("[Server] Client removed: " + handler.getClientId());
        System.out.println("[Server] Remaining active clients: "
            + activeClients.size());

        broadcastToAll("USER_LEFT:" + handler.getClientId());
    }

    public int getActiveClientCount() {
        return activeClients.size();
    }

    public void stop() {
        running = false;
        System.out.println("[Server] Shutting down server...");

        for (ClientHandler client : activeClients) {
            client.sendToClient("SERVER_SHUTDOWN:Server is shutting down");
            client.getConnection().close();
        }
        activeClients.clear();

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[Server] Error closing server socket: "
                + e.getMessage());
        }

        System.out.println("[Server] Server stopped.");
    }

    public static void main(String[] args) {
        System.out.println("[Main] Starting Whiteboard Server...");

        WhiteboardServer server = new WhiteboardServer();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Main] Shutdown signal received.");
            server.stop();
        }));

        server.start();
    }
}
