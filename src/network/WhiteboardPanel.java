package network;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import com.google.gson.Gson;

public class WhiteboardPanel extends JPanel {

    public static class DrawPoint {
        public final int x;
        public final int y;

        public DrawPoint(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public static class Stroke {
        public enum ShapeType { FREEHAND, LINE, RECTANGLE, CIRCLE, TRIANGLE }

        private List<DrawPoint> points;
        private Color color;
        private int strokeWidth;
        private ShapeType type = ShapeType.FREEHAND;
        private int x1, y1, x2, y2;

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

        public void addPoint(int x, int y) {
            points.add(new DrawPoint(x, y));
        }

        public List<DrawPoint> getPoints() {
            return points;
        }

        public Color getColor() {
            return color;
        }

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

    private final List<String> actionHistory = java.util.Collections.synchronizedList(new ArrayList<>());
    private final Gson gson = new Gson();

    public WhiteboardPanel() {
        setBackground(Color.WHITE);
        // Spacious scrollable canvas size (3000 x 2000)
        setPreferredSize(new Dimension(3000, 2000));

        strokes = java.util.Collections.synchronizedList(new ArrayList<>());
        textElements = java.util.Collections.synchronizedList(new ArrayList<>());

        currentColor = Color.BLACK;
        currentStrokeWidth = 3;
        currentStroke = null;
        client = null;

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // Map coordinates using zoomFactor
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
                        "Text Tool", "Enter text to place on the whiteboard:");
                    if (text != null && !text.trim().isEmpty()) {
                        String moderatedText = ContentModerator.moderateText(text);

                        Color textColor = currentColor;
                        if (textColor.equals(Color.WHITE)) {
                            textColor = Color.BLACK;
                        }

                        TextElement te = new TextElement(moderatedText, mouseX, mouseY, textColor, currentFontSize);
                        textElements.add(te);
                        actionHistory.add("T");

                        NetworkMessage textMsg = new NetworkMessage("TEXT");
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

                currentStroke = new Stroke(currentColor, currentStrokeWidth);
                currentStroke.addPoint(mouseX, mouseY);
                strokes.add(currentStroke);

                if (!autoNormalize) {
                    NetworkMessage startMsg = new NetworkMessage("DRAW_START");
                    startMsg.setX1(mouseX);
                    startMsg.setY1(mouseY);
                    startMsg.setColorRgb(currentColor.getRGB());
                    startMsg.setStrokeWidth(currentStrokeWidth);
                    sendJsonMessage(startMsg);
                }

                repaint();
            }

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
                            Stroke normLine = new Stroke(currentColor, currentStrokeWidth, Stroke.ShapeType.LINE, ns.x1, ns.y1, ns.x2, ns.y2);
                            strokes.add(normLine);
                            actionHistory.add("S");
                            
                            NetworkMessage lineMsg = new NetworkMessage("DRAW_LINE");
                            lineMsg.setX1(ns.x1);
                            lineMsg.setY1(ns.y1);
                            lineMsg.setX2(ns.x2);
                            lineMsg.setY2(ns.y2);
                            lineMsg.setColorRgb(currentColor.getRGB());
                            lineMsg.setStrokeWidth(currentStrokeWidth);
                            sendJsonMessage(lineMsg);
                        } else if (ns.type == ShapeRecognizer.NormalizedShape.Type.RECTANGLE) {
                            Stroke normRect = new Stroke(currentColor, currentStrokeWidth, Stroke.ShapeType.RECTANGLE, ns.x1, ns.y1, ns.x2, ns.y2);
                            strokes.add(normRect);
                            actionHistory.add("S");

                            NetworkMessage rectMsg = new NetworkMessage("DRAW_RECT");
                            rectMsg.setX1(ns.x1);
                            rectMsg.setY1(ns.y1);
                            rectMsg.setX2(ns.x2);
                            rectMsg.setY2(ns.y2);
                            rectMsg.setColorRgb(currentColor.getRGB());
                            rectMsg.setStrokeWidth(currentStrokeWidth);
                            sendJsonMessage(rectMsg);
                        } else if (ns.type == ShapeRecognizer.NormalizedShape.Type.CIRCLE) {
                            Stroke normCircle = new Stroke(currentColor, currentStrokeWidth, Stroke.ShapeType.CIRCLE, ns.x1, ns.y1, ns.x2, ns.y2);
                            strokes.add(normCircle);
                            actionHistory.add("S");

                            NetworkMessage circleMsg = new NetworkMessage("DRAW_CIRCLE");
                            circleMsg.setX1(ns.x1);
                            circleMsg.setY1(ns.y1);
                            circleMsg.setX2(ns.x2);
                            circleMsg.setY2(ns.y2);
                            circleMsg.setColorRgb(currentColor.getRGB());
                            circleMsg.setStrokeWidth(currentStrokeWidth);
                            sendJsonMessage(circleMsg);
                        } else if (ns.type == ShapeRecognizer.NormalizedShape.Type.TRIANGLE) {
                            Stroke normTri = new Stroke(currentColor, currentStrokeWidth, Stroke.ShapeType.TRIANGLE, ns.x1, ns.y1, ns.x2, ns.y2);
                            strokes.add(normTri);
                            actionHistory.add("S");

                            NetworkMessage triMsg = new NetworkMessage("DRAW_TRI");
                            triMsg.setX1(ns.x1);
                            triMsg.setY1(ns.y1);
                            triMsg.setX2(ns.x2);
                            triMsg.setY2(ns.y2);
                            triMsg.setColorRgb(currentColor.getRGB());
                            triMsg.setStrokeWidth(currentStrokeWidth);
                            sendJsonMessage(triMsg);
                        } else {
                            strokes.add(currentStroke);
                            actionHistory.add("S");
                            List<DrawPoint> pts = currentStroke.getPoints();
                            if (pts.size() > 0) {
                                NetworkMessage startMsg = new NetworkMessage("DRAW_START");
                                startMsg.setX1(pts.get(0).x);
                                startMsg.setY1(pts.get(0).y);
                                startMsg.setColorRgb(currentColor.getRGB());
                                startMsg.setStrokeWidth(currentStrokeWidth);
                                sendJsonMessage(startMsg);

                                for (int i = 0; i < pts.size() - 1; i++) {
                                    DrawPoint p1 = pts.get(i);
                                    DrawPoint p2 = pts.get(i + 1);
                                    NetworkMessage lineMsg = new NetworkMessage("DRAW_LINE");
                                    lineMsg.setX1(p1.x);
                                    lineMsg.setY1(p1.y);
                                    lineMsg.setX2(p2.x);
                                    lineMsg.setY2(p2.y);
                                    lineMsg.setColorRgb(currentColor.getRGB());
                                    lineMsg.setStrokeWidth(currentStrokeWidth);
                                    sendJsonMessage(lineMsg);
                                }
                                sendJsonMessage(new NetworkMessage("DRAW_END"));
                            }
                        }
                    } else {
                        sendJsonMessage(new NetworkMessage("DRAW_END"));
                        actionHistory.add("S");
                    }

                    currentStroke = null;
                    repaint();
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
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

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Apply scale transformation for zooming
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

    public void addRemoteStroke(Stroke stroke) {
        if (stroke != null) {
            strokes.add(stroke);
            repaint();
        }
    }

    public void addRemoteLine(int x1, int y1, int x2, int y2, Color color, int lineWidth) {
        Stroke segment = new Stroke(color, lineWidth);
        segment.addPoint(x1, y1);
        segment.addPoint(x2, y2);
        strokes.add(segment);
        actionHistory.add("S");
        repaint();
    }

    public void clearCanvas() {
        strokes.clear();
        textElements.clear();
        actionHistory.clear();
        repaint();
        System.out.println("[WhiteboardPanel] Canvas cleared.");
    }

    public void addRemoteShape(Stroke.ShapeType type, int x, int y, int w, int h, Color color, int strokeWidth) {
        Stroke shape = new Stroke(color, strokeWidth, type, x, y, w, h);
        strokes.add(shape);
        actionHistory.add("S");
        repaint();
    }

    public void addRemoteText(String text, int x, int y, Color color, int fontSize) {
        TextElement te = new TextElement(text, x, y, color, fontSize);
        textElements.add(te);
        actionHistory.add("T");
        repaint();
    }

    public void addLocalTextElement(String text, int x, int y, Color color, int fontSize) {
        TextElement te = new TextElement(text, x, y, color, fontSize);
        textElements.add(te);
        actionHistory.add("T");

        NetworkMessage textMsg = new NetworkMessage("TEXT");
        textMsg.setX1(x);
        textMsg.setY1(y);
        textMsg.setColorRgb(color.getRGB());
        textMsg.setFontSize(fontSize);
        textMsg.setText(text);
        sendJsonMessage(textMsg);

        repaint();
    }

    public void undoLastAction() {
        if (!actionHistory.isEmpty()) {
            String lastAction = actionHistory.remove(actionHistory.size() - 1);
            if ("S".equals(lastAction) && !strokes.isEmpty()) {
                strokes.remove(strokes.size() - 1);
            } else if ("T".equals(lastAction) && !textElements.isEmpty()) {
                textElements.remove(textElements.size() - 1);
            }
            repaint();
            sendJsonMessage(new NetworkMessage("UNDO"));
        }
    }

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

    public void setDrawingMode(Mode mode) {
        if (mode != null) {
            this.drawingMode = mode;
        }
    }

    public Mode getDrawingMode() {
        return drawingMode;
    }

    public void setAutoNormalize(boolean autoNormalize) {
        this.autoNormalize = autoNormalize;
    }

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

    public void setDrawingColor(Color color) {
        if (color != null) {
            this.currentColor = color;
        }
    }

    public Color getDrawingColor() {
        return currentColor;
    }

    public void setStrokeWidth(int width) {
        if (width >= 1) {
            this.currentStrokeWidth = width;
        }
    }

    public int getCurrentStrokeWidth() {
        return currentStrokeWidth;
    }

    public void setZoomFactor(double zoom) {
        // Range clamp between 20% and 400%
        this.zoomFactor = Math.max(0.2, Math.min(4.0, zoom));
        setPreferredSize(new Dimension((int) (3000 * zoomFactor), (int) (2000 * zoomFactor)));
        revalidate();
        repaint();
    }

    public double getZoomFactor() {
        return zoomFactor;
    }

    public void setClient(WhiteboardClient client) {
        this.client = client;
    }

    private void sendJsonMessage(NetworkMessage msg) {
        if (client != null) {
            client.sendMessage(gson.toJson(msg));
        }
    }

    public int getStrokeCount() {
        return strokes.size();
    }

    public List<Stroke> getStrokes() {
        return java.util.Collections.unmodifiableList(strokes);
    }

    public List<String> serializeCanvasState() {
        List<String> list = new ArrayList<>();
        synchronized (strokes) {
            for (Stroke stroke : strokes) {
                if (stroke.getType() == Stroke.ShapeType.FREEHAND) {
                    List<DrawPoint> pts = stroke.getPoints();
                    if (pts.size() > 0) {
                        NetworkMessage start = new NetworkMessage("DRAW_START");
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
                        end.setSenderId(client != null ? client.getClientId() : "local");
                        list.add(gson.toJson(end));
                    }
                } else {
                    String typeStr = "DRAW_LINE";
                    if (stroke.getType() == Stroke.ShapeType.RECTANGLE) typeStr = "DRAW_RECT";
                    else if (stroke.getType() == Stroke.ShapeType.CIRCLE) typeStr = "DRAW_CIRCLE";
                    else if (stroke.getType() == Stroke.ShapeType.TRIANGLE) typeStr = "DRAW_TRI";
                    
                    NetworkMessage shape = new NetworkMessage(typeStr);
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
                NetworkMessage text = new NetworkMessage("TEXT");
                text.setX1(te.getX());
                text.setY1(te.getY());
                text.setColorRgb(te.getColor().getRGB());
                text.setFontSize(te.getFontSize());
                text.setText(te.getText());
                text.setSenderId(client != null ? client.getClientId() : "local");
                list.add(gson.toJson(text));
            }
        }
        return list;
    }

    public void exportToPNG(File file) {
        // Create an unscaled high-resolution image of the entire whiteboard space
        BufferedImage image = new BufferedImage(3000, 2000, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // Render pure white background
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, 3000, 2000);

        // Rendering presets
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Render strokes
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

        // Render text
        synchronized (textElements) {
            for (TextElement te : textElements) {
                g2d.setColor(te.getColor());
                g2d.setFont(new Font("Segoe UI", Font.BOLD, te.getFontSize()));
                g2d.drawString(te.getText(), te.getX(), te.getY());
            }
        }

        g2d.dispose();

        // Write image file
        try {
            javax.imageio.ImageIO.write(image, "PNG", file);
            System.out.println("[WhiteboardPanel] Exported whiteboard canvas to: " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("[WhiteboardPanel] Export failed: " + e.getMessage());
        }
    }

    public static String showCustomInputDialog(Component parent, String title, String prompt) {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(parent), title, true);
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

        dialog.add(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);

        return result[0];
    }
}
