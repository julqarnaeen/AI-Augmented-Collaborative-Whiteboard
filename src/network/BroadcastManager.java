package network;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;

/**
 * Centralized message routing, serialization, and dispatch layer for the
 * collaborative whiteboard.
 *
 * <p>{@code BroadcastManager} is the single gateway for all outbound and
 * inbound whiteboard messages.  It serializes {@link DrawingAction} objects
 * into the wire protocol via {@link DrawingAction#toMessage()}, and
 * deserializes incoming protocol strings via
 * {@link DrawingAction#fromMessage(String, String)}.
 *
 * <p>Outbound messages are enqueued into a {@link LinkedBlockingQueue} and
 * flushed by a dedicated sender thread, keeping {@code send()} calls off
 * the EDT.  Inbound messages are read on a background receiver thread and
 * dispatched to a {@link BroadcastListener} on the EDT.
 */
public class BroadcastManager {

    // ---------------------------------------------------------------- listener

    /**
     * Callback interface for receiving remote actions and system events.
     * All methods are invoked on the Swing Event Dispatch Thread.
     */
    public interface BroadcastListener {

        /** Called when a remote drawing action is received and parsed. */
        void onRemoteAction(DrawingAction action);

        /**
         * Called when a system event is received.
         *
         * @param eventType one of WELCOME, USER_JOINED, USER_LEFT,
         *                  SERVER_SHUTDOWN, ERROR, or DISCONNECTED
         * @param eventData the payload after the colon, or empty string
         */
        void onSystemEvent(String eventType, String eventData);
    }

    // ----------------------------------------------------------------- fields

    private final Connection connection;
    private final String clientId;
    private volatile BroadcastListener listener;
    private volatile boolean receiving;
    private Thread receiveThread;
    private Thread senderThread;
    private final LinkedBlockingQueue<String> sendQueue;

    // ------------------------------------------------------------- constructor

    /**
     * Creates a new BroadcastManager.
     *
     * @param connection the TCP connection to use for sending/receiving
     * @param clientId   the local client's identifier
     */
    public BroadcastManager(Connection connection, String clientId) {
        this.connection = connection;
        this.clientId = clientId;
        this.sendQueue = new LinkedBlockingQueue<>();
        this.receiving = false;

        System.out.println("[BroadcastManager] Created for client: " + clientId);
    }

    // -------------------------------------------------------------- listener

    /**
     * Registers the callback that receives remote actions and system events.
     *
     * @param listener the listener to register (may be null to unregister)
     */
    public void setListener(BroadcastListener listener) {
        this.listener = listener;
    }

    // --------------------------------------------------------- sending

    /**
     * Serializes and enqueues a {@link DrawingAction} for sending.
     * Safe to call from any thread, including the EDT.
     *
     * @param action the action to send
     */
    public void send(DrawingAction action) {
        if (action == null) return;

        String message = action.toMessage();
        if (message != null) {
            sendQueue.offer(message);
        }
    }

    /**
     * Enqueues a pre-formatted protocol string for sending.
     * Used for messages like {@code DISCONNECT:} that don't map to a
     * {@link DrawingAction}.
     *
     * @param message the raw message string to send
     */
    public void sendRaw(String message) {
        if (message != null) {
            sendQueue.offer(message);
        }
    }

    // --------------------------------------------------------- receiving

    /**
     * Spawns background threads for sending and receiving messages.
     * The receiver reads from the connection and dispatches parsed actions
     * to the registered {@link BroadcastListener} on the EDT.
     */
    public void startReceiving() {
        if (receiving) {
            System.out.println("[BroadcastManager] Already receiving.");
            return;
        }

        receiving = true;

        // --- Sender thread ---
        senderThread = new Thread(() -> {
            System.out.println("[BroadcastManager] Sender thread started.");
            try {
                while (receiving || !sendQueue.isEmpty()) {
                    String message = sendQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (message != null) {
                        connection.sendMessage(message);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("[BroadcastManager] Sender thread interrupted.");
            }
            System.out.println("[BroadcastManager] Sender thread stopped.");
        }, "BroadcastManager-Sender");
        senderThread.setDaemon(true);
        senderThread.start();

        // --- Receiver thread ---
        receiveThread = new Thread(() -> {
            System.out.println("[BroadcastManager] Receiver thread started.");
            try {
                String message;
                while (receiving && (message = connection.receiveMessage()) != null) {
                    System.out.println("[BroadcastManager] Received: " + message);
                    handleIncomingMessage(message);
                }
                System.out.println("[BroadcastManager] Connection stream ended.");
            } catch (IOException e) {
                if (receiving) {
                    System.err.println("[BroadcastManager] Connection error: "
                        + e.getMessage());
                }
            } finally {
                receiving = false;
                BroadcastListener currentListener = listener;
                if (currentListener != null) {
                    SwingUtilities.invokeLater(() ->
                        currentListener.onSystemEvent("DISCONNECTED", ""));
                }
                System.out.println("[BroadcastManager] Receiver thread stopped.");
            }
        }, "BroadcastManager-Receiver");
        receiveThread.setDaemon(true);
        receiveThread.start();

        System.out.println("[BroadcastManager] Receiving started.");
    }

    /**
     * Stops the receive loop and interrupts the receiver thread.
     */
    public void stopReceiving() {
        receiving = false;

        if (receiveThread != null && receiveThread.isAlive()) {
            receiveThread.interrupt();
            System.out.println("[BroadcastManager] Receiver thread interrupt requested.");
        }

        System.out.println("[BroadcastManager] Receiving stopped.");
    }

    /**
     * Returns whether the receive loop is currently active.
     */
    public boolean isReceiving() {
        return receiving;
    }

    /**
     * Shuts down both the sender and receiver threads and clears the
     * outbound message queue.
     */
    public void shutdown() {
        System.out.println("[BroadcastManager] Shutting down...");
        stopReceiving();

        if (senderThread != null && senderThread.isAlive()) {
            senderThread.interrupt();
        }

        sendQueue.clear();
        System.out.println("[BroadcastManager] Shutdown complete.");
    }

    // ------------------------------------------------- internal message handling

    /**
     * Routes an incoming message to the appropriate handler based on
     * whether it is a broadcast (FROM:...) or a system message.
     */
    private void handleIncomingMessage(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }

        BroadcastListener currentListener = listener;
        if (currentListener == null) {
            return;
        }

        if (message.startsWith("FROM:")) {
            handleBroadcast(message, currentListener);
        } else {
            handleSystemMessage(message, currentListener);
        }
    }

    /**
     * Parses a broadcast message of the form {@code FROM:senderId|ACTION:data},
     * converts the action part into a {@link DrawingAction}, and dispatches
     * it to the listener on the EDT.
     */
    private void handleBroadcast(String message, BroadcastListener currentListener) {
        int pipeIndex = message.indexOf("|");
        if (pipeIndex == -1) {
            System.err.println("[BroadcastManager] Malformed broadcast (no pipe): " + message);
            return;
        }

        // Extract sender ID: between "FROM:" and "|"
        String senderId = message.substring(5, pipeIndex);

        // Extract action part: everything after "|"
        String actionPart = message.substring(pipeIndex + 1);

        DrawingAction action = DrawingAction.fromMessage(actionPart, senderId);
        if (action != null) {
            SwingUtilities.invokeLater(() -> currentListener.onRemoteAction(action));
        }
    }

    /**
     * Parses a system message of the form {@code TYPE:data} and dispatches
     * it to the listener on the EDT.
     */
    private void handleSystemMessage(String message, BroadcastListener currentListener) {
        String eventType;
        String eventData = "";

        if (message.contains(":")) {
            int colonIndex = message.indexOf(":");
            eventType = message.substring(0, colonIndex).toUpperCase();
            eventData = message.substring(colonIndex + 1);
        } else {
            eventType = message.toUpperCase();
        }

        final String type = eventType;
        final String data = eventData;
        SwingUtilities.invokeLater(() -> currentListener.onSystemEvent(type, data));
    }
}
