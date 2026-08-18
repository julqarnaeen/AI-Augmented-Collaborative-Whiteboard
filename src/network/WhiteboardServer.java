package network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WhiteboardServer.java
 * =====================
 * 
 * This is the CENTRAL SERVER of the Real-Time AI-Augmented Collaborative
 * Whiteboard System. It is the heart of the networking architecture.
 * 
 * *** LAB SHEET 8 COMPLIANCE ***
 * This class follows the Lab Sheet 8 socket-programming approach exactly:
 * 
 *   1. Create a ServerSocket and bind it to a port
 *   2. Continuously wait for incoming client connections using accept()
 *   3. When a client connects, accept() returns a Socket for that client
 *   4. Create input/output streams from the Socket (done inside Connection)
 *   5. Create a new ClientHandler (which extends Thread) for the client
 *   6. Start the ClientHandler thread using start()
 *   7. Go back to waiting for the next client
 * 
 * Lab Sheet 8 flow:
 *   ServerSocket -> accept() -> Socket -> Streams -> ClientHandler -> start()
 * 
 * The server can handle MULTIPLE clients simultaneously because each client
 * gets its own ClientHandler thread. While one thread is communicating with
 * Client A, another thread can independently communicate with Client B.
 * This is the core concept of multithreaded server programming.
 * 
 * For the collaborative whiteboard, the server also provides BROADCASTING:
 * when one client draws something, the server sends that drawing action
 * to all other connected clients so their canvases stay synchronized.
 * 
 * @author Green University of Bangladesh - CSE Networking Lab Project
 */
public class WhiteboardServer {

    // ===== Constants =====

    // The port number on which the server listens for incoming connections.
    // Port numbers 0-1023 are reserved for well-known services (HTTP=80, etc.).
    // We use a port in the range 1024-65535 to avoid conflicts.
    // All clients must connect to this same port.
    private static final int SERVER_PORT = 12345;

    // ===== Instance Variables =====

    // The ServerSocket is the server-side socket that LISTENS for incoming
    // client connection requests. It does NOT communicate directly with clients.
    // Its only job is to wait for new connections and create a Socket for each one.
    // This is the fundamental Lab Sheet 8 server-side component.
    private ServerSocket serverSocket;

    // A thread-safe list that stores all currently active ClientHandler objects.
    // We use CopyOnWriteArrayList because multiple threads (ClientHandlers)
    // may add or remove entries concurrently. This prevents
    // ConcurrentModificationException errors during iteration.
    //
    // This list enables BROADCASTING: to send a message to all clients,
    // we iterate through this list and call sendToClient() on each handler.
    private List<ClientHandler> activeClients;

    // A counter to generate unique client IDs (Client-1, Client-2, etc.).
    private int clientCounter;

    // Flag to control the server's main accept() loop.
    private volatile boolean running;

    /**
     * Constructor: Initializes the server's data structures.
     * The ServerSocket is NOT created here — it is created in start().
     */
    public WhiteboardServer() {
        this.activeClients = new CopyOnWriteArrayList<>();
        this.clientCounter = 0;
        this.running = false;
    }

    /**
     * Starts the server: creates the ServerSocket and begins accepting clients.
     * 
     * *** LAB SHEET 8 PATTERN ***
     * Step 1: Create a ServerSocket on the specified port.
     * Step 2: Enter a loop that continuously calls accept() to wait for clients.
     * Step 3: For each client, create a ClientHandler and start its thread.
     * 
     * The accept() method is a BLOCKING call — it pauses execution and waits
     * until a client actually connects. Once a client connects, accept()
     * returns a new Socket object representing the connection to that client.
     */
    public void start() {
        try {
            // ===== STEP 1: Create the ServerSocket =====
            // ServerSocket binds to the specified port and starts listening.
            // This is like opening a shop — the server is now ready for customers.
            // The operating system reserves this port for our server.
            serverSocket = new ServerSocket(SERVER_PORT);
            running = true;

            System.out.println("============================================");
            System.out.println("  AI-Augmented Collaborative Whiteboard");
            System.out.println("  Server Started Successfully");
            System.out.println("============================================");
            System.out.println("[Server] Listening on port: " + SERVER_PORT);
            System.out.println("[Server] Waiting for clients to connect...");
            System.out.println();

            // ===== STEP 2: Continuously Accept Client Connections =====
            // This is the main server loop. It runs indefinitely, accepting
            // one client at a time. The loop does NOT block other clients
            // because each client is handed off to its own thread.
            while (running) {

                // *** accept() — THE KEY LAB SHEET 8 METHOD ***
                // This call BLOCKS (waits) until a client connects.
                // When a client creates "new Socket(host, port)", this
                // accept() call returns a new Socket for that client.
                //
                // The returned Socket is a DIFFERENT socket from the
                // ServerSocket. The ServerSocket only listens; the returned
                // Socket is used for actual communication with the client.
                System.out.println("[Server] Waiting for a new client connection...");
                Socket clientSocket = serverSocket.accept();

                // A new client has connected!
                // Generate a unique ID for this client.
                clientCounter++;
                String clientId = "Client-" + clientCounter;

                System.out.println("[Server] *** New client connected! ***");
                System.out.println("[Server] Client ID: " + clientId);
                System.out.println("[Server] Client Address: "
                    + clientSocket.getInetAddress().getHostAddress()
                    + ":" + clientSocket.getPort());

                // ===== STEP 3: Create a ClientHandler Thread =====
                // Following Lab Sheet 8:
                //   Socket -> InputStream/OutputStream -> ClientHandler -> start()
                //
                // The ClientHandler constructor will:
                //   1. Store the Socket
                //   2. Create a Connection object (which creates the streams)
                //   3. Prepare the handler for communication
                try {
                    // Create a new ClientHandler for this client.
                    // The ClientHandler extends Thread (Lab Sheet 8 requirement).
                    ClientHandler handler = new ClientHandler(
                        clientSocket, this, clientId
                    );

                    // Add this handler to the list of active clients.
                    // This enables broadcasting to all connected clients.
                    activeClients.add(handler);

                    // ===== STEP 4: Start the ClientHandler Thread =====
                    // start() creates a new Java thread and calls the handler's
                    // run() method in that new thread.
                    //
                    // *** THIS IS THE KEY MULTITHREADING CONCEPT ***
                    // After start(), the handler runs INDEPENDENTLY in its own
                    // thread. The main server loop immediately goes back to
                    // accept() to wait for the next client. This is how the
                    // server handles multiple clients simultaneously.
                    handler.start();

                    System.out.println("[Server] ClientHandler thread started for "
                        + clientId);
                    System.out.println("[Server] Total active clients: "
                        + activeClients.size());
                    System.out.println();

                    // Notify all existing clients that a new user has joined.
                    broadcastMessage("USER_JOINED:" + clientId, handler);

                } catch (IOException e) {
                    // If creating the ClientHandler fails (e.g., stream error),
                    // close the client socket and continue accepting others.
                    System.err.println("[Server] Error creating handler for "
                        + clientId + ": " + e.getMessage());
                    clientSocket.close();
                }
            }

        } catch (IOException e) {
            // This catch handles ServerSocket creation errors and accept() errors.
            if (running) {
                System.err.println("[Server] Server error: " + e.getMessage());
                e.printStackTrace();
            } else {
                System.out.println("[Server] Server stopped.");
            }

        } finally {
            // Ensure the server is properly shut down.
            stop();
        }
    }

    /**
     * Broadcasts a message to all connected clients EXCEPT the sender.
     * 
     * This is the core of the collaborative whiteboard's real-time synchronization.
     * When Client A draws something, the server broadcasts that drawing action
     * to Clients B, C, D, etc. — everyone except the sender.
     * 
     * The sender is excluded because they already performed the action locally
     * on their own canvas.
     * 
     * Diagram:
     *   Client A sends "DRAW_LINE:100,200,300,400"
     *        |
     *        v
     *      Server receives the message
     *        |
     *        +----> sends to Client B
     *        |
     *        +----> sends to Client C
     *        |
     *        +----> sends to Client D
     *        |
     *        X----> does NOT send back to Client A (the sender)
     * 
     * @param message The message to broadcast
     * @param sender  The ClientHandler that sent the message (excluded from broadcast)
     */
    public void broadcastMessage(String message, ClientHandler sender) {
        System.out.println("[Server] Broadcasting: " + message);

        // Iterate through all active clients.
        for (ClientHandler client : activeClients) {
            // Send to everyone EXCEPT the sender.
            if (client != sender && client.isClientConnected()) {
                client.sendToClient(message);
            }
        }
    }

    /**
     * Broadcasts a message to ALL connected clients, including the sender.
     * Used for server-wide announcements.
     * 
     * @param message The message to broadcast to all clients
     */
    public void broadcastToAll(String message) {
        System.out.println("[Server] Broadcasting to all: " + message);

        for (ClientHandler client : activeClients) {
            if (client.isClientConnected()) {
                client.sendToClient(message);
            }
        }
    }

    /**
     * Removes a ClientHandler from the list of active clients.
     * 
     * This is called by a ClientHandler when its client disconnects.
     * Removing the handler prevents the server from trying to send
     * messages to a disconnected client.
     * 
     * @param handler The ClientHandler to remove
     */
    public void removeClient(ClientHandler handler) {
        activeClients.remove(handler);

        System.out.println("[Server] Client removed: " + handler.getClientId());
        System.out.println("[Server] Remaining active clients: "
            + activeClients.size());

        // Notify remaining clients about the departure.
        broadcastToAll("USER_LEFT:" + handler.getClientId());
    }

    /**
     * Returns the number of currently connected clients.
     * @return The count of active client connections
     */
    public int getActiveClientCount() {
        return activeClients.size();
    }

    /**
     * Stops the server gracefully.
     * 
     * This method:
     *   1. Sets the running flag to false to stop the accept() loop
     *   2. Disconnects all active clients
     *   3. Closes the ServerSocket to release the port
     */
    public void stop() {
        running = false;
        System.out.println("[Server] Shutting down server...");

        // Disconnect all active clients.
        for (ClientHandler client : activeClients) {
            client.sendToClient("SERVER_SHUTDOWN:Server is shutting down");
            client.getConnection().close();
        }
        activeClients.clear();

        // Close the ServerSocket to release the port.
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

    // ===== Main Method — Entry Point =====

    /**
     * The main method — starting point of the server application.
     * 
     * To run the server:
     *   1. Compile:  javac network/WhiteboardServer.java
     *   2. Run:      java network.WhiteboardServer
     * 
     * The server will start listening on port 12345 and wait for clients.
     * 
     * @param args Command-line arguments (not used)
     */
    public static void main(String[] args) {
        System.out.println("[Main] Starting Whiteboard Server...");

        // Create the server instance.
        WhiteboardServer server = new WhiteboardServer();

        // Add a shutdown hook to handle Ctrl+C gracefully.
        // When the user presses Ctrl+C, this hook runs before the JVM exits,
        // ensuring all resources are properly released.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Main] Shutdown signal received.");
            server.stop();
        }));

        // Start the server. This call blocks because it enters
        // the accept() loop and waits for clients indefinitely.
        server.start();
    }
}
