package network;

import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import javax.swing.*;

public class WhiteboardClient {

    private static final String SERVER_HOST = "localhost";

    private static final int SERVER_PORT = 12345;

    private Connection connection;

    private JFrame frame;

    private WhiteboardPanel whiteboardPanel;

    private JLabel statusLabel;

    private volatile boolean running;

    private String clientId;

    public WhiteboardClient() {
        this.running = false;
        this.clientId = "Not connected";
    }

    private void createGUI() {

        frame = new JFrame("AI-Augmented Collaborative Whiteboard");

        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {

                disconnect();

                frame.dispose();

                System.exit(0);
            }
        });

        whiteboardPanel = new WhiteboardPanel();

        whiteboardPanel.setClient(this);

        JScrollPane scrollPane = new JScrollPane(whiteboardPanel);
        scrollPane.getViewport().setBackground(Color.WHITE);

        JPanel toolbar = createToolbar();

        statusLabel = new JLabel("  Status: Not connected");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        statusLabel.setForeground(new Color(148, 163, 184));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(new Color(15, 23, 42));
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(30, 41, 59)),
            BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));

        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(toolbar, BorderLayout.NORTH);
        frame.getContentPane().add(scrollPane, BorderLayout.CENTER);
        frame.getContentPane().add(statusLabel, BorderLayout.SOUTH);

        frame.pack();

        frame.setLocationRelativeTo(null);

        frame.setVisible(true);

        System.out.println("[Client] GUI created and visible.");
    }

    private void styleButton(AbstractButton btn) {
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setForeground(Color.WHITE);
        btn.setBackground(new Color(30, 41, 59));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!btn.isSelected()) {
                    btn.setBackground(new Color(51, 65, 85));
                }
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (!btn.isSelected()) {
                    btn.setBackground(new Color(30, 41, 59));
                }
            }
        });

        btn.addChangeListener(e -> {
            if (btn.isSelected()) {
                btn.setBackground(new Color(59, 130, 246));
            } else {
                btn.setBackground(new Color(30, 41, 59));
            }
        });
    }

    private JPanel createToolbar() {
        JPanel toolbar = new JPanel();
        toolbar.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 8));
        toolbar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(30, 41, 59)),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        toolbar.setBackground(new Color(15, 23, 42));

        JLabel colorLabel = new JLabel("Color: ");
        colorLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        colorLabel.setForeground(new Color(148, 163, 184));
        toolbar.add(colorLabel);

        Color[] colors = {Color.BLACK, Color.RED, Color.BLUE,
                          new Color(16, 185, 129), new Color(245, 158, 11)};
        String[] colorNames = {"Black", "Red", "Blue", "Green", "Orange"};

        for (int i = 0; i < colors.length; i++) {
            JButton colorBtn = new JButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    g2.setColor(new Color(15, 23, 42));
                    g2.fillRect(0, 0, getWidth(), getHeight());

                    g2.setColor(getBackground());
                    g2.fillOval(3, 3, getWidth() - 6, getHeight() - 6);

                    if (whiteboardPanel != null && whiteboardPanel.getDrawingColor().equals(getBackground())) {
                        g2.setColor(Color.WHITE);
                        g2.setStroke(new BasicStroke(2.0f));
                        g2.drawOval(1, 1, getWidth() - 3, getHeight() - 3);
                    }
                    g2.dispose();
                }
            };
            colorBtn.setPreferredSize(new Dimension(26, 26));
            colorBtn.setBackground(colors[i]);
            colorBtn.setToolTipText(colorNames[i]);
            colorBtn.setOpaque(false);
            colorBtn.setBorderPainted(false);
            colorBtn.setContentAreaFilled(false);
            colorBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

            final Color selectedColor = colors[i];
            colorBtn.addActionListener(e -> {
                whiteboardPanel.setDrawingColor(selectedColor);
                updateStatus("Color changed to " + selectedColor);
                toolbar.repaint();
            });
            toolbar.add(colorBtn);
        }

        toolbar.add(new JLabel("  |  "));

        JLabel widthLabel = new JLabel("Width: ");
        widthLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        widthLabel.setForeground(new Color(148, 163, 184));
        toolbar.add(widthLabel);

        SpinnerNumberModel widthModel = new SpinnerNumberModel(3, 1, 20, 1);
        JSpinner widthSpinner = new JSpinner(widthModel);
        widthSpinner.setPreferredSize(new Dimension(50, 28));
        widthSpinner.setBackground(Color.WHITE);
        widthSpinner.setForeground(new Color(15, 23, 42));
        widthSpinner.setBorder(BorderFactory.createLineBorder(new Color(51, 65, 85)));
        JComponent spinnerEditor = widthSpinner.getEditor();
        if (spinnerEditor instanceof JSpinner.DefaultEditor) {
            JFormattedTextField tf = ((JSpinner.DefaultEditor) spinnerEditor).getTextField();
            tf.setBackground(Color.WHITE);
            tf.setForeground(new Color(15, 23, 42));
        }
        widthSpinner.addChangeListener(e -> {
            int width = (int) widthSpinner.getValue();
            whiteboardPanel.setStrokeWidth(width);
        });
        toolbar.add(widthSpinner);

        toolbar.add(new JLabel("  |  "));

        JToggleButton drawBtn = new JToggleButton("Draw", true);
        JToggleButton textBtn = new JToggleButton("Text", false);
        styleButton(drawBtn);
        styleButton(textBtn);
        drawBtn.setBackground(new Color(59, 130, 246));

        drawBtn.addActionListener(e -> {
            drawBtn.setSelected(true);
            textBtn.setSelected(false);
            whiteboardPanel.setDrawingMode(WhiteboardPanel.Mode.FREEHAND);
            updateStatus("Freehand drawing mode active");
        });

        textBtn.addActionListener(e -> {
            textBtn.setSelected(true);
            drawBtn.setSelected(false);
            whiteboardPanel.setDrawingMode(WhiteboardPanel.Mode.TEXT);
            updateStatus("Text input mode active. Click on canvas to type.");
        });

        toolbar.add(drawBtn);
        toolbar.add(textBtn);

        JLabel sizeLabel = new JLabel("  Size: ");
        sizeLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        sizeLabel.setForeground(new Color(148, 163, 184));
        toolbar.add(sizeLabel);

        String[] fontSizes = {"Small (14px)", "Medium (20px)", "Large (32px)", "Huge (48px)"};
        int[] fontSizeVals = {14, 20, 32, 48};
        JComboBox<String> sizeCombo = new JComboBox<>(fontSizes);
        sizeCombo.setFont(new Font("Segoe UI", Font.BOLD, 12));
        sizeCombo.setBackground(Color.WHITE);
        sizeCombo.setForeground(new Color(15, 23, 42));
        sizeCombo.setSelectedIndex(1);
        sizeCombo.setBorder(BorderFactory.createLineBorder(new Color(51, 65, 85)));
        sizeCombo.addActionListener(e -> {
            int idx = sizeCombo.getSelectedIndex();
            if (idx >= 0 && idx < fontSizeVals.length) {
                int size = fontSizeVals[idx];
                whiteboardPanel.setCurrentFontSize(size);
                updateStatus("Font size changed to " + size + "px");
            }
        });
        toolbar.add(sizeCombo);

        toolbar.add(new JLabel("  |  "));

        JButton eraserBtn = new JButton("Eraser");
        styleButton(eraserBtn);
        eraserBtn.setToolTipText("Draw in white to erase");
        eraserBtn.addActionListener(e -> {
            whiteboardPanel.setDrawingColor(Color.WHITE);
            updateStatus("Eraser selected");
            toolbar.repaint();
        });
        toolbar.add(eraserBtn);

        JButton clearBtn = new JButton("Clear All");
        styleButton(clearBtn);
        clearBtn.setBackground(new Color(239, 68, 68));
        clearBtn.setToolTipText("Clear the entire canvas");
        clearBtn.addActionListener(e -> {
            whiteboardPanel.clearCanvas();
            sendMessage("CLEAR_CANVAS:");
            updateStatus("Canvas cleared");
        });
        toolbar.add(clearBtn);

        JButton undoBtn = new JButton("Undo");
        styleButton(undoBtn);
        undoBtn.setBackground(new Color(245, 158, 11));
        undoBtn.setToolTipText("Undo last stroke or text element");
        undoBtn.addActionListener(e -> {
            whiteboardPanel.undoLastAction();
            updateStatus("Undo action performed");
        });
        toolbar.add(undoBtn);

        toolbar.add(new JLabel("  |  "));

        JCheckBox normalizeBox = new JCheckBox("Auto-Normalize");
        normalizeBox.setToolTipText("Automatically smooth hand-drawn circles, rectangles, and lines");
        normalizeBox.setBackground(new Color(15, 23, 42));
        normalizeBox.setForeground(new Color(148, 163, 184));
        normalizeBox.setFont(new Font("Segoe UI", Font.BOLD, 12));
        normalizeBox.setCursor(new Cursor(Cursor.HAND_CURSOR));
        normalizeBox.addActionListener(e -> {
            boolean enabled = normalizeBox.isSelected();
            whiteboardPanel.setAutoNormalize(enabled);
            updateStatus("Shape auto-normalization: " + (enabled ? "ENABLED" : "DISABLED"));
        });
        toolbar.add(normalizeBox);

        JCheckBox gridBox = new JCheckBox("Grid", true);
        gridBox.setToolTipText("Toggle background dotted grid");
        gridBox.setBackground(new Color(15, 23, 42));
        gridBox.setForeground(new Color(148, 163, 184));
        gridBox.setFont(new Font("Segoe UI", Font.BOLD, 12));
        gridBox.setCursor(new Cursor(Cursor.HAND_CURSOR));
        gridBox.addActionListener(e -> {
            boolean enabled = gridBox.isSelected();
            whiteboardPanel.setShowGrid(enabled);
            updateStatus("Canvas grid: " + (enabled ? "VISIBLE" : "HIDDEN"));
        });
        toolbar.add(gridBox);

        JButton blockSlangBtn = new JButton("Block Slang");
        styleButton(blockSlangBtn);
        blockSlangBtn.setBackground(new Color(99, 102, 241));
        blockSlangBtn.setToolTipText("Block a new slang word dynamically");
        blockSlangBtn.addActionListener(e -> {
            String word = WhiteboardPanel.showCustomInputDialog(frame, "Block Slang", "Enter custom slang word to block:");
            if (word != null && !word.trim().isEmpty()) {
                String targetWord = word.trim().toLowerCase();
                ContentModerator.addBlockedWord(targetWord);
                sendMessage("BLOCK_SLANG:" + targetWord);
                updateStatus("Custom slang blocked: " + targetWord);
            }
        });
        toolbar.add(blockSlangBtn);

        toolbar.add(new JLabel("  |  "));

        JButton connectBtn = new JButton("Connect");
        styleButton(connectBtn);
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

    private void connectToServer() {

        new Thread(() -> {
            try {
                System.out.println("[Client] Connecting to " + SERVER_HOST
                    + ":" + SERVER_PORT + "...");

                java.net.Socket socket = new java.net.Socket(SERVER_HOST, SERVER_PORT);

                connection = new Connection(socket, "local-client");

                running = true;

                updateStatus("Connected to server at " + SERVER_HOST
                    + ":" + SERVER_PORT);
                System.out.println("[Client] Connected successfully!");

                receiveMessages();

            } catch (IOException e) {
                System.err.println("[Client] Connection failed: " + e.getMessage());
                updateStatus("Connection failed: " + e.getMessage());

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

    private void receiveMessages() {
        try {
            String message;

            while (running && (message = connection.receiveMessage()) != null) {

                System.out.println("[Client] Received: " + message);

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

    private void handleServerMessage(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }

        if (message.startsWith("FROM:")) {
            handleBroadcastMessage(message);
            return;
        }

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

    private void handleBroadcastMessage(String message) {
        try {

            int pipeIndex = message.indexOf("|");
            if (pipeIndex == -1) return;

            String actionPart = message.substring(pipeIndex + 1);

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

                    String[] coords = actionData.split(",");
                    if (coords.length >= 4) {
                        int x1 = Integer.parseInt(coords[0].trim());
                        int y1 = Integer.parseInt(coords[1].trim());
                        int x2 = Integer.parseInt(coords[2].trim());
                        int y2 = Integer.parseInt(coords[3].trim());
                        Color col = Color.BLACK;
                        int w = 3;
                        if (coords.length >= 6) {
                            col = new Color(Integer.parseInt(coords[4].trim()));
                            w = Integer.parseInt(coords[5].trim());
                        }
                        whiteboardPanel.addRemoteLine(x1, y1, x2, y2, col, w);
                    }
                    break;

                case "DRAW_RECT":

                    String[] rectData = actionData.split(",");
                    if (rectData.length >= 6) {
                        int x = Integer.parseInt(rectData[0].trim());
                        int y = Integer.parseInt(rectData[1].trim());
                        int w = Integer.parseInt(rectData[2].trim());
                        int h = Integer.parseInt(rectData[3].trim());
                        Color col = new Color(Integer.parseInt(rectData[4].trim()));
                        int wSize = Integer.parseInt(rectData[5].trim());
                        whiteboardPanel.addRemoteShape(WhiteboardPanel.Stroke.ShapeType.RECTANGLE, x, y, w, h, col, wSize);
                    }
                    break;

                case "DRAW_CIRCLE":

                    String[] circleData = actionData.split(",");
                    if (circleData.length >= 6) {
                        int x = Integer.parseInt(circleData[0].trim());
                        int y = Integer.parseInt(circleData[1].trim());
                        int w = Integer.parseInt(circleData[2].trim());
                        int h = Integer.parseInt(circleData[3].trim());
                        Color col = new Color(Integer.parseInt(circleData[4].trim()));
                        int wSize = Integer.parseInt(circleData[5].trim());
                        whiteboardPanel.addRemoteShape(WhiteboardPanel.Stroke.ShapeType.CIRCLE, x, y, w, h, col, wSize);
                    }
                    break;

                case "DRAW_TRI":

                    String[] triData = actionData.split(",");
                    if (triData.length >= 6) {
                        int x = Integer.parseInt(triData[0].trim());
                        int y = Integer.parseInt(triData[1].trim());
                        int w = Integer.parseInt(triData[2].trim());
                        int h = Integer.parseInt(triData[3].trim());
                        Color col = new Color(Integer.parseInt(triData[4].trim()));
                        int wSize = Integer.parseInt(triData[5].trim());
                        whiteboardPanel.addRemoteShape(WhiteboardPanel.Stroke.ShapeType.TRIANGLE, x, y, w, h, col, wSize);
                    }
                    break;

                case "TEXT":

                    String[] textParts = actionData.split(",", 5);
                    if (textParts.length >= 4) {
                        int x = Integer.parseInt(textParts[0].trim());
                        int y = Integer.parseInt(textParts[1].trim());
                        Color col = new Color(Integer.parseInt(textParts[2].trim()));
                        int fontSize = 20;
                        String content;
                        if (textParts.length == 5) {
                            fontSize = Integer.parseInt(textParts[3].trim());
                            content = textParts[4];
                        } else {
                            content = textParts[3];
                        }
                        whiteboardPanel.addRemoteText(content, x, y, col, fontSize);
                    }
                    break;

                case "MOVE_TEXT":

                    String[] moveData = actionData.split(",");
                    if (moveData.length >= 4) {
                        int oldX = Integer.parseInt(moveData[0].trim());
                        int oldY = Integer.parseInt(moveData[1].trim());
                        int newX = Integer.parseInt(moveData[2].trim());
                        int newY = Integer.parseInt(moveData[3].trim());
                        whiteboardPanel.moveRemoteText(oldX, oldY, newX, newY);
                    }
                    break;

                case "BLOCK_SLANG":

                    ContentModerator.addBlockedWord(actionData);
                    updateStatus("Dynamic slang blocked: " + actionData);
                    break;

                case "UNDO":
                    whiteboardPanel.undoRemoteAction();
                    updateStatus("Undo action performed by remote user");
                    break;

                case "DRAW_START":

                    break;

                case "CLEAR_CANVAS":

                    whiteboardPanel.clearCanvas();
                    updateStatus("Canvas cleared by remote user");
                    break;

                case "DRAW_END":

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

    public void sendMessage(String message) {
        if (connection != null && running) {
            connection.sendMessage(message);
        }
    }

    private void disconnect() {
        if (connection != null && running) {
            System.out.println("[Client] Disconnecting...");
            running = false;

            connection.sendMessage("DISCONNECT:");

            connection.close();
            connection = null;

            updateStatus("Disconnected");
            System.out.println("[Client] Disconnected from server.");
        }
    }

    private void updateStatus(String message) {
        if (statusLabel != null) {

            if (SwingUtilities.isEventDispatchThread()) {
                statusLabel.setText("  Status: " + message);
            } else {
                SwingUtilities.invokeLater(() ->
                    statusLabel.setText("  Status: " + message));
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("[Main] Starting Whiteboard Client...");

        SwingUtilities.invokeLater(() -> {

            try {
                UIManager.setLookAndFeel(
                    UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                System.err.println("[Client] Could not set look and feel: "
                    + e.getMessage());
            }

            WhiteboardClient client = new WhiteboardClient();
            client.createGUI();

            System.out.println("[Main] Whiteboard Client is ready.");
            System.out.println("[Main] Click 'Connect' to connect to the server.");
        });
    }
}
