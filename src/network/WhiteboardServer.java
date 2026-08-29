// Server entry point: accepts client sockets, relays messages, and manages the Python AI service.
package network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import com.google.gson.Gson;

public class WhiteboardServer {

    private static final int SERVER_PORT = 12345;

    private ServerSocket serverSocket;

    private ConnectionManager connectionManager;

    private int clientCounter;

    private volatile boolean running;

    private Process pythonProcess;

    private final Gson gson;

    // Creates the server with its connection registry and database.
    public WhiteboardServer() {
        this.connectionManager = new ConnectionManager();
        this.clientCounter = 0;
        this.running = false;
        this.gson = new Gson();
    }

    // Binds the listening socket and accepts clients until stopped.
    public void start() {

        startPythonService();

        try {
            java.util.List<String> persistedSlangs = DatabaseManager.getAllBlockedSlangs();
            for (String slang : persistedSlangs) {
                ContentModerator.addBlockedWord(slang);
            }
            System.out.println("[Server] Loaded " + persistedSlangs.size() + " persistent blocked slangs from SQLite DB.");
        } catch (Exception e) {
            System.err.println("[Server] Error loading persisted slangs: " + e.getMessage());
        }

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

                    connectionManager.addClient(handler);
                    handler.start();

                    System.out.println("[Server] ClientHandler thread started for " + clientId);
                    System.out.println("[Server] Total active clients: " + connectionManager.getActiveClientCount());
                    System.out.println();

                    NetworkMessage joinMsg = new NetworkMessage("USER_JOINED");
                    joinMsg.setSenderId(clientId);
                    broadcastMessage(gson.toJson(joinMsg), handler);

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

    // Relays a message to every client except the sender.
    public void broadcastMessage(String jsonMessage, ClientHandler sender) {
        connectionManager.broadcastToOthers(jsonMessage, sender);
    }

    // Relays a message to every connected client.
    public void broadcastToAll(String jsonMessage) {
        connectionManager.broadcastToAll(jsonMessage);
    }

    // Drops a disconnected client from the registry.
    public void removeClient(ClientHandler handler) {
        connectionManager.removeClient(handler);

        System.out.println("[Server] Client removed: " + handler.getClientId());
        System.out.println("[Server] Remaining active clients: " + connectionManager.getActiveClientCount());

        NetworkMessage leaveMsg = new NetworkMessage("USER_LEFT");
        leaveMsg.setSenderId(handler.getClientId());
        broadcastToAll(gson.toJson(leaveMsg));
    }

    // Returns how many clients are currently connected.
    public int getActiveClientCount() {
        return connectionManager.getActiveClientCount();
    }

    // Closes all clients, the listening socket, and the Python service.
    public void stop() {
        running = false;
        System.out.println("[Server] Shutting down server...");

        NetworkMessage shutdownMsg = new NetworkMessage("SERVER_SHUTDOWN");
        shutdownMsg.setText("Server is shutting down");
        broadcastToAll(gson.toJson(shutdownMsg));

        connectionManager.closeAll();

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[Server] Error closing server socket: " + e.getMessage());
        }

        stopPythonService();

        System.out.println("[Server] Server stopped.");
    }

    // Launches the Python AI service as a child process.
    private void startPythonService() {
        new Thread(() -> {
            try {
                System.out.println("[Server] Starting Python AI Microservice process...");
                ProcessBuilder pb = new ProcessBuilder("python", "src/network/ai_service.py");
                pb.redirectErrorStream(true);
                pythonProcess = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(pythonProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[AI Service] " + line);
                    }
                }
            } catch (IOException e) {
                System.err.println("[Server] Failed to run Python AI microservice: " + e.getMessage());
                System.err.println("[Server] Please make sure python and requirements are installed.");
            }
        }, "PythonAIServiceRunner").start();
    }

    // Terminates the Python AI service child process.
    private void stopPythonService() {
        if (pythonProcess != null) {
            System.out.println("[Server] Terminating Python AI process...");
            pythonProcess.destroy();
            try {
                pythonProcess.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("[Server] Python AI process terminated.");
        }
    }

    // Starts the server and registers a shutdown hook.
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
