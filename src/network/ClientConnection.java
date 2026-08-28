package network;

import java.io.*;
import java.net.Socket;

public class ClientConnection {

    private Socket socket;

    private BufferedReader inputReader;

    private PrintWriter outputWriter;

    private String clientId;

    private volatile boolean connected;

    public ClientConnection(Socket socket, String clientId) throws IOException {
        this.socket = socket;
        this.clientId = clientId;

        this.inputReader = new BufferedReader(
            new InputStreamReader(socket.getInputStream())
        );

        this.outputWriter = new PrintWriter(
            socket.getOutputStream(), true
        );

        this.connected = true;

        System.out.println("[ClientConnection] Connection established for " + clientId
            + " from " + socket.getInetAddress().getHostAddress()
            + ":" + socket.getPort());
    }

    public synchronized void sendMessage(String message) {
        if (connected && outputWriter != null) {

            outputWriter.println(message);

            if (outputWriter.checkError()) {
                System.err.println("[ClientConnection] Error sending message to " + clientId);
                connected = false;
            }
        }
    }

    public String receiveMessage() throws IOException {
        if (connected && inputReader != null) {

            String message = inputReader.readLine();

            if (message == null) {
                connected = false;
            }

            return message;
        }
        return null;
    }

    public synchronized void close() {
        if (!connected) {
            return;
        }

        connected = false;
        System.out.println("[ClientConnection] Closing connection for " + clientId);

        try {
            if (inputReader != null) {
                inputReader.close();
            }
        } catch (IOException e) {
            System.err.println("[ClientConnection] Error closing input stream for "
                + clientId + ": " + e.getMessage());
        }

        if (outputWriter != null) {
            outputWriter.close();
        }

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("[ClientConnection] Error closing socket for "
                + clientId + ": " + e.getMessage());
        }

        System.out.println("[ClientConnection] Connection closed for " + clientId);
    }

    public String getClientId() {
        return clientId;
    }

    public Socket getSocket() {
        return socket;
    }

    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    public String getClientAddress() {
        if (socket != null) {
            return socket.getInetAddress().getHostAddress();
        }
        return "unknown";
    }

    public int getClientPort() {
        if (socket != null) {
            return socket.getPort();
        }
        return -1;
    }
}
