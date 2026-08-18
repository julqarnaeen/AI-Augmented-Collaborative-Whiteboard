package network;

import java.io.*;
import java.net.Socket;

/**
 * Connection.java
 * ===============
 * 
 * This class encapsulates the communication state of a single client connection.
 * Instead of spreading Socket and stream management across multiple classes,
 * Connection provides a clean, reusable wrapper around:
 *   - The client's Socket
 *   - The input stream (for receiving data from the client)
 *   - The output stream (for sending data to the client)
 * 
 * This follows good object-oriented design while keeping the Lab Sheet 8
 * socket-programming model intact. WhiteboardServer still uses ServerSocket
 * and accept(), and ClientHandler still extends Thread.
 * Connection is a supporting utility class, NOT a replacement for the
 * Lab Sheet 8 server/thread model.
 * 
 * Communication uses BufferedReader and PrintWriter (character streams)
 * for text-based message exchange. This makes it easy to send and receive
 * String messages, which will later carry JSON-formatted whiteboard actions.
 * 
 * @author Green University of Bangladesh - CSE Networking Lab Project
 */
public class Connection {

    // ===== Instance Variables =====

    // The Socket object representing the TCP connection to this client.
    // A Socket provides the endpoint for two-way communication between
    // the server and a specific client over the network.
    private Socket socket;

    // BufferedReader wraps the socket's InputStream to read text data
    // sent by the client, one line at a time.
    private BufferedReader inputReader;

    // PrintWriter wraps the socket's OutputStream to send text data
    // to the client. Auto-flush is enabled so data is sent immediately.
    private PrintWriter outputWriter;

    // A unique identifier for this connection (e.g., "Client-1", "Client-2").
    // Useful for logging and for identifying clients in broadcast messages.
    private String clientId;

    // Tracks whether this connection is currently open and active.
    private volatile boolean connected;

    /**
     * Constructor: Creates a new Connection by wrapping the given Socket.
     * 
     * This follows the Lab Sheet 8 pattern where, after accept() returns a Socket,
     * we obtain input and output streams from that socket for communication.
     * 
     * Lab Sheet 8 concept:
     *   Socket -> socket.getInputStream()  -> InputStreamReader -> BufferedReader
     *   Socket -> socket.getOutputStream() -> PrintWriter
     * 
     * @param socket   The Socket obtained from ServerSocket.accept()
     * @param clientId A unique identifier string for this client
     * @throws IOException If an error occurs while creating the streams
     */
    public Connection(Socket socket, String clientId) throws IOException {
        this.socket = socket;
        this.clientId = clientId;

        // Create the input stream to RECEIVE data from the client.
        // We wrap the raw InputStream in an InputStreamReader (byte-to-char bridge)
        // and then in a BufferedReader for efficient line-by-line reading.
        this.inputReader = new BufferedReader(
            new InputStreamReader(socket.getInputStream())
        );

        // Create the output stream to SEND data to the client.
        // PrintWriter provides convenient print/println methods.
        // The 'true' parameter enables auto-flush: data is sent immediately
        // after each println() call, which is important for real-time communication.
        this.outputWriter = new PrintWriter(
            socket.getOutputStream(), true  // true = auto-flush enabled
        );

        // Mark this connection as active.
        this.connected = true;

        System.out.println("[Connection] Connection established for " + clientId
            + " from " + socket.getInetAddress().getHostAddress()
            + ":" + socket.getPort());
    }

    // ===== Sending Data =====

    /**
     * Sends a text message to the client through the output stream.
     * 
     * This is the fundamental "send" operation in socket communication.
     * The message is written to the socket's output stream, which transmits
     * it over the TCP connection to the client.
     * 
     * The method is synchronized to prevent multiple threads from writing
     * to the same output stream simultaneously, which could corrupt messages.
     * This is important because the server may broadcast messages from
     * different ClientHandler threads.
     * 
     * @param message The text message to send to the client
     */
    public synchronized void sendMessage(String message) {
        if (connected && outputWriter != null) {
            // println() writes the message followed by a newline character.
            // The newline acts as a message delimiter so the receiver knows
            // where one message ends and the next begins.
            outputWriter.println(message);

            // Check if an error occurred during writing.
            if (outputWriter.checkError()) {
                System.err.println("[Connection] Error sending message to " + clientId);
                connected = false;
            }
        }
    }

    // ===== Receiving Data =====

    /**
     * Receives a text message from the client through the input stream.
     * 
     * This is the fundamental "receive" operation in socket communication.
     * readLine() blocks (waits) until the client sends a complete line of text.
     * 
     * Returns null if:
     *   - The client has disconnected (end of stream reached)
     *   - The connection has been closed
     *   - An I/O error occurs
     * 
     * @return The received message String, or null if the connection is closed
     * @throws IOException If an I/O error occurs while reading
     */
    public String receiveMessage() throws IOException {
        if (connected && inputReader != null) {
            // readLine() reads one line of text from the input stream.
            // It blocks until data is available, the end of the stream is
            // detected (client disconnected), or an exception is thrown.
            String message = inputReader.readLine();

            // If readLine() returns null, the client has disconnected.
            if (message == null) {
                connected = false;
            }

            return message;
        }
        return null;
    }

    // ===== Connection Management =====

    /**
     * Closes the connection safely, releasing all resources.
     * 
     * Proper resource management is critical in socket programming.
     * Failing to close sockets and streams leads to resource leaks,
     * which can exhaust the operating system's file descriptors and
     * prevent new connections.
     * 
     * We close in the order: streams first, then the socket.
     * Each close operation is wrapped in its own try-catch to ensure
     * that a failure in closing one resource does not prevent the
     * others from being closed.
     */
    public synchronized void close() {
        if (!connected) {
            return; // Already closed, nothing to do.
        }

        connected = false;
        System.out.println("[Connection] Closing connection for " + clientId);

        // Close the input stream (BufferedReader).
        try {
            if (inputReader != null) {
                inputReader.close();
            }
        } catch (IOException e) {
            System.err.println("[Connection] Error closing input stream for "
                + clientId + ": " + e.getMessage());
        }

        // Close the output stream (PrintWriter).
        // PrintWriter.close() does not throw IOException.
        if (outputWriter != null) {
            outputWriter.close();
        }

        // Close the socket itself.
        // This releases the underlying TCP connection and the port.
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("[Connection] Error closing socket for "
                + clientId + ": " + e.getMessage());
        }

        System.out.println("[Connection] Connection closed for " + clientId);
    }

    // ===== Getter Methods =====

    /**
     * Returns the unique client identifier for this connection.
     * @return The client ID string
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Returns the underlying Socket object.
     * @return The client Socket
     */
    public Socket getSocket() {
        return socket;
    }

    /**
     * Checks whether this connection is currently active.
     * @return true if the connection is open and active, false otherwise
     */
    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    /**
     * Returns the IP address of the connected client.
     * @return The client's IP address as a String
     */
    public String getClientAddress() {
        if (socket != null) {
            return socket.getInetAddress().getHostAddress();
        }
        return "unknown";
    }

    /**
     * Returns the port number of the connected client.
     * @return The client's port number
     */
    public int getClientPort() {
        if (socket != null) {
            return socket.getPort();
        }
        return -1;
    }
}
