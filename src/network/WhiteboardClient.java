// Swing client application: builds the window and toolbar, talks to the server, and drives the canvas.
package network;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import javax.swing.*;
import com.google.gson.Gson;

public class WhiteboardClient {

    private static final String SERVER_HOST = "localhost";

    private static final int SERVER_PORT = 12345;

    private ClientConnection connection;

    private JFrame frame;

    private WhiteboardPanel whiteboardPanel;

    private JLabel statusLabel;

    private volatile boolean running;

    private String clientId;

    // Returns the name of the logged-in user.
    public String getClientId() {
        return clientId;
    }

    private final Gson gson;

    private static String loggedInUser = "Guest";

    private JButton connectBtn;
    private JTextPane chatPane;
    private javax.swing.text.html.HTMLDocument doc;
    private javax.swing.text.html.HTMLEditorKit kit;
    private JTextField chatInput;

    // Creates a client with its JSON codec ready.
    public WhiteboardClient() {
        this.running = false;
        this.clientId = "Not connected";
        this.gson = new Gson();
    }

    // Assembles the window: toolbar, canvas, chat panel, and status bar.
    private void createGUI() {

        frame = new JFrame("AI-Augmented Collaborative Whiteboard — " + loggedInUser);

        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        frame.addWindowListener(new WindowAdapter() {
            // Disconnects and exits when the window is closed.
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

        scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel toolbar = createToolbar();

        JPanel chatPanel = createChatPanel();

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, chatPanel);
        splitPane.setDividerLocation(900);
        splitPane.setResizeWeight(1.0);
        splitPane.setBorder(null);

        statusLabel = new JLabel("  Status: Not connected | User: " + loggedInUser);
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
        frame.getContentPane().add(splitPane, BorderLayout.CENTER);
        frame.getContentPane().add(statusLabel, BorderLayout.SOUTH);

        frame.setPreferredSize(new Dimension(1200, 800));
        frame.setMinimumSize(new Dimension(720, 520));
        frame.pack();

        frame.setLocationRelativeTo(null);

        frame.setVisible(true);

        System.out.println("[Client] GUI created and visible.");

        connectToServer();
    }

    // Applies the shared dark button styling and hover behaviour.
    private void styleButton(AbstractButton btn) {
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        final Color originalBackground = btn.getBackground() != null ? btn.getBackground() : new Color(30, 41, 59);
        final Color hoverBackground = new Color(
            Math.min(255, originalBackground.getRed() + 20),
            Math.min(255, originalBackground.getGreen() + 20),
            Math.min(255, originalBackground.getBlue() + 20)
        );

        btn.addMouseListener(new MouseAdapter() {
            // Lightens the button while the pointer is over it.
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!btn.isSelected()) {
                    btn.setBackground(hoverBackground);
                }
            }
            // Restores the button colour when the pointer leaves.
            @Override
            public void mouseExited(MouseEvent e) {
                if (!btn.isSelected()) {
                    btn.setBackground(originalBackground);
                }
            }
        });

        btn.addChangeListener(e -> {
            if (btn.isSelected()) {
                btn.setBackground(new Color(59, 130, 246));
            } else {
                btn.setBackground(originalBackground);
            }
        });
    }

    // Toolbar container that reflows its cards onto extra rows instead of clipping them.
    private static class WrapPanel extends JPanel {

        private final int hgap;
        private final int vgap;

        // Creates a wrapping container with the given gaps.
        WrapPanel(int hgap, int vgap) {
            super(null);
            this.hgap = hgap;
            this.vgap = vgap;
            addComponentListener(new ComponentAdapter() {
                // Re-runs layout whenever the toolbar width changes.
                @Override
                public void componentResized(ComponentEvent e) {
                    revalidate();
                }
            });
        }

        // Packs the child cards into rows that fit the available width.
        private java.util.List<java.util.List<Component>> rows(int available) {
            java.util.List<java.util.List<Component>> rows = new java.util.ArrayList<>();
            java.util.List<Component> row = new java.util.ArrayList<>();
            int rowWidth = 0;
            for (Component c : getComponents()) {
                if (!c.isVisible()) {
                    continue;
                }
                int width = c.getPreferredSize().width;
                int next = row.isEmpty() ? width : rowWidth + hgap + width;
                if (next > available && !row.isEmpty()) {
                    rows.add(row);
                    row = new java.util.ArrayList<>();
                    next = width;
                }
                row.add(c);
                rowWidth = next;
            }
            if (!row.isEmpty()) {
                rows.add(row);
            }
            return rows;
        }

        // Positions each row and stretches its cards to fill the full width.
        @Override
        public void doLayout() {
            Insets insets = getInsets();
            int available = getWidth() - insets.left - insets.right;
            if (available <= 0) {
                return;
            }
            int y = insets.top;
            for (java.util.List<Component> row : rows(available)) {
                int preferred = 0;
                int height = 0;
                for (Component c : row) {
                    preferred += c.getPreferredSize().width;
                    height = Math.max(height, c.getPreferredSize().height);
                }
                int extra = available - preferred - hgap * (row.size() - 1);
                int share = extra > 0 ? extra / row.size() : 0;
                int x = insets.left;
                for (int i = 0; i < row.size(); i++) {
                    Component c = row.get(i);
                    int width = c.getPreferredSize().width + share;
                    if (i == row.size() - 1) {
                        width = Math.max(width, insets.left + available - x);
                    }
                    c.setBounds(x, y, width, height);
                    x += width + hgap;
                }
                y += height + vgap;
            }
        }

        // Reports the height needed once the cards have wrapped.
        @Override
        public Dimension getPreferredSize() {
            Insets insets = getInsets();
            int available = getWidth() - insets.left - insets.right;
            if (available <= 0) {
                return super.getPreferredSize();
            }
            int height = insets.top + insets.bottom;
            java.util.List<java.util.List<Component>> rows = rows(available);
            for (java.util.List<Component> row : rows) {
                int rowHeight = 0;
                for (Component c : row) {
                    rowHeight = Math.max(rowHeight, c.getPreferredSize().height);
                }
                height += rowHeight + vgap;
            }
            return new Dimension(available, Math.max(height - vgap, 0) + insets.bottom);
        }
    }

    // Builds the toolbar cards: colours, tools, canvas actions, and board actions.
    private JPanel createToolbar() {
        JPanel mainToolbar = new WrapPanel(12, 4);
        mainToolbar.setBackground(new Color(15, 23, 42));
        mainToolbar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(30, 41, 59)),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));

        JPanel cardLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        cardLeft.setBackground(new Color(24, 32, 51));
        cardLeft.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(51, 65, 85), 1),
            BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));

        JLabel colorLabel = new JLabel("Color: ");
        colorLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        colorLabel.setForeground(new Color(148, 163, 184));
        cardLeft.add(colorLabel);

        Color[] colors = {Color.BLACK, Color.RED, Color.BLUE,
                          new Color(16, 185, 129), new Color(245, 158, 11)};
        String[] colorNames = {"Black", "Red", "Blue", "Green", "Orange"};

        for (int i = 0; i < colors.length; i++) {
            JButton colorBtn = new JButton() {
                // Draws the colour swatch as a filled circle, ringed when selected.
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    g2.setColor(new Color(24, 32, 51));
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
            colorBtn.setPreferredSize(new Dimension(24, 24));
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
                cardLeft.repaint();
            });
            cardLeft.add(colorBtn);
        }

        cardLeft.add(new JLabel(" | "));

        JLabel widthLabel = new JLabel("Width: ");
        widthLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        widthLabel.setForeground(new Color(148, 163, 184));
        cardLeft.add(widthLabel);

        SpinnerNumberModel widthModel = new SpinnerNumberModel(3, 1, 20, 1);
        JSpinner widthSpinner = new JSpinner(widthModel);
        widthSpinner.setPreferredSize(new Dimension(46, 26));
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
        cardLeft.add(widthSpinner);

        cardLeft.add(new JLabel(" | "));

        JToggleButton drawBtn = new JToggleButton("Draw", true);
        drawBtn.setBackground(new Color(30, 41, 59));
        JToggleButton textBtn = new JToggleButton("Text", false);
        textBtn.setBackground(new Color(30, 41, 59));
        styleButton(drawBtn);
        styleButton(textBtn);
        drawBtn.setPreferredSize(new Dimension(72, 28));
        textBtn.setPreferredSize(new Dimension(72, 28));

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

        cardLeft.add(drawBtn);
        cardLeft.add(textBtn);

        JLabel sizeLabel = new JLabel(" Size: ");
        sizeLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        sizeLabel.setForeground(new Color(148, 163, 184));
        cardLeft.add(sizeLabel);

        String[] fontSizes = {"14px", "20px", "32px", "48px"};
        int[] fontSizeVals = {14, 20, 32, 48};
        JComboBox<String> sizeCombo = new JComboBox<>(fontSizes);
        sizeCombo.setFont(new Font("Segoe UI", Font.BOLD, 12));
        sizeCombo.setBackground(Color.WHITE);
        sizeCombo.setForeground(new Color(15, 23, 42));
        sizeCombo.setSelectedIndex(1);
        sizeCombo.setBorder(BorderFactory.createLineBorder(new Color(51, 65, 85)));
        sizeCombo.setPreferredSize(new Dimension(65, 26));
        sizeCombo.addActionListener(e -> {
            int idx = sizeCombo.getSelectedIndex();
            if (idx >= 0 && idx < fontSizeVals.length) {
                int size = fontSizeVals[idx];
                whiteboardPanel.setCurrentFontSize(size);
                updateStatus("Font size changed to " + size + "px");
            }
        });
        cardLeft.add(sizeCombo);

        JPanel cardCenter = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        cardCenter.setBackground(new Color(24, 32, 51));
        cardCenter.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(51, 65, 85), 1),
            BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));

        JCheckBox normalizeBox = new JCheckBox("Snap");
        normalizeBox.setToolTipText("Automatically smooth circles, rectangles, and lines");
        normalizeBox.setBackground(new Color(24, 32, 51));
        normalizeBox.setForeground(new Color(148, 163, 184));
        normalizeBox.setFont(new Font("Segoe UI", Font.BOLD, 11));
        normalizeBox.setCursor(new Cursor(Cursor.HAND_CURSOR));
        normalizeBox.addActionListener(e -> {
            boolean enabled = normalizeBox.isSelected();
            whiteboardPanel.setAutoNormalize(enabled);
            updateStatus("Shape snapping: " + (enabled ? "ENABLED" : "DISABLED"));
        });
        cardCenter.add(normalizeBox);

        JCheckBox gridBox = new JCheckBox("Grid", true);
        gridBox.setToolTipText("Toggle background dotted grid");
        gridBox.setBackground(new Color(24, 32, 51));
        gridBox.setForeground(new Color(148, 163, 184));
        gridBox.setFont(new Font("Segoe UI", Font.BOLD, 11));
        gridBox.setCursor(new Cursor(Cursor.HAND_CURSOR));
        gridBox.addActionListener(e -> {
            boolean enabled = gridBox.isSelected();
            whiteboardPanel.setShowGrid(enabled);
            updateStatus("Canvas grid: " + (enabled ? "VISIBLE" : "HIDDEN"));
        });
        cardCenter.add(gridBox);

        cardCenter.add(new JLabel(" | "));

        JButton eraserBtn = new JButton("Eraser");
        eraserBtn.setBackground(new Color(30, 41, 59));
        styleButton(eraserBtn);
        eraserBtn.setToolTipText("Draw in white to erase");
        eraserBtn.addActionListener(e -> {
            whiteboardPanel.setDrawingColor(Color.WHITE);
            updateStatus("Eraser selected");
            cardCenter.repaint();
        });
        cardCenter.add(eraserBtn);

        JButton undoBtn = new JButton("Undo");
        undoBtn.setBackground(new Color(245, 158, 11));
        styleButton(undoBtn);
        undoBtn.setToolTipText("Undo last stroke or text element");
        undoBtn.addActionListener(e -> {
            whiteboardPanel.undoLastAction();
            updateStatus("Undo action performed");
        });
        cardCenter.add(undoBtn);

        JButton clearBtn = new JButton("Clear");
        clearBtn.setBackground(new Color(239, 68, 68));
        styleButton(clearBtn);
        clearBtn.setToolTipText("Clear the entire canvas");
        clearBtn.addActionListener(e -> {
            whiteboardPanel.clearCanvas();
            NetworkMessage clearMsg = new NetworkMessage("CLEAR_CANVAS");
            sendMessage(gson.toJson(clearMsg));
            updateStatus("Canvas cleared");
        });
        cardCenter.add(clearBtn);

        cardCenter.add(new JLabel(" | "));

        JButton zoomOutBtn = new JButton("-");
        zoomOutBtn.setBackground(new Color(30, 41, 59));
        styleButton(zoomOutBtn);
        zoomOutBtn.setToolTipText("Zoom Out");
        zoomOutBtn.setPreferredSize(new Dimension(36, 26));
        zoomOutBtn.addActionListener(e -> {
            whiteboardPanel.setZoomFactor(whiteboardPanel.getZoomFactor() - 0.1);
            updateStatus("Zoom: " + (int) (whiteboardPanel.getZoomFactor() * 100) + "%");
        });
        cardCenter.add(zoomOutBtn);

        JButton zoomResetBtn = new JButton("100%");
        zoomResetBtn.setBackground(new Color(30, 41, 59));
        styleButton(zoomResetBtn);
        zoomResetBtn.setToolTipText("Reset Zoom");
        zoomResetBtn.addActionListener(e -> {
            whiteboardPanel.setZoomFactor(1.0);
            updateStatus("Zoom: 100%");
        });
        cardCenter.add(zoomResetBtn);

        JButton zoomInBtn = new JButton("+");
        zoomInBtn.setBackground(new Color(30, 41, 59));
        styleButton(zoomInBtn);
        zoomInBtn.setToolTipText("Zoom In");
        zoomInBtn.setPreferredSize(new Dimension(36, 26));
        zoomInBtn.addActionListener(e -> {
            whiteboardPanel.setZoomFactor(whiteboardPanel.getZoomFactor() + 0.1);
            updateStatus("Zoom: " + (int) (whiteboardPanel.getZoomFactor() * 100) + "%");
        });
        cardCenter.add(zoomInBtn);

        JPanel cardRight = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        cardRight.setBackground(new Color(24, 32, 51));
        cardRight.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(51, 65, 85), 1),
            BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));

        JButton blockSlangBtn = new JButton("Block Slang");
        blockSlangBtn.setBackground(new Color(99, 102, 241));
        styleButton(blockSlangBtn);
        blockSlangBtn.addActionListener(e -> {
            String word = WhiteboardPanel.showCustomInputDialog(frame, "Block Slang", "Enter slang word to block:");
            if (word != null && !word.trim().isEmpty()) {
                String targetWord = word.trim().toLowerCase();
                ContentModerator.addBlockedWord(targetWord);

                NetworkMessage blockMsg = new NetworkMessage("BLOCK_SLANG");
                blockMsg.setText(targetWord);
                sendMessage(gson.toJson(blockMsg));

                updateStatus("Custom slang blocked: " + targetWord);
            }
        });
        cardRight.add(blockSlangBtn);

        JButton solveMathBtn = new JButton("Solve Math");
        solveMathBtn.setBackground(new Color(16, 185, 129));
        styleButton(solveMathBtn);
        solveMathBtn.setToolTipText("Solve a math expression written with the Text tool");
        solveMathBtn.addActionListener(e -> {
            MathExpressionSolver.MathSolveResponse resp =
                    MathExpressionSolver.solve(whiteboardPanel.getTextElements());
            if (resp == null) {
                String typed = WhiteboardPanel.showCustomInputDialog(frame, "Solve Math",
                        "No solvable expression found on the canvas. Enter one (e.g. 5 + 5 * 2):");
                if (typed == null || typed.trim().isEmpty()) {
                    updateStatus("Math solving cancelled");
                    return;
                }
                Double value = MathExpressionSolver.eval(typed);
                if (value == null) {
                    JOptionPane.showMessageDialog(frame,
                            "Could not parse: " + typed + "\n" +
                            "Supported: + - * / % ^ ( ) sqrt abs sin cos tan log ln round floor ceil pi e",
                            "Math Solver", JOptionPane.WARNING_MESSAGE);
                    updateStatus("Math solving failed");
                    return;
                }
                resp = new MathExpressionSolver.MathSolveResponse();
                resp.expression = typed.trim();
                resp.result = MathExpressionSolver.format(value);
                resp.text_x = 40;
                resp.text_y = 40;
                whiteboardPanel.addLocalTextElement(resp.expression + " = " + resp.result,
                        resp.text_x, resp.text_y,
                        whiteboardPanel.getDrawingColor(), whiteboardPanel.getCurrentFontSize());
                updateStatus("Math solved: " + resp.expression + " = " + resp.result);
                return;
            }
            whiteboardPanel.addLocalTextElement("= " + resp.result, resp.text_x, resp.text_y,
                    whiteboardPanel.getDrawingColor(), whiteboardPanel.getCurrentFontSize());
            updateStatus("Math solved: " + resp.expression + " = " + resp.result);
        });
        cardRight.add(solveMathBtn);

        cardRight.add(new JLabel(" | "));

        JButton exportBtn = new JButton("Export");
        exportBtn.setBackground(new Color(245, 158, 11));
        styleButton(exportBtn);
        exportBtn.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Export Whiteboard as PNG");
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG Image (*.png)", "png"));
            int userSelection = fileChooser.showSaveDialog(frame);
            if (userSelection == JFileChooser.APPROVE_OPTION) {
                File fileToSave = fileChooser.getSelectedFile();
                String path = fileToSave.getAbsolutePath();
                if (!path.toLowerCase().endsWith(".png")) {
                    fileToSave = new File(path + ".png");
                }
                whiteboardPanel.exportToPNG(fileToSave);
                updateStatus("Canvas exported: " + fileToSave.getName());
            }
        });
        cardRight.add(exportBtn);

        JButton saveBoardBtn = new JButton("Save Board");
        saveBoardBtn.setBackground(new Color(30, 41, 59));
        styleButton(saveBoardBtn);
        saveBoardBtn.addActionListener(e -> {
            if (!running) {
                JOptionPane.showMessageDialog(frame, "You must be connected to the server to save your board.", "Save Board", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String boardName = WhiteboardPanel.showCustomInputDialog(frame, "Save Board", "Enter name for this board file:");
            if (boardName != null && !boardName.trim().isEmpty()) {
                java.util.List<String> actions = whiteboardPanel.serializeCanvasState();
                String jsonData = gson.toJson(actions);

                NetworkMessage saveMsg = new NetworkMessage("SAVE_BOARD");
                saveMsg.setSenderId(loggedInUser);
                saveMsg.setText(boardName.trim());
                saveMsg.setJsonData(jsonData);
                sendMessage(gson.toJson(saveMsg));
                updateStatus("Board '" + boardName + "' saved to SQLite DB.");
            }
        });
        cardRight.add(saveBoardBtn);

        JButton loadBoardBtn = new JButton("Load Board");
        loadBoardBtn.setBackground(new Color(30, 41, 59));
        styleButton(loadBoardBtn);
        loadBoardBtn.addActionListener(e -> {
            if (!running) {
                JOptionPane.showMessageDialog(frame, "You must be connected to the server to load a board.", "Load Board", JOptionPane.WARNING_MESSAGE);
                return;
            }
            NetworkMessage getMsg = new NetworkMessage("GET_BOARDS");
            getMsg.setSenderId(loggedInUser);
            sendMessage(gson.toJson(getMsg));
            updateStatus("Retrieving saved board files...");
        });
        cardRight.add(loadBoardBtn);

        cardRight.add(new JLabel(" | "));

        connectBtn = new JButton("Connect");
        connectBtn.setBackground(new Color(30, 41, 59));
        styleButton(connectBtn);
        connectBtn.addActionListener(e -> {
            if (!running) {
                connectToServer();
            } else {
                disconnect();
            }
        });
        cardRight.add(connectBtn);

        mainToolbar.add(cardLeft);
        mainToolbar.add(cardCenter);
        mainToolbar.add(cardRight);

        return mainToolbar;
    }

    // Opens the socket to the server on a background thread.
    private void connectToServer() {

        new Thread(() -> {
            try {
                System.out.println("[Client] Connecting to " + SERVER_HOST + ":" + SERVER_PORT + "...");

                java.net.Socket socket = new java.net.Socket(SERVER_HOST, SERVER_PORT);
                connection = new ClientConnection(socket, "local-client");
                running = true;

                updateStatus("Connected to server as " + loggedInUser + " | Server: " + SERVER_HOST + ":" + SERVER_PORT);
                System.out.println("[Client] Connected successfully!");
                SwingUtilities.invokeLater(() -> {
                    if (connectBtn != null) {
                        connectBtn.setText("Disconnect");
                    }
                });

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

    // Reads server messages in a loop until the connection ends.
    private void receiveMessages() {
        try {
            String message;
            while (running && (message = connection.receiveMessage()) != null) {
                System.out.println("[Client] Received raw string: " + message);
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

    // Decodes one server message and applies it to the UI.
    private void handleServerMessage(String jsonMessage) {
        if (jsonMessage == null || jsonMessage.isEmpty()) {
            return;
        }

        try {
            NetworkMessage msg = gson.fromJson(jsonMessage, NetworkMessage.class);
            if (msg == null || msg.getType() == null) {
                return;
            }

            String messageType = msg.getType().toUpperCase();

            if (msg.getSenderId() != null && !msg.getSenderId().equals(clientId)) {
                handleBroadcastMessage(msg);
                return;
            }

            switch (messageType) {
                case "WELCOME":
                    clientId = msg.getSenderId();
                    frame.setTitle("AI-Augmented Collaborative Whiteboard — " + loggedInUser + " (" + clientId + ")");
                    updateStatus("Connected as " + loggedInUser + " (" + clientId + ")");
                    System.out.println("[Client] Assigned ID: " + clientId);
                    break;

                case "USER_JOINED":
                    updateStatus("User joined: " + msg.getSenderId());
                    System.out.println("[Client] User joined: " + msg.getSenderId());
                    break;

                case "USER_LEFT":
                    updateStatus("User left: " + msg.getSenderId());
                    System.out.println("[Client] User left: " + msg.getSenderId());
                    break;

                case "SERVER_SHUTDOWN":
                    updateStatus("Server is shutting down: " + msg.getText());
                    running = false;
                    break;

                case "ERROR":
                    System.err.println("[Client] Server error: " + msg.getText());
                    updateStatus("Server error: " + msg.getText());
                    break;

                case "BOARD_LIST":
                    String listJson = msg.getJsonData();
                    java.util.List<String> boards = gson.fromJson(listJson, new com.google.gson.reflect.TypeToken<java.util.List<String>>(){}.getType());
                    SwingUtilities.invokeLater(() -> showLoadBoardDialog(boards));
                    break;

                case "LOAD_BOARD_STATE":
                case "CHAT_MESSAGE":
                case "CLEAR_CANVAS":
                case "UNDO":
                case "BLOCK_SLANG":
                case "DRAW_START":
                case "DRAW_LINE":
                case "DRAW_END":
                case "DRAW_RECT":
                case "DRAW_CIRCLE":
                case "DRAW_TRI":
                case "TEXT":
                case "MOVE_TEXT":
                    handleBroadcastMessage(msg);
                    break;

                default:
                    System.out.println("[Client] Unknown system message: " + messageType);
                    break;
            }

        } catch (Exception e) {
            System.err.println("[Client] Error parsing message: " + e.getMessage());
        }
    }

    // Applies one remote drawing, text, or board action to the canvas.
    private void handleBroadcastMessage(NetworkMessage msg) {
        try {
            String actionType = msg.getType().toUpperCase();

            switch (actionType) {
                case "DRAW_START": {
                    String sender = msg.getSenderId();
                    int x = msg.getX1() != null ? msg.getX1() : 0;
                    int y = msg.getY1() != null ? msg.getY1() : 0;
                    Color col = msg.getColorRgb() != null ? new Color(msg.getColorRgb()) : Color.BLACK;
                    int w = msg.getStrokeWidth() != null ? msg.getStrokeWidth() : 3;
                    whiteboardPanel.startRemoteStroke(sender, msg.getStrokeId(), x, y, col, w);
                    break;
                }

                case "DRAW_LINE": {
                    String sender = msg.getSenderId();
                    int x1 = msg.getX1() != null ? msg.getX1() : 0;
                    int y1 = msg.getY1() != null ? msg.getY1() : 0;
                    int x2 = msg.getX2() != null ? msg.getX2() : x1;
                    int y2 = msg.getY2() != null ? msg.getY2() : y1;
                    Color col = msg.getColorRgb() != null ? new Color(msg.getColorRgb()) : Color.BLACK;
                    int w = msg.getStrokeWidth() != null ? msg.getStrokeWidth() : 3;

                    if (sender != null && whiteboardPanel.hasActiveRemoteStroke(sender)) {
                        whiteboardPanel.appendRemoteStrokePoint(sender, x2, y2);
                    } else {
                        whiteboardPanel.addRemoteLine(msg.getStrokeId(), x1, y1, x2, y2, col, w);
                    }
                    break;
                }

                case "DRAW_END": {
                    String sender = msg.getSenderId();
                    whiteboardPanel.endRemoteStroke(sender, msg.getStrokeId());
                    break;
                }

                case "DRAW_RECT": {
                    int x = msg.getX1();
                    int y = msg.getY1();
                    int w = msg.getX2();
                    int h = msg.getY2();
                    Color col = new Color(msg.getColorRgb());
                    int wSize = msg.getStrokeWidth();
                    whiteboardPanel.addRemoteShape(WhiteboardPanel.Stroke.ShapeType.RECTANGLE, msg.getStrokeId(), x, y, w, h, col, wSize);
                    break;
                }

                case "DRAW_CIRCLE": {
                    int x = msg.getX1();
                    int y = msg.getY1();
                    int w = msg.getX2();
                    int h = msg.getY2();
                    Color col = new Color(msg.getColorRgb());
                    int wSize = msg.getStrokeWidth();
                    whiteboardPanel.addRemoteShape(WhiteboardPanel.Stroke.ShapeType.CIRCLE, msg.getStrokeId(), x, y, w, h, col, wSize);
                    break;
                }

                case "DRAW_TRI": {
                    int x = msg.getX1();
                    int y = msg.getY1();
                    int w = msg.getX2();
                    int h = msg.getY2();
                    Color col = new Color(msg.getColorRgb());
                    int wSize = msg.getStrokeWidth();
                    whiteboardPanel.addRemoteShape(WhiteboardPanel.Stroke.ShapeType.TRIANGLE, msg.getStrokeId(), x, y, w, h, col, wSize);
                    break;
                }

                case "TEXT": {
                    int x = msg.getX1();
                    int y = msg.getY1();
                    Color col = new Color(msg.getColorRgb());
                    int fontSize = msg.getFontSize();
                    String content = msg.getText();
                    whiteboardPanel.addRemoteText(msg.getStrokeId(), content, x, y, col, fontSize);
                    break;
                }

                case "MOVE_TEXT": {
                    int oldX = msg.getX1();
                    int oldY = msg.getY1();
                    int newX = msg.getX2();
                    int newY = msg.getY2();
                    whiteboardPanel.moveRemoteText(oldX, oldY, newX, newY);
                    break;
                }

                case "BLOCK_SLANG":
                    ContentModerator.addBlockedWord(msg.getText());
                    updateStatus("Dynamic slang blocked: " + msg.getText());
                    break;

                case "UNDO":
                    whiteboardPanel.undoRemoteAction(msg.getStrokeId());
                    updateStatus("Undo action performed by remote user");
                    break;

                case "CLEAR_CANVAS":
                    whiteboardPanel.clearCanvas();
                    updateStatus("Canvas cleared by remote user");
                    break;

                case "CHAT_MESSAGE": {
                    String sender = msg.getSenderId();
                    String content = msg.getText();
                    appendChat(sender, content);
                    break;
                }

                case "LOAD_BOARD_STATE": {
                    String boardData = msg.getJsonData();
                    if (boardData != null) {
                        whiteboardPanel.clearCanvas();
                        String[] actions = gson.fromJson(boardData, String[].class);
                        for (String actionJson : actions) {
                            try {
                                NetworkMessage actionMsg = gson.fromJson(actionJson, NetworkMessage.class);
                                handleBroadcastMessage(actionMsg);
                            } catch (Exception ex) {
                                System.err.println("[Client] Error rebuilding canvas state: " + ex.getMessage());
                            }
                        }
                        updateStatus("Whiteboard loaded and synchronized.");
                    }
                    break;
                }

                default:
                    System.out.println("[Client] Unknown broadcast action: " + actionType);
                    break;
            }

        } catch (Exception e) {
            System.err.println("[Client] Error parsing broadcast message: " + e.getMessage());
        }
    }

    // Sends a raw JSON message to the server if connected.
    public void sendMessage(String message) {
        if (connection != null && running) {
            connection.sendMessage(message);
        }
    }

    // Closes the connection and resets the connect button.
    private void disconnect() {
        if (connection != null && running) {
            System.out.println("[Client] Disconnecting...");
            running = false;

            NetworkMessage disc = new NetworkMessage("DISCONNECT");
            connection.sendMessage(gson.toJson(disc));

            connection.close();
            connection = null;

            updateStatus("Disconnected");
            System.out.println("[Client] Disconnected from server.");
            SwingUtilities.invokeLater(() -> {
                if (connectBtn != null) {
                    connectBtn.setText("Connect");
                }
            });
        }
    }

    // Builds the side chat panel with its history and input row.
    private JPanel createChatPanel() {
        JPanel chatPanel = new JPanel(new BorderLayout(5, 5));
        chatPanel.setPreferredSize(new Dimension(280, 0));
        chatPanel.setBackground(new Color(15, 23, 42));
        chatPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(30, 41, 59)),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        JLabel titleLabel = new JLabel("Collaborative Chat");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        chatPanel.add(titleLabel, BorderLayout.NORTH);

        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatPane.setContentType("text/html");

        kit = new javax.swing.text.html.HTMLEditorKit();
        doc = new javax.swing.text.html.HTMLDocument();
        chatPane.setEditorKit(kit);
        chatPane.setDocument(doc);

        javax.swing.text.html.StyleSheet styleSheet = kit.getStyleSheet();
        styleSheet.addRule("body { font-family: 'Segoe UI', -apple-system, sans-serif; color: #f8fafc; background-color: #182033; margin: 4px; padding: 0; }");

        chatPane.setBackground(new Color(24, 32, 51));
        chatPane.setForeground(Color.WHITE);
        chatPane.setCaretColor(Color.WHITE);
        chatPane.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JScrollPane chatScroll = new JScrollPane(chatPane);
        chatScroll.setBorder(BorderFactory.createLineBorder(new Color(51, 65, 85)));
        chatScroll.getVerticalScrollBar().setUnitIncrement(12);
        chatPanel.add(chatScroll, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout(6, 0));
        inputPanel.setBackground(new Color(15, 23, 42));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        chatInput = new JTextField();
        chatInput.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        chatInput.setBackground(new Color(30, 41, 59));
        chatInput.setForeground(Color.WHITE);
        chatInput.setCaretColor(Color.WHITE);
        chatInput.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(51, 65, 85)),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));

        JButton sendBtn = new JButton("Send");
        sendBtn.setBackground(new Color(59, 130, 246));
        styleButton(sendBtn);

        ActionListener sendAction = e -> {
            String text = chatInput.getText().trim();
            if (!text.isEmpty()) {
                sendChatMessage(text);
                chatInput.setText("");
            }
        };
        chatInput.addActionListener(sendAction);
        sendBtn.addActionListener(sendAction);

        inputPanel.add(chatInput, BorderLayout.CENTER);
        inputPanel.add(sendBtn, BorderLayout.EAST);

        chatPanel.add(inputPanel, BorderLayout.SOUTH);

        appendChat("System", "Welcome to Collaborative Chat! Keep it clean.");

        return chatPanel;
    }

    // Moderates and sends one chat line to the server.
    private void sendChatMessage(String rawMessage) {
        if (!running) {
            appendChat("System", "Not connected to the server. Message not sent.");
            return;
        }
        NetworkMessage msg = new NetworkMessage("CHAT_MESSAGE");
        msg.setText(rawMessage);
        msg.setSenderId(loggedInUser);
        sendMessage(gson.toJson(msg));
    }

    // Appends one formatted message to the chat history.
    private void appendChat(String sender, String message) {
        if (chatPane != null) {
            SwingUtilities.invokeLater(() -> {
                try {
                    boolean isMe = loggedInUser.equalsIgnoreCase(sender);
                    String color = isMe ? "#60a5fa" : "#818cf8";
                    if ("System".equalsIgnoreCase(sender)) {
                        color = "#f59e0b";
                    }

                    String displayName = sender;
                    if (isMe) {
                        displayName = sender + " (You)";
                    }

                    String html = "<div style=\"margin-bottom: 8px; padding: 8px 10px; border-radius: 6px; background-color: #1e293b; border: 1px solid #334155;\">" +
                            "<span style=\"color: " + color + "; font-weight: bold; font-size: 13px; font-family: 'Segoe UI', sans-serif;\">" + displayName + "</span>" +
                            "<div style=\"color: #f1f5f9; font-size: 14px; margin-top: 4px; font-family: 'Segoe UI', sans-serif; font-weight: normal;\">" + message + "</div>" +
                            "</div>";

                    kit.insertHTML(doc, doc.getLength(), html, 0, 0, null);
                    chatPane.setCaretPosition(doc.getLength());
                } catch (Exception e) {
                    System.err.println("[Client] Error writing HTML chat: " + e.getMessage());
                }
            });
        }
    }

    // Lets the user pick one of their saved boards to load.
    private void showLoadBoardDialog(java.util.List<String> boards) {
        if (boards == null || boards.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "No saved boards found for this account.", "Load Board", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String[] boardArray = boards.toArray(new String[0]);
        String selectedBoard = (String) JOptionPane.showInputDialog(
            frame,
            "Select a board to load:",
            "Load Board from DB",
            JOptionPane.QUESTION_MESSAGE,
            null,
            boardArray,
            boardArray[0]
        );

        if (selectedBoard != null) {
            NetworkMessage loadMsg = new NetworkMessage("LOAD_BOARD");
            loadMsg.setSenderId(loggedInUser);
            loadMsg.setText(selectedBoard);
            sendMessage(gson.toJson(loadMsg));
            updateStatus("Requesting load of board '" + selectedBoard + "'...");
        }
    }

    // Writes one line to the status bar on the Swing thread.
    private void updateStatus(String message) {
        if (statusLabel != null) {
            if (SwingUtilities.isEventDispatchThread()) {
                statusLabel.setText("  Status: " + message + " | User: " + loggedInUser);
            } else {
                SwingUtilities.invokeLater(() ->
                    statusLabel.setText("  Status: " + message + " | User: " + loggedInUser));
            }
        }
    }

    // Shows the login window and reports whether sign-in succeeded.
    public static boolean showLoginDialog(JFrame parentFrame) {
        JDialog dialog = new JDialog(parentFrame, "Whiteboard Login", true);
        dialog.setUndecorated(true);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(new Color(51, 65, 85), 2));

        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.setBackground(new Color(15, 23, 42));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel titleLabel = new JLabel("Collaborative Whiteboard", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setForeground(Color.WHITE);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(titleLabel, gbc);

        JLabel userLabel = new JLabel("Username:");
        userLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        userLabel.setForeground(new Color(148, 163, 184));
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        panel.add(userLabel, gbc);

        JTextField userField = new JTextField(15);
        userField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        userField.setBackground(new Color(30, 41, 59));
        userField.setForeground(Color.WHITE);
        userField.setCaretColor(Color.WHITE);
        userField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(51, 65, 85)),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        gbc.gridx = 1;
        panel.add(userField, gbc);

        JLabel passLabel = new JLabel("Password:");
        passLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        passLabel.setForeground(new Color(148, 163, 184));
        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(passLabel, gbc);

        JPasswordField passField = new JPasswordField(15);
        passField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        passField.setBackground(new Color(30, 41, 59));
        passField.setForeground(Color.WHITE);
        passField.setCaretColor(Color.WHITE);
        passField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(51, 65, 85)),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        gbc.gridx = 1;
        panel.add(passField, gbc);

        JLabel msgLabel = new JLabel("Enter username and password.", SwingConstants.CENTER);
        msgLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        msgLabel.setForeground(new Color(148, 163, 184));
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        panel.add(msgLabel, gbc);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnPanel.setBackground(new Color(15, 23, 42));

        JButton registerBtn = new JButton("Register");
        registerBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        registerBtn.setForeground(Color.WHITE);
        registerBtn.setBackground(new Color(99, 102, 241));
        registerBtn.setOpaque(true);
        registerBtn.setBorderPainted(false);
        registerBtn.setFocusPainted(false);
        registerBtn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        registerBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JButton loginBtn = new JButton("Login");
        loginBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        loginBtn.setForeground(Color.WHITE);
        loginBtn.setBackground(new Color(59, 130, 246));
        loginBtn.setOpaque(true);
        loginBtn.setBorderPainted(false);
        loginBtn.setFocusPainted(false);
        loginBtn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        loginBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JButton exitBtn = new JButton("Exit");
        exitBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        exitBtn.setForeground(Color.WHITE);
        exitBtn.setBackground(new Color(51, 65, 85));
        exitBtn.setOpaque(true);
        exitBtn.setBorderPainted(false);
        exitBtn.setFocusPainted(false);
        exitBtn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        exitBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        final boolean[] loggedIn = {false};

        loginBtn.addActionListener(e -> {
            String user = userField.getText().trim();
            String pass = new String(passField.getPassword());
            if (DatabaseManager.loginUser(user, pass)) {
                loggedIn[0] = true;
                loggedInUser = user;
                dialog.dispose();
            } else {
                msgLabel.setText("Invalid username or password.");
                msgLabel.setForeground(new Color(239, 68, 68));
            }
        });

        registerBtn.addActionListener(e -> {
            String user = userField.getText().trim();
            String pass = new String(passField.getPassword());
            if (user.isEmpty() || pass.isEmpty()) {
                msgLabel.setText("Username and password cannot be empty.");
                msgLabel.setForeground(new Color(239, 68, 68));
                return;
            }
            if (DatabaseManager.registerUser(user, pass)) {
                msgLabel.setText("Registration successful! Click Login.");
                msgLabel.setForeground(new Color(16, 185, 129));
            } else {
                msgLabel.setText("Username already exists.");
                msgLabel.setForeground(new Color(239, 68, 68));
            }
        });

        exitBtn.addActionListener(e -> {
            System.exit(0);
        });

        btnPanel.add(exitBtn);
        btnPanel.add(registerBtn);
        btnPanel.add(loginBtn);

        gbc.gridy = 4;
        panel.add(btnPanel, gbc);

        dialog.add(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);

        return loggedIn[0];
    }

    // Entry point: applies the system look and feel, logs in, then opens the window.
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

            if (!showLoginDialog(null)) {
                System.out.println("[Client] Login canceled. Exiting.");
                System.exit(0);
            }

            WhiteboardClient client = new WhiteboardClient();
            client.createGUI();

            System.out.println("[Main] Whiteboard Client is ready.");
            System.out.println("[Main] Click 'Connect' to connect to the server.");
        });
    }
}
