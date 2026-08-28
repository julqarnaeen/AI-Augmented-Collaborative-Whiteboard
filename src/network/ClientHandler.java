package network;

import java.io.IOException;
import java.net.Socket;
import com.google.gson.Gson;

public class ClientHandler extends Thread {

    private ClientConnection connection;

    private WhiteboardServer server;

    private final Gson gson;

    public ClientHandler(Socket socket, WhiteboardServer server, String clientId)
            throws IOException {

        this.server = server;
        this.connection = new ClientConnection(socket, clientId);
        this.gson = new Gson();
        this.setName("ClientHandler-" + clientId);

        System.out.println("[ClientHandler] Handler created for " + clientId);
    }

    @Override
    public void run() {
        System.out.println("[ClientHandler] Thread started for "
            + connection.getClientId()
            + " | Thread: " + Thread.currentThread().getName());

        // Send WELCOME message as JSON
        NetworkMessage welcome = new NetworkMessage("WELCOME");
        welcome.setSenderId(connection.getClientId());
        connection.sendMessage(gson.toJson(welcome));

        // Stream all historical drawing actions from SQLite database
        java.util.List<String> history = DatabaseManager.getAllDrawings();
        for (String actionJson : history) {
            connection.sendMessage(actionJson);
        }

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

        try {
            NetworkMessage msg = gson.fromJson(message, NetworkMessage.class);
            if (msg == null || msg.getType() == null) {
                return;
            }

            String messageType = msg.getType().toUpperCase();

            if ("DISCONNECT".equals(messageType)) {
                System.out.println("[ClientHandler] " + connection.getClientId()
                    + " requested disconnect.");

                NetworkMessage leaveMsg = new NetworkMessage("USER_LEFT");
                leaveMsg.setSenderId(connection.getClientId());
                server.broadcastMessage(gson.toJson(leaveMsg), this);

                connection.close();
            } else if ("CHAT_MESSAGE".equals(messageType)) {
                // Moderate chat message using ContentModerator
                String rawText = msg.getText();
                String moderated = ContentModerator.moderateText(rawText);
                msg.setText(moderated);
                // Retain client-submitted username senderId
                server.broadcastMessage(gson.toJson(msg), null);
            } else if ("SAVE_BOARD".equals(messageType)) {
                String username = msg.getSenderId();
                String boardName = msg.getText();
                String jsonData = msg.getJsonData();
                DatabaseManager.saveBoard(username, boardName, jsonData);
                System.out.println("[Server] Saved board '" + boardName + "' for user '" + username + "'");
            } else if ("LOAD_BOARD".equals(messageType)) {
                String username = msg.getSenderId();
                String boardName = msg.getText();
                String boardData = DatabaseManager.loadBoard(username, boardName);
                if (boardData != null) {
                    DatabaseManager.clearDrawings();
                    String[] actions = gson.fromJson(boardData, String[].class);
                    for (String actionJson : actions) {
                        try {
                            NetworkMessage actionMsg = gson.fromJson(actionJson, NetworkMessage.class);
                            DatabaseManager.saveAction(actionMsg.getType(), actionJson);
                        } catch (Exception ex) {
                            System.err.println("[Server] Error restoring drawing: " + ex.getMessage());
                        }
                    }
                    NetworkMessage stateMsg = new NetworkMessage("LOAD_BOARD_STATE");
                    stateMsg.setJsonData(boardData);
                    server.broadcastMessage(gson.toJson(stateMsg), null);
                    System.out.println("[Server] Loaded board '" + boardName + "' for user '" + username + "' and synced all clients.");
                }
            } else if ("GET_BOARDS".equals(messageType)) {
                String username = msg.getSenderId();
                java.util.List<String> boards = DatabaseManager.getSavedBoards(username);
                NetworkMessage resp = new NetworkMessage("BOARD_LIST");
                resp.setJsonData(gson.toJson(boards));
                connection.sendMessage(gson.toJson(resp));
            } else if ("BLOCK_SLANG".equals(messageType)) {
                String targetWord = msg.getText().trim().toLowerCase();
                DatabaseManager.addBlockedSlang(targetWord);
                ContentModerator.addBlockedWord(targetWord);
                
                msg.setSenderId(connection.getClientId());
                server.broadcastMessage(gson.toJson(msg), this);
            } else {
                // Set sender ID
                msg.setSenderId(connection.getClientId());
                String jsonToSend = gson.toJson(msg);

                // Persist drawing action to SQLite
                if ("CLEAR_CANVAS".equals(messageType)) {
                    DatabaseManager.clearDrawings();
                } else if ("UNDO".equals(messageType)) {
                    DatabaseManager.removeLastAction();
                } else if (!"DRAW_START".equals(messageType) && !"DRAW_END".equals(messageType)) {
                    DatabaseManager.saveAction(messageType, jsonToSend);
                }

                // Broadcast JSON message
                server.broadcastMessage(jsonToSend, this);
            }
        } catch (Exception e) {
            System.err.println("[ClientHandler] Error parsing message: " + e.getMessage());
            NetworkMessage errMsg = new NetworkMessage("ERROR");
            errMsg.setText("Invalid JSON message format");
            connection.sendMessage(gson.toJson(errMsg));
        }
    }

    public void sendToClient(String message) {
        connection.sendMessage(message);
    }

    public ClientConnection getConnection() {
        return connection;
    }

    public String getClientId() {
        return connection.getClientId();
    }

    public boolean isClientConnected() {
        return connection.isConnected();
    }
}
