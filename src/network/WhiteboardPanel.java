// The drawing canvas: captures mouse input, paints strokes and text, and mirrors changes to the server.
package network;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import com.google.gson.Gson;

public class WhiteboardPanel extends JPanel {

    // One captured pen position in canvas coordinates.
    public static class DrawPoint {
        public final int x;
        public final int y;

        // Holds one canvas coordinate pair.
        public DrawPoint(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    // One drawn item: a freehand path or a normalized shape.
    public static class Stroke {
        // The forms a stroke can take once recognized.
        public enum ShapeType { FREEHAND, LINE, RECTANGLE, CIRCLE, TRIANGLE }

        private String strokeId;
        private List<DrawPoint> points;
        private Color color;
        private int strokeWidth;
        private ShapeType type = ShapeType.FREEHAND;
        private int x1, y1, x2, y2;

        // Starts an empty freehand stroke.
        public Stroke(Color color, int strokeWidth) {
            this(color, strokeWidth, ShapeType.FREEHAND, 0, 0, 0, 0);
        }

        // Creates a finished shape stroke from its bounding coordinates.
        public Stroke(Color color, int strokeWidth, ShapeType type, int x1, int y1, int x2, int y2) {
            this.points = new ArrayList<>();
            this.color = color;
            this.strokeWidth = strokeWidth;
            this.type = type;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.strokeId = java.util.UUID.randomUUID().toString();
        }

        public String getStrokeId() {
            return strokeId;
        }

        public void setStrokeId(String strokeId) {
            this.strokeId = strokeId;
        }

        // Appends one pen position to a freehand stroke.
        public void addPoint(int x, int y) {
            points.add(new DrawPoint(x, y));
        }

        // Returns the captured pen positions.
        public List<DrawPoint> getPoints() {
            return points;
        }

        // Returns the stroke colour.
        public Color getColor() {
            return color;
        }

        // Returns the pen width in pixels.
        public int getStrokeWidth() {
            return strokeWidth;
        }

        // Returns whether this is freehand or a recognized shape.
        public ShapeType getType() {
            return type;
        }

        // Returns the first x coordinate of a shape stroke.
        public int getX1() { return x1; }
        // Returns the first y coordinate of a shape stroke.
        public int getY1() { return y1; }
        // Returns the second x coordinate, or the shape width.
        public int getX2() { return x2; }
        // Returns the second y coordinate, or the shape height.
        public int getY2() { return y2; }
    }

    // One piece of text placed on the canvas.
    public static class TextElement {
        private String textId;
        private final String text;
        private int x;
        private int y;
        private final Color color;
        private final int fontSize;

        // Holds one placed text item and its style.
        public TextElement(String text, int x, int y, Color color, int fontSize) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
            this.fontSize = fontSize;
            this.textId = java.util.UUID.randomUUID().toString();
        }

        public String getTextId() {
            return textId;
        }

        public void setTextId(String textId) {
            this.textId = textId;
        }

        // Returns the displayed text.
        public String getText() { return text; }
        // Returns the text x position.
        public int getX() { return x; }
        // Returns the text y position.
        public int getY() { return y; }
        // Moves the text horizontally.
        public void setX(int x) { this.x = x; }
        // Moves the text vertically.
        public void setY(int y) { this.y = y; }
        // Returns the text colour.
        public Color getColor() { return color; }
        // Returns the font size in points.
        public int getFontSize() { return fontSize; }
    }

    // Identifies one undoable canvas item by unique ID and type.
    public static class CanvasAction {
        public final String id;
        public final String type;

        public CanvasAction(String id, String type) {
            this.id = id;
            this.type = type;
        }
    }

    // Whether clicks draw freehand or place text.
    public enum Mode { FREEHAND, TEXT }

    private final List<Stroke> strokes;

    private final List<TextElement> textElements;

    private Stroke currentStroke;

    private Color currentColor;

    private int currentStrokeWidth;

    private WhiteboardClient client;

    private Mode drawingMode = Mode.FREEHAND;

    private boolean autoNormalize = false;

    private int currentFontSize = 20;

    private boolean showGrid = true;

    private TextElement selectedTextElement = null;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    private double zoomFactor = 1.0;

    private String currentStrokeId = null;
    private final java.util.Map<String, Stroke> activeRemoteStrokes = new java.util.concurrent.ConcurrentHashMap<>();

    private final List<CanvasAction> actionHistory = java.util.Collections.synchronizedList(new ArrayList<>());
    private final Gson gson = new Gson();

    // Builds the canvas and wires up the mouse listeners.
    public WhiteboardPanel() {
        setBackground(Color.WHITE);

        setPreferredSize(new Dimension(3000, 2000));

        strokes = java.util.Collections.synchronizedList(new ArrayList<>());
        textElements = java.util.Collections.synchronizedList(new ArrayList<>());

        currentColor = Color.BLACK;
        currentStrokeWidth = 3;
        currentStroke = null;
        client = null;

        addMouseListener(new MouseAdapter() {
            // Starts a stroke, places text, or picks up a text element.
            @Override
            public void mousePressed(MouseEvent e) {

                int mouseX = (int) (e.getX() / zoomFactor);
                int mouseY = (int) (e.getY() / zoomFactor);

                synchronized (textElements) {
                    for (int i = textElements.size() - 1; i >= 0; i--) {
                        TextElement te = textElements.get(i);
                        int height = te.getFontSize();
                        int width = (int) (te.getText().length() * (te.getFontSize() * 0.55));

                        if (mouseX >= te.getX() - 6 && mouseX <= te.getX() + width + 6 &&
                            mouseY >= te.getY() - height && mouseY <= te.getY() + 8) {

                            selectedTextElement = te;
                            dragOffsetX = mouseX - te.getX();
                            dragOffsetY = mouseY - te.getY();
                            repaint();
                            return;
                        }
                    }
                }

                if (drawingMode == Mode.TEXT) {
                    String text = showCustomInputDialog(WhiteboardPanel.this,
                        "Text Tool", "Enter text to place on the whiteboard:",
                        e.getLocationOnScreen());
                    if (text != null && !text.trim().isEmpty()) {
                        String moderatedText = ContentModerator.moderateText(text);

                        Color textColor = currentColor;
                        if (textColor.equals(Color.WHITE)) {
                            textColor = Color.BLACK;
                        }

                        String textStrokeId = java.util.UUID.randomUUID().toString();
                        TextElement te = new TextElement(moderatedText, mouseX, mouseY, textColor, currentFontSize);
                        te.setTextId(textStrokeId);
                        textElements.add(te);
                        actionHistory.add(new CanvasAction(textStrokeId, "T"));

                        NetworkMessage textMsg = new NetworkMessage("TEXT");
                        textMsg.setStrokeId(textStrokeId);
                        textMsg.setX1(mouseX);
                        textMsg.setY1(mouseY);
                        textMsg.setColorRgb(textColor.getRGB());
                        textMsg.setFontSize(currentFontSize);
                        textMsg.setText(moderatedText);
                        sendJsonMessage(textMsg);

                        repaint();
                    }
                    return;
                }

                currentStrokeId = java.util.UUID.randomUUID().toString();
                currentStroke = new Stroke(currentColor, currentStrokeWidth);
                currentStroke.setStrokeId(currentStrokeId);
                currentStroke.addPoint(mouseX, mouseY);
                strokes.add(currentStroke);

                if (!autoNormalize) {
                    NetworkMessage startMsg = new NetworkMessage("DRAW_START");
                    startMsg.setStrokeId(currentStrokeId);
                    startMsg.setX1(mouseX);
                    startMsg.setY1(mouseY);
                    startMsg.setColorRgb(currentColor.getRGB());
                    startMsg.setStrokeWidth(currentStrokeWidth);
                    sendJsonMessage(startMsg);
                }

                repaint();
            }

            // Finishes the stroke, snapping it to a shape when snapping is on.
            @Override
            public void mouseReleased(MouseEvent e) {
                if (selectedTextElement != null) {
                    selectedTextElement = null;
                    repaint();
                    return;
                }

                if (drawingMode == Mode.TEXT) {
                    return;
                }

                if (currentStroke != null) {
                    int mouseX = (int) (e.getX() / zoomFactor);
                    int mouseY = (int) (e.getY() / zoomFactor);
                    currentStroke.addPoint(mouseX, mouseY);

                    if (autoNormalize) {
                        strokes.remove(currentStroke);
                        ShapeRecognizer.NormalizedShape ns = ShapeRecognizer.normalize(currentStroke.getPoints());

                        if (ns.type == ShapeRecognizer.NormalizedShape.Type.LINE) {
                            String shapeId = currentStrokeId != null ? currentStrokeId : java.util.UUID.randomUUID().toString();
                            Stroke normLine = new Stroke(currentColor, currentStrokeWidth, Stroke.ShapeType.LINE, ns.x1, ns.y1, ns.x2, ns.y2);
                            normLine.setStrokeId(shapeId);
                            strokes.add(normLine);
                            actionHistory.add(new CanvasAction(shapeId, "S"));

                            NetworkMessage lineMsg = new NetworkMessage("DRAW_LINE");
                            lineMsg.setStrokeId(shapeId);
                            lineMsg.setX1(ns.x1);
                            lineMsg.setY1(ns.y1);
                            lineMsg.setX2(ns.x2);
                            lineMsg.setY2(ns.y2);
                            lineMsg.setColorRgb(currentColor.getRGB());
                            lineMsg.setStrokeWidth(currentStrokeWidth);
                            sendJsonMessage(lineMsg);
                        } else if (ns.type == ShapeRecognizer.NormalizedShape.Type.RECTANGLE) {
                            String shapeId = currentStrokeId != null ? currentStrokeId : java.util.UUID.randomUUID().toString();
                            Stroke normRect = new Stroke(currentColor, currentStrokeWidth, Stroke.ShapeType.RECTANGLE, ns.x1, ns.y1, ns.x2, ns.y2);
                            normRect.setStrokeId(shapeId);
                            strokes.add(normRect);
                            actionHistory.add(new CanvasAction(shapeId, "S"));

                            NetworkMessage rectMsg = new NetworkMessage("DRAW_RECT");
                            rectMsg.setStrokeId(shapeId);
                            rectMsg.setX1(ns.x1);
                            rectMsg.setY1(ns.y1);
                            rectMsg.setX2(ns.x2);
                            rectMsg.setY2(ns.y2);
                            rectMsg.setColorRgb(currentColor.getRGB());
                            rectMsg.setStrokeWidth(currentStrokeWidth);
                            sendJsonMessage(rectMsg);
                        } else if (ns.type == ShapeRecognizer.NormalizedShape.Type.CIRCLE) {
                            String shapeId = currentStrokeId != null ? currentStrokeId : java.util.UUID.randomUUID().toString();
                            Stroke normCircle = new Stroke(currentColor, currentStrokeWidth, Stroke.ShapeType.CIRCLE, ns.x1, ns.y1, ns.x2, ns.y2);
                            normCircle.setStrokeId(shapeId);
                            strokes.add(normCircle);
                            actionHistory.add(new CanvasAction(shapeId, "S"));

                            NetworkMessage circleMsg = new NetworkMessage("DRAW_CIRCLE");
                            circleMsg.setStrokeId(shapeId);
                            circleMsg.setX1(ns.x1);
                            circleMsg.setY1(ns.y1);
                            circleMsg.setX2(ns.x2);
                            circleMsg.setY2(ns.y2);
                            circleMsg.setColorRgb(currentColor.getRGB());
                            circleMsg.setStrokeWidth(currentStrokeWidth);
                            sendJsonMessage(circleMsg);
                        } else if (ns.type == ShapeRecognizer.NormalizedShape.Type.TRIANGLE) {
                            String shapeId = currentStrokeId != null ? currentStrokeId : java.util.UUID.randomUUID().toString();
                            Stroke normTri = new Stroke(currentColor, currentStrokeWidth, Stroke.ShapeType.TRIANGLE, ns.x1, ns.y1, ns.x2, ns.y2);
                            normTri.setStrokeId(shapeId);
                            strokes.add(normTri);
                            actionHistory.add(new CanvasAction(shapeId, "S"));

                            NetworkMessage triMsg = new NetworkMessage("DRAW_TRI");
                            triMsg.setStrokeId(shapeId);
                            triMsg.setX1(ns.x1);
                            triMsg.setY1(ns.y1);
                            triMsg.setX2(ns.x2);
                            triMsg.setY2(ns.y2);
                            triMsg.setColorRgb(currentColor.getRGB());
                            triMsg.setStrokeWidth(currentStrokeWidth);
                            sendJsonMessage(triMsg);
                        } else {
                            String sId = currentStrokeId != null ? currentStrokeId : java.util.UUID.randomUUID().toString();
                            currentStroke.setStrokeId(sId);
                            strokes.add(currentStroke);
                            actionHistory.add(new CanvasAction(sId, "S"));
                            List<DrawPoint> pts = currentStroke.getPoints();
                            if (pts.size() > 0) {
                                NetworkMessage startMsg = new NetworkMessage("DRAW_START");
                                startMsg.setStrokeId(sId);
                                startMsg.setX1(pts.get(0).x);
                                startMsg.setY1(pts.get(0).y);
                                startMsg.setColorRgb(currentColor.getRGB());
                                startMsg.setStrokeWidth(currentStrokeWidth);
                                sendJsonMessage(startMsg);

                                for (int i = 0; i < pts.size() - 1; i++) {
                                    DrawPoint p1 = pts.get(i);
                                    DrawPoint p2 = pts.get(i + 1);
                                    NetworkMessage lineMsg = new NetworkMessage("DRAW_LINE");
                                    lineMsg.setStrokeId(sId);
                                    lineMsg.setX1(p1.x);
                                    lineMsg.setY1(p1.y);
                                    lineMsg.setX2(p2.x);
                                    lineMsg.setY2(p2.y);
                                    lineMsg.setColorRgb(currentColor.getRGB());
                                    lineMsg.setStrokeWidth(currentStrokeWidth);
                                    sendJsonMessage(lineMsg);
                                }
                                NetworkMessage endMsg = new NetworkMessage("DRAW_END");
                                endMsg.setStrokeId(sId);
                                sendJsonMessage(endMsg);
                            }
                        }
                    } else {
                        NetworkMessage endMsg = new NetworkMessage("DRAW_END");
                        endMsg.setStrokeId(currentStrokeId);
                        sendJsonMessage(endMsg);
                        actionHistory.add(new CanvasAction(currentStrokeId, "S"));
                    }

                    currentStroke = null;
                    currentStrokeId = null;
                    repaint();
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            // Extends the current stroke or drags the selected text.
            @Override
            public void mouseDragged(MouseEvent e) {
                int mouseX = (int) (e.getX() / zoomFactor);
                int mouseY = (int) (e.getY() / zoomFactor);

                if (selectedTextElement != null) {
                    int oldX = selectedTextElement.getX();
                    int oldY = selectedTextElement.getY();
                    int newX = mouseX - dragOffsetX;
                    int newY = mouseY - dragOffsetY;

                    selectedTextElement.setX(newX);
                    selectedTextElement.setY(newY);

                    NetworkMessage moveMsg = new NetworkMessage("MOVE_TEXT");
                    moveMsg.setX1(oldX);
                    moveMsg.setY1(oldY);
                    moveMsg.setX2(newX);
                    moveMsg.setY2(newY);
                    sendJsonMessage(moveMsg);

                    repaint();
                    return;
                }

                if (drawingMode == Mode.TEXT) {
                    return;
                }

                if (currentStroke != null) {
                    List<DrawPoint> points = currentStroke.getPoints();
                    DrawPoint prev = points.get(points.size() - 1);

                    currentStroke.addPoint(mouseX, mouseY);

                    if (!autoNormalize) {
                        NetworkMessage lineMsg = new NetworkMessage("DRAW_LINE");
                        lineMsg.setStrokeId(currentStrokeId);
                        lineMsg.setX1(prev.x);
                        lineMsg.setY1(prev.y);
                        lineMsg.setX2(mouseX);
                        lineMsg.setY2(mouseY);
                        lineMsg.setColorRgb(currentColor.getRGB());
                        lineMsg.setStrokeWidth(currentStrokeWidth);
                        sendJsonMessage(lineMsg);
                    }

                    repaint();
                }
            }
        });

        System.out.println("[WhiteboardPanel] Panel initialized (3000x2000, white background).");
    }

    // Paints the grid, every stroke, and every text element at the current zoom.
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2d.scale(zoomFactor, zoomFactor);

        if (showGrid) {
            g2d.setColor(new Color(225, 231, 239));
            int gridSize = 25;
            int widthVal = 3000;
            int heightVal = 2000;
            for (int x = 0; x < widthVal; x += gridSize) {
                for (int y = 0; y < heightVal; y += gridSize) {
                    g2d.fillRect(x, y, 2, 2);
                }
            }
        }

        synchronized (strokes) {
            for (Stroke stroke : strokes) {
                g2d.setColor(stroke.getColor());
                g2d.setStroke(new BasicStroke(
                    stroke.getStrokeWidth(),
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND
                ));

                if (stroke.getType() == Stroke.ShapeType.FREEHAND) {
                    List<DrawPoint> points = stroke.getPoints();

                    if (points.size() == 1) {
                        DrawPoint p = points.get(0);
                        int radius = stroke.getStrokeWidth() / 2;
                        g2d.fillOval(p.x - radius, p.y - radius,
                            stroke.getStrokeWidth(), stroke.getStrokeWidth());
                    }

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

        synchronized (textElements) {
            for (TextElement te : textElements) {
                g2d.setColor(te.getColor());
                g2d.setFont(new Font("Segoe UI", Font.BOLD, te.getFontSize()));
                g2d.drawString(te.getText(), te.getX(), te.getY());

                if (te == selectedTextElement) {
                    int height = te.getFontSize();
                    int width = (int) (te.getText().length() * (te.getFontSize() * 0.55));

                    g2d.setColor(new Color(59, 130, 246));
                    g2d.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{4.0f}, 0.0f));
                    g2d.drawRect(te.getX() - 6, te.getY() - height, width + 12, height + 8);

                    g2d.fillRect(te.getX() - 9, te.getY() - height - 3, 6, 6);
                    g2d.fillRect(te.getX() + width + 3, te.getY() - height - 3, 6, 6);
                    g2d.fillRect(te.getX() - 9, te.getY() + 5, 6, 6);
                    g2d.fillRect(te.getX() + width + 3, te.getY() + 5, 6, 6);
                }
            }
        }
    }

    // Adds a stroke received from another client.
    public void addRemoteStroke(Stroke stroke) {
        if (stroke != null) {
            strokes.add(stroke);
            if (stroke.getStrokeId() == null) {
                stroke.setStrokeId(java.util.UUID.randomUUID().toString());
            }
            actionHistory.add(new CanvasAction(stroke.getStrokeId(), "S"));
            repaint();
        }
    }

    // Starts an active remote freehand stroke.
    public void startRemoteStroke(String senderId, String strokeId, int x, int y, Color color, int strokeWidth) {
        Stroke stroke = new Stroke(color, strokeWidth);
        String sId = strokeId != null && !strokeId.isEmpty() ? strokeId : java.util.UUID.randomUUID().toString();
        stroke.setStrokeId(sId);
        stroke.addPoint(x, y);
        strokes.add(stroke);
        if (senderId != null) {
            activeRemoteStrokes.put(senderId, stroke);
        }
        repaint();
    }

    public void startRemoteStroke(String senderId, int x, int y, Color color, int strokeWidth) {
        startRemoteStroke(senderId, null, x, y, color, strokeWidth);
    }

    // Appends a point to an active remote stroke.
    public void appendRemoteStrokePoint(String senderId, int x2, int y2) {
        Stroke stroke = senderId != null ? activeRemoteStrokes.get(senderId) : null;
        if (stroke != null) {
            stroke.addPoint(x2, y2);
            repaint();
        }
    }

    // Completes an active remote stroke and records it once in actionHistory.
    public void endRemoteStroke(String senderId, String strokeId) {
        Stroke stroke = senderId != null ? activeRemoteStrokes.remove(senderId) : null;
        String sId = strokeId != null && !strokeId.isEmpty() ? strokeId : (stroke != null ? stroke.getStrokeId() : null);
        if (sId != null) {
            actionHistory.add(new CanvasAction(sId, "S"));
        }
        repaint();
    }

    public void endRemoteStroke(String senderId) {
        endRemoteStroke(senderId, null);
    }

    // Checks whether there is an in-progress remote stroke for this sender.
    public boolean hasActiveRemoteStroke(String senderId) {
        return senderId != null && activeRemoteStrokes.containsKey(senderId);
    }

    // Appends one remote line segment to the canvas.
    public void addRemoteLine(String strokeId, int x1, int y1, int x2, int y2, Color color, int lineWidth) {
        String sId = strokeId != null && !strokeId.isEmpty() ? strokeId : java.util.UUID.randomUUID().toString();
        Stroke segment = new Stroke(color, lineWidth, Stroke.ShapeType.LINE, x1, y1, x2, y2);
        segment.setStrokeId(sId);
        strokes.add(segment);
        actionHistory.add(new CanvasAction(sId, "S"));
        repaint();
    }

    public void addRemoteLine(int x1, int y1, int x2, int y2, Color color, int lineWidth) {
        addRemoteLine(null, x1, y1, x2, y2, color, lineWidth);
    }

    // Removes every stroke and text element.
    public void clearCanvas() {
        strokes.clear();
        textElements.clear();
        actionHistory.clear();
        activeRemoteStrokes.clear();
        repaint();
        System.out.println("[WhiteboardPanel] Canvas cleared.");
    }

    // Adds a normalized shape received from another client.
    public void addRemoteShape(Stroke.ShapeType type, String strokeId, int x, int y, int w, int h, Color color, int strokeWidth) {
        String sId = strokeId != null && !strokeId.isEmpty() ? strokeId : java.util.UUID.randomUUID().toString();
        Stroke shape = new Stroke(color, strokeWidth, type, x, y, w, h);
        shape.setStrokeId(sId);
        strokes.add(shape);
        actionHistory.add(new CanvasAction(sId, "S"));
        repaint();
    }

    public void addRemoteShape(Stroke.ShapeType type, int x, int y, int w, int h, Color color, int strokeWidth) {
        addRemoteShape(type, null, x, y, w, h, color, strokeWidth);
    }

    // Adds a text element received from another client.
    public void addRemoteText(String textId, String text, int x, int y, Color color, int fontSize) {
        String tId = textId != null && !textId.isEmpty() ? textId : java.util.UUID.randomUUID().toString();
        TextElement te = new TextElement(text, x, y, color, fontSize);
        te.setTextId(tId);
        textElements.add(te);
        actionHistory.add(new CanvasAction(tId, "T"));
        repaint();
    }

    public void addRemoteText(String text, int x, int y, Color color, int fontSize) {
        addRemoteText(null, text, x, y, color, fontSize);
    }

    // Adds a text element here and broadcasts it to the others.
    public void addLocalTextElement(String text, int x, int y, Color color, int fontSize) {
        String textId = java.util.UUID.randomUUID().toString();
        TextElement te = new TextElement(text, x, y, color, fontSize);
        te.setTextId(textId);
        textElements.add(te);
        actionHistory.add(new CanvasAction(textId, "T"));

        NetworkMessage textMsg = new NetworkMessage("TEXT");
        textMsg.setStrokeId(textId);
        textMsg.setX1(x);
        textMsg.setY1(y);
        textMsg.setColorRgb(color.getRGB());
        textMsg.setFontSize(fontSize);
        textMsg.setText(text);
        sendJsonMessage(textMsg);

        repaint();
    }

    // Removes the most recent local stroke or text element.
    public void undoLastAction() {
        if (!actionHistory.isEmpty()) {
            CanvasAction lastAction = actionHistory.remove(actionHistory.size() - 1);
            String targetId = lastAction.id;
            synchronized (strokes) {
                strokes.removeIf(s -> targetId != null && targetId.equals(s.getStrokeId()));
            }
            synchronized (textElements) {
                textElements.removeIf(t -> targetId != null && targetId.equals(t.getTextId()));
            }
            repaint();

            NetworkMessage undoMsg = new NetworkMessage("UNDO");
            undoMsg.setStrokeId(targetId);
            sendJsonMessage(undoMsg);
        }
    }

    // Removes the most recent item after a remote undo.
    public void undoRemoteAction(String targetId) {
        if (targetId != null && !targetId.trim().isEmpty()) {
            synchronized (strokes) {
                strokes.removeIf(s -> targetId.equals(s.getStrokeId()));
            }
            synchronized (textElements) {
                textElements.removeIf(t -> targetId.equals(t.getTextId()));
            }
            synchronized (actionHistory) {
                actionHistory.removeIf(a -> targetId.equals(a.id));
            }
        } else if (!actionHistory.isEmpty()) {
            CanvasAction lastAction = actionHistory.remove(actionHistory.size() - 1);
            String tid = lastAction.id;
            synchronized (strokes) {
                strokes.removeIf(s -> tid != null && tid.equals(s.getStrokeId()));
            }
            synchronized (textElements) {
                textElements.removeIf(t -> tid != null && tid.equals(t.getTextId()));
            }
        }
        repaint();
    }

    public void undoRemoteAction() {
        undoRemoteAction(null);
    }

    // Repositions a text element that another client dragged.
    public void moveRemoteText(int oldX, int oldY, int newX, int newY) {
        synchronized (textElements) {
            for (TextElement te : textElements) {
                if (Math.abs(te.getX() - oldX) < 15 && Math.abs(te.getY() - oldY) < 15) {
                    te.setX(newX);
                    te.setY(newY);
                    break;
                }
            }
        }
        repaint();
    }

    // Switches between freehand drawing and text placement.
    public void setDrawingMode(Mode mode) {
        if (mode != null) {
            this.drawingMode = mode;
        }
    }

    // Returns the active input mode.
    public Mode getDrawingMode() {
        return drawingMode;
    }

    // Turns shape snapping on or off.
    public void setAutoNormalize(boolean autoNormalize) {
        this.autoNormalize = autoNormalize;
    }

    // Reports whether shape snapping is on.
    public boolean isAutoNormalize() {
        return autoNormalize;
    }

    // Sets the font size used for new text.
    public void setCurrentFontSize(int fontSize) {
        if (fontSize >= 8 && fontSize <= 100) {
            this.currentFontSize = fontSize;
        }
    }

    // Returns the font size used for new text.
    public int getCurrentFontSize() {
        return currentFontSize;
    }

    // Shows or hides the dotted background grid.
    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
        repaint();
    }

    // Reports whether the grid is visible.
    public boolean isShowGrid() {
        return showGrid;
    }

    // Sets the colour used for new strokes and text.
    public void setDrawingColor(Color color) {
        if (color != null) {
            this.currentColor = color;
        }
    }

    // Returns the current drawing colour.
    public Color getDrawingColor() {
        return currentColor;
    }

    // Sets the pen width used for new strokes.
    public void setStrokeWidth(int width) {
        if (width >= 1) {
            this.currentStrokeWidth = width;
        }
    }

    // Returns the current pen width.
    public int getCurrentStrokeWidth() {
        return currentStrokeWidth;
    }

    // Sets the zoom, clamped to a sane range and rounded to whole percents.
    public void setZoomFactor(double zoom) {

        this.zoomFactor = Math.round(Math.max(0.2, Math.min(4.0, zoom)) * 100) / 100.0;
        setPreferredSize(new Dimension((int) (3000 * zoomFactor), (int) (2000 * zoomFactor)));
        revalidate();
        repaint();
    }

    // Returns the current zoom factor.
    public double getZoomFactor() {
        return zoomFactor;
    }

    // Attaches the client used to broadcast local changes.
    public void setClient(WhiteboardClient client) {
        this.client = client;
    }

    // Sends one message to the server when a client is attached.
    private void sendJsonMessage(NetworkMessage msg) {
        if (client != null) {
            client.sendMessage(gson.toJson(msg));
        }
    }

    // Returns how many strokes are on the canvas.
    public int getStrokeCount() {
        return strokes.size();
    }

    // Returns a read-only view of the strokes.
    public List<Stroke> getStrokes() {
        return java.util.Collections.unmodifiableList(strokes);
    }

    // Returns a read-only view of the text elements.
    public List<TextElement> getTextElements() {
        return java.util.Collections.unmodifiableList(textElements);
    }

    // Encodes the whole canvas as JSON messages for saving.
    public List<String> serializeCanvasState() {
        List<String> list = new ArrayList<>();
        synchronized (strokes) {
            for (Stroke stroke : strokes) {
                if (stroke.getType() == Stroke.ShapeType.FREEHAND) {
                    List<DrawPoint> pts = stroke.getPoints();
                    if (pts.size() > 0) {
                        String sId = java.util.UUID.randomUUID().toString();
                        NetworkMessage start = new NetworkMessage("DRAW_START");
                        start.setStrokeId(sId);
                        start.setX1(pts.get(0).x);
                        start.setY1(pts.get(0).y);
                        start.setColorRgb(stroke.getColor().getRGB());
                        start.setStrokeWidth(stroke.getStrokeWidth());
                        start.setSenderId(client != null ? client.getClientId() : "local");
                        list.add(gson.toJson(start));

                        for (int i = 0; i < pts.size() - 1; i++) {
                            DrawPoint p1 = pts.get(i);
                            DrawPoint p2 = pts.get(i + 1);
                            NetworkMessage line = new NetworkMessage("DRAW_LINE");
                            line.setStrokeId(sId);
                            line.setX1(p1.x);
                            line.setY1(p1.y);
                            line.setX2(p2.x);
                            line.setY2(p2.y);
                            line.setColorRgb(stroke.getColor().getRGB());
                            line.setStrokeWidth(stroke.getStrokeWidth());
                            line.setSenderId(client != null ? client.getClientId() : "local");
                            list.add(gson.toJson(line));
                        }
                        NetworkMessage end = new NetworkMessage("DRAW_END");
                        end.setStrokeId(sId);
                        end.setSenderId(client != null ? client.getClientId() : "local");
                        list.add(gson.toJson(end));
                    }
                } else {
                    String typeStr = "DRAW_LINE";
                    if (stroke.getType() == Stroke.ShapeType.RECTANGLE) typeStr = "DRAW_RECT";
                    else if (stroke.getType() == Stroke.ShapeType.CIRCLE) typeStr = "DRAW_CIRCLE";
                    else if (stroke.getType() == Stroke.ShapeType.TRIANGLE) typeStr = "DRAW_TRI";

                    NetworkMessage shape = new NetworkMessage(typeStr);
                    shape.setStrokeId(java.util.UUID.randomUUID().toString());
                    shape.setX1(stroke.getX1());
                    shape.setY1(stroke.getY1());
                    shape.setX2(stroke.getX2());
                    shape.setY2(stroke.getY2());
                    shape.setColorRgb(stroke.getColor().getRGB());
                    shape.setStrokeWidth(stroke.getStrokeWidth());
                    shape.setSenderId(client != null ? client.getClientId() : "local");
                    list.add(gson.toJson(shape));
                }
            }
        }

        synchronized (textElements) {
            for (TextElement te : textElements) {
                NetworkMessage textMsg = new NetworkMessage("TEXT");
                textMsg.setStrokeId(java.util.UUID.randomUUID().toString());
                textMsg.setX1(te.getX());
                textMsg.setY1(te.getY());
                textMsg.setColorRgb(te.getColor().getRGB());
                textMsg.setFontSize(te.getFontSize());
                textMsg.setText(te.getText());
                textMsg.setSenderId(client != null ? client.getClientId() : "local");
                list.add(gson.toJson(textMsg));
            }
        }

        return list;
    }

    // Renders the canvas contents to a PNG file.
    public void exportToPNG(File file) {

        BufferedImage image = new BufferedImage(3000, 2000, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, 3000, 2000);

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        synchronized (strokes) {
            for (Stroke stroke : strokes) {
                g2d.setColor(stroke.getColor());
                g2d.setStroke(new BasicStroke(
                    stroke.getStrokeWidth(),
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND
                ));

                if (stroke.getType() == Stroke.ShapeType.FREEHAND) {
                    List<DrawPoint> points = stroke.getPoints();

                    if (points.size() == 1) {
                        DrawPoint p = points.get(0);
                        int radius = stroke.getStrokeWidth() / 2;
                        g2d.fillOval(p.x - radius, p.y - radius,
                            stroke.getStrokeWidth(), stroke.getStrokeWidth());
                    }

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

        synchronized (textElements) {
            for (TextElement te : textElements) {
                g2d.setColor(te.getColor());
                g2d.setFont(new Font("Segoe UI", Font.BOLD, te.getFontSize()));
                g2d.drawString(te.getText(), te.getX(), te.getY());
            }
        }

        g2d.dispose();

        try {
            javax.imageio.ImageIO.write(image, "PNG", file);
            System.out.println("[WhiteboardPanel] Exported whiteboard canvas to: " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("[WhiteboardPanel] Export failed: " + e.getMessage());
        }
    }

    // Prompts for one line of text, centred on the window.
    public static String showCustomInputDialog(Component parent, String title, String prompt) {
        return showCustomInputDialog(parent, title, prompt, null);
    }

    // Prompts for one line of text, opening next to the given screen point.
    public static String showCustomInputDialog(Component parent, String title, String prompt, Point anchorOnScreen) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(new Color(51, 65, 85), 2));

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        panel.setBackground(new Color(15, 23, 42));

        JLabel label = new JLabel(prompt);
        label.setFont(new Font("Segoe UI", Font.BOLD, 12));
        label.setForeground(new Color(148, 163, 184));
        panel.add(label, BorderLayout.NORTH);

        JTextField textField = new JTextField(20);
        textField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        textField.setBackground(new Color(30, 41, 59));
        textField.setForeground(Color.WHITE);
        textField.setCaretColor(Color.WHITE);
        textField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(51, 65, 85)),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        panel.add(textField, BorderLayout.CENTER);

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
        okBtn.setBackground(new Color(59, 130, 246));
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

        dialog.getRootPane().setDefaultButton(okBtn);
        dialog.getRootPane().registerKeyboardAction(
            e -> { result[0] = null; dialog.dispose(); },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);

        dialog.addWindowListener(new WindowAdapter() {
            // Puts the caret in the text field as soon as the dialog appears.
            @Override
            public void windowOpened(WindowEvent e) {
                textField.requestFocusInWindow();
            }
        });

        dialog.add(panel);
        dialog.pack();
        placeDialog(dialog, owner, anchorOnScreen);
        dialog.setVisible(true);

        return result[0];
    }

    // Positions the dialog at the anchor point, kept fully on screen.
    private static void placeDialog(JDialog dialog, Window owner, Point anchorOnScreen) {
        if (anchorOnScreen == null) {
            dialog.setLocationRelativeTo(owner);
            return;
        }
        Rectangle screen = owner != null
                ? owner.getGraphicsConfiguration().getBounds()
                : new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        int x = anchorOnScreen.x + 12;
        int y = anchorOnScreen.y + 12;
        x = Math.max(screen.x, Math.min(x, screen.x + screen.width - dialog.getWidth()));
        y = Math.max(screen.y, Math.min(y, screen.y + screen.height - dialog.getHeight()));
        dialog.setLocation(x, y);
    }
}
