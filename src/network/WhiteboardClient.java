package network;

import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import javax.swing.*;

/**
 * WhiteboardClient.java
 * =====================
 *
 * This is the CLIENT application for the Real-Time AI-Augmented
 * Collaborative Whiteboard System.
 *
 * This class is responsible for:
 *   1. Creating the Swing GUI (JFrame + WhiteboardPanel + toolbar)
 *   2. Connecting to the WhiteboardServer via Socket (Lab Sheet 8 pattern)
 *   3. Sending local drawing actions to the server
 *   4. Receiving drawing actions from other clients (via the server)
 *   5. Rendering remote drawings on the local WhiteboardPanel
 *
 * *** LAB SHEET 8 CLIENT-SIDE PATTERN ***
 * The Lab Sheet 8 client:
 *   - Creates a Socket using the server host/IP and port
 *   - Obtains input and output streams from the socket
 *   - Communicates with the server through the socket
 *   - Continues communication until the client exits
 *   - Closes the connection properly
 *
 * This implementation follows the same pattern, using the Connection class
 * to wrap the Socket and streams.
 *
 * @author Green University of Bangladesh - CSE Networking Lab Project
 */
public class WhiteboardClient {

    // ===== Constants =====

    // Server address — "localhost" means the server is on the same machine.
    // For connecting to a remote server, change this to the server's IP.
    private static final String SERVER_HOST = "localhost";

    // Must match the port in WhiteboardServer.java.
    private static final int SERVER_PORT = 12345;

    // ===== Instance Variables =====

    // The Connection object wrapping our Socket + streams to the server.
    // This follows the Lab Sheet 8 pattern:
    //   Socket → getInputStream() → BufferedReader
    //   Socket → getOutputStream() → PrintWriter
    private Connection connection;

    // The main Swing window (JFrame) for the whiteboard application.
    private JFrame frame;

    // The whiteboard drawing panel where users draw with the mouse.
    private WhiteboardPanel whiteboardPanel;

    // The status bar label at the bottom of the window.
    private JLabel statusLabel;

    // Flag to control the message-receiving loop.
    private volatile boolean running;

    // The client's ID assigned by the server (e.g., "Client-1").
    private String clientId;

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    /**
     * Creates the WhiteboardClient, initializes the GUI, and prepares
     * for connection to the server.
     *
     * The GUI is created using SwingUtilities.invokeLater() to ensure
     * all Swing components are created on the Event Dispatch Thread (EDT).
     * This is a Swing best practice — creating GUI components on a
     * non-EDT thread can cause subtle bugs and race conditions.
     */
    public WhiteboardClient() {
        this.running = false;
        this.clientId = "Not connected";
    }

    // =========================================================================
    // GUI CREATION
    // =========================================================================

    /**
     * Builds the complete Swing GUI for the whiteboard application.
     *
     * Layout:
     * ┌──────────────────────────────────────────────┐
     * │  Toolbar (color buttons, stroke width, clear) │
     * ├──────────────────────────────────────────────┤
     * │                                              │
     * │           WhiteboardPanel                    │
     * │        (Drawing Canvas - CENTER)             │
     * │                                              │
     * ├──────────────────────────────────────────────┤
     * │  Status Bar (connection info)                │
     * └──────────────────────────────────────────────┘
     *
     * JFrame uses BorderLayout by default:
     *   - NORTH:  Toolbar
     *   - CENTER: WhiteboardPanel (takes all remaining space)
     *   - SOUTH:  Status bar
     */
    private void createGUI() {
        // ===== CREATE THE MAIN JFRAME =====
        // JFrame is the top-level Swing window with a title bar, borders,
        // and close/minimize/maximize buttons.
        frame = new JFrame("AI-Augmented Collaborative Whiteboard");

        // Set the default close operation. We use DO_NOTHING_ON_CLOSE
        // and handle the close event manually so we can disconnect
        // from the server gracefully before exiting.
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        // Add a window listener to handle the close button (X).
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Disconnect from the server before closing.
                disconnect();
                // Dispose the frame (release native resources).
                frame.dispose();
                // Exit the application.
                System.exit(0);
            }
        });

        // ===== CREATE THE WHITEBOARD PANEL =====
        // WhiteboardPanel extends JPanel and provides the drawing canvas.
        // It handles mouse input, stores strokes, and renders them.
        whiteboardPanel = new WhiteboardPanel();

        // Connect the panel to this client so it can send drawing data
        // to the server when the user draws locally.
        whiteboardPanel.setClient(this);

        // Wrap the panel in a JScrollPane so the user can scroll
        // if the drawing area is larger than the visible window.
        JScrollPane scrollPane = new JScrollPane(whiteboardPanel);
        scrollPane.getViewport().setBackground(Color.WHITE);

        // ===== CREATE THE TOOLBAR =====
        JPanel toolbar = createToolbar();

        // ===== CREATE THE STATUS BAR =====
        statusLabel = new JLabel("  Status: Not connected");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));

        // ===== ASSEMBLE THE LAYOUT =====
        // BorderLayout is the default layout for JFrame's content pane.
        // NORTH = toolbar, CENTER = drawing canvas, SOUTH = status bar.
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(toolbar, BorderLayout.NORTH);
        frame.getContentPane().add(scrollPane, BorderLayout.CENTER);
        frame.getContentPane().add(statusLabel, BorderLayout.SOUTH);

        // ===== SIZE AND POSITION =====
        // pack() calculates the preferred size of the frame based on its
        // components. The WhiteboardPanel's preferred size (900x600) drives
        // the overall window size.
        frame.pack();

        // Center the window on the screen.
        frame.setLocationRelativeTo(null);

        // ===== MAKE THE FRAME VISIBLE =====
        // THIS IS CRITICAL. Without setVisible(true), the JFrame exists
        // in memory but is NOT shown on screen. This is one of the most
        // common reasons a Swing window "doesn't appear."
        frame.setVisible(true);

        System.out.println("[Client] GUI created and visible.");
    }

    /**
     * Creates the toolbar panel with drawing controls.
     *
     * The toolbar provides:
     *   - Color selection buttons (Black, Red, Blue, Green, Orange)
     *   - Stroke width control (spinner)
     *   - Eraser button (draws in white = "erasing")
     *   - Clear canvas button
     *   - Connect/Disconnect button
     *
     * @return The configured toolbar JPanel
     */
    private JPanel createToolbar() {
        JPanel toolbar = new JPanel();
        toolbar.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        toolbar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        toolbar.setBackground(new Color(245, 245, 245));

        // --- Color Label ---
        toolbar.add(new JLabel("Color:"));

        // --- Color Buttons ---
        // Each button sets the drawing color on the WhiteboardPanel.
        Color[] colors = {Color.BLACK, Color.RED, Color.BLUE,
                          new Color(0, 150, 0), new Color(255, 140, 0)};
        String[] colorNames = {"Black", "Red", "Blue", "Green", "Orange"};

        for (int i = 0; i < colors.length; i++) {
            JButton colorBtn = new JButton();
            colorBtn.setPreferredSize(new Dimension(28, 28));
            colorBtn.setBackground(colors[i]);
            colorBtn.setToolTipText(colorNames[i]);
            colorBtn.setOpaque(true);
            colorBtn.setBorderPainted(true);

            final Color selectedColor = colors[i];
            colorBtn.addActionListener(e -> {
                whiteboardPanel.setDrawingColor(selectedColor);
                updateStatus("Color changed to " + selectedColor);
            });
            toolbar.add(colorBtn);
        }

        // --- Separator ---
        toolbar.add(new JLabel("  |  "));

        // --- Stroke Width ---
        toolbar.add(new JLabel("Width:"));
        SpinnerNumberModel widthModel = new SpinnerNumberModel(3, 1, 20, 1);
        JSpinner widthSpinner = new JSpinner(widthModel);
        widthSpinner.setPreferredSize(new Dimension(50, 28));
        widthSpinner.addChangeListener(e -> {
            int width = (int) widthSpinner.getValue();
            whiteboardPanel.setStrokeWidth(width);
        });
        toolbar.add(widthSpinner);

        // --- Separator ---
        toolbar.add(new JLabel("  |  "));

        // --- Eraser Button ---
        JButton eraserBtn = new JButton("Eraser");
        eraserBtn.setToolTipText("Draw in white to erase");
        eraserBtn.addActionListener(e -> {
            whiteboardPanel.setDrawingColor(Color.WHITE);
            updateStatus("Eraser selected");
        });
        toolbar.add(eraserBtn);

        // --- Clear Button ---
        JButton clearBtn = new JButton("Clear All");
        clearBtn.setToolTipText("Clear the entire canvas");
        clearBtn.addActionListener(e -> {
            whiteboardPanel.clearCanvas();
            // Also send CLEAR_CANVAS to the server so other clients clear too.
            sendMessage("CLEAR_CANVAS:");
            updateStatus("Canvas cleared");
        });
        toolbar.add(clearBtn);

        // --- Separator ---
        toolbar.add(new JLabel("  |  "));

        // --- Connect Button ---
        JButton connectBtn = new JButton("Connect");
        connectBtn.setToolTipText("Connect to the whiteboard server");
        connectBtn.addActionListener(e -> {
            if (!running) {
                connectToServer();
                connectBtn.setText("Disconnect");
            } else {
                disconnect();
                connectBtn.setText("Connect");
            }
        });
        toolbar.add(connectBtn);

        return toolbar;
    }

    // =========================================================================
    // NETWORKING — Lab Sheet 8 Client-Side Pattern
    // =========================================================================

    /**
     * Connects to the WhiteboardServer.
     *
     * *** LAB SHEET 8 CLIENT-SIDE PATTERN ***
     * Step 1: Create a Socket using the server host and port.
     *         new Socket(host, port) establishes a TCP connection.
     * Step 2: Create input/output streams (done inside Connection).
     * Step 3: Start a separate thread to receive messages from the server.
     *
     * The Socket constructor is a BLOCKING call — it attempts to connect
     * to the server and waits until the connection is established or fails.
     */
    private void connectToServer() {
        // Run the connection attempt in a background thread so the GUI
        // doesn't freeze while connecting.
        new Thread(() -> {
            try {
                System.out.println("[Client] Connecting to " + SERVER_HOST
                    + ":" + SERVER_PORT + "...");

                // *** LAB SHEET 8: Create a Socket ***
                // This establishes a TCP connection to the server.
                // The Socket constructor sends a connection request to
                // the server's ServerSocket, which accept() picks up.
                java.net.Socket socket = new java.net.Socket(SERVER_HOST, SERVER_PORT);

                // *** LAB SHEET 8: Create input/output streams ***
                // The Connection object wraps the Socket and creates
                // BufferedReader (input) and PrintWriter (output).
                connection = new Connection(socket, "local-client");

                running = true;

                updateStatus("Connected to server at " + SERVER_HOST
                    + ":" + SERVER_PORT);
                System.out.println("[Client] Connected successfully!");

                // *** Start the message-receiving loop ***
                // This runs on this background thread, continuously
                // reading messages from the server.
                receiveMessages();

            } catch (IOException e) {
                System.err.println("[Client] Connection failed: " + e.getMessage());
                updateStatus("Connection failed: " + e.getMessage());

                // Show an error dialog to the user.
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(frame,
                        "Could not connect to server at "
                            + SERVER_HOST + ":" + SERVER_PORT
                            + "\n\nMake sure the server is running first."
                            + "\n\nError: " + e.getMessage(),
                        "Connection Error",
                        JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "ClientConnectionThread").start();
    }

    /**
     * Message-receiving loop — reads messages from the server continuously.
     *
     * *** LAB SHEET 8 PATTERN ***
     * The client continuously reads from the input stream until:
     *   - The server closes the connection (readLine returns null)
     *   - An IOException occurs (network error)
     *   - The client disconnects
     *
     * This runs on a background thread so the GUI remains responsive.
     * When a message is received, it is processed on the EDT using
     * SwingUtilities.invokeLater() to safely update the GUI.
     */
    private void receiveMessages() {
        try {
            String message;

            // *** LAB SHEET 8: Continuously read from the server ***
            // receiveMessage() calls readLine() which BLOCKS until data arrives.
            while (running && (message = connection.receiveMessage()) != null) {

                System.out.println("[Client] Received: " + message);

                // Process the message. We use a final variable for
                // the lambda capture.
                final String msg = message;
                SwingUtilities.invokeLater(() -> handleServerMessage(msg));
            }

            System.out.println("[Client] Server connection closed.");

        } catch (IOException e) {
            if (running) {
                System.err.println("[Client] Connection lost: " + e.getMessage());
                SwingUtilities.invokeLater(() ->
                    updateStatus("Connection lost: " + e.getMessage()));
            }

        } finally {
            running = false;
            SwingUtilities.invokeLater(() ->
                updateStatus("Disconnected from server"));
        }
    }

    /**
     * Processes a message received from the server.
     *
     * Message types received from the server:
     *   - WELCOME:clientId         — Server confirms our connection
     *   - USER_JOINED:clientId     — Another client joined
     *   - USER_LEFT:clientId       — Another client left
     *   - FROM:clientId|ACTION:data — Drawing action from another client
     *   - CLEAR_CANVAS             — Another client cleared the canvas
     *   - SERVER_SHUTDOWN:message   — Server is shutting down
     *   - ERROR:message            — Error from the server
     *
     * @param message The raw message string from the server
     */
    private void handleServerMessage(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }

        // Check if it's a broadcast message from another client.
        // Format: "FROM:Client-1|DRAW_LINE:x1,y1,x2,y2"
        if (message.startsWith("FROM:")) {
            handleBroadcastMessage(message);
            return;
        }

        // Extract the message type.
        String messageType;
        String messageData = "";
        if (message.contains(":")) {
            int colonIndex = message.indexOf(":");
            messageType = message.substring(0, colonIndex).toUpperCase();
            messageData = message.substring(colonIndex + 1);
        } else {
            messageType = message.toUpperCase();
        }

        switch (messageType) {
            case "WELCOME":
                // Server has confirmed our connection and assigned an ID.
                clientId = messageData;
                frame.setTitle("AI-Augmented Collaborative Whiteboard — " + clientId);
                updateStatus("Connected as " + clientId);
                System.out.println("[Client] Assigned ID: " + clientId);
                break;

            case "USER_JOINED":
                updateStatus("User joined: " + messageData);
                System.out.println("[Client] User joined: " + messageData);
                break;

            case "USER_LEFT":
                updateStatus("User left: " + messageData);
                System.out.println("[Client] User left: " + messageData);
                break;

            case "SERVER_SHUTDOWN":
                updateStatus("Server is shutting down: " + messageData);
                running = false;
                break;

            case "ERROR":
                System.err.println("[Client] Server error: " + messageData);
                updateStatus("Server error: " + messageData);
                break;

            default:
                System.out.println("[Client] Unknown message: " + message);
                break;
        }
    }

    /**
     * Handles a broadcast message containing drawing data from another client.
     *
     * Format: "FROM:Client-1|DRAW_LINE:x1,y1,x2,y2"
     *
     * This method:
     *   1. Extracts the sender ID and the action
     *   2. Parses the coordinates from the action data
     *   3. Renders the drawing on the local WhiteboardPanel
     *
     * @param message The broadcast message from the server
     */
    private void handleBroadcastMessage(String message) {
        try {
            // Split "FROM:Client-1|DRAW_LINE:x1,y1,x2,y2" into parts.
            int pipeIndex = message.indexOf("|");
            if (pipeIndex == -1) return;

            // Extract the action part after the pipe: "DRAW_LINE:x1,y1,x2,y2"
            String actionPart = message.substring(pipeIndex + 1);

            // Split the action into type and data.
            String actionType;
            String actionData = "";
            if (actionPart.contains(":")) {
                int colonIndex = actionPart.indexOf(":");
                actionType = actionPart.substring(0, colonIndex).toUpperCase();
                actionData = actionPart.substring(colonIndex + 1);
            } else {
                actionType = actionPart.toUpperCase();
            }

            switch (actionType) {
                case "DRAW_LINE":
                    // Parse "x1,y1,x2,y2"
                    String[] coords = actionData.split(",");
                    if (coords.length >= 4) {
                        int x1 = Integer.parseInt(coords[0].trim());
                        int y1 = Integer.parseInt(coords[1].trim());
                        int x2 = Integer.parseInt(coords[2].trim());
                        int y2 = Integer.parseInt(coords[3].trim());

                        // Add the remote line segment to the whiteboard panel.
                        // Uses default black color and width 3 for remote drawings.
                        whiteboardPanel.addRemoteLine(x1, y1, x2, y2,
                            Color.BLACK, 3);
                    }
                    break;

                case "DRAW_START":
                    // Parse "x,y,colorRGB,strokeWidth"
                    String[] startData = actionData.split(",");
                    if (startData.length >= 4) {
                        int colorRgb = Integer.parseInt(startData[2].trim());
                        int strokeW = Integer.parseInt(startData[3].trim());
                        // Store the remote user's color/width for subsequent lines.
                        // For now, DRAW_LINE messages handle individual segments.
                    }
                    break;

                case "CLEAR_CANVAS":
                    // Another client cleared their canvas — clear ours too.
                    whiteboardPanel.clearCanvas();
                    updateStatus("Canvas cleared by remote user");
                    break;

                case "DRAW_END":
                    // Stroke completed — no action needed for segment-based rendering.
                    break;

                default:
                    System.out.println("[Client] Unknown broadcast action: " + actionType);
                    break;
            }

        } catch (NumberFormatException e) {
            System.err.println("[Client] Error parsing broadcast message: "
                + e.getMessage());
        }
    }

    // =========================================================================
    // SEND MESSAGES
    // =========================================================================

    /**
     * Sends a message to the server through the Connection object.
     *
     * *** LAB SHEET 8 PATTERN ***
     * This follows the client-side sending concept:
     *   client → PrintWriter.println(message) → Socket → Server
     *
     * This method is called by WhiteboardPanel when the user draws,
     * and by toolbar buttons (e.g., Clear All).
     *
     * @param message The message to send to the server
     */
    public void sendMessage(String message) {
        if (connection != null && running) {
            connection.sendMessage(message);
        }
    }

    // =========================================================================
    // DISCONNECT
    // =========================================================================

    /**
     * Disconnects from the server gracefully.
     *
     * *** LAB SHEET 8 PATTERN ***
     * The client must close the connection properly when finished.
     * We send a DISCONNECT message to notify the server, then close
     * the Socket and streams.
     */
    private void disconnect() {
        if (connection != null && running) {
            System.out.println("[Client] Disconnecting...");
            running = false;

            // Send a DISCONNECT message so the server knows we're leaving.
            connection.sendMessage("DISCONNECT:");

            // Close the connection (Socket + streams).
            connection.close();
            connection = null;

            updateStatus("Disconnected");
            System.out.println("[Client] Disconnected from server.");
        }
    }

    // =========================================================================
    // STATUS BAR
    // =========================================================================

    /**
     * Updates the status bar at the bottom of the window.
     * Safely callable from any thread.
     *
     * @param message The status message to display
     */
    private void updateStatus(String message) {
        if (statusLabel != null) {
            // If we're already on the EDT, update directly.
            // Otherwise, schedule the update on the EDT.
            if (SwingUtilities.isEventDispatchThread()) {
                statusLabel.setText("  Status: " + message);
            } else {
                SwingUtilities.invokeLater(() ->
                    statusLabel.setText("  Status: " + message));
            }
        }
    }

    // =========================================================================
    // MAIN METHOD — Entry Point
    // =========================================================================

    /**
     * The main method — starting point of the client application.
     *
     * *** IMPORTANT SWING RULE ***
     * All Swing GUI creation must happen on the Event Dispatch Thread (EDT).
     * SwingUtilities.invokeLater() schedules the GUI creation code to run
     * on the EDT. Creating Swing components on any other thread can cause
     * race conditions, visual glitches, and hard-to-debug crashes.
     *
     * To run the client:
     *   1. First, start WhiteboardServer
     *   2. Then compile and run: java network.WhiteboardClient
     *   3. The whiteboard window will appear
     *   4. Click "Connect" to connect to the server
     *   5. Draw with the mouse
     *
     * @param args Command-line arguments (not used)
     */
    public static void main(String[] args) {
        System.out.println("[Main] Starting Whiteboard Client...");

        // *** SwingUtilities.invokeLater() ***
        // This is the CORRECT way to start a Swing application.
        // It ensures the GUI is created on the Event Dispatch Thread (EDT).
        // Without this, the GUI might not appear, or might appear with glitches.
        SwingUtilities.invokeLater(() -> {
            // Set the look and feel to the system default for a native appearance.
            try {
                UIManager.setLookAndFeel(
                    UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                System.err.println("[Client] Could not set look and feel: "
                    + e.getMessage());
            }

            // Create the client instance and build the GUI.
            WhiteboardClient client = new WhiteboardClient();
            client.createGUI();

            System.out.println("[Main] Whiteboard Client is ready.");
            System.out.println("[Main] Click 'Connect' to connect to the server.");
        });
    }
}
