package network;

import java.io.IOException;
import java.net.Socket;

/**
 * ClientHandler.java
 * ===================
 * 
 * This class handles the communication with ONE connected client.
 * 
 * *** LAB SHEET 8 COMPLIANCE ***
 * ClientHandler extends Thread, exactly as demonstrated in Lab Sheet 8.
 * The university lab teaches that the server creates a separate Thread
 * (ClientHandler) for each connected client. This allows the server to
 * handle multiple clients concurrently — while one ClientHandler is
 * communicating with Client A, another ClientHandler can simultaneously
 * communicate with Client B.
 * 
 * Lab Sheet 8 pattern:
 *   - ClientHandler extends Thread
 *   - Constructor receives the Socket and communication-related objects
 *   - run() method contains the communication loop
 *   - The server calls clientHandler.start() to begin the thread
 * 
 * In this whiteboard project, ClientHandler:
 *   1. Receives whiteboard-related messages from the client (drawing actions,
 *      text input, canvas operations, etc.)
 *   2. Processes the incoming messages
 *   3. Delegates broadcasting to the server so other clients receive updates
 *   4. Handles client disconnection gracefully
 * 
 * The ClientHandler uses a Connection object to manage the socket and streams,
 * keeping the code clean and organized.
 * 
 * @author Green University of Bangladesh - CSE Networking Lab Project
 */
public class ClientHandler extends Thread {

    // ===== Instance Variables =====

    // Reference to the Connection object that wraps this client's
    // Socket, input stream, and output stream.
    private Connection connection;

    // Reference back to the WhiteboardServer.
    // This is needed so the ClientHandler can ask the server to broadcast
    // messages to all other connected clients (for real-time synchronization).
    private WhiteboardServer server;

    // The client's Socket, stored here for direct access if needed.
    // In Lab Sheet 8, the ClientHandler receives the Socket in its constructor.
    private Socket clientSocket;

    /**
     * Constructor: Creates a new ClientHandler for a connected client.
     * 
     * *** LAB SHEET 8 PATTERN ***
     * In Lab Sheet 8, the ClientHandler constructor receives the Socket
     * and the communication streams. Here, we receive the Socket and wrap
     * it inside a Connection object. The Connection object internally
     * creates the input/output streams from the socket, following the
     * exact same Lab Sheet 8 concept:
     *   Socket -> getInputStream()  -> BufferedReader (for receiving)
     *   Socket -> getOutputStream() -> PrintWriter   (for sending)
     * 
     * @param socket   The Socket obtained from ServerSocket.accept()
     * @param server   Reference to the WhiteboardServer (for broadcasting)
     * @param clientId A unique identifier for this client (e.g., "Client-1")
     * @throws IOException If an error occurs while setting up streams
     */
    public ClientHandler(Socket socket, WhiteboardServer server, String clientId)
            throws IOException {

        // Store the client socket (Lab Sheet 8 stores Socket in the handler).
        this.clientSocket = socket;

        // Store reference to the server for broadcasting.
        this.server = server;

        // Create the Connection object, which sets up the input/output streams.
        // This is equivalent to the Lab Sheet 8 step where the handler creates
        // DataInputStream and DataOutputStream from the socket.
        this.connection = new Connection(socket, clientId);

        // Set the thread name for easier identification in logs and debugging.
        // When we call start(), Java creates a new thread with this name.
        this.setName("ClientHandler-" + clientId);

        System.out.println("[ClientHandler] Handler created for " + clientId);
    }

    /**
     * The run() method — the heart of the ClientHandler thread.
     * 
     * *** LAB SHEET 8 PATTERN ***
     * In Lab Sheet 8, the run() method contains the communication loop.
     * When the server calls clientHandler.start(), Java automatically
     * invokes this run() method in a NEW, SEPARATE thread. This means
     * each client's communication runs independently and concurrently.
     * 
     * The communication loop:
     *   1. Wait for a message from the client (blocking call)
     *   2. Process the received message
     *   3. If the message is a whiteboard action, broadcast to other clients
     *   4. If the message is a disconnect command, exit the loop
     *   5. Repeat until the client disconnects
     * 
     * This loop keeps running until:
     *   - The client sends a "DISCONNECT" message
     *   - The client closes its connection (readLine returns null)
     *   - An IOException occurs (network error)
     */
    @Override
    public void run() {
        System.out.println("[ClientHandler] Thread started for "
            + connection.getClientId()
            + " | Thread: " + Thread.currentThread().getName());

        // Send a welcome message to the newly connected client.
        // This confirms that the connection was established successfully.
        connection.sendMessage("WELCOME:" + connection.getClientId());

        try {
            // ===== MAIN COMMUNICATION LOOP =====
            // This loop continuously reads messages from the client.
            // It follows the Lab Sheet 8 pattern where the handler
            // keeps communicating with the client until disconnection.

            String receivedMessage;

            // receiveMessage() calls readLine(), which BLOCKS (waits)
            // until the client sends data. This is how socket communication
            // works — the thread sleeps until there is data to read.
            while ((receivedMessage = connection.receiveMessage()) != null) {

                System.out.println("[ClientHandler] Received from "
                    + connection.getClientId() + ": " + receivedMessage);

                // Process the received message based on its type/content.
                handleMessage(receivedMessage);
            }

            // If we reach here, receiveMessage() returned null,
            // which means the client closed its connection gracefully.
            System.out.println("[ClientHandler] " + connection.getClientId()
                + " disconnected (end of stream).");

        } catch (IOException e) {
            // An IOException means the connection was lost unexpectedly.
            // This can happen if the client crashes, the network drops,
            // or the client forcefully closes without sending DISCONNECT.
            System.err.println("[ClientHandler] Connection error with "
                + connection.getClientId() + ": " + e.getMessage());

        } finally {
            // ===== CLEANUP =====
            // The finally block ALWAYS executes, whether the loop ended
            // normally or due to an exception. This ensures resources
            // are properly released.

            // Remove this client from the server's list of active clients.
            // This prevents the server from trying to broadcast to a
            // disconnected client.
            server.removeClient(this);

            // Close the connection (socket and streams).
            // Proper resource management as required by Lab Sheet 8.
            connection.close();

            System.out.println("[ClientHandler] Handler thread ended for "
                + connection.getClientId());
        }
    }

    /**
     * Processes a received message and takes appropriate action.
     * 
     * Messages from the whiteboard client will describe drawing actions.
     * This method examines the message type and decides what to do.
     * 
     * For now, most whiteboard actions are broadcast to all other clients
     * so their canvases stay synchronized in real time.
     * 
     * Message format (simple text protocol, upgradeable to JSON later):
     *   TYPE:data
     * 
     * Examples of expected message types:
     *   DRAW_START:x,y                  — User started drawing at (x,y)
     *   DRAW_POINT:x,y                  — User is drawing at point (x,y)
     *   DRAW_LINE:x1,y1,x2,y2          — Draw a line segment
     *   DRAW_RECT:x,y,width,height      — Draw a rectangle
     *   DRAW_CIRCLE:x,y,radius          — Draw a circle
     *   DRAW_END:                       — Drawing stroke completed
     *   TEXT:x,y,content                — Text input at position
     *   CLEAR_CANVAS:                   — Clear the entire canvas
     *   COLOR:r,g,b                     — Change drawing color
     *   STROKE_WIDTH:width              — Change stroke width
     *   DISCONNECT:                     — Client wants to disconnect
     * 
     * @param message The raw message received from the client
     */
    private void handleMessage(String message) {
        // Guard against null or empty messages.
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        // Extract the message type (everything before the first colon).
        // For example, "DRAW_LINE:100,200,300,400" -> type = "DRAW_LINE"
        String messageType;
        if (message.contains(":")) {
            messageType = message.substring(0, message.indexOf(":")).toUpperCase();
        } else {
            messageType = message.toUpperCase();
        }

        // Handle different message types.
        switch (messageType) {

            case "DISCONNECT":
                // Client wants to disconnect gracefully.
                System.out.println("[ClientHandler] " + connection.getClientId()
                    + " requested disconnect.");

                // Notify other clients that this user has left.
                server.broadcastMessage(
                    "USER_LEFT:" + connection.getClientId(), this);

                // Close the connection, which will cause receiveMessage()
                // to return null and exit the communication loop.
                connection.close();
                break;

            case "DRAW_START":
            case "DRAW_POINT":
            case "DRAW_LINE":
            case "DRAW_RECT":
            case "DRAW_CIRCLE":
            case "DRAW_TRI":
            case "DRAW_END":
            case "TEXT":
            case "MOVE_TEXT":
            case "BLOCK_SLANG":
            case "CLEAR_CANVAS":
            case "COLOR":
            case "STROKE_WIDTH":
                // ===== WHITEBOARD DRAWING ACTIONS =====
                // These are collaborative actions that must be synchronized
                // across all connected clients. The server broadcasts the
                // message to every client EXCEPT the sender (because the
                // sender already performed the action locally).

                // Prepend the sender's ID so other clients know who drew.
                String broadcastMessage = "FROM:" + connection.getClientId()
                    + "|" + message;

                // Ask the server to broadcast this message to all other clients.
                server.broadcastMessage(broadcastMessage, this);
                break;

            default:
                // Unknown message type — log it for debugging.
                System.out.println("[ClientHandler] Unknown message type from "
                    + connection.getClientId() + ": " + message);

                // Send an error response back to the client.
                connection.sendMessage("ERROR:Unknown message type: " + messageType);
                break;
        }
    }

    // ===== Public Methods =====

    /**
     * Sends a message to this handler's client.
     * This is called by the server when broadcasting messages from other clients.
     * 
     * @param message The message to send to this client
     */
    public void sendToClient(String message) {
        connection.sendMessage(message);
    }

    /**
     * Returns the Connection object for this client.
     * @return The Connection object
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * Returns the unique identifier for this client.
     * @return The client ID string
     */
    public String getClientId() {
        return connection.getClientId();
    }

    /**
     * Checks whether this client is still connected.
     * @return true if the client connection is active
     */
    public boolean isClientConnected() {
        return connection.isConnected();
    }
}
