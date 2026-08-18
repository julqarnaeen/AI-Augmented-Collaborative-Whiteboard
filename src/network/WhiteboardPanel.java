package network;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;

/**
 * WhiteboardPanel.java
 * ====================
 *
 * This class is the main Swing whiteboard drawing canvas.
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
     * It stores a list of DrawPoints, a color, and a stroke width.
     *
     * WHY STORE STROKES?
     * When Swing calls repaint() — for example after a window resize, another
     * window overlapping, or a programmatic repaint — the entire panel is cleared
     * and paintComponent() is called again. If we don't store the drawing data,
     * everything the user drew would disappear. By keeping all strokes in a list,
     * we can redraw the complete picture every time paintComponent() is called.
     */
    public static class Stroke {
        private List<DrawPoint> points;  // The sequence of points in this stroke
        private Color color;             // The color used for this stroke
        private int strokeWidth;         // The thickness of this stroke (in pixels)

        public Stroke(Color color, int strokeWidth) {
            this.points = new ArrayList<>();
            this.color = color;
            this.strokeWidth = strokeWidth;
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
    }

    // =========================================================================
    // INSTANCE VARIABLES
    // =========================================================================

    // Stores ALL completed and in-progress strokes.
    // This list is the "memory" of the whiteboard — everything drawn is here.
    // Using a synchronized list wrapper because the networking thread may
    // add remote strokes while Swing's EDT is repainting.
    private final List<Stroke> strokes;

    // The stroke currently being drawn by the LOCAL user (mouse is pressed).
    // This is null when the user is not actively drawing.
    private Stroke currentStroke;

    // The current drawing color selected by the user.
    private Color currentColor;

    // The current stroke width (line thickness) selected by the user.
    private int currentStrokeWidth;

    // Reference to the WhiteboardClient, used to send drawing data to the server.
    // This can be null if the panel is used in offline/standalone mode.
    private WhiteboardClient client;

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
             * This starts a new drawing stroke.
             */
            @Override
            public void mousePressed(MouseEvent e) {
                // Create a new Stroke with the current color and width.
                currentStroke = new Stroke(currentColor, currentStrokeWidth);

                // Add the initial point where the user pressed.
                currentStroke.addPoint(e.getX(), e.getY());

                // Add this stroke to the master list immediately so it
                // gets rendered even while the user is still drawing.
                strokes.add(currentStroke);

                // Send the DRAW_START message to the server so other
                // clients know a new stroke has begun.
                sendDrawMessage("DRAW_START:" + e.getX() + "," + e.getY()
                    + "," + currentColor.getRGB() + "," + currentStrokeWidth);

                // Trigger a repaint so the starting point is visible.
                repaint();
            }

            /**
             * Called when the user RELEASES the mouse button.
             * This finalizes the current stroke.
             */
            @Override
            public void mouseReleased(MouseEvent e) {
                if (currentStroke != null) {
                    // Add the final point.
                    currentStroke.addPoint(e.getX(), e.getY());

                    // Send DRAW_END to the server to signal stroke completion.
                    sendDrawMessage("DRAW_END:");

                    // The stroke is now complete. Set currentStroke to null
                    // so subsequent mouse events don't add to a finished stroke.
                    currentStroke = null;

                    // Final repaint to show the completed stroke.
                    repaint();
                }
            }
        });

        // --- MouseMotionListener: Handles mouse drag (drawing) ---
        addMouseMotionListener(new MouseMotionAdapter() {

            /**
             * Called repeatedly while the user DRAGS the mouse (button held down).
             * Each call adds another point to the current stroke, creating a
             * continuous freehand line.
             *
             * HOW MOUSE DRAGGING BECOMES A DRAWING:
             *   mousePressed  → new Stroke, first point added
             *   mouseDragged  → more points added (called many times per second)
             *   mouseDragged  → more points added...
             *   mouseReleased → last point added, stroke finalized
             *
             * In paintComponent(), adjacent points in a stroke are connected
             * by lines (drawLine), creating a smooth freehand curve.
             */
            @Override
            public void mouseDragged(MouseEvent e) {
                if (currentStroke != null) {
                    // Get the previous point (the last point in the stroke).
                    List<DrawPoint> points = currentStroke.getPoints();
                    DrawPoint prev = points.get(points.size() - 1);

                    // Add the new point.
                    currentStroke.addPoint(e.getX(), e.getY());

                    // Send a DRAW_LINE message to the server with the line
                    // segment from the previous point to the current point.
                    // This allows other clients to reconstruct the drawing
                    // incrementally, point by point.
                    sendDrawMessage("DRAW_LINE:" + prev.x + "," + prev.y
                        + "," + e.getX() + "," + e.getY());

                    // WHY CALL repaint()?
                    // repaint() tells Swing to schedule a call to paintComponent()
                    // on the Event Dispatch Thread. This ensures the new point
                    // is rendered on screen. Swing does NOT automatically
                    // update the display when data changes — we must ask for it.
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
        // Graphics2D extends Graphics with features needed for our whiteboard.
        Graphics2D g2d = (Graphics2D) g;

        // Enable anti-aliasing for smoother, more professional-looking lines.
        // Without this, diagonal lines appear jagged ("staircase" effect).
        g2d.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        );

        // Enable high-quality rendering for the best visual output.
        g2d.setRenderingHint(
            RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_QUALITY
        );

        // ===== DRAW ALL STORED STROKES =====
        // We iterate through every stroke and draw it. This ensures that
        // ALL previous drawings remain visible after each repaint.
        //
        // We synchronize on the strokes list because the network-receiving
        // thread might be adding a remote stroke at the same time the EDT
        // is iterating here. Without synchronization, we could get a
        // ConcurrentModificationException or see a partial stroke.
        synchronized (strokes) {
            for (Stroke stroke : strokes) {
                // Set the color for this stroke.
                g2d.setColor(stroke.getColor());

                // Set the stroke width (line thickness).
                // BasicStroke defines the width and style of drawn lines.
                // CAP_ROUND makes the ends of lines rounded.
                // JOIN_ROUND makes the connection between line segments smooth.
                g2d.setStroke(new BasicStroke(
                    stroke.getStrokeWidth(),
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND
                ));

                // Get the points in this stroke.
                List<DrawPoint> points = stroke.getPoints();

                // If the stroke has only one point (a dot), draw a small circle.
                if (points.size() == 1) {
                    DrawPoint p = points.get(0);
                    int radius = stroke.getStrokeWidth() / 2;
                    g2d.fillOval(p.x - radius, p.y - radius,
                        stroke.getStrokeWidth(), stroke.getStrokeWidth());
                }

                // Connect adjacent points with lines to form the freehand curve.
                // For example, if points are [A, B, C, D], we draw:
                //   line(A→B), line(B→C), line(C→D)
                // This creates a smooth, continuous curve.
                for (int i = 0; i < points.size() - 1; i++) {
                    DrawPoint p1 = points.get(i);
                    DrawPoint p2 = points.get(i + 1);
                    g2d.drawLine(p1.x, p1.y, p2.x, p2.y);
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
        repaint();
        System.out.println("[WhiteboardPanel] Canvas cleared.");
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
}
