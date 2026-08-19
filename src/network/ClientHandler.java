package network;

import java.io.IOException;
import java.net.Socket;

public class ClientHandler extends Thread {

    private Connection connection;

    private WhiteboardServer server;

    private Socket clientSocket;

    public ClientHandler(Socket socket, WhiteboardServer server, String clientId)
            throws IOException {

        this.clientSocket = socket;

        this.server = server;

        this.connection = new Connection(socket, clientId);

        this.setName("ClientHandler-" + clientId);

        System.out.println("[ClientHandler] Handler created for " + clientId);
    }

    @Override
    public void run() {
        System.out.println("[ClientHandler] Thread started for "
            + connection.getClientId()
            + " | Thread: " + Thread.currentThread().getName());

        connection.sendMessage("WELCOME:" + connection.getClientId());

        try {

            String receivedMessage;

            while ((receivedMessage = connection.receiveMessage()) != null) {

                System.out.println("[ClientHandler] Received from "
                    + connection.getClientId() + ": " + receivedMessage);

                handleMessage(receivedMessage);
            }

            System.out.println("[ClientHandler] " + connection.getClientId()
                + " disconnected (end of stream).");

        } catch (IOException e) {

            System.err.println("[ClientHandler] Connection error with "
                + connection.getClientId() + ": " + e.getMessage());

        } finally {

            server.removeClient(this);

            connection.close();

            System.out.println("[ClientHandler] Handler thread ended for "
                + connection.getClientId());
        }
    }

    private void handleMessage(String message) {

        if (message == null || message.trim().isEmpty()) {
            return;
        }

        String messageType;
        if (message.contains(":")) {
            messageType = message.substring(0, message.indexOf(":")).toUpperCase();
        } else {
            messageType = message.toUpperCase();
        }

        switch (messageType) {

            case "DISCONNECT":

                System.out.println("[ClientHandler] " + connection.getClientId()
                    + " requested disconnect.");

                server.broadcastMessage(
                    "USER_LEFT:" + connection.getClientId(), this);

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

                String broadcastMessage = "FROM:" + connection.getClientId()
                    + "|" + message;

                server.broadcastMessage(broadcastMessage, this);
                break;

            default:

                System.out.println("[ClientHandler] Unknown message type from "
                    + connection.getClientId() + ": " + message);

                connection.sendMessage("ERROR:Unknown message type: " + messageType);
                break;
        }
    }

    public void sendToClient(String message) {
        connection.sendMessage(message);
    }

    public Connection getConnection() {
        return connection;
    }

    public String getClientId() {
        return connection.getClientId();
    }

    public boolean isClientConnected() {
        return connection.isConnected();
    }
}
