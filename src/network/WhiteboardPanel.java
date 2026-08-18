package network;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/**
 * WhiteboardPanel.java
 * ====================
 *
 * This class is the main Swing whiteboard drawing canvas.
 * It has been augmented with shape normalization and collaborative text input features.
 *
 * WHY EXTEND JPanel?
 * ------------------
 * In Java Swing, JPanel is a lightweight container that we can paint on.
 * By extending JPanel, we gain the ability to override paintComponent()
 * and draw custom graphics using Graphics2D. This is the standard way
 * to create a custom drawing surface in Java Swing, as specified in the
 * project proposal (JFrame + JPanel + Graphics2D).
 *
 * HOW DRAWING WORKS:
 * ------------------
 * 1. The user presses the mouse button → a new stroke begins.
 * 2. The user drags the mouse → points are collected into the current stroke.
 * 3. The user releases the mouse → the stroke is finalized.
 * 4. All strokes are stored in a List so they survive repaint() calls.
 * 5. paintComponent() iterates all stored strokes and redraws them.
 *
 * COLLABORATION-READY:
 * --------------------
 * This panel provides the addRemoteStroke() method so that drawing data
 * received from other clients (via the server) can be added and rendered.
 * The architecture supports:
 *
 *   Client A draws → WhiteboardPanel stores stroke
 *                   → WhiteboardClient sends DRAW_LINE messages to server
 *                   → Server broadcasts to Client B
 *                   → Client B's WhiteboardClient receives the messages
 *                   → Client B calls addRemoteStroke() on its WhiteboardPanel
 *                   → Drawing appears on Client B's canvas
 *
 * @author Green University of Bangladesh - CSE Networking Lab Project
 */
public class WhiteboardPanel extends JPanel {

    // =========================================================================
    // INNER CLASS: DrawPoint
    // =========================================================================

    /**
     * DrawPoint represents a single (x, y) coordinate in a drawing stroke.
     * We store x and y as integers because pixel coordinates are integers.
     */
    public static class DrawPoint {
        public final int x;
        public final int y;

        public DrawPoint(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    // =========================================================================
    // INNER CLASS: Stroke
    // =========================================================================

    /**
     * A Stroke represents one continuous drawing action (mouse press → drag → release).
     * It stores a list of DrawPoints, a color, a stroke width, and its geometric type.
     *
     * WHY STORE STROKES?
     * When Swing calls repaint() — for example after a window resize, another
     * window overlapping, or a programmatic repaint — the entire panel is cleared
     * and paintComponent() is called again. If we don't store the drawing data,
     * everything the user drew would disappear. By keeping all strokes in a list,
     * we can redraw the complete picture every time paintComponent() is called.
     */
    public static class Stroke {
        public enum ShapeType { FREEHAND, LINE, RECTANGLE, CIRCLE, TRIANGLE }

        private List<DrawPoint> points;  // The sequence of points in this stroke (used for FREEHAND)
        private Color color;             // The color used for this stroke
        private int strokeWidth;         // The thickness of this stroke (in pixels)
        private ShapeType type = ShapeType.FREEHAND;
        private int x1, y1, x2, y2;      // Coordinates/bounding box for normalized vector shapes

        public Stroke(Color color, int strokeWidth) {
            this(color, strokeWidth, ShapeType.FREEHAND, 0, 0, 0, 0);
        }

        public Stroke(Color color, int strokeWidth, ShapeType type, int x1, int y1, int x2, int y2) {
            this.points = new ArrayList<>();
            this.color = color;
            this.strokeWidth = strokeWidth;
            this.type = type;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        /** Adds a point to this stroke. */
        public void addPoint(int x, int y) {
            points.add(new DrawPoint(x, y));
        }

        /** Returns the list of points in this stroke. */
        public List<DrawPoint> getPoints() {
            return points;
        }

        /** Returns the color of this stroke. */
        public Color getColor() {
            return color;
        }

        /** Returns the stroke width. */
        public int getStrokeWidth() {
            return strokeWidth;
        }

        public ShapeType getType() {
            return type;
        }

        public int getX1() { return x1; }
        public int getY1() { return y1; }
        public int getX2() { return x2; }
        public int getY2() { return y2; }
    }

    // =========================================================================
    // INNER CLASS: TextElement
    // =========================================================================
    public static class TextElement {
        private final String text;
        private int x;
        private int y;
        private final Color color;
        private final int fontSize;

        public TextElement(String text, int x, int y, Color color, int fontSize) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
            this.fontSize = fontSize;
        }

        public String getText() { return text; }
        public int getX() { return x; }
        public int getY() { return y; }
        public void setX(int x) { this.x = x; }
        public void setY(int y) { this.y = y; }
        public Color getColor() { return color; }
        public int getFontSize() { return fontSize; }
    }

    public enum Mode { FREEHAND, TEXT }

    // =========================================================================
    // INSTANCE VARIABLES
    // =========================================================================

    // Stores ALL completed and in-progress strokes.
    private final List<Stroke> strokes;

    // Stores ALL text elements placed on the whiteboard.
    private final List<TextElement> textElements;

    // The stroke currently being drawn by the LOCAL user (mouse is pressed).
    private Stroke currentStroke;

    // The current drawing color selected by the user.
    private Color currentColor;

    // The current stroke width (line thickness) selected by the user.
    private int currentStrokeWidth;

    // Reference to the WhiteboardClient, used to send drawing data to the server.
    private WhiteboardClient client;

    // Drawing mode: FREEHAND or TEXT
    private Mode drawingMode = Mode.FREEHAND;

    // If true, automatically recognizes and snaps rough sketches into clean vector shapes
    private boolean autoNormalize = false;

    // Selected font size for placed text elements (default: 20)
    private int currentFontSize = 20;

    // True if canvas dotted grid is visible
    private boolean showGrid = true;

    // Selected text element for drag-and-drop movement
    private TextElement selectedTextElement = null;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    // History stack to track the order of additions (for undo functionality)
    // "S" for Stroke, "T" for TextElement
    private final List<String> actionHistory = java.util.Collections.synchronizedList(new ArrayList<>());

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    /**
     * Creates a new WhiteboardPanel — the interactive drawing canvas.
     *
     * This constructor:
     *   1. Sets the background to white (standard whiteboard color).
     *   2. Sets a reasonable preferred size for the panel.
     *   3. Initializes the data structures for storing drawing data.
     *   4. Attaches mouse listeners to capture drawing input.
     */
    public WhiteboardPanel() {
        // Set the background color to white — a clean whiteboard surface.
        setBackground(Color.WHITE);

        // Set a reasonable preferred size so the panel has a visible area
        // when added to a JFrame. Without this, the panel might have zero
        // size and appear invisible.
        setPreferredSize(new Dimension(900, 600));

        // Initialize the list that stores all drawing strokes.
        // We use Collections.synchronizedList for thread safety because
        // the network-receiving thread may add remote strokes while the
        // Swing Event Dispatch Thread (EDT) is iterating the list in
        // paintComponent(). However, we still synchronize on the list
        // during iteration in paintComponent() for full safety.
        strokes = java.util.Collections.synchronizedList(new ArrayList<>());
        textElements = java.util.Collections.synchronizedList(new ArrayList<>());

        // Default drawing settings.
        currentColor = Color.BLACK;
        currentStrokeWidth = 3;
        currentStroke = null;
        client = null;

        // ===== MOUSE INPUT: HOW DRAWING IS CAPTURED =====
        // In Java Swing, mouse events are captured using listener interfaces:
        //   - MouseListener: handles press, release, click, enter, exit
        //   - MouseMotionListener: handles move and drag
        //
        // We use adapter classes (MouseAdapter, MouseMotionAdapter) which
        // provide empty default implementations. We only override the methods
        // we need, keeping the code clean.

        // --- MouseListener: Handles mouse press and release ---
        addMouseListener(new MouseAdapter() {

            /**
             * Called when the user PRESSES the mouse button.
             * This starts text dragging, a new drawing stroke, or text placement.
             */
            @Override
            public void mousePressed(MouseEvent e) {
                // 1. Check if user clicked on any existing text element (for dragging)
                synchronized (textElements) {
                    for (int i = textElements.size() - 1; i >= 0; i--) {
                        TextElement te = textElements.get(i);
                        int height = te.getFontSize();
                        int width = (int) (te.getText().length() * (te.getFontSize() * 0.55));
                        
                        // Check if click coordinate is within estimated bounding box of the text
                        if (e.getX() >= te.getX() - 6 && e.getX() <= te.getX() + width + 6 &&
                            e.getY() >= te.getY() - height && e.getY() <= te.getY() + 8) {
                            
                            selectedTextElement = te;
                            dragOffsetX = e.getX() - te.getX();
                            dragOffsetY = e.getY() - te.getY();
                            repaint();
                            return; // Bypass drawing/text placement
                        }
                    }
                }

                if (drawingMode == Mode.TEXT) {
                    // Prompt for text using custom styled input dialog
                    String text = showCustomInputDialog(WhiteboardPanel.this, 
                        "Text Tool", "Enter text to place on the whiteboard:");
                    if (text != null && !text.trim().isEmpty()) {
                        // Censor text using the ContentModerator ruleset
                        String moderatedText = ContentModerator.moderateText(text);

                        // Safeguard: If the eraser (Color.WHITE) is selected, force text color to Black so it is visible
                        Color textColor = currentColor;
                        if (textColor.equals(Color.WHITE)) {
                            textColor = Color.BLACK;
                        }

                        // Add locally with current font size
                        TextElement te = new TextElement(moderatedText, e.getX(), e.getY(), textColor, currentFontSize);
                        textElements.add(te);
                        actionHistory.add("T"); // Track text in history

                        // Broadcast to server, format: TEXT:x,y,colorRGB,fontSize,content
                        sendDrawMessage("TEXT:" + e.getX() + "," + e.getY() + "," 
                            + textColor.getRGB() + "," + currentFontSize + "," + moderatedText);

                        repaint();
                    }
                    return;
                }

                // Create a new Stroke with the current color and width.
                currentStroke = new Stroke(currentColor, currentStrokeWidth);
                currentStroke.addPoint(e.getX(), e.getY());
                strokes.add(currentStroke);

                // If not auto-normalizing, stream starting point immediately
                if (!autoNormalize) {
                    sendDrawMessage("DRAW_START:" + e.getX() + "," + e.getY()
                        + "," + currentColor.getRGB() + "," + currentStrokeWidth);
                }

                repaint();
            }

            /**
             * Called when the user RELEASES the mouse button.
             * This finalizes the current stroke or text drag.
             */
            @Override
            public void mouseReleased(MouseEvent e) {
                if (selectedTextElement != null) {
                    selectedTextElement = null; // Clear selection
                    repaint();
                    return;
                }

                if (drawingMode == Mode.TEXT) {
                    return;
                }

                if (currentStroke != null) {
                    currentStroke.addPoint(e.getX(), e.getY());

                    if (autoNormalize) {
                        // Remove the temporary raw stroke from canvas
                        strokes.remove(currentStroke);

                        // Recognize shape
                        ShapeNormalizer.NormalizedShape ns = ShapeNormalizer.normalize(currentStroke.getPoints());

                        if (ns.type == ShapeNormalizer.NormalizedShape.Type.LINE) {
                            Stroke normLine = new Stroke(currentColor, currentStrokeWidth, Stroke.ShapeType.LINE, ns.x1, ns.y1, ns.x2, ns.y2);
                            strokes.add(normLine);
                            actionHistory.add("S");
                            sendDrawMessage("DRAW_LINE:" + ns.x1 + "," + ns.y1 + "," + ns.x2 + "," + ns.y2 + "," + currentColor.getRGB() + "," + currentStrokeWidth);
                        } else if (ns.type == ShapeNormalizer.NormalizedShape.Type.RECTANGLE) {
                            Stroke normRect = new Stroke(currentColor, currentStrokeWidth, Stroke.ShapeType.RECTANGLE, ns.x1, ns.y1, ns.x2, ns.y2);
                            strokes.add(normRect);
                            actionHistory.add("S");
                            sendDrawMessage("DRAW_RECT:" + ns.x1 + "," + ns.y1 + "," + ns.x2 + "," + ns.y2 + "," + currentColor.getRGB() + "," + currentStrokeWidth);
                        } else if (ns.type == ShapeNormalizer.NormalizedShape.Type.CIRCLE) {
                            Stroke normCircle = new Stroke(currentColor, currentStrokeWidth, Stroke.ShapeType.CIRCLE, ns.x1, ns.y1, ns.x2, ns.y2);
                            strokes.add(normCircle);
                            actionHistory.add("S");
                            sendDrawMessage("DRAW_CIRCLE:" + ns.x1 + "," + ns.y1 + "," + ns.x2 + "," + ns.y2 + "," + currentColor.getRGB() + "," + currentStrokeWidth);
                        } else if (ns.type == ShapeNormalizer.NormalizedShape.Type.TRIANGLE) {
                            Stroke normTri = new Stroke(currentColor, currentStrokeWidth, Stroke.ShapeType.TRIANGLE, ns.x1, ns.y1, ns.x2, ns.y2);
                            strokes.add(normTri);
                            actionHistory.add("S");
                            sendDrawMessage("DRAW_TRI:" + ns.x1 + "," + ns.y1 + "," + ns.x2 + "," + ns.y2 + "," + currentColor.getRGB() + "," + currentStrokeWidth);
                        } else {
                            // Re-add freehand stroke and stream segments all at once
                            strokes.add(currentStroke);
                            actionHistory.add("S");
                            List<DrawPoint> pts = currentStroke.getPoints();
                            if (pts.size() > 0) {
                                sendDrawMessage("DRAW_START:" + pts.get(0).x + "," + pts.get(0).y + "," + currentColor.getRGB() + "," + currentStrokeWidth);
                                for (int i = 0; i < pts.size() - 1; i++) {
                                    DrawPoint p1 = pts.get(i);
                                    DrawPoint p2 = pts.get(i + 1);
                                    sendDrawMessage("DRAW_LINE:" + p1.x + "," + p1.y + "," + p2.x + "," + p2.y + "," + currentColor.getRGB() + "," + currentStrokeWidth);
                                }
                                sendDrawMessage("DRAW_END:");
                            }
                        }
                    } else {
                        // Send DRAW_END to the server to signal stroke completion.
                        sendDrawMessage("DRAW_END:");
                        actionHistory.add("S");
                    }

                    // The stroke is now complete.
                    currentStroke = null;
                    repaint();
                }
            }
        });

        // --- MouseMotionListener: Handles mouse drag (drawing & dragging text) ---
        addMouseMotionListener(new MouseMotionAdapter() {

            @Override
            public void mouseDragged(MouseEvent e) {
                // Handle text dragging
                if (selectedTextElement != null) {
                    int oldX = selectedTextElement.getX();
                    int oldY = selectedTextElement.getY();
                    int newX = e.getX() - dragOffsetX;
                    int newY = e.getY() - dragOffsetY;

                    selectedTextElement.setX(newX);
                    selectedTextElement.setY(newY);

                    // Sync movement: MOVE_TEXT:oldX,oldY,newX,newY
                    sendDrawMessage("MOVE_TEXT:" + oldX + "," + oldY + "," + newX + "," + newY);
                    repaint();
                    return;
                }

                if (drawingMode == Mode.TEXT) {
                    return;
                }

                if (currentStroke != null) {
                    List<DrawPoint> points = currentStroke.getPoints();
                    DrawPoint prev = points.get(points.size() - 1);

                    // Add the new point.
                    currentStroke.addPoint(e.getX(), e.getY());

                    // If not auto-normalizing, stream intermediate drawing line segment
                    if (!autoNormalize) {
                        sendDrawMessage("DRAW_LINE:" + prev.x + "," + prev.y
                            + "," + e.getX() + "," + e.getY() + "," + currentColor.getRGB() + "," + currentStrokeWidth);
                    }

                    repaint();
                }
            }
        });

        System.out.println("[WhiteboardPanel] Panel initialized (900x600, white background).");
    }

    // =========================================================================
    // PAINTING — THE HEART OF THE VISUAL RENDERING
    // =========================================================================

    /**
     * paintComponent() is called by the Swing framework whenever the panel
     * needs to be redrawn. This happens when:
     *   - The window is first shown
     *   - The window is resized
     *   - Another window was covering this one and moves away
     *   - repaint() is called programmatically
     *
     * WHY OVERRIDE paintComponent()?
     * This is the standard Swing mechanism for custom rendering. By overriding
     * this method, we control exactly what appears on the panel.
     *
     * WHY CALL super.paintComponent(g)?
     * The parent JPanel's paintComponent() clears the background and performs
     * essential housekeeping. If we skip this call, visual artifacts (ghost
     * images, flickering) may appear.
     *
     * HOW Graphics2D IS USED:
     * Graphics2D is an enhanced version of the Graphics class. It provides
     * advanced 2D rendering features such as:
     *   - Anti-aliasing (smooth edges)
     *   - Variable stroke widths
     *   - Better color management
     * The project proposal specifically requires Graphics2D for rendering.
     *
     * @param g The Graphics context provided by Swing for painting
     */
    @Override
    protected void paintComponent(Graphics g) {
        // MUST call super first to clear the background and prevent artifacts.
        super.paintComponent(g);

        // Cast Graphics to Graphics2D for advanced rendering capabilities.
        Graphics2D g2d = (Graphics2D) g;

        // Enable anti-aliasing for smoother, more professional-looking lines.
        g2d.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        );

        // Enable high-quality rendering for the best visual output.
        g2d.setRenderingHint(
            RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_QUALITY
        );

        // Enable text anti-aliasing.
        g2d.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );

        // ===== DRAW DOTTED GRID BACKGROUND =====
        if (showGrid) {
            g2d.setColor(new Color(225, 231, 239)); // Sleek slate-gray dots
            int gridSize = 25;
            int widthVal = getWidth();
            int heightVal = getHeight();
            for (int x = 0; x < widthVal; x += gridSize) {
                for (int y = 0; y < heightVal; y += gridSize) {
                    g2d.fillRect(x, y, 2, 2);
                }
            }
        }

        // ===== DRAW ALL STORED STROKES & GEOMETRIES =====
        synchronized (strokes) {
            for (Stroke stroke : strokes) {
                // Set the color for this stroke.
                g2d.setColor(stroke.getColor());

                // Set the stroke width.
                g2d.setStroke(new BasicStroke(
                    stroke.getStrokeWidth(),
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND
                ));

                if (stroke.getType() == Stroke.ShapeType.FREEHAND) {
                    // Get the points in this stroke.
                    List<DrawPoint> points = stroke.getPoints();

                    // If the stroke has only one point (a dot), draw a small circle.
                    if (points.size() == 1) {
                        DrawPoint p = points.get(0);
                        int radius = stroke.getStrokeWidth() / 2;
                        g2d.fillOval(p.x - radius, p.y - radius,
                            stroke.getStrokeWidth(), stroke.getStrokeWidth());
                    }

                    // Connect adjacent points with lines.
                    for (int i = 0; i < points.size() - 1; i++) {
                        DrawPoint p1 = points.get(i);
                        DrawPoint p2 = points.get(i + 1);
                        g2d.drawLine(p1.x, p1.y, p2.x, p2.y);
                    }
                } else if (stroke.getType() == Stroke.ShapeType.LINE) {
                    g2d.drawLine(stroke.getX1(), stroke.getY1(), stroke.getX2(), stroke.getY2());
                } else if (stroke.getType() == Stroke.ShapeType.RECTANGLE) {
                    g2d.drawRect(stroke.getX1(), stroke.getY1(), stroke.getX2(), stroke.getY2());
                } else if (stroke.getType() == Stroke.ShapeType.CIRCLE) {
                    g2d.drawOval(stroke.getX1(), stroke.getY1(), stroke.getX2(), stroke.getY2());
                } else if (stroke.getType() == Stroke.ShapeType.TRIANGLE) {
                    int[] xPoints = {stroke.getX1() + stroke.getX2() / 2, stroke.getX1(), stroke.getX1() + stroke.getX2()};
                    int[] yPoints = {stroke.getY1(), stroke.getY1() + stroke.getY2(), stroke.getY1() + stroke.getY2()};
                    g2d.drawPolygon(xPoints, yPoints, 3);
                }
            }
        }

        // ===== DRAW ALL TEXT ELEMENTS =====
        synchronized (textElements) {
            for (TextElement te : textElements) {
                g2d.setColor(te.getColor());
                g2d.setFont(new Font("Segoe UI", Font.BOLD, te.getFontSize()));
                g2d.drawString(te.getText(), te.getX(), te.getY());

                // If selected for dragging, draw a modern dashed blue outline
                if (te == selectedTextElement) {
                    int height = te.getFontSize();
                    int width = (int) (te.getText().length() * (te.getFontSize() * 0.55));
                    
                    g2d.setColor(new Color(59, 130, 246)); // blue-500
                    g2d.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{4.0f}, 0.0f));
                    g2d.drawRect(te.getX() - 6, te.getY() - height, width + 12, height + 8);
                    
                    // Draw small corner control handles
                    g2d.fillRect(te.getX() - 9, te.getY() - height - 3, 6, 6);
                    g2d.fillRect(te.getX() + width + 3, te.getY() - height - 3, 6, 6);
                    g2d.fillRect(te.getX() - 9, te.getY() + 5, 6, 6);
                    g2d.fillRect(te.getX() + width + 3, te.getY() + 5, 6, 6);
                }
            }
        }
    }

    // =========================================================================
    // COLLABORATION METHODS — For receiving drawing from other clients
    // =========================================================================

    /**
     * Adds a complete stroke received from another client (via the server).
     *
     * This is the key method for REAL-TIME COLLABORATION.
     * When the server broadcasts a drawing action from Client A,
     * Client B's WhiteboardClient receives the data, constructs a Stroke,
     * and calls this method to add it to Client B's canvas.
     *
     * The flow:
     *   Server sends broadcast → WhiteboardClient receives it
     *     → client constructs a Stroke → calls addRemoteStroke()
     *     → stroke is added to the list → repaint() renders it
     *
     * @param stroke The Stroke object received from a remote client
     */
    public void addRemoteStroke(Stroke stroke) {
        if (stroke != null) {
            strokes.add(stroke);

            // repaint() must be called to make the new stroke visible.
            // repaint() is thread-safe — it can be called from any thread
            // and Swing will schedule the actual painting on the EDT.
            repaint();
        }
    }

    /**
     * Adds a single remote line segment to the currently building remote stroke.
     * This is used for incremental drawing updates: as a remote user drags
     * their mouse, we receive individual line segments and append them
     * to a stroke in real time, rather than waiting for the complete stroke.
     *
     * @param x1         Start X coordinate
     * @param y1         Start Y coordinate
     * @param x2         End X coordinate
     * @param y2         End Y coordinate
     * @param color      The color of the line
     * @param lineWidth  The width of the line
     */
    public void addRemoteLine(int x1, int y1, int x2, int y2,
                              Color color, int lineWidth) {
        // Create a mini-stroke containing just this line segment.
        Stroke segment = new Stroke(color, lineWidth);
        segment.addPoint(x1, y1);
        segment.addPoint(x2, y2);
        strokes.add(segment);
        actionHistory.add("S"); // Track remote action in history

        // Schedule a repaint so the line appears immediately.
        repaint();
    }

    /**
     * Clears the entire whiteboard canvas.
     * Removes all stored strokes and triggers a repaint.
     * The panel will show a blank white surface.
     */
    public void clearCanvas() {
        strokes.clear();
        textElements.clear();
        actionHistory.clear();
        repaint();
        System.out.println("[WhiteboardPanel] Canvas cleared.");
    }

    /**
     * Adds a complete vector shape received from a remote client.
     */
    public void addRemoteShape(Stroke.ShapeType type, int x, int y, int w, int h, Color color, int strokeWidth) {
        Stroke shape = new Stroke(color, strokeWidth, type, x, y, w, h);
        strokes.add(shape);
        actionHistory.add("S"); // Track remote action in history
        repaint();
    }

    /**
     * Adds a text element received from a remote client.
     */
    public void addRemoteText(String text, int x, int y, Color color, int fontSize) {
        TextElement te = new TextElement(text, x, y, color, fontSize);
        textElements.add(te);
        actionHistory.add("T"); // Track remote action in history
        repaint();
    }

    /**
     * Undo the last drawing or text action locally, and sync it to the network.
     */
    public void undoLastAction() {
        if (!actionHistory.isEmpty()) {
            String lastAction = actionHistory.remove(actionHistory.size() - 1);
            if ("S".equals(lastAction) && !strokes.isEmpty()) {
                strokes.remove(strokes.size() - 1);
            } else if ("T".equals(lastAction) && !textElements.isEmpty()) {
                textElements.remove(textElements.size() - 1);
            }
            repaint();
            sendDrawMessage("UNDO:");
        }
    }

    /**
     * Undo the last drawing or text action received from a remote user.
     */
    public void undoRemoteAction() {
        if (!actionHistory.isEmpty()) {
            String lastAction = actionHistory.remove(actionHistory.size() - 1);
            if ("S".equals(lastAction) && !strokes.isEmpty()) {
                strokes.remove(strokes.size() - 1);
            } else if ("T".equals(lastAction) && !textElements.isEmpty()) {
                textElements.remove(textElements.size() - 1);
            }
            repaint();
        }
    }

    /**
     * Moves a text element when dragged by a remote client.
     */
    public void moveRemoteText(int oldX, int oldY, int newX, int newY) {
        synchronized (textElements) {
            for (TextElement te : textElements) {
                // Find the text element closest to the original coordinates
                if (Math.abs(te.getX() - oldX) < 15 && Math.abs(te.getY() - oldY) < 15) {
                    te.setX(newX);
                    te.setY(newY);
                    break;
                }
            }
        }
        repaint();
    }

    /**
     * Sets the active drawing mode.
     */
    public void setDrawingMode(Mode mode) {
        if (mode != null) {
            this.drawingMode = mode;
        }
    }

    /**
     * Returns the active drawing mode.
     */
    public Mode getDrawingMode() {
        return drawingMode;
    }

    /**
     * Enables or disables automatic shape normalization.
     */
    public void setAutoNormalize(boolean autoNormalize) {
        this.autoNormalize = autoNormalize;
    }

    /**
     * Checks if automatic shape normalization is enabled.
     */
    public boolean isAutoNormalize() {
        return autoNormalize;
    }

    public void setCurrentFontSize(int fontSize) {
        if (fontSize >= 8 && fontSize <= 100) {
            this.currentFontSize = fontSize;
        }
    }

    public int getCurrentFontSize() {
        return currentFontSize;
    }

    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
        repaint();
    }

    public boolean isShowGrid() {
        return showGrid;
    }

    // =========================================================================
    // DRAWING CONFIGURATION METHODS
    // =========================================================================

    /**
     * Sets the drawing color for future strokes.
     * Existing strokes keep their original colors.
     *
     * @param color The new drawing color
     */
    public void setDrawingColor(Color color) {
        if (color != null) {
            this.currentColor = color;
        }
    }

    /**
     * Returns the current drawing color.
     * @return The current Color
     */
    public Color getDrawingColor() {
        return currentColor;
    }

    /**
     * Sets the stroke width (line thickness) for future strokes.
     *
     * @param width The stroke width in pixels (minimum 1)
     */
    public void setStrokeWidth(int width) {
        if (width >= 1) {
            this.currentStrokeWidth = width;
        }
    }

    /**
     * Returns the current stroke width.
     * @return The current stroke width in pixels
     */
    public int getCurrentStrokeWidth() {
        return currentStrokeWidth;
    }

    // =========================================================================
    // NETWORKING INTEGRATION
    // =========================================================================

    /**
     * Sets the WhiteboardClient reference so the panel can send drawing
     * data to the server when the user draws locally.
     *
     * @param client The WhiteboardClient managing the network connection
     */
    public void setClient(WhiteboardClient client) {
        this.client = client;
    }

    /**
     * Sends a drawing message to the server through the WhiteboardClient.
     * If no client is connected (standalone mode), the message is silently
     * ignored — the panel still works for local drawing.
     *
     * @param message The drawing message to send (e.g., "DRAW_LINE:10,20,30,40")
     */
    private void sendDrawMessage(String message) {
        if (client != null) {
            client.sendMessage(message);
        }
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    /**
     * Returns the total number of strokes on the canvas.
     * Useful for debugging and status display.
     *
     * @return The number of strokes
     */
    public int getStrokeCount() {
        return strokes.size();
    }

    /**
     * Returns the list of all strokes (for serialization or backup).
     * @return An unmodifiable view of the strokes list
     */
    public List<Stroke> getStrokes() {
        return java.util.Collections.unmodifiableList(strokes);
    }

    /**
     * Display a custom, highly styled input dialog for text entry.
     * Bypasses OS look-and-feel issues by explicitly styling components.
     */
    public static String showCustomInputDialog(Component parent, String title, String prompt) {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(parent), title, true);
        dialog.setUndecorated(true);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(new Color(51, 65, 85), 2));

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        panel.setBackground(new Color(15, 23, 42)); // slate-900

        JLabel label = new JLabel(prompt);
        label.setFont(new Font("Segoe UI", Font.BOLD, 12));
        label.setForeground(new Color(148, 163, 184)); // slate-400
        panel.add(label, BorderLayout.NORTH);

        JTextField textField = new JTextField(20);
        textField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        textField.setBackground(new Color(30, 41, 59)); // slate-800
        textField.setForeground(Color.WHITE);
        textField.setCaretColor(Color.WHITE);
        textField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(51, 65, 85)),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        panel.add(textField, BorderLayout.CENTER);

        // Buttons Panel
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnPanel.setBackground(new Color(15, 23, 42));

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        cancelBtn.setForeground(Color.WHITE);
        cancelBtn.setBackground(new Color(51, 65, 85));
        cancelBtn.setOpaque(true);
        cancelBtn.setBorderPainted(false);
        cancelBtn.setFocusPainted(false);
        cancelBtn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        cancelBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JButton okBtn = new JButton("OK");
        okBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        okBtn.setForeground(Color.WHITE);
        okBtn.setBackground(new Color(59, 130, 246)); // blue-500
        okBtn.setOpaque(true);
        okBtn.setBorderPainted(false);
        okBtn.setFocusPainted(false);
        okBtn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        okBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        final String[] result = {null};

        okBtn.addActionListener(e -> {
            result[0] = textField.getText();
            dialog.dispose();
        });

        cancelBtn.addActionListener(e -> {
            result[0] = null;
            dialog.dispose();
        });

        textField.addActionListener(e -> {
            result[0] = textField.getText();
            dialog.dispose();
        });

        btnPanel.add(cancelBtn);
        btnPanel.add(okBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);

        dialog.add(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);

        return result[0];
    }
}
