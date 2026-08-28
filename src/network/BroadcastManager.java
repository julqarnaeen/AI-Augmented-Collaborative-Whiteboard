package network;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import com.google.gson.Gson;

/**
 * Centralized message routing, serialization, and dispatch layer for the
 * collaborative whiteboard using JSON.
 */
public class BroadcastManager {

    public interface BroadcastListener {
        void onRemoteAction(DrawingAction action);
        void onSystemEvent(String eventType, String eventData);
    }

    private final ClientConnection connection;
    private final String clientId;
    private volatile BroadcastListener listener;
    private volatile boolean receiving;
    private Thread receiveThread;
    private Thread senderThread;
    private final LinkedBlockingQueue<String> sendQueue;
    private final Gson gson;

    public BroadcastManager(ClientConnection connection, String clientId) {
        this.connection = connection;
        this.clientId = clientId;
        this.sendQueue = new LinkedBlockingQueue<>();
        this.receiving = false;
        this.gson = new Gson();

        System.out.println("[BroadcastManager] Created for client: " + clientId);
    }

    public void setListener(BroadcastListener listener) {
        this.listener = listener;
    }

    public void send(DrawingAction action) {
        if (action == null) return;

        String message = action.toMessage();
        if (message != null) {
            sendQueue.offer(message);
        }
    }

    public void sendRaw(String message) {
        if (message != null) {
            sendQueue.offer(message);
        }
    }

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
                    System.out.println("[BroadcastManager] Received raw JSON: " + message);
                    handleIncomingMessage(message);
                }
                System.out.println("[BroadcastManager] Connection stream ended.");
            } catch (IOException e) {
                if (receiving) {
                    System.err.println("[BroadcastManager] Connection error: " + e.getMessage());
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

    public void stopReceiving() {
        receiving = false;

        if (receiveThread != null && receiveThread.isAlive()) {
            receiveThread.interrupt();
            System.out.println("[BroadcastManager] Receiver thread interrupt requested.");
        }

        System.out.println("[BroadcastManager] Receiving stopped.");
    }

    public boolean isReceiving() {
        return receiving;
    }

    public void shutdown() {
        System.out.println("[BroadcastManager] Shutting down...");
        stopReceiving();

        if (senderThread != null && senderThread.isAlive()) {
            senderThread.interrupt();
        }

        sendQueue.clear();
        System.out.println("[BroadcastManager] Shutdown complete.");
    }

    private void handleIncomingMessage(String jsonMessage) {
        if (jsonMessage == null || jsonMessage.isEmpty()) {
            return;
        }

        BroadcastListener currentListener = listener;
        if (currentListener == null) {
            return;
        }

        try {
            NetworkMessage msg = gson.fromJson(jsonMessage, NetworkMessage.class);
            if (msg == null || msg.getType() == null) {
                return;
            }

            String type = msg.getType().toUpperCase();

            // Check if it's a system message
            if ("WELCOME".equals(type) || "USER_JOINED".equals(type) || 
                "USER_LEFT".equals(type) || "SERVER_SHUTDOWN".equals(type) || "ERROR".equals(type)) {
                
                String eventData = msg.getSenderId();
                if ("SERVER_SHUTDOWN".equals(type) || "ERROR".equals(type)) {
                    eventData = msg.getText();
                }
                final String finalData = eventData != null ? eventData : "";
                SwingUtilities.invokeLater(() -> currentListener.onSystemEvent(type, finalData));
            } else {
                // It's a remote drawing action
                DrawingAction action = DrawingAction.fromMessage(jsonMessage, msg.getSenderId());
                if (action != null) {
                    SwingUtilities.invokeLater(() -> currentListener.onRemoteAction(action));
                }
            }
        } catch (Exception e) {
            System.err.println("[BroadcastManager] Error handling incoming JSON: " + e.getMessage());
        }
    }
}
