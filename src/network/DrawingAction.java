package network;

import java.awt.Color;
import java.util.List;
import com.google.gson.Gson;

/**
 * A unified action model that wraps every discrete user action on the
 * whiteboard canvas.  Instances are immutable after construction.
 */
public class DrawingAction {

    public enum Type {
        STROKE,
        TEXT,
        MOVE_TEXT,
        CLEAR_CANVAS,
        UNDO,
        BLOCK_SLANG
    }

    private final Type type;
    private final String clientId;
    private final long timestamp;

    /** Non-null only when {@code type == STROKE}. */
    private final WhiteboardPanel.Stroke stroke;

    /** Non-null only when {@code type == TEXT}. */
    private final WhiteboardPanel.TextElement textElement;

    /** Coordinates used when {@code type == MOVE_TEXT}. */
    private final int oldX;
    private final int oldY;
    private final int newX;
    private final int newY;

    /** Free-form payload, e.g. the blocked word for {@code BLOCK_SLANG}. */
    private final String extraData;

    public DrawingAction(Type type, String clientId) {
        this.type = type;
        this.clientId = clientId;
        this.timestamp = System.currentTimeMillis();
        this.stroke = null;
        this.textElement = null;
        this.oldX = 0;
        this.oldY = 0;
        this.newX = 0;
        this.newY = 0;
        this.extraData = null;
    }

    public DrawingAction(Type type, String clientId, WhiteboardPanel.Stroke stroke) {
        this.type = type;
        this.clientId = clientId;
        this.timestamp = System.currentTimeMillis();
        this.stroke = stroke;
        this.textElement = null;
        this.oldX = 0;
        this.oldY = 0;
        this.newX = 0;
        this.newY = 0;
        this.extraData = null;
    }

    public DrawingAction(Type type, String clientId, WhiteboardPanel.TextElement textElement) {
        this.type = type;
        this.clientId = clientId;
        this.timestamp = System.currentTimeMillis();
        this.stroke = null;
        this.textElement = textElement;
        this.oldX = 0;
        this.oldY = 0;
        this.newX = 0;
        this.newY = 0;
        this.extraData = null;
    }

    public DrawingAction(Type type, String clientId, int oldX, int oldY, int newX, int newY) {
        this.type = type;
        this.clientId = clientId;
        this.timestamp = System.currentTimeMillis();
        this.stroke = null;
        this.textElement = null;
        this.oldX = oldX;
        this.oldY = oldY;
        this.newX = newX;
        this.newY = newY;
        this.extraData = null;
    }

    public DrawingAction(Type type, String clientId, String extraData) {
        this.type = type;
        this.clientId = clientId;
        this.timestamp = System.currentTimeMillis();
        this.stroke = null;
        this.textElement = null;
        this.oldX = 0;
        this.oldY = 0;
        this.newX = 0;
        this.newY = 0;
        this.extraData = extraData;
    }

    public Type getType() {
        return type;
    }

    public String getClientId() {
        return clientId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public WhiteboardPanel.Stroke getStroke() {
        return stroke;
    }

    public WhiteboardPanel.TextElement getTextElement() {
        return textElement;
    }

    public int getOldX() {
        return oldX;
    }

    public int getOldY() {
        return oldY;
    }

    public int getNewX() {
        return newX;
    }

    public int getNewY() {
        return newY;
    }

    public String getExtraData() {
        return extraData;
    }

    /**
     * Serializes this action into a JSON string using NetworkMessage.
     */
    public String toMessage() {
        Gson gson = new Gson();
        NetworkMessage msg = new NetworkMessage();
        msg.setType(type.name());
        msg.setSenderId(clientId);

        switch (type) {
            case STROKE:
                if (stroke == null) return null;
                msg.setStrokeWidth(stroke.getStrokeWidth());
                msg.setColorRgb(stroke.getColor().getRGB());
                
                if (stroke.getType() == WhiteboardPanel.Stroke.ShapeType.FREEHAND) {
                    List<WhiteboardPanel.DrawPoint> pts = stroke.getPoints();
                    if (pts != null && pts.size() >= 2) {
                        WhiteboardPanel.DrawPoint p1 = pts.get(0);
                        WhiteboardPanel.DrawPoint p2 = pts.get(1);
                        msg.setType("DRAW_LINE");
                        msg.setX1(p1.x);
                        msg.setY1(p1.y);
                        msg.setX2(p2.x);
                        msg.setY2(p2.y);
                    } else {
                        return null;
                    }
                } else {
                    if (stroke.getType() == WhiteboardPanel.Stroke.ShapeType.LINE) msg.setType("DRAW_LINE");
                    else if (stroke.getType() == WhiteboardPanel.Stroke.ShapeType.RECTANGLE) msg.setType("DRAW_RECT");
                    else if (stroke.getType() == WhiteboardPanel.Stroke.ShapeType.CIRCLE) msg.setType("DRAW_CIRCLE");
                    else if (stroke.getType() == WhiteboardPanel.Stroke.ShapeType.TRIANGLE) msg.setType("DRAW_TRI");
                    
                    msg.setX1(stroke.getX1());
                    msg.setY1(stroke.getY1());
                    msg.setX2(stroke.getX2()); // w is serialized to x2
                    msg.setY2(stroke.getY2()); // h is serialized to y2
                }
                break;

            case TEXT:
                if (textElement == null) return null;
                msg.setX1(textElement.getX());
                msg.setY1(textElement.getY());
                msg.setColorRgb(textElement.getColor().getRGB());
                msg.setFontSize(textElement.getFontSize());
                msg.setText(textElement.getText());
                break;

            case MOVE_TEXT:
                msg.setX1(oldX);
                msg.setY1(oldY);
                msg.setX2(newX);
                msg.setY2(newY);
                break;

            case CLEAR_CANVAS:
            case UNDO:
                break;

            case BLOCK_SLANG:
                msg.setText(extraData);
                break;

            default:
                return null;
        }

        return gson.toJson(msg);
    }

    /**
     * Parses a JSON message into a DrawingAction.
     */
    public static DrawingAction fromMessage(String jsonMessage, String clientId) {
        if (jsonMessage == null || jsonMessage.isEmpty()) {
            return null;
        }

        Gson gson = new Gson();
        try {
            NetworkMessage msg = gson.fromJson(jsonMessage, NetworkMessage.class);
            if (msg == null || msg.getType() == null) {
                return null;
            }

            String type = msg.getType().toUpperCase();

            switch (type) {
                case "DRAW_LINE": {
                    int x1 = msg.getX1();
                    int y1 = msg.getY1();
                    int x2 = msg.getX2();
                    int y2 = msg.getY2();
                    Color color = new Color(msg.getColorRgb());
                    int sw = msg.getStrokeWidth();
                    WhiteboardPanel.Stroke stroke = new WhiteboardPanel.Stroke(color, sw);
                    stroke.addPoint(x1, y1);
                    stroke.addPoint(x2, y2);
                    return new DrawingAction(Type.STROKE, clientId, stroke);
                }

                case "DRAW_RECT":
                case "DRAW_CIRCLE":
                case "DRAW_TRI": {
                    int x = msg.getX1();
                    int y = msg.getY1();
                    int w = msg.getX2();
                    int h = msg.getY2();
                    Color color = new Color(msg.getColorRgb());
                    int sw = msg.getStrokeWidth();
                    WhiteboardPanel.Stroke.ShapeType shapeType = WhiteboardPanel.Stroke.ShapeType.RECTANGLE;
                    if ("DRAW_CIRCLE".equals(type)) shapeType = WhiteboardPanel.Stroke.ShapeType.CIRCLE;
                    else if ("DRAW_TRI".equals(type)) shapeType = WhiteboardPanel.Stroke.ShapeType.TRIANGLE;

                    WhiteboardPanel.Stroke stroke = new WhiteboardPanel.Stroke(color, sw, shapeType, x, y, w, h);
                    return new DrawingAction(Type.STROKE, clientId, stroke);
                }

                case "TEXT": {
                    int x = msg.getX1();
                    int y = msg.getY1();
                    Color color = new Color(msg.getColorRgb());
                    int fontSize = msg.getFontSize();
                    String text = msg.getText();
                    WhiteboardPanel.TextElement te = new WhiteboardPanel.TextElement(text, x, y, color, fontSize);
                    return new DrawingAction(Type.TEXT, clientId, te);
                }

                case "MOVE_TEXT": {
                    int oldX = msg.getX1();
                    int oldY = msg.getY1();
                    int newX = msg.getX2();
                    int newY = msg.getY2();
                    return new DrawingAction(Type.MOVE_TEXT, clientId, oldX, oldY, newX, newY);
                }

                case "CLEAR_CANVAS":
                    return new DrawingAction(Type.CLEAR_CANVAS, clientId);

                case "UNDO":
                    return new DrawingAction(Type.UNDO, clientId);

                case "BLOCK_SLANG":
                    return new DrawingAction(Type.BLOCK_SLANG, clientId, msg.getText());

                default:
                    return null;
            }
        } catch (Exception e) {
            System.err.println("[DrawingAction] Error parsing message: " + e.getMessage());
            return null;
        }
    }

    @Override
    public String toString() {
        return "DrawingAction[" + type + " by " + clientId + " at " + timestamp + "]";
    }
}
